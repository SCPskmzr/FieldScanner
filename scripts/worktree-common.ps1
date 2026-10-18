# Shared helpers for the Windows-only git-worktree pipeline (TASK-019).
# Dot-source it:  . "$PSScriptRoot\worktree-common.ps1"
#
# There are deliberately no POSIX equivalents of the worktree scripts. The
# parallel pipeline is Windows-only; on Linux/macOS use a plain second clone.

$ErrorActionPreference = 'Stop'

function Get-BaseBranch {
    # The single integration branch, named in exactly one place. No remote and
    # no `main` branch exist in this repo: integration is a local merge.
    $configPath = Join-Path $PSScriptRoot 'base-branch.txt'
    if (-not (Test-Path -LiteralPath $configPath)) {
        throw "scripts/base-branch.txt is missing; cannot determine the base branch."
    }
    return (Get-Content -LiteralPath $configPath -Raw).Trim()
}

function Get-MainWorktreePath {
    # The first entry of `git worktree list --porcelain` is always the main
    # worktree (the one that owns .git). Returned with Windows separators.
    foreach ($line in (git worktree list --porcelain)) {
        if ($line -like 'worktree *') { return ($line.Substring(9) -replace '/', '\') }
    }
    throw "Could not determine the main worktree path."
}

function Test-LinkedWorktree {
    # True when the caller is inside a linked worktree rather than the main one.
    return (git rev-parse --git-dir) -ne (git rev-parse --git-common-dir)
}

function Test-GitRef([string]$ref) {
    git show-ref --verify --quiet -- $ref
    return $LASTEXITCODE -eq 0
}

function ConvertTo-TaskId([string]$raw) {
    # 'task-9', 'TASK_9', '9', 'TASK-009' -> 'TASK-009'. $null when not a task id.
    if ($raw -match '(?i)^\s*(?:task[-_]?)?0*([0-9]+)\s*$') {
        return 'TASK-{0:D3}' -f [int]$Matches[1]
    }
    return $null
}

function Get-TaskNumber([string]$text) {
    # First TASK-NNN anywhere in $text -> [int]; $null when there is none.
    if ($text -match '(?i)task[-_]?0*([0-9]+)') { return [int]$Matches[1] }
    return $null
}

function Get-WorktreeBranchName([string]$taskId) {
    # 'TASK-009' -> 'task-009'
    return $taskId.ToLowerInvariant()
}
