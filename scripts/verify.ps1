param([string]$BaseRef)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repositoryRoot

# Use Gradle's normal per-user Gradle User Home. Gradle's dependency cache uses
# file locks for concurrent processes on this host, while each worktree still
# keeps its own project-local `.gradle/` and `app/build/` directories.
# Do not override GRADLE_USER_HOME here: callers may deliberately configure it.

# The gate builds with AGP 8.0.2, which needs JDK 17. Resolve one now (honouring
# an existing JAVA_HOME, else the Android Studio JBR) so a shell whose PATH
# `java` is older stops here with a clear message instead of deep in Gradle.
# scripts/verify-and-install.ps1 / scripts/install.ps1 get the same fallback via
# device-install-common.ps1.
. (Join-Path $PSScriptRoot 'jdk-common.ps1')
Resolve-Jdk17

# --- worktree awareness (TASK-019) ---
# In a task worktree, compare the documentation gate against the base branch so a
# missed docs/modules/ update is caught even after it is committed on the branch
# (check-documentation.ps1 -BaseRef unions <base>...HEAD with the working tree).
# The main checkout on the base branch is unaffected. If -BaseRef is passed
# explicitly it always wins.
$common = Join-Path $PSScriptRoot 'worktree-common.ps1'
$baseBranch = $null
if (Test-Path -LiteralPath $common) { . $common; $baseBranch = Get-BaseBranch }

$worktreeCount = @(git worktree list --porcelain | Where-Object { $_ -like 'worktree *' }).Count
$inLinkedWorktree = (git rev-parse --git-dir) -ne (git rev-parse --git-common-dir)
$currentBranch = (git rev-parse --abbrev-ref HEAD)

if (-not $PSBoundParameters.ContainsKey('BaseRef') -and $inLinkedWorktree -and $baseBranch) {
    if ($currentBranch -eq 'HEAD') {
        Write-Warning "Detached HEAD in a linked worktree; running the documentation gate against the working tree only (no -BaseRef)."
    } elseif (-not (Test-GitRef "refs/heads/$baseBranch")) {
        Write-Warning "Base branch '$baseBranch' not found; running the documentation gate against the working tree only (no -BaseRef)."
    } else {
        git merge-base $baseBranch HEAD > $null 2> $null
        if ($LASTEXITCODE -eq 0) {
            $BaseRef = $baseBranch
            Write-Host "Worktree documentation gate: comparing against '$baseBranch'."
        } else {
            Write-Warning "This worktree's branch '$currentBranch' shares no history with '$baseBranch'; running the documentation gate against the working tree only (no -BaseRef)."
        }
    }
}

if (-not $inLinkedWorktree -and $baseBranch -and $currentBranch -eq $baseBranch -and $worktreeCount -gt 1) {
    Write-Warning "Running verify on the base branch '$baseBranch' while $worktreeCount worktrees exist. Task work belongs in its own worktree (scripts/new-worktree.ps1); this run is only meaningful for the integration gate on a merged result."
}

# Documentation change-gate. -BaseRef unions the branch commits (<base>...HEAD)
# with the working tree; without it the behaviour is the working tree only.
# Call explicitly rather than array-splatting: `@('-BaseRef', $x)` splat binds
# $BaseRef to the literal string '-BaseRef', which git then reads as `-B`.
if ($BaseRef) {
    & .\scripts\check-documentation.ps1 -BaseRef $BaseRef
} else {
    & .\scripts\check-documentation.ps1
}
if ($LASTEXITCODE -ne 0) {
    throw "Documentation coverage failed with exit code $LASTEXITCODE."
}

# Completeness self-check: every source area under app/src/main/ and every root
# Gradle file must map to a module page or an explicit in-script exclusion.
& .\scripts\check-documentation.ps1 -SelfCheck
if ($LASTEXITCODE -ne 0) {
    throw "Documentation coverage self-check failed with exit code $LASTEXITCODE."
}

& .\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
if ($LASTEXITCODE -ne 0) {
    throw "Gradle verification failed with exit code $LASTEXITCODE."
}
