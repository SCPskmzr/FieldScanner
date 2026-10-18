<#
.SYNOPSIS
  Remove a task worktree + its branch, only once the branch is merged into the
  base branch. Windows-only.

.DESCRIPTION
  Refuses to remove a branch that is not an ancestor of the base branch (an
  unmerged, or squash-merged, branch). For a merged branch it:
    1. `git worktree remove` the worktree,
    2. deletes that worktree's `.gradle-user-home/` (~1 GB; `git worktree
       remove` leaves it behind),
    3. `git worktree prune`,
    4. `git branch -d` the branch.
  On locked handles (Android Studio, antivirus, leftover *.lock) step 1 fails
  with a clear message and nothing else runs -- rerun with -Force once the
  path is free, or after closing the tools. No half-removed state.

.PARAMETER TaskId
  e.g. TASK-022 (also accepts task-22 or 22).

.PARAMETER Force
  Pass `--force` to `git worktree remove` (for locked/dirty worktrees). Does
  NOT bypass the merged-into-base check.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$TaskId,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\worktree-common.ps1"

$id = ConvertTo-TaskId $TaskId
if (-not $id) { throw "Not a task id: '$TaskId' (expected e.g. TASK-022)." }
$branch = Get-WorktreeBranchName $id
$base   = Get-BaseBranch

if (-not (Test-GitRef "refs/heads/$branch")) {
    throw "Branch '$branch' does not exist; nothing to remove."
}

# Locate the worktree registered for this branch, if any.
$path = $null
$current = $null
foreach ($line in (git worktree list --porcelain)) {
    if ($line -like 'worktree *') { $current = $line.Substring(9) -replace '/', '\' }
    elseif ($line -eq "branch refs/heads/$branch") { $path = $current }
}

# Merged check: branch tip must be an ancestor of the base branch.
git merge-base --is-ancestor $branch $base
if ($LASTEXITCODE -ne 0) {
    throw ("Branch '$branch' is not merged into '$base' (a squash-merge does not count). " +
           "Merge it first, or if you are abandoning the work delete it by hand: git branch -D $branch")
}

if ($path) {
    if ($Force) { git worktree remove --force -- $path } else { git worktree remove -- $path }
    if ($LASTEXITCODE -ne 0) {
        throw ("git worktree remove failed for '$path' (exit $LASTEXITCODE). " +
               "Close Android Studio / pause antivirus on that path, then rerun" +
               $(if (-not $Force) { " with -Force" } else { "" }) +
               ". Nothing was changed.")
    }
    $gradleHome = Join-Path $path '.gradle-user-home'
    if (Test-Path -LiteralPath $gradleHome) {
        try {
            Remove-Item -LiteralPath $gradleHome -Recurse -Force -ErrorAction Stop
            Write-Host "Deleted $gradleHome"
        } catch {
            Write-Warning "Worktree removed, but '$gradleHome' is locked. Delete it manually to reclaim ~1 GB."
        }
    }
} else {
    Write-Warning "Branch '$branch' has no registered worktree (already removed?); pruning and deleting the branch only."
}

git worktree prune
git branch -d $branch
if ($LASTEXITCODE -ne 0) {
    throw "Worktree cleaned up, but 'git branch -d $branch' failed (exit $LASTEXITCODE)."
}

Write-Host "Removed worktree and branch '$branch'."
