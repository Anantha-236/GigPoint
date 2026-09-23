param(
    [string]$ProjectUrl = "",
    [string]$PublishableKey = "",
    [switch]$Build
)

$ErrorActionPreference = "Stop"

$projectRoot =
    Split-Path -Parent $MyInvocation.MyCommand.Path

$supabaseXml =
    Join-Path `
        $projectRoot `
        "app\src\main\res\values\supabase.xml"

$gradlew =
    Join-Path `
        $projectRoot `
        "gradlew.bat"

function Fail([string]$message) {
    Write-Host "[FAIL] $message" -ForegroundColor Red
    exit 1
}

function Ok([string]$message) {
    Write-Host "[OK] $message" -ForegroundColor Green
}

if ([string]::IsNullOrWhiteSpace($ProjectUrl)) {
    $ProjectUrl =
        Read-Host "Paste the hosted Supabase Project URL (https://<project-ref>.supabase.co)"
}

if ([string]::IsNullOrWhiteSpace($PublishableKey)) {
    $PublishableKey =
        Read-Host "Paste the Supabase publishable key (sb_publishable_...)"
}

$ProjectUrl =
    $ProjectUrl.Trim().TrimEnd('/')

$PublishableKey =
    $PublishableKey.Trim()

if ($ProjectUrl -notmatch '^https://[^/]+$') {
    Fail "Project URL must be HTTPS, for example https://abcxyz.supabase.co"
}

if ($PublishableKey -notmatch '^sb_publishable_[A-Za-z0-9_-]+$') {
    Fail "Use the client-side publishable key beginning with sb_publishable_. Do NOT use sb_secret_."
}

Write-Host ""
Write-Host "Checking hosted Supabase..." -ForegroundColor Cyan

try {
    $headers = @{
        "apikey" = $PublishableKey
    }

    $response =
        Invoke-WebRequest `
            -Uri "$ProjectUrl/auth/v1/health" `
            -Headers $headers `
            -UseBasicParsing `
            -TimeoutSec 15

    if ($response.StatusCode -ne 200) {
        Fail "Supabase health check returned HTTP $($response.StatusCode)."
    }
} catch {
    Fail "Could not reach hosted Supabase: $($_.Exception.Message)"
}

Ok "Hosted Supabase Auth is reachable."

$xmlDirectory =
    Split-Path -Parent $supabaseXml

New-Item `
    -ItemType Directory `
    -Path $xmlDirectory `
    -Force |
    Out-Null

$escapedUrl =
    [System.Security.SecurityElement]::Escape(
        $ProjectUrl
    )

$escapedKey =
    [System.Security.SecurityElement]::Escape(
        $PublishableKey
    )

$content = @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="supabase_url" translatable="false">$escapedUrl</string>
    <string name="supabase_publishable_key" translatable="false">$escapedKey</string>
</resources>
"@

Set-Content `
    -LiteralPath $supabaseXml `
    -Value $content `
    -Encoding UTF8

Ok "Android app now points to hosted Supabase."
Write-Host "Endpoint: $ProjectUrl"
Write-Host "Updated : app\src\main\res\values\supabase.xml"

if ($Build) {
    Write-Host ""
    Write-Host "Building debug APK..." -ForegroundColor Cyan

    & $gradlew ":app:assembleDebug"

    if ($LASTEXITCODE -ne 0) {
        Fail "Android build failed."
    }

    Ok "Build successful."
}

Write-Host ""
Write-Host "Next:"
Write-Host "  .\gradlew :app:assembleDebug"
Write-Host ""
Write-Host "The Android APK must contain the publishable key, which is intended for client apps."
Write-Host "Never put an sb_secret_ key inside the Android application."
