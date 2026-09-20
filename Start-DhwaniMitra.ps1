param(
    [switch]$Build,
    [switch]$Install,
    [switch]$OpenStudio,
    [switch]$NoFunctions,
    [switch]$NoLaunch,
    [string]$DeviceSerial = ""
)

$ErrorActionPreference = "Stop"

function Write-Step($message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}
function Write-Ok($message) {
    Write-Host "[OK] $message" -ForegroundColor Green
}
function Write-Warn($message) {
    Write-Host "[WARN] $message" -ForegroundColor Yellow
}
function Write-Fail($message) {
    Write-Host "[FAIL] $message" -ForegroundColor Red
}
function Find-Adb {
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }

    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
        (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"),
        (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe")
    ) | Where-Object { $_ -and (Test-Path $_) }

    if ($candidates.Count -gt 0) { return $candidates[0] }
    return $null
}
function Test-TcpPort([string]$HostName, [int]$Port) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        $connected = $async.AsyncWaitHandle.WaitOne(1500, $false)
        if ($connected -and $client.Connected) {
            $client.EndConnect($async)
            $client.Close()
            return $true
        }
        $client.Close()
        return $false
    } catch {
        return $false
    }
}

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BackendRoot = Join-Path $ProjectRoot "backend"
$SupabaseConfig = Join-Path $BackendRoot "supabase\config.toml"
$AndroidSupabaseXml = Join-Path $ProjectRoot "app\src\main\res\values\supabase.xml"
$Gradlew = Join-Path $ProjectRoot "gradlew.bat"
$DebugApk = Join-Path $ProjectRoot "app\build\outputs\apk\debug\app-debug.apk"

$PackageName = "com.example.gigpoint"
$LauncherActivity = ".LauncherActivity"
$ApiPort = 55321
$StudioPort = 55323

Write-Host ""
Write-Host "============================================" -ForegroundColor DarkGreen
Write-Host "       DhwaniMitra Development Launcher     " -ForegroundColor Green
Write-Host "============================================" -ForegroundColor DarkGreen
Write-Host "Project : $ProjectRoot"
Write-Host "Backend : $BackendRoot"

Write-Step "Checking project structure"
if (-not (Test-Path $BackendRoot)) {
    Write-Fail "Backend folder not found: $BackendRoot"
    exit 1
}
if (-not (Test-Path $SupabaseConfig)) {
    Write-Fail "Supabase config not found: $SupabaseConfig"
    exit 1
}
if (-not (Test-Path (Join-Path $BackendRoot "package.json"))) {
    Write-Fail "backend\package.json is missing."
    exit 1
}
if (-not (Test-Path (Join-Path $ProjectRoot "app"))) {
    Write-Fail "Android app folder is missing."
    exit 1
}
Write-Ok "Project structure looks correct."

Write-Step "Checking Docker"
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Fail "Docker CLI is not available. Start/install Docker Desktop first."
    exit 1
}
try {
    & docker info *> $null
    Write-Ok "Docker daemon is running."
} catch {
    Write-Fail "Docker Desktop is installed but the Docker daemon is not running."
    Write-Host "Start Docker Desktop and run this launcher again."
    exit 1
}

Write-Step "Checking local Supabase backend"
Push-Location $BackendRoot
try {
    $statusOutput = (& npx supabase status 2>&1 | Out-String)
    $statusExit = $LASTEXITCODE

    if ($statusExit -ne 0 -or $statusOutput -notmatch "local development setup is running") {
        Write-Warn "Supabase is not running. Starting it now..."
        & npx supabase start
        if ($LASTEXITCODE -ne 0) {
            throw "Supabase failed to start."
        }
        $statusOutput = (& npx supabase status 2>&1 | Out-String)
    } else {
        Write-Ok "Supabase local development setup is already running."
    }
} finally {
    Pop-Location
}

if (-not (Test-TcpPort "127.0.0.1" $ApiPort)) {
    Write-Fail "Supabase API is not reachable on 127.0.0.1:$ApiPort."
    exit 1
}
Write-Ok "Supabase API is reachable on port $ApiPort."

