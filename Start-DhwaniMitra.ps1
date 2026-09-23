param(
    [ValidateSet("Hosted", "Local")]
    [string]$BackendMode = "Hosted",

    [switch]$Build,
    [switch]$Install,
    [switch]$OpenStudio,
    [switch]$NoLaunch,
    [string]$DeviceSerial = ""
)

$ErrorActionPreference = "Stop"

function Step([string]$message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Ok([string]$message) {
    Write-Host "[OK] $message" -ForegroundColor Green
}

function Warn([string]$message) {
    Write-Host "[WARN] $message" -ForegroundColor Yellow
}

function Fail([string]$message) {
    Write-Host "[FAIL] $message" -ForegroundColor Red
    exit 1
}

function Find-Adb {
    $command =
        Get-Command adb -ErrorAction SilentlyContinue

    if ($command) {
        return $command.Source
    }

    $candidates = @(
        (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"),
        (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"),
        (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe")
    ) | Where-Object {
        $_ -and (Test-Path $_)
    }

    if ($candidates.Count -gt 0) {
        return $candidates[0]
    }

    return $null
}

function Read-SupabaseXml(
    [string]$path
) {
    if (-not (Test-Path $path)) {
        Fail "Missing $path"
    }

    [xml]$xml =
        Get-Content -LiteralPath $path

    $url =
        [string](
            $xml.resources.string |
            Where-Object {
                $_.name -eq "supabase_url"
            } |
            Select-Object -First 1
        ).'#text'

    $key =
        [string](
            $xml.resources.string |
            Where-Object {
                $_.name -eq "supabase_publishable_key"
            } |
            Select-Object -First 1
        ).'#text'

    return @{
        Url = $url.Trim().TrimEnd('/')
        Key = $key.Trim()
    }
}

$projectRoot =
    Split-Path -Parent $MyInvocation.MyCommand.Path

$backendRootCandidates = @(
    (Join-Path $projectRoot "Backend"),
    (Join-Path $projectRoot "backend")
)

$backendRoot =
    $backendRootCandidates |
    Where-Object {
        Test-Path $_
    } |
    Select-Object -First 1

$supabaseXml =
    Join-Path `
        $projectRoot `
        "app\src\main\res\values\supabase.xml"

$gradlew =
    Join-Path `
        $projectRoot `
        "gradlew.bat"

$debugApk =
    Join-Path `
        $projectRoot `
        "app\build\outputs\apk\debug\app-debug.apk"

$packageName =
    "com.example.gigpoint"

$launcherActivity =
    ".LauncherActivity"

$apiPort =
    55321

$studioPort =
    55323

Write-Host ""
Write-Host "============================================" -ForegroundColor DarkGreen
Write-Host "       DhwaniMitra Development Launcher     " -ForegroundColor Green
Write-Host "============================================" -ForegroundColor DarkGreen
Write-Host "Backend mode : $BackendMode"

if ($BackendMode -eq "Hosted") {
    Step "Checking hosted Supabase configuration"

    $config =
        Read-SupabaseXml $supabaseXml

    if (
        $config.Url -notmatch '^https://' -or
        $config.Key -notmatch '^sb_publishable_'
    ) {
        Fail @"
The Android app is not configured for hosted Supabase.
Current endpoint: $($config.Url)

Run:
  .\Configure-DhwaniMitra-Backend.ps1

Then paste the Project URL and publishable key from the Supabase Connect dialog.
"@
    }

    try {
        $health =
            Invoke-WebRequest `
                -Uri "$($config.Url)/auth/v1/health" `
                -Headers @{
                    "apikey" = $config.Key
                } `
                -UseBasicParsing `
                -TimeoutSec 15

        if ($health.StatusCode -ne 200) {
            Fail "Hosted Supabase health check returned HTTP $($health.StatusCode)."
        }
    } catch {
        Fail "Hosted Supabase is not reachable: $($_.Exception.Message)"
    }

    Ok "Hosted Supabase is reachable: $($config.Url)"
}
else {
    Step "Starting local Supabase"

    if (-not $backendRoot) {
        Fail "Backend folder was not found."
    }

    $docker =
        Get-Command docker -ErrorAction SilentlyContinue

    if (-not $docker) {
        Fail "Docker is required for Local backend mode."
    }

    & docker info *> $null

    Push-Location $backendRoot
    try {
        $statusOutput =
            (& npx supabase status 2>&1 | Out-String)

        if (
            $LASTEXITCODE -ne 0 -or
            $statusOutput -notmatch
                "local development setup is running"
        ) {
            & npx supabase start

            if ($LASTEXITCODE -ne 0) {
                Fail "Supabase local stack failed to start."
            }

            $statusOutput =
                (& npx supabase status 2>&1 | Out-String)
        }
    }
    finally {
        Pop-Location
    }

    $projectUrl =
        "http://127.0.0.1:$apiPort"

    $publishableKey =
        $null

    if (
        $statusOutput -match
        "Publishable\s+[│|]?\s*(sb_publishable_[^\s│]+)"
    ) {
        $publishableKey =
            $Matches[1].Trim()
    }

    if (-not $publishableKey) {
        Fail "Could not read the local publishable key."
    }

    $escapedUrl =
        [System.Security.SecurityElement]::Escape(
            $projectUrl
        )

    $escapedKey =
        [System.Security.SecurityElement]::Escape(
            $publishableKey
        )

    Set-Content `
        -LiteralPath $supabaseXml `
        -Encoding UTF8 `
        -Value @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="supabase_url" translatable="false">$escapedUrl</string>
    <string name="supabase_publishable_key" translatable="false">$escapedKey</string>
</resources>
"@

    Ok "Local Android backend configuration written."
}

$adb =
    Find-Adb

if (-not $adb) {
    Warn "ADB was not found. Backend checks completed; device operations skipped."
    exit 0
}

Step "Checking Android devices"

$devices = @(
    & $adb devices |
    Select-Object -Skip 1 |
    Where-Object {
        $_ -match "\sdevice$"
    } |
    ForEach-Object {
        ($_ -split "\s+")[0]
    }
)

if ($DeviceSerial) {
    if ($devices -notcontains $DeviceSerial) {
        Fail "Requested device is not connected: $DeviceSerial"
    }

    $selectedDevice =
        $DeviceSerial
}
elseif ($devices.Count -eq 0) {
    Warn "No Android device is connected."
    $selectedDevice =
        $null
}
elseif ($devices.Count -eq 1) {
    $selectedDevice =
        $devices[0]
}
else {
    Write-Host "Connected devices:"
    $devices | ForEach-Object {
        Write-Host "  $_"
    }

    Fail "More than one device/emulator is connected. Re-run with -DeviceSerial <serial>."
}

if (
    $BackendMode -eq "Local" -and
    $selectedDevice
) {
    Step "Creating ADB reverse tunnel"

    & $adb `
        -s $selectedDevice `
        reverse `
        "tcp:$apiPort" `
        "tcp:$apiPort"

    if ($LASTEXITCODE -ne 0) {
        Fail "ADB reverse failed."
    }

    Ok "Phone 127.0.0.1:$apiPort -> PC 127.0.0.1:$apiPort"
}

if ($Build) {
    Step "Building Android APK"

    & $gradlew ":app:assembleDebug"

    if ($LASTEXITCODE -ne 0) {
        Fail "Android build failed."
    }

    if (-not (Test-Path $debugApk)) {
        Fail "APK not found: $debugApk"
    }

    Ok "APK built."
}

if (
    $Install -and
    $selectedDevice
) {
    if (-not (Test-Path $debugApk)) {
        Fail "Debug APK is missing. Use -Build -Install."
    }

    Step "Installing APK"

    & $adb `
        -s $selectedDevice `
        install `
        -r `
        $debugApk

    if ($LASTEXITCODE -ne 0) {
        Fail "APK installation failed."
    }

    Ok "APK installed."
}

if (
    $OpenStudio -and
    $BackendMode -eq "Local"
) {
    Start-Process `
        "http://127.0.0.1:$studioPort"
}

if (
    -not $NoLaunch -and
    $selectedDevice
) {
    Step "Launching DhwaniMitra"

    & $adb `
        -s $selectedDevice `
        shell `
        am `
        start `
        -n `
        "$packageName/$launcherActivity" |
        Out-Null

    if ($LASTEXITCODE -eq 0) {
        Ok "DhwaniMitra launched."
    }
}

Write-Host ""
Write-Host "Ready." -ForegroundColor Green
Write-Host "Backend mode : $BackendMode"
if ($selectedDevice) {
    Write-Host "Device       : $selectedDevice"
}
Write-Host ""
Write-Host "Hosted normal use:"
Write-Host "  .\Start-DhwaniMitra.ps1 -BackendMode Hosted -Build -Install"
Write-Host ""
Write-Host "Local development:"
Write-Host "  .\Start-DhwaniMitra.ps1 -BackendMode Local -Build -Install"
