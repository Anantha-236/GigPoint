<#
Automatically commits saved project changes after inactivity and retries pushes.
Run: powershell -ExecutionPolicy Bypass -File .\AutoSync-GitHub.ps1 -Background
Logs: git rev-parse --git-path autosync.log (and autosync-error.log).
Ignored/generated/private files are excluded. Submodule contents belong to their
own repository and must be committed/pushed there separately.
No force-push, automatic pull/rebase, or startup-task installation is performed.
#>
[CmdletBinding()]
param(
    [ValidateRange(1, 3600)][int]$CheckEverySeconds = 2,
    [ValidateRange(1, 3600)][int]$IdleSeconds = 5,
    [ValidateRange(5, 86400)][int]$RetrySeconds = 30,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._/-]*$')][string]$Remote = "origin",
    [switch]$Background,
    [switch]$Once
)

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
Set-Location -LiteralPath $ProjectRoot

function Invoke-Git {
    param([string[]]$GitArgs)
    # PowerShell 5.1 treats redirected native stderr as errors under Stop.
    $ErrorActionPreference = 'Continue'
    $output = @(& git -c core.quotepath=false @GitArgs 2>&1)
    $code = $LASTEXITCODE
    if ($code -ne 0) { throw "git $($GitArgs[0]) failed (exit $code): $($output -join ' ')" }
    return ($output | ForEach-Object { "$_" })
}

Get-Command git -ErrorAction Stop | Out-Null
$GitDir = (Invoke-Git @('rev-parse', '--absolute-git-dir') | Select-Object -Last 1)
Invoke-Git @('remote', 'get-url', $Remote) | Out-Null
foreach ($key in @('user.name', 'user.email')) {
    if (-not (Invoke-Git @('config', $key))) { throw "Configure git $key first." }
}

if ($Background) {
    if ($Once) { throw 'Choose either -Background or -Once.' }
    $arguments = '-NoProfile -ExecutionPolicy Bypass -File "{0}" -CheckEverySeconds {1} -IdleSeconds {2} -RetrySeconds {3} -Remote "{4}"' -f $PSCommandPath, $CheckEverySeconds, $IdleSeconds, $RetrySeconds, $Remote
    $process = Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList $arguments -WindowStyle Hidden -WorkingDirectory $ProjectRoot -RedirectStandardOutput (Join-Path $GitDir 'autosync.log') -RedirectStandardError (Join-Path $GitDir 'autosync-error.log') -PassThru
    Write-Host "Started background worker PID $($process.Id). See $GitDir\autosync.log for status."
    Write-Host "Stop with: Stop-Process -Id $($process.Id)"
    return
}

$BlockedFilePatterns = @(

    # Environment files
    '(^|/)\.env$',
    '(^|/)\.env\.',

    # Android local SDK configuration
    '(^|/)local\.properties$',

    # Signing material
    '\.jks$',
    '\.keystore$',
    '\.pem$',
    '\.p12$',

    # Firebase private configuration
    '(^|/)google-services\.json$',

    # Temporary audits
    '^audit-[^/]+/',

    # Build/generated files
    '(^|/)build/',
    '(^|/)\.cxx/',
    '(^|/)\.externalNativeBuild/',

    # Gradle generated state
    '(^|/)\.gradle/',

    # Node dependencies
    '(^|/)node_modules/',

    # Supabase local generated files
    '(^|/)supabase/\.temp/',
    '(^|/)supabase/\.branches/',

    # Android Studio machine-local state
    '^\.idea/caches/',
    '^\.idea/libraries/',
    '^\.idea/shelf/',
    '^\.idea/workspace\.xml$',
    '^\.idea/modules\.xml$',
    '^\.idea/navEditor\.xml$',
    '^\.idea/assetWizardSettings\.xml$'
)


# ============================================================
# REAL SECRET PATTERNS
#
# These match actual secret VALUE formats.
#
# We deliberately do NOT scan for mere words like:
#
# service_role
# SUPABASE_SERVICE_ROLE_KEY
# PRIVATE_KEY
#
# because those can safely exist as source-code variable names.
# ============================================================

$SecretPatterns = @(

    # Supabase secret key
    'sb_secret_[A-Za-z0-9_-]{20,}',

    # Common live secret-key style
    'sk_live_[A-Za-z0-9_-]{15,}',

    # AWS Access Key ID
    'AKIA[0-9A-Z]{16}',

    # PEM/private key material
    '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----'
)


function Test-Blocked([string]$File) {
    foreach ($pattern in $BlockedFilePatterns) {
        if ($File -match $pattern) { return $true }
    }
    return $false
}

function Get-GitPaths([string[]]$GitArgs) {
    # NUL separation preserves spaces, Unicode, and unusual file names.
    $raw = (Invoke-Git $GitArgs) -join "`n"
    return @($raw.Split([char]0) | Where-Object { $_.Length -gt 0 })
}

