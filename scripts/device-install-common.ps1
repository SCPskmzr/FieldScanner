# Shared device install + launch helpers for scripts/verify-and-install.ps1 and
# scripts/install.ps1. Dot-source this file; it defines functions only and runs
# nothing on load. The status-marker contract
# (app/build/verify-and-install-status.txt) stays owned by
# scripts/verify-and-install.ps1 — nothing here writes it.

. (Join-Path $PSScriptRoot 'jdk-common.ps1')

$script:FieldScannerDebugApkRelativePath = 'app\build\outputs\apk\debug\app-debug.apk'
$script:FieldScannerMainActivity = 'com.fieldscanner.app/.MainActivity'

function Get-FieldScannerDebugApkPath {
    param([Parameter(Mandatory = $true)][string]$RepositoryRoot)
    return (Join-Path $RepositoryRoot $script:FieldScannerDebugApkRelativePath)
}

function Resolve-AndroidAdbPath {
    # Prefer a JDK 17 (the Android Studio JBR) when no JDK is configured, matching
    # the historical behaviour of verify-and-install.ps1. Unlike the task gate the
    # device path only ever *preferred* the JBR, so a missing one is not fatal
    # here — swallow the throw and let adb resolution proceed.
    try { Resolve-Jdk17 -Quiet } catch { }

    $sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $adb = Join-Path $sdkRoot 'platform-tools\adb.exe'
    if (-not (Test-Path $adb)) {
        throw "ADB не найден: $adb. Задайте ANDROID_SDK_ROOT или установите Android SDK."
    }
    return $adb
}

function Get-AdbDeviceSelector {
    param([Parameter(Mandatory = $true)][ValidateSet('usb', 'emulator')][string]$Target)
    if ($Target -eq 'usb') { return '-d' } else { return '-e' }
}

function Assert-AdbDeviceReady {
    # Returns the adb device selector ('-d' / '-e') for $Target, or throws naming
    # $Target when no matching device is ready.
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [Parameter(Mandatory = $true)][ValidateSet('usb', 'emulator')][string]$Target,
        [Parameter(Mandatory = $true)][string]$WorkDirectory
    )

    $deviceSelector = Get-AdbDeviceSelector -Target $Target
    $adbStateOutput = Join-Path $WorkDirectory 'adb-device-state.txt'
    $adbStateProcess = Start-Process `
        -FilePath $Adb `
        -ArgumentList @($deviceSelector, 'get-state') `
        -RedirectStandardOutput $adbStateOutput `
        -Wait `
        -PassThru `
        -NoNewWindow
    $deviceState = (Get-Content -LiteralPath $adbStateOutput -Raw).Trim()
    if ($adbStateProcess.ExitCode -ne 0 -or $deviceState -ne 'device') {
        throw "Не найдено готовое $Target-устройство. Проверьте '$Adb devices'."
    }
    return $deviceSelector
}

function Install-AndLaunchFieldScanner {
    param(
        [Parameter(Mandatory = $true)][string]$Adb,
        [Parameter(Mandatory = $true)][string]$DeviceSelector,
        [Parameter(Mandatory = $true)][string]$ApkPath
    )

    & $Adb $DeviceSelector install -r $ApkPath
    if ($LASTEXITCODE -ne 0) {
        throw "APK installation failed with exit code $LASTEXITCODE."
    }

    & $Adb $DeviceSelector shell am start -n $script:FieldScannerMainActivity
    if ($LASTEXITCODE -ne 0) {
        throw "FieldScanner launch failed with exit code $LASTEXITCODE."
    }
}
