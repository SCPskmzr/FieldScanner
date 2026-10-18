<#
.SYNOPSIS
  Integrate a closed task branch into the base branch on the main checkout,
  running the task gate only when the merge is not a fast-forward. Windows-only.

.DESCRIPTION
  `/merge TASK-NNN` (or `task-nnn`) merges the closed `task-nnn` branch into the
  integration branch named in `scripts/base-branch.txt`.

    * Fast-forward (the base branch is an ancestor of `task-nnn`, no
      divergence): the merged tree is byte-identical to the branch tip the task
      worktree already verified, so this fast-forwards and runs NO Gradle work.
    * Divergent (the base branch moved since the branch was cut, or several
      branches are integrated in one batch): the merged tree is new. Runs
      `git merge --no-ff --no-commit`, then `scripts/verify.ps1` on the result,
      and keeps the merge commit only if the gate passes. On a merge conflict or
      a gate failure it runs `git merge --abort` and leaves the base branch
      untouched.

  Refuses to run from a linked worktree, off the base branch, with a dirty tree,
  for an unknown branch, or for an already-merged branch. Runs no device/adb
  work and starts no emulator.

.PARAMETER Task
  e.g. TASK-032 (also accepts task-032 or 32). Normalised to the branch name.

.PARAMETER NoInstallReminder
  Suppress the trailing reminder about `scripts/verify-and-install.ps1` and
  `scripts/remove-worktree.ps1`. Nothing else changes.
#>
[CmdletBinding()]
param(
    [string]$Task,
    [switch]$NoInstallReminder
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\worktree-common.ps1"

$usage = @'
Usage: scripts/merge-task.ps1 <TASK-NNN|task-nnn> [-NoInstallReminder]

Integrate a closed task branch into the base branch (scripts/base-branch.txt)
on the main checkout. Runs scripts/verify.ps1 only when the merge is not a
fast-forward of the already-verified branch. Repo-owner action; never run from
a task worktree.
'@

function Invoke-MergeAbort {
    # Single rollback for both a conflict and a gate failure. `git merge --abort`
    # restores the pre-merge HEAD, index and working tree; the reset is a
    # belt-and-braces fallback if MERGE_HEAD somehow survives.
    git merge --abort 2>$null
    git rev-parse -q --verify MERGE_HEAD 2>$null > $null
    if ($LASTEXITCODE -eq 0) {
        Write-Warning "git merge --abort did not clear MERGE_HEAD; running git reset --hard HEAD."
        git reset --hard HEAD | Out-Null
    }
}

function Write-MergeReminder {
    if ($NoInstallReminder) { return }
    Write-Host ""
    Write-Host "Next (repo owner, separate manual steps):"
    Write-Host "  ./scripts/verify-and-install.ps1          install and launch the merged '$base' on the Galaxy XCover 4s"
    Write-Host "  ./scripts/remove-worktree.ps1 $id     remove the merged task worktree and its branch"
}

if ([string]::IsNullOrWhiteSpace($Task)) {
    Write-Host $usage
    exit 1
}

$id = ConvertTo-TaskId $Task
if (-not $id) {
    Write-Host $usage
    throw "Not a task id: '$Task' (expected e.g. TASK-032 or task-032)."
}
$branch = Get-WorktreeBranchName $id

$repositoryRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repositoryRoot

# --- guards: no side effects until every one passes ---
if (Test-LinkedWorktree) {
    throw "merge-task must run in the main checkout, not a linked worktree. cd to the main worktree and retry."
}

$base = Get-BaseBranch
$currentBranch = "$(git rev-parse --abbrev-ref HEAD)".Trim()
if ($currentBranch -ne $base) {
    throw "HEAD is '$currentBranch', not the base branch '$base'. Check out '$base' first."
}

$dirty = (git status --porcelain)
if ($dirty) {
    throw "Working tree or index is dirty. Commit, stash, or clean it before merging:`n$dirty"
}

if (-not (Test-GitRef "refs/heads/$branch")) {
    throw "Branch '$branch' does not exist. Nothing to merge."
}

git merge-base --is-ancestor $branch $base
if ($LASTEXITCODE -eq 0) {
    throw "Branch '$branch' is already merged into '$base'. Nothing to do (clean up with scripts/remove-worktree.ps1 $id)."
}

$baseShaShort = "$(git rev-parse --short $base)".Trim()

# --- fast-forward path: the branch tip is an already-verified tree, no gate ---
git merge-base --is-ancestor $base $branch
$isFastForward = ($LASTEXITCODE -eq 0)

if ($isFastForward) {
    git merge --ff-only $branch
    if ($LASTEXITCODE -ne 0) {
        throw "git merge --ff-only $branch failed (exit $LASTEXITCODE). '$base' unchanged."
    }
    Write-Host ""
    Write-Host "Fast-forwarded '$base': $baseShaShort -> $("$(git rev-parse --short HEAD)".Trim()) (= tip of '$branch')."
    Write-Host "Task gate skipped: the merge was a fast-forward of an already-verified branch, so no Gradle work ran."
    Write-MergeReminder
    exit 0
}

# --- divergent path: the merged tree is new, run the gate on it ---
Write-Host "Merge is not a fast-forward ('$base' moved since '$branch' was cut). Running a --no-ff merge and the task gate on the result."
git merge --no-ff --no-commit $branch
if ($LASTEXITCODE -ne 0) {
    Invoke-MergeAbort
    throw ("git could not auto-merge '$branch' into '$base' (conflict). '$base' and the working tree are unchanged. " +
           "Merge and resolve it by hand, then re-run /merge.")
}

$gatePassed = $false
try {
    & "$PSScriptRoot\verify.ps1"
    $gatePassed = ($LASTEXITCODE -eq 0 -or $null -eq $LASTEXITCODE)
} catch {
    $gatePassed = $false
    Write-Warning "Task gate error: $($_.Exception.Message)"
}

if (-not $gatePassed) {
    Invoke-MergeAbort
    throw "Task gate FAILED on the merged tree. Ran 'git merge --abort'; '$base' is unchanged, tree clean, no MERGE_HEAD."
}

git commit --no-edit -m "merge: integrate $id ($branch)"
if ($LASTEXITCODE -ne 0) {
    throw "Task gate passed but 'git commit' failed (exit $LASTEXITCODE). Resolve by hand; MERGE_HEAD may still be present."
}

Write-Host ""
Write-Host "Merged '$branch' into '$base' (--no-ff) after the task gate passed: $("$(git rev-parse --short HEAD)".Trim())."
Write-MergeReminder
exit 0
