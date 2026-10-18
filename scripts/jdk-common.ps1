# Shared JDK-17 resolution for the Windows pipeline. Dot-source it:
#   . "$PSScriptRoot\jdk-common.ps1"
# Defines a function only; runs nothing on load.
#
# The task gate builds with AGP 8.0.2, which requires JDK 17. On the repo
# owner's workstation the `PATH` `java` is Java 8, so a standalone Gradle run
# fails deep in AGP with an unhelpful message unless JAVA_HOME points at a
# JDK 17. The Android Studio JBR (C:\AndroidStudio\jbr) is a JDK 17 and is the
# same fallback scripts/verify-and-install.ps1 has always used via
# device-install-common.ps1.

function Resolve-Jdk17 {
    <#
    .SYNOPSIS
    Ensure JAVA_HOME points at a JDK 17 for the current process.

    .DESCRIPTION
    - If JAVA_HOME is already set, leave it untouched (the caller wins, matching
      how the pipeline treats GRADLE_USER_HOME).
    - Otherwise, if the preferred JBR path exists, set JAVA_HOME to it and
      prepend its bin to PATH for this process only.
    - Otherwise throw, naming JAVA_HOME and "JDK 17".

    .PARAMETER PreferredJbr
    JDK 17 to fall back to when JAVA_HOME is unset. Defaults to the environment
    variable FIELDSCANNER_JBR when set (so the task gate's test scenarios can
    point it elsewhere or at a missing path), otherwise C:\AndroidStudio\jbr.

    .PARAMETER Quiet
    Suppress the informational line when the fallback is applied.
    #>
    param(
        [string]$PreferredJbr = $(if ($env:FIELDSCANNER_JBR) { $env:FIELDSCANNER_JBR } else { 'C:\AndroidStudio\jbr' }),
        [switch]$Quiet
    )

    if ($env:JAVA_HOME) {
        return
    }

    if ($PreferredJbr -and (Test-Path -LiteralPath $PreferredJbr)) {
        $env:JAVA_HOME = $PreferredJbr
        $env:Path = "$PreferredJbr\bin;$env:Path"
        if (-not $Quiet) {
            Write-Host "Using JDK at $PreferredJbr (JAVA_HOME was unset)."
        }
        return
    }

    throw ("No JDK 17 found. Set JAVA_HOME to a JDK 17 before running the task gate. " +
        "AGP 8.0.2 requires JDK 17 and the 'java' on PATH here is older, so Gradle would " +
        "otherwise fail deep in the build. On this workstation set " +
        "`$env:JAVA_HOME = 'C:\AndroidStudio\jbr' (the Android Studio JBR is a JDK 17), then re-run.")
}
