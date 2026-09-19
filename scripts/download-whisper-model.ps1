$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$modelDir = Join-Path $projectRoot "app\src\main\assets\models"
$modelPath = Join-Path $modelDir "ggml-tiny-q5_1.bin"

New-Item -ItemType Directory -Force -Path $modelDir | Out-Null

$url = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny-q5_1.bin"

Write-Host "Downloading Whisper Tiny Multilingual Q5_1..."
Invoke-WebRequest -Uri $url -OutFile $modelPath

Write-Host ""
Write-Host "Model saved to:"
Write-Host $modelPath
Write-Host ""

$hash = (Get-FileHash -Algorithm SHA256 $modelPath).Hash.ToLower()
$expected = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7"

Write-Host "SHA256: $hash"

if ($hash -ne $expected) {
    throw "Model checksum does not match the expected official file."
}

Write-Host "Checksum OK."
