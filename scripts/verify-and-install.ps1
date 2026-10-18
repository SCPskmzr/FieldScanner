param(
    [ValidateSet('usb', 'emulator')]
    [string]$Target = 'usb'
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repositoryRoot

. (Join-Path $PSScriptRoot 'device-install-common.ps1')

# Use Gradle's normal per-user Gradle User Home, matching verify.ps1. Each
# worktree still owns its project-local `.gradle/` and `app/build/` state.

# Persist the outcome independently of a terminal that can stop streaming long commands.
$verificationOutputDirectory = Join-Path $repositoryRoot 'app\build'
$statusMarker = Join-Path $verificationOutputDirectory 'verify-and-install-status.txt'
$logPath = Join-Path $verificationOutputDirectory 'verify-and-install.log'
New-Item -ItemType Directory -Force -Path $verificationOutputDirectory | Out-Null

function Set-VerificationStatus([string]$state, [string]$details = '') {
    @(
        "state=$state"
        "updatedAt=$(Get-Date -Format o)"
        "target=$Target"
        $details
    ) | Set-Content -LiteralPath $statusMarker -Encoding utf8
}

Set-VerificationStatus 'RUNNING'
$transcriptStarted = $false

try {
    Start-Transcript -LiteralPath $logPath -Append | Out-Null
    $transcriptStarted = $true

    # Fail fast before the full gate if no device is attached (shared with install.ps1).
    $adb = Resolve-AndroidAdbPath
    $deviceSelector = Assert-AdbDeviceReady -Adb $adb -Target $Target -WorkDirectory $verificationOutputDirectory

    & "$PSScriptRoot\verify.ps1"

    $apk = Get-FieldScannerDebugApkPath -RepositoryRoot $repositoryRoot
    Install-AndLaunchFieldScanner -Adb $adb -DeviceSelector $deviceSelector -ApkPath $apk

    Set-VerificationStatus 'PASS'
    Write-Host "FieldScanner установлен и запущен на $Target."
} catch {
    Set-VerificationStatus 'FAIL' "reason=$($_.Exception.Message)"
    Write-Error $_
    exit 1
} finally {
    if ($transcriptStarted) {
        Stop-Transcript | Out-Null
    }
}
