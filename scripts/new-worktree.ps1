<#
.SYNOPSIS
  Create an isolated git worktree + branch for one task. Windows-only.

.DESCRIPTION
  Adds branch `task-nnn` from the base branch and checks it out in a worktree
  under a SHORT root (default C:\fs-wt\task-nnn). The short root is the only
  real mitigation for Windows MAX_PATH during the Gradle/kapt/javac build --
  `core.longpaths` only affects git itself, not the build.

  Each worktree gets its own project-local `.gradle/` and `app/build/` state.
  Gradle distributions and dependency caches stay in the normal per-user Gradle
  User Home, which Gradle safely shares between concurrent processes on this
  host using file locks. No caches or build outputs are copied into worktrees.

  Re-running for an existing branch, an already-registered worktree, or an
  existing target path fails BEFORE any change is made.

.PARAMETER TaskId
  e.g. TASK-022 (also accepts task-22 or 22).

.PARAMETER WorktreeRoot
  Parent directory for worktrees. Default C:\fs-wt. Keep it short.

.PARAMETER ApplyRepoGitConfig
  Also set, REPOSITORY-WIDE (every worktree shares .git/config), and report
  exactly what changed:
    gc.auto = 0          stop auto-gc taking a packed-refs lock mid ref-op in
                         another worktree during a parallel session
    core.longpaths = true let git handle >260-char paths (does NOT fix the
                         build's MAX_PATH; the short worktree root does)
  These are not per-worktree and this script never unsets them. Revert by hand
  with `git config --unset gc.auto` / `git config --unset core.longpaths`.

#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$TaskId,
    [string]$WorktreeRoot = 'C:\fs-wt',
    [switch]$ApplyRepoGitConfig
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\worktree-common.ps1"

$id = ConvertTo-TaskId $TaskId
if (-not $id) { throw "Not a task id: '$TaskId' (expected e.g. TASK-022)." }
$branch = Get-WorktreeBranchName $id
$base   = Get-BaseBranch
$target = Join-Path $WorktreeRoot $branch
$mainRoot = Get-MainWorktreePath

# --- pre-flight: no side effects if anything here fails ---
if (-not (Test-GitRef "refs/heads/$base")) {
    throw "Base branch '$base' not found (see scripts/base-branch.txt)."
}
if (Test-GitRef "refs/heads/$branch") {
    throw "Branch '$branch' already exists. Finish or remove it first: scripts/remove-worktree.ps1 $id"
}
$targetForward = $target -replace '\\', '/'
foreach ($line in (git worktree list --porcelain)) {
    if ($line -like 'worktree *') {
        $wt = $line.Substring(9)
        if ($wt -ieq $targetForward) { throw "A worktree is already registered at '$target'." }
    }
}
if (Test-Path -LiteralPath $target) {
    throw "Path '$target' already exists. Delete it or pass a different -WorktreeRoot."
}
if ($target.Length -ge 60) {
    Write-Warning "Worktree path '$target' is $($target.Length) chars; the build may still hit MAX_PATH. Prefer a shorter -WorktreeRoot."
}

# --- optional, explicit, repository-wide git config ---
if (-not $ApplyRepoGitConfig -and -not (git config --get gc.auto)) {
    Write-Host "Hint: run once per parallel session with -ApplyRepoGitConfig to set gc.auto=0 + core.longpaths=true REPOSITORY-WIDE (not per-worktree)."
}
if ($ApplyRepoGitConfig) {
    Write-Host "Setting REPOSITORY-WIDE git config (all worktrees share .git/config):"
    $configPairs = @(
        @{ Key = 'gc.auto';        Want = '0' }
        @{ Key = 'core.longpaths'; Want = 'true' }
    )
    foreach ($p in $configPairs) {
        $have = (git config --get $p.Key)
        if ($have -ne $p.Want) {
            git config $p.Key $p.Want
            $shown = if ($have) { $have } else { '<unset>' }
            Write-Host ("  {0} = {1}  (was {2})  -- revert: git config --unset {0}" -f $p.Key, $p.Want, $shown)
        } else {
            Write-Host ("  {0} already = {1}" -f $p.Key, $p.Want)
        }
    }
}

# --- create ---
New-Item -ItemType Directory -Force -Path $WorktreeRoot | Out-Null
git worktree add -b $branch $target $base
if ($LASTEXITCODE -ne 0) { throw "git worktree add failed with exit code $LASTEXITCODE." }

try {
    # Copy the git-invisible local build config the Gradle build needs. Never
    # copy build output (build/, app/build/).
    foreach ($name in @('local.properties')) {
        $src = Join-Path $mainRoot $name
        if (Test-Path -LiteralPath $src) {
            Copy-Item -LiteralPath $src -Destination (Join-Path $target $name) -Force
            Write-Host "Copied $name"
        }
    }

} catch {
    $failure = $_
    Write-Warning "Post-create setup failed; removing partial worktree '$target' and branch '$branch'."
    git worktree remove --force -- $target 2>$null
    # Remove any exact, preflight-checked target that Git could not clean up.
    if (Test-Path -LiteralPath $target) {
        $extendedTarget = "\\?\$target"
        Remove-Item -LiteralPath $extendedTarget -Recurse -Force -ErrorAction Stop
    }
    git worktree prune
    git branch -D $branch 2>$null
    throw $failure
}

Write-Host ""
Write-Host "Worktree ready: $target"
Write-Host "  branch $branch  from  $base"
Write-Host "Next:"
Write-Host "  cd `"$target`""
