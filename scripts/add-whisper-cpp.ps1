$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $projectRoot

if (Test-Path "third_party\whisper.cpp\.git") {
    Write-Host "whisper.cpp submodule already exists."
    git submodule update --init --recursive
    exit 0
}

New-Item -ItemType Directory -Force -Path "third_party" | Out-Null

git submodule add https://github.com/ggml-org/whisper.cpp.git third_party/whisper.cpp
git submodule update --init --recursive

Write-Host "whisper.cpp added at third_party\whisper.cpp"