Write-Step "Reading Supabase client configuration"
$projectUrl = $null
$publishableKey = $null

if ($statusOutput -match "Project URL\s+[│|]?\s*(http://[^\s│]+)") {
    $projectUrl = $Matches[1].Trim()
}
if ($statusOutput -match "Publishable\s+[│|]?\s*(sb_publishable_[^\s│]+)") {
    $publishableKey = $Matches[1].Trim()
}
if (-not $projectUrl) {
    $projectUrl = "http://127.0.0.1:$ApiPort"
}
if (-not $publishableKey) {
    Write-Fail "Could not extract the local Supabase publishable key."
    exit 1
}
Write-Ok "Project URL: $projectUrl"
Write-Ok "Publishable key detected."

Write-Step "Configuring Android Supabase client"
$xmlDirectory = Split-Path -Parent $AndroidSupabaseXml
if (-not (Test-Path $xmlDirectory)) {
    New-Item -ItemType Directory -Force -Path $xmlDirectory | Out-Null
}
$escapedUrl = [System.Security.SecurityElement]::Escape($projectUrl)
$escapedKey = [System.Security.SecurityElement]::Escape($publishableKey)

$supabaseXmlContent = @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="supabase_url" translatable="false">$escapedUrl</string>
    <string name="supabase_publishable_key" translatable="false">$escapedKey</string>
</resources>
"@

Set-Content -LiteralPath $AndroidSupabaseXml -Value $supabaseXmlContent -Encoding UTF8
Write-Ok "Updated: app\src\main\res\values\supabase.xml"

$functionsPath = Join-Path $BackendRoot "supabase\functions"
if (-not $NoFunctions -and (Test-Path $functionsPath)) {
    $functionDirectories = @(Get-ChildItem $functionsPath -Directory -ErrorAction SilentlyContinue |
        Where-Object { -not $_.Name.StartsWith("_") })

    if ($functionDirectories.Count -gt 0) {
        Write-Step "Checking local Edge Functions"
        $deleteAccountDir = Join-Path $functionsPath "delete-account"
        $functionsNeedServe = $true

        if (Test-Path $deleteAccountDir) {
            try {
                $response = Invoke-WebRequest `
                    -Uri "$projectUrl/functions/v1/delete-account" `
                    -Method Options `
                    -TimeoutSec 2 `
                    -UseBasicParsing `
                    -ErrorAction Stop

                if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                    $functionsNeedServe = $false
                }
            } catch {
            }
        }

        if ($functionsNeedServe) {
            Write-Warn "Starting Supabase Edge Functions in a separate PowerShell window..."
            $escapedBackend = $BackendRoot.Replace("'", "''")
            $functionCommand = @"
`$Host.UI.RawUI.WindowTitle = 'DhwaniMitra Edge Functions'
Set-Location '$escapedBackend'
Write-Host 'Serving DhwaniMitra Edge Functions...' -ForegroundColor Cyan
npx supabase functions serve
"@
            Start-Process powershell.exe -ArgumentList @(
                "-NoExit",
                "-ExecutionPolicy",
                "Bypass",
                "-Command",
                $functionCommand
            )
            Start-Sleep -Seconds 4
        } else {
            Write-Ok "Edge Functions appear to be available."
        }
    }
}

Write-Step "Checking Android device"
$Adb = Find-Adb
if (-not $Adb) {
    Write-Warn "adb.exe was not found."
    Write-Host "Backend is running, but Android device setup was skipped."
    exit 0
}
Write-Ok "ADB: $Adb"

$deviceLines = & $Adb devices |
    Select-Object -Skip 1 |
    Where-Object { $_ -match "\sdevice$" }

$devices = @()
foreach ($line in $deviceLines) {
    $serial = ($line -split "\s+")[0]
    if ($serial) { $devices += $serial }
}

if ($DeviceSerial) {
    if ($devices -notcontains $DeviceSerial) {
        Write-Fail "Requested device is not connected: $DeviceSerial"
        exit 1
    }
    $SelectedDevice = $DeviceSerial
} elseif ($devices.Count -eq 0) {
    Write-Warn "No Android device is connected."
    Write-Host "Supabase is running, but ADB reverse / app launch were skipped."
    exit 0
} elseif ($devices.Count -eq 1) {
    $SelectedDevice = $devices[0]
} else {
    $SelectedDevice = $devices[0]
    Write-Warn "Multiple devices found. Using first: $SelectedDevice"
}
Write-Ok "Device: $SelectedDevice"

Write-Step "Creating Android -> Supabase ADB reverse tunnel"
& $Adb -s $SelectedDevice reverse "tcp:$ApiPort" "tcp:$ApiPort" | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Fail "Could not create adb reverse mapping."
    exit 1
}

