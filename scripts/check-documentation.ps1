param(
    [string]$BaseRef,
    [switch]$SelfCheck
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repositoryRoot

# Source area (path prefix, from the repository root) -> module page that must be
# updated in the same change. Prefix matching is CASE-SENSITIVE (Ordinal): the
# rule '.../app/Display' matches the root Display*.kt files only, never the
# '.../app/display/' package, so both need their own entry.
$coverage = @(
    # UI, rune input, on-screen display
    @{ Source = 'app/src/main/java/com/fieldscanner/app/MainActivity.kt';             Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/MainScreenView.kt';           Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/Display';                      Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/display/';                     Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/RuneActionRouter.kt';         Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/RuneHintPager.kt';            Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/runes/';                      Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt'; Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/ScannerScreenModel.kt';       Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/SignalAnalysis.kt';           Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/res/layout/';                                           Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/res/drawable-nodpi/';                                   Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/assets/scan_outlines/';                                 Document = 'docs/modules/ui-and-input.md' }
    @{ Source = 'app/src/main/assets/call_contacts_family';                           Document = 'docs/modules/ui-and-input.md' }
    # Scanner flow, camera, object graph
    @{ Source = 'app/src/main/java/com/fieldscanner/app/ScannerViewModel';            Document = 'docs/modules/scanner.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/AppContainer.kt';             Document = 'docs/modules/scanner.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/camera/';                     Document = 'docs/modules/scanner.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/data/camera/';                Document = 'docs/modules/scanner.md' }
    # Findings, journal, persistence, media, device location
    @{ Source = 'app/src/main/java/com/fieldscanner/app/data/finding/';               Document = 'docs/modules/findings-and-journal.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/data/location/';              Document = 'docs/modules/findings-and-journal.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/journal/';                    Document = 'docs/modules/findings-and-journal.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/audio/';                      Document = 'docs/modules/findings-and-journal.md' }
    # Image analysis, speech, shared networking, app language
    @{ Source = 'app/src/main/java/com/fieldscanner/app/data/analysis/';             Document = 'docs/modules/ai-services.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/data/speech/';               Document = 'docs/modules/ai-services.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/data/net/';                  Document = 'docs/modules/ai-services.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/data/diagnostics/';          Document = 'docs/modules/platform-and-build.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/voice/';                     Document = 'docs/modules/ai-services.md' }
    @{ Source = 'app/src/main/assets/prompts/';                                       Document = 'docs/modules/ai-services.md' }
    # Telephony / whitelist
    @{ Source = 'app/src/main/java/com/fieldscanner/app/calls/';                     Document = 'docs/modules/telephony.md' }
    # Platform, manifest, build config, app resources and theme
    @{ Source = 'app/src/main/AndroidManifest.xml';                                  Document = 'docs/modules/platform-and-build.md' }
    @{ Source = 'app/src/main/java/com/fieldscanner/app/FieldScannerApplication.kt'; Document = 'docs/modules/platform-and-build.md' }
    @{ Source = 'app/src/main/res/values';                                           Document = 'docs/modules/platform-and-build.md' }
    @{ Source = 'app/src/main/res/xml/';                                             Document = 'docs/modules/platform-and-build.md' }
    @{ Source = 'build.gradle';                                                       Document = 'docs/modules/platform-and-build.md' }
    @{ Source = 'app/build.gradle';                                                   Document = 'docs/modules/platform-and-build.md' }
    @{ Source = 'settings.gradle';                                                    Document = 'docs/modules/platform-and-build.md' }
    @{ Source = 'gradle.properties';                                                  Document = 'docs/modules/platform-and-build.md' }
    @{ Source = 'gradle/wrapper/';                                                    Document = 'docs/modules/platform-and-build.md' }
)

# Paths under app/src/main/ (and root Gradle files) that intentionally have no
# module page. Keep this list short and each entry commented.
$selfCheckExclusions = @(
    # Launcher icon set generated by the Android Studio asset wizard.
    'app/src/main/res/mipmap-'
    'app/src/main/res/drawable/ic_launcher_background.xml'
    'app/src/main/res/drawable-v24/ic_launcher_foreground.xml'
    # Gradle wrapper launcher scripts (gradlew, gradlew.bat); the wrapper version
    # is in gradle/wrapper/ and is covered by platform-and-build.md.
    'gradlew'
)

function Test-Prefix([string]$value, [string]$prefix) {
    return $value.StartsWith($prefix, [System.StringComparison]::Ordinal)
}

# Pathspecs that bound both the change-gate coverage and the self-check walk.
$auditPathspecs = @('app/src/main', '*.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', 'gradle/wrapper')

if ($SelfCheck) {
    $tracked = @(git ls-files -- $auditPathspecs)
    $untracked = @(git ls-files --others --exclude-standard -- $auditPathspecs)
    $candidates = @($tracked + $untracked) | Where-Object { $_ } | Sort-Object -Unique

    $uncovered = foreach ($path in $candidates) {
        $matched = $false
        foreach ($item in $coverage) {
            if (Test-Prefix $path $item.Source) { $matched = $true; break }
        }
        if (-not $matched) {
            foreach ($prefix in $selfCheckExclusions) {
                if (Test-Prefix $path $prefix) { $matched = $true; break }
            }
        }
        if (-not $matched) { $path }
    }

    if ($uncovered) {
        Write-Error ("Documentation coverage self-check: no rule or exclusion for:`n" +
            (($uncovered | ForEach-Object { "  $_" }) -join "`n") +
            "`nAdd a coverage entry (source prefix -> module page) or an explicit exclusion with a reason.")
        exit 1
    }

    Write-Host 'Documentation coverage self-check: PASS'
    exit 0
}

# Change-gate: union of the working tree (uncommitted + untracked) and, when a
# base ref is given, the branch commits (<base>...HEAD merge-base range).
$changed = @(git diff --name-only HEAD) + @(git ls-files --others --exclude-standard)
if ($BaseRef) {
    $changed += @(git diff --name-only "${BaseRef}...HEAD")
}
$changed = @($changed | Where-Object { $_ } | Sort-Object -Unique)

$missing = foreach ($item in $coverage) {
    $sourceChanged = $changed | Where-Object { Test-Prefix $_ $item.Source }
    if ($sourceChanged -and ($changed -cnotcontains $item.Document)) { $item.Document }
}

$missing = $missing | Sort-Object -Unique
if ($missing) {
    Write-Error "Documentation update required: $($missing -join ', '). Update the matching module page in this change."
    exit 1
}

Write-Host 'Documentation coverage: PASS'
# Explicit: internal `git` calls above can leave a non-zero $LASTEXITCODE that
# would otherwise be read as this script's exit code by the caller.
exit 0