function Get-Snapshot {
    $paths = @(Get-GitPaths @('ls-files', '-z', '--cached', '--others', '--exclude-standard') | Sort-Object -Unique)
    $rows = foreach ($file in $paths) {
        if (Test-Blocked $file) { continue }
        if (Test-Path -LiteralPath $file -PathType Leaf) {
            # Content hashes also catch whitespace and same-length edits.
            $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
            "$file`0$hash"
        } elseif (Test-Path -LiteralPath $file -PathType Container) {
            # Gitlinks are updated only after a commit inside the submodule.
            "$file`0$(Invoke-Git @('-C', $file, 'rev-parse', 'HEAD'))"
        } else { "$file`0DELETED" }
    }
    $branch = Invoke-Git @('symbolic-ref', '--quiet', '--short', 'HEAD')
    return "$branch`n$($rows -join "`n")"
}

function Assert-Ready {
    foreach ($marker in @('index.lock', 'HEAD.lock', 'MERGE_HEAD', 'CHERRY_PICK_HEAD', 'REVERT_HEAD', 'rebase-merge', 'rebase-apply', 'sequencer')) {
        $path = Invoke-Git @('rev-parse', '--git-path', $marker)
        if (Test-Path -LiteralPath $path) { throw "Git operation active ($marker); waiting." }
    }
    if (Get-GitPaths @('diff', '--name-only', '-z', '--diff-filter=U')) {
        throw 'Unresolved conflicts; waiting for manual resolution.'
    }
}

function Sync-Project {
    Assert-Ready
    $branch = (Invoke-Git @('symbolic-ref', '--quiet', '--short', 'HEAD') | Select-Object -Last 1)
    # Exclude protected paths before staging. Never reset the user's index.
    $paths = @(Get-GitPaths @('ls-files', '-z', '--cached', '--others', '--exclude-standard') | Sort-Object -Unique)
    $specs = @('.')
    foreach ($file in $paths) {
        if (Test-Blocked $file) { $specs += ":(exclude,literal)$file" }
    }
    Invoke-Git (@('add', '-A', '--') + $specs) | Out-Null
    $staged = @(Get-GitPaths @('diff', '--cached', '--name-only', '-z'))
    foreach ($file in $staged) {
        if (Test-Blocked $file) { throw "Protected file already staged: $file. Unstage it manually to resume." }
    }
    $scannable = @(Get-GitPaths @('diff', '--cached', '--name-only', '-z', '--diff-filter=ACMR'))
    foreach ($file in $scannable) {
        # Scan the exact staged blob, including the sync script itself.
        $content = (Invoke-Git @('show', ":$file")) -join "`n"
        foreach ($pattern in $SecretPatterns) {
            if ($content -match $pattern) { throw "Possible secret in staged file: $file. Sync paused; no secret value logged." }
        }
    }
    if ($staged.Count -gt 0) {
        Invoke-Git @('commit', '-m', "Auto sync: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')") | Out-Host
    }
    # Always attempt the push, even when the working tree is clean. This retries
    # commits left behind by an earlier network/authentication/push failure.
    Invoke-Git @('push', '--porcelain', $Remote, "HEAD:refs/heads/$branch") | Out-Host
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Sync completed on $branch."
}

# Exclusive file handle prevents duplicate workers across terminal sessions.
$lock = $null
$oldPrompt = $env:GIT_TERMINAL_PROMPT
$oldInteractive = $env:GCM_INTERACTIVE
try {
    try {
        $lock = [IO.File]::Open((Join-Path $GitDir 'autosync.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    } catch { throw 'Another auto-sync worker is running, or its lock cannot be opened.' }
    $env:GIT_TERMINAL_PROMPT = '0'
    $env:GCM_INTERACTIVE = 'Never'
    Write-Host "Watching $ProjectRoot; poll ${CheckEverySeconds}s, idle ${IdleSeconds}s, retry ${RetrySeconds}s."
    Write-Host 'Only saved, Git-eligible changes are published. Ctrl+C stops foreground mode.'
    if ($Once) { Sync-Project; return }
    $previous = $null
    $lastChange = [DateTime]::UtcNow
    $nextAttempt = [DateTime]::MinValue
    while ($true) {
        try {
            $snapshot = Get-Snapshot
            $now = [DateTime]::UtcNow
            if ($snapshot -cne $previous) {
                $previous = $snapshot
                $lastChange = $now
                $nextAttempt = [DateTime]::MinValue
            }
            if (($now - $lastChange).TotalSeconds -ge $IdleSeconds -and $now -ge $nextAttempt) {
                # Retry periodically even if there are no further edits.
                try {
                    Sync-Project
                    $nextAttempt = [DateTime]::UtcNow.AddSeconds($RetrySeconds)
                } catch {
                    $nextAttempt = [DateTime]::UtcNow.AddSeconds($RetrySeconds)
                    throw
                }
            }
        } catch {
            Write-Warning "[$(Get-Date -Format 'HH:mm:ss')] $($_.Exception.Message)"
        }
        Start-Sleep -Seconds $CheckEverySeconds
    }
} finally {
    $env:GIT_TERMINAL_PROMPT = $oldPrompt
    $env:GCM_INTERACTIVE = $oldInteractive
    if ($null -ne $lock) { $lock.Dispose() }
}
