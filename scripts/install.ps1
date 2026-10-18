param(
    [ValidateSet('usb', 'emulator')]
    [string]$Target = 'usb'
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repositoryRoot

. (Join-Path $PSScriptRoot 'device-install-common.ps1')

# Install-and-launch only. Runs no Gradle work — no documentation gate, no static
# analysis, no unit run, no APK build — and does not touch the
# verify-and-install status marker; that contract stays owned by
# scripts/verify-and-install.ps1. Build the APK first with /verify or
# /verify-install.

$workDirectory = Join-Path $repositoryRoot 'app\build'
New-Item -ItemType Directory -Force -Path $workDirectory | Out-Null

$apk = Get-FieldScannerDebugApkPath -RepositoryRoot $repositoryRoot
if (-not (Test-Path -LiteralPath $apk)) {
    [Console]::Error.WriteLine("Debug APK not found: $apk")
    [Console]::Error.WriteLine("Build it first: ./scripts/verify.ps1 (/verify) or ./scripts/verify-and-install.ps1 (/verify-install).")
    exit 1
}

$adb = Resolve-AndroidAdbPath
$deviceSelector = Assert-AdbDeviceReady -Adb $adb -Target $Target -WorkDirectory $workDirectory

Install-AndLaunchFieldScanner -Adb $adb -DeviceSelector $deviceSelector -ApkPath $apk

Write-Host "FieldScanner установлен и запущен на $Target (гейт не выполнялся)."
