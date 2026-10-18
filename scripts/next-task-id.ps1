<#
.SYNOPSIS
  Print the next free TASK-NNN id.

.DESCRIPTION
  Scans docs/tasks/ and docs/tasks/done/ in EVERY registered git worktree
  (`git worktree list`), plus untracked task files in each, and prints the
  next id as TASK-NNN (three digits, e.g. TASK-022).

  Issuance is NOT atomic: two agents calling this at the same time, in
  different worktrees, can get the same id because an id is only "taken" once
  its task file is committed and merged. Confirm the printed id is still free
  before you commit it.

  Windows-only helper for the parallel worktree pipeline (TASK-019).
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\worktree-common.ps1"

$worktrees = @()
foreach ($line in (git worktree list --porcelain)) {
    if ($line -like 'worktree *') { $worktrees += ($line.Substring(9) -replace '/', '\') }
}
if ($worktrees.Count -eq 0) { $worktrees = @((Get-Location).Path) }

$numbers = [System.Collections.Generic.HashSet[int]]::new()

foreach ($wt in $worktrees) {
    foreach ($rel in @('docs\tasks', 'docs\tasks\done')) {
        $dir = Join-Path $wt $rel
        if (Test-Path -LiteralPath $dir) {
            Get-ChildItem -LiteralPath $dir -Filter 'TASK-*.md' -File -ErrorAction SilentlyContinue | ForEach-Object {
                $n = Get-TaskNumber $_.Name
                if ($null -ne $n) { [void]$numbers.Add($n) }
            }
        }
    }

    if (Test-Path -LiteralPath (Join-Path $wt '.git')) {
        Push-Location -LiteralPath $wt
        try {
            foreach ($p in (git ls-files --others --exclude-standard -- 'docs/tasks')) {
                $n = Get-TaskNumber (Split-Path $p -Leaf)
                if ($null -ne $n) { [void]$numbers.Add($n) }
            }
        } finally { Pop-Location }
    }
}

[int]$next = 0
if ($numbers.Count -gt 0) {
    [int]$max = ($numbers | Measure-Object -Maximum).Maximum
    $next = $max + 1
}
$id = 'TASK-{0:D3}' -f $next

# The id goes to stdout (only line there, so `$id = ./scripts/next-task-id.ps1`
# works); the non-atomic notice goes to stderr so it never pollutes a capture.
Write-Output $id
[Console]::Error.WriteLine("note: id issuance is not atomic - a simultaneous call in another worktree can return '$id' too. Confirm it is still free before committing its task file.")