$reverseList = (& $Adb -s $SelectedDevice reverse --list | Out-String)
if ($reverseList -notmatch "tcp:$ApiPort\s+tcp:$ApiPort") {
    Write-Fail "ADB reverse mapping was not found after creation."
    Write-Host $reverseList
    exit 1
}
Write-Ok "Android 127.0.0.1:$ApiPort -> PC 127.0.0.1:$ApiPort"

if ($Build) {
    Write-Step "Building Android debug APK"
    if (-not (Test-Path $Gradlew)) {
        Write-Fail "gradlew.bat not found: $Gradlew"
        exit 1
    }
    Push-Location $ProjectRoot
    try {
        & $Gradlew ":app:assembleDebug"
        if ($LASTEXITCODE -ne 0) {
            throw "Android build failed."
        }
    } finally {
        Pop-Location
    }

    if (-not (Test-Path $DebugApk)) {
        Write-Fail "APK was not found: $DebugApk"
        exit 1
    }
    Write-Ok "APK built: $DebugApk"
}

if ($Install) {
    Write-Step "Installing DhwaniMitra on Android device"
    if (-not (Test-Path $DebugApk)) {
        Write-Fail "Debug APK not found. Use -Build -Install."
        exit 1
    }

    & $Adb -s $SelectedDevice install -r $DebugApk
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "APK installation failed."
        exit 1
    }

    & $Adb -s $SelectedDevice reverse "tcp:$ApiPort" "tcp:$ApiPort" | Out-Null
    Write-Ok "DhwaniMitra installed."
}

if ($OpenStudio) {
    Write-Step "Opening Supabase Studio"
    Start-Process "http://127.0.0.1:$StudioPort"
}

if (-not $NoLaunch) {
    Write-Step "Launching DhwaniMitra"

    $packagePath = (& $Adb -s $SelectedDevice shell pm path $PackageName 2>$null | Out-String).Trim()

    if ($packagePath -match "^package:") {
        & $Adb -s $SelectedDevice shell am start -n "$PackageName/$LauncherActivity" | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Ok "DhwaniMitra launched."
        } else {
            Write-Warn "App is installed but LauncherActivity could not be started."
        }
    } else {
        Write-Warn "DhwaniMitra is not installed on this device."
        Write-Host "Use: .\Start-DhwaniMitra.ps1 -Build -Install"
    }
}

Write-Host ""
Write-Host "============================================" -ForegroundColor DarkGreen
Write-Host " DhwaniMitra development environment ready " -ForegroundColor Green
Write-Host "============================================" -ForegroundColor DarkGreen
Write-Host ""
Write-Host "Supabase API : $projectUrl"
Write-Host "Studio       : http://127.0.0.1:$StudioPort"
Write-Host "Android      : $SelectedDevice"
Write-Host "ADB reverse  : tcp:$ApiPort -> tcp:$ApiPort"
Write-Host ""
Write-Host "Build + install : .\Start-DhwaniMitra.ps1 -Build -Install"
Write-Host "Open Studio     : .\Start-DhwaniMitra.ps1 -OpenStudio"
Write-Host "Services only   : .\Start-DhwaniMitra.ps1 -NoLaunch"
Write-Host ""
