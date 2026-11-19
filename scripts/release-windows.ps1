# Builds a production desktop release for Windows.
#
# Produces dist\scrollix-<version>-windows-x64.zip containing the executable JAR
# and a launcher, plus a SHA-256 checksum. The desktop module pulls Compose
# Desktop for the host OS, so this must run on Windows.
#
# Usage: .\scripts\release-windows.ps1 [-Version 1.2.3]

[CmdletBinding()]
param(
    [string]$Version = $env:VERSION
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not $Version) {
    $Version = (git describe --tags --always --dirty 2>$null)
    if (-not $Version) {
        $Version = Get-Date -Format 'yyyyMMdd-HHmm'
    }
    $Version = $Version -replace '/', '-'
}

if (-not (Test-Path 'local.properties')) {
    throw 'local.properties is missing. Copy local.properties.example and fill in your Telegram API credentials.'
}

$started = Get-Date
$name = "scrollix-$Version-windows-x64"
$dist = Join-Path $root 'dist'
$stage = Join-Path $dist $name

Write-Host "==> Building desktop release $Version"
& (Join-Path $root 'kotlin.bat') package -m desktopApp -p jvm -v release -f executable-jar
if ($LASTEXITCODE -ne 0) {
    throw "Packaging failed with exit code $LASTEXITCODE."
}

$jar = Get-ChildItem -Path (Join-Path $root 'build\tasks\_desktopApp_executableJarJvm') -Filter '*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.LastWriteTime -gt $started } |
    Sort-Object LastWriteTime |
    Select-Object -Last 1

if (-not $jar) {
    throw 'No executable JAR was produced by the package command.'
}

if (Test-Path $stage) {
    Remove-Item -Recurse -Force $stage
}
New-Item -ItemType Directory -Path $stage -Force | Out-Null

Copy-Item $jar.FullName (Join-Path $stage 'scrollix.jar')

$launcher = @'
@echo off
setlocal
java -Dfile.encoding=UTF-8 -jar "%~dp0scrollix.jar" %*
'@
Set-Content -Path (Join-Path $stage 'scrollix.bat') -Value $launcher -Encoding ASCII

$readme = @"
Scrollix $Version

Requires a Java 17 (or newer) runtime with "java" available in PATH.

Run scrollix.bat to start the application.
"@
Set-Content -Path (Join-Path $stage 'README.txt') -Value $readme -Encoding ASCII

$zip = Join-Path $dist "$name.zip"
if (Test-Path $zip) {
    Remove-Item -Force $zip
}
Compress-Archive -Path $stage -DestinationPath $zip

$hash = (Get-FileHash -Algorithm SHA256 $zip).Hash
"$hash  $(Split-Path -Leaf $zip)" | Set-Content -Path "$zip.sha256" -Encoding ASCII

Write-Host "==> Artifact: $zip"
