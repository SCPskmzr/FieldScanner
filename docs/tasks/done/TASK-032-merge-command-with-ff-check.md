# TASK-032 — `/merge` command: integrate a task branch with a fast-forward check

## Goal

The repo owner has one command — `/merge TASK-NNN` — that integrates a closed
`task-nnn` branch into `master` on the main checkout, and only re-runs the task
gate when the merge actually produces a tree that was not already verified:

- **Fast-forwardable merge** (`master` is an ancestor of `task-nnn`, no
  divergence): the merged tree is byte-identical to the branch tip the worktree
  already verified — `/merge` fast-forwards and runs **no** Gradle work.
- **Divergent merge** (`master` moved since the branch was cut, or several
  branches are being integrated in one batch): the merged tree is new and
  untested — `/merge` performs a `--no-ff` merge, runs `./scripts/verify.ps1` on
  the result, and keeps the merge commit only if the gate passes; on failure or
  on a merge conflict it aborts and leaves `master` untouched.

Installing and launching on the Galaxy XCover 4s stays the owner's separate
manual step (`./scripts/verify-and-install.ps1`); `/merge` never touches a
device.

## Context

Today the integration step (`docs/AI_DEVELOPMENT_WORKFLOW.md`, "Parallel work"
step 6) is manual: the owner runs `git merge` for each green `task-nnn` branch
and then re-runs `./scripts/verify` on the merged result "after **each** merge".
Two problems:

1. Nothing enforces it — a merge can land on `master` without the post-merge
   gate ever running, so a clean-but-broken combination of two independently
   green branches (same `AndroidManifest` entry, same resource/string, same
   shared constant, overlapping `AppContainer` wiring, two tests with the same
   name, version-catalog edits) reaches `master` unnoticed.
2. When the merge is a plain fast-forward and `master` has not moved, the
   post-merge tree is identical to what `./scripts/verify.ps1` already checked in
   the task worktree, so re-running the full gate is pure duplication.

A `/merge` command that branches on "is this a fast-forward?" enforces the gate
exactly where it adds information and skips it where it does not.

## Requirements

- **New `scripts/merge-task.ps1`** (Windows-only, consistent with
  `scripts/verify-and-install.ps1` and the worktree helpers; no POSIX
  counterpart):
  - Signature: `scripts/merge-task.ps1 <TASK-NNN|task-nnn> [-NoInstallReminder]`.
    Accept either the task id (`TASK-032`) or the branch name (`task-032`);
    normalise to the branch name.
  - Read the integration branch name from `scripts/base-branch.txt` (currently
    `master`) — do not hardcode it.
  - **Refuse to run** (exit non-zero, clear message, `master` untouched) when:
    - the current working directory is a linked worktree, not the main checkout
      (compare `git rev-parse --git-dir` / `--git-common-dir`, or check
      `git rev-parse --is-inside-work-tree` + `git worktree list`);
    - `HEAD` is not the integration branch;
    - the working tree or index is dirty (`git status --porcelain` non-empty);
    - the named branch does not exist, or is already merged
      (`git merge-base --is-ancestor task-nnn master` — nothing to do).
  - **Fast-forward path** — when `git merge-base --is-ancestor master task-nnn`
    is true (the branch is strictly ahead of `master`):
    - run `git merge --ff-only task-nnn`;
    - run **no** Gradle task (no `gradlew`, no `verify.ps1` call);
    - print that the gate was skipped because the merge was a fast-forward of an
      already-verified branch.
  - **Divergent path** — otherwise:
    - `git merge --no-ff --no-commit task-nnn`;
    - if git reports a conflict: `git merge --abort`, exit non-zero telling the
      user to merge and resolve manually, then re-run `/merge` — `master`
      unchanged, no `MERGE_HEAD` left;
    - otherwise run `./scripts/verify.ps1`:
      - **FAIL** → `git merge --abort`, exit non-zero with the gate result;
        `master` unchanged, tree clean, no `MERGE_HEAD`;
      - **PASS** → `git commit --no-edit` with a merge subject naming the task,
        e.g. `merge: integrate TASK-032 (task-032)`.
  - On success, print a one-line reminder that the owner still runs
    `./scripts/verify-and-install.ps1` on the merged `master` to install and
    launch on the XCover 4s, and `./scripts/remove-worktree.ps1 TASK-NNN` to
    clean up. `-NoInstallReminder` suppresses the reminder line only.
  - Runs no device/`adb` work and starts no emulator.
- **`.claude/commands/merge.md`** — thin pointer (front-matter `description:`
  plus a one-line "Follow …" body), matching `.claude/commands/verify.md` in
  style. Points at `scripts/merge-task.ps1` and the AGENTS.md row/section that
  describes the integration procedure.
- **`.claude/settings.json`** — add `scripts/merge-task.ps1` to the committed
  PowerShell allow-list, using the same pattern as the other `scripts/*.ps1`
  entries.
- **`AGENTS.md` "Agent scripts" table** — add a `scripts/merge-task.ps1` row
  (Windows column only; POSIX `—`; args
  `<TASK-NNN|task-nnn> [-NoInstallReminder]`; purpose "Integrate a closed task
  branch into the base branch; runs the task gate only when the merge is not a
  fast-forward"; Run in "repo owner, main checkout only — never in a worktree").
- **`AGENTS.md` "Agent procedures" list** — add a line:
  `Integrate a closed task branch — /merge: "Parallel work" in docs/AI_DEVELOPMENT_WORKFLOW.md`.
- **`docs/AI_DEVELOPMENT_WORKFLOW.md`** — rewrite "Parallel work" step 6
  ("Integration gate") so the per-branch merge is `./scripts/merge-task.ps1`
  (`/merge`), state that it runs `./scripts/verify` on the merged result unless
  the merge is a fast-forward of an already-verified branch, and that installing
  and launching on the device (`./scripts/verify-and-install.ps1`) remains the
  owner's separate step after the batch. Also touch the "Standard task" list
  item 4 / "Task lifecycle" only if they currently describe the manual
  post-merge `verify`.
- **`docs/modules/platform-and-build.md`** — module-doc sync: document `/merge`,
  `scripts/merge-task.ps1`, the fast-forward vs divergent behaviour, and the
  refusal conditions.
- No new `docs/skills/*/GUIDE.md`: like `/verify` and `/install` (TASK-030) the
  procedure stays defined in the AGENTS.md section + the script, and the command
  is a thin pointer.

## Acceptance criteria

- [x] `scripts/merge-task.ps1` exists and parses (`powershell -NoProfile -File
  scripts/merge-task.ps1` with no args prints usage / exits non-zero). No POSIX
  `scripts/merge-task`.
- [x] `.claude/commands/merge.md` exists and is a thin pointer matching
  `.claude/commands/verify.md` in form; `.claude/settings.json` lists
  `scripts/merge-task.ps1`.
- [x] Refusal paths each exit non-zero without changing `master` (verified by
  running the script): (a) from a linked worktree; (b) with `HEAD` not on the
  base branch; (c) with a dirty tree; (d) with an unknown branch name; (e) with
  an already-merged branch.
- [x] Fast-forward scenario: with `task-NNN` strictly ahead of `master`,
  `./scripts/merge-task.ps1 TASK-NNN` advances `master` to the branch tip, runs
  no Gradle task (verified by inspection of the script and by the run output —
  no `gradlew` / `verify.ps1` invocation), and prints the "gate skipped —
  fast-forward" message.
- [x] Divergent scenario, gate PASS: with a throwaway commit on `master` that
  does not conflict, `./scripts/merge-task.ps1 TASK-NNN` runs
  `./scripts/verify.ps1` and, on PASS, leaves a `--no-ff` merge commit whose
  subject names the task.
- [x] Divergent scenario, gate FAIL: when `./scripts/verify.ps1` fails on the
  merged tree, the script runs `git merge --abort`; afterwards `git status` is
  clean, `git rev-parse master` is unchanged, and no `.git/MERGE_HEAD` remains.
- [x] Conflict scenario: when git cannot auto-merge, the script aborts, exits
  non-zero with a "resolve manually" message, and leaves `master` and the tree
  unchanged.
- [x] `AGENTS.md` "Agent scripts" table has the `scripts/merge-task.ps1` row and
  "Agent procedures" lists `/merge`; `docs/AI_DEVELOPMENT_WORKFLOW.md` step 6
  describes `/merge` and the fast-forward skip.
- [x] `docs/modules/platform-and-build.md` describes `/merge` and
  `scripts/merge-task.ps1`.
- [x] Task gate `./scripts/verify.ps1` passes (`JAVA_HOME` at a JDK 17).

## Out of scope

- **Tree-hash verify cache** — having `verify.ps1` record `tree=<sha>
  state=PASS` in a worktree-shared location and having `/merge` skip the gate on
  the divergent path too when the post-merge tree already has a PASS record.
  Worth a follow-up task; not needed for the fast-forward win.
- Merging more than one branch per invocation / a batch driver looping over
  branches.
- Any change to what `scripts/verify.ps1` or `scripts/verify-and-install.ps1`
  do, or adding device/install behaviour to `/merge`.
- Branch/worktree cleanup — that stays `scripts/remove-worktree.ps1`.
- A `pre-merge-commit` git hook as a second guard against a manual `git merge`
  (possible later; the command + workflow doc are enough for now).
- A POSIX `scripts/merge-task` (integration is the Windows repo-owner path, like
  `verify-and-install.ps1` and the worktree helpers).
- Any app source or instrumented-test change.

## Relevant areas

- `scripts/merge-task.ps1` (new); `scripts/base-branch.txt` (read, not edited);
  `scripts/verify.ps1` (invoked on the divergent path);
  `scripts/verify-and-install.ps1`, `scripts/remove-worktree.ps1`,
  `scripts/worktree-common.ps1` (patterns for arg parsing, worktree detection,
  base-branch read, `$LASTEXITCODE` handling).
- `.claude/commands/merge.md` (new); `.claude/commands/verify.md` (style
  reference); `.claude/settings.json` (allow-list).
- `AGENTS.md` — "Agent procedures" list, "Agent scripts" table.
- `docs/AI_DEVELOPMENT_WORKFLOW.md` — "Parallel work" step 6, and the
  "Standard task" / "Task lifecycle" mentions of the manual post-merge verify.
- `docs/modules/platform-and-build.md` (module-doc sync);
  `docs/agent-memory/workflows.md` (memory write-back — integration procedure /
  command inventory).

## Plan

### Existing behavior

- Integration is fully manual (`docs/AI_DEVELOPMENT_WORKFLOW.md` "Parallel work"
  step 6): `git merge` each green `task-nnn` into `master`, then `./scripts/verify`
  on the merged result after each merge; `./scripts/verify-and-install.ps1` on the
  device is the owner's separate step.
- `scripts/verify.ps1` — doc gate + self-check + `:app:lintDebug` +
  `:app:testDebugUnitTest` + `:app:assembleDebug`; needs `JAVA_HOME` at a JDK 17,
  falls back to `C:\AndroidStudio\jbr`.
- `scripts/base-branch.txt` holds the integration branch name (`master`); the
  pipeline scripts read it from there.
- `scripts/worktree-common.ps1` already has helpers for base-branch read and
  worktree enumeration that `merge-task.ps1` can reuse.

### Approach

Add `scripts/merge-task.ps1` implementing the guard checks, then the
fast-forward vs `--no-ff` branch described in Requirements, calling
`scripts/verify.ps1` only on the divergent path and using `git merge --abort` as
the single rollback for both a conflict and a gate failure. Add the thin
`/merge` command, the allow-list entry, the AGENTS.md table/list rows, the
workflow-doc rewrite of step 6, the module-doc update, and the memory
write-back.

### Likely files

- New: `scripts/merge-task.ps1`, `.claude/commands/merge.md`.
- Edited: `.claude/settings.json`, `AGENTS.md`,
  `docs/AI_DEVELOPMENT_WORKFLOW.md`, `docs/modules/platform-and-build.md`,
  `docs/agent-memory/workflows.md`.
- Possibly dot-source `scripts/worktree-common.ps1` for shared helpers rather
  than duplicating base-branch / worktree logic.

### Ordered steps

1. Read `scripts/worktree-common.ps1`, `scripts/remove-worktree.ps1`, and
   `scripts/verify-and-install.ps1` for the established arg-parsing,
   base-branch-read, worktree-detection, and `$LASTEXITCODE` patterns.
2. Write `scripts/merge-task.ps1`: arg normalisation → guard checks → FF check
   (`git merge-base --is-ancestor`) → FF path (`--ff-only`, no gate) / divergent
   path (`--no-ff --no-commit` → conflict abort, or `verify.ps1` → abort on
   FAIL / `commit --no-edit` on PASS) → success reminder.
3. Add `.claude/commands/merge.md` (thin pointer) and the
   `scripts/merge-task.ps1` line in `.claude/settings.json`.
4. Update `AGENTS.md` — "Agent scripts" table row and "Agent procedures" list.
5. Rewrite `docs/AI_DEVELOPMENT_WORKFLOW.md` "Parallel work" step 6; adjust the
   "Standard task" / "Task lifecycle" wording if they describe the manual
   post-merge verify.
6. Module-doc sync: `docs/modules/platform-and-build.md`.
7. Memory write-back: `docs/agent-memory/workflows.md` — the integration
   procedure now runs through `/merge`, with evidence (script path, commit SHA).
8. Manually exercise the FF, divergent-PASS, divergent-FAIL, conflict, and
   refusal scenarios on scratch branches (see below).
9. Run `./scripts/verify.ps1`.

### Test / verification strategy

- Scratch branches off `master` in the main checkout:
  - FF: create `task-999` one commit ahead, run the script, confirm `master`
    fast-forwards and no Gradle ran.
  - Divergent PASS: add a non-conflicting commit on `master`, re-point
    `task-999`, run, confirm `verify.ps1` ran and a `--no-ff` commit landed.
  - Divergent FAIL: make the merged tree fail the gate (e.g. a deliberate lint
    break on the branch), confirm `git merge --abort` ran and `master` /
    `git status` / `MERGE_HEAD` are clean.
  - Conflict: edit the same line on `master` and `task-999`, confirm abort +
    non-zero + message.
  - Refusals: dirty tree, detached/other branch, unknown branch name,
    already-merged branch, and invocation from a linked worktree.
  - Delete the scratch branches afterwards.
- `./scripts/verify.ps1` for the doc gate (the module-doc change must register),
  self-check, lint, unit, assemble.
- Static: `.claude/commands/merge.md` form, `.claude/settings.json` entry,
  AGENTS.md table/list, no `gradlew`/`verify` call on the FF code path.

### Risks

- **Doc-only closing commit after the worktree's last verify.** `close-task`
  runs `verify.ps1` on the finished change, but the `git mv` + `## Outcome`
  commit usually lands afterwards, so on the FF path `/merge` fast-forwards a tip
  one doc-only commit ahead of the verified tree. That commit only moves a task
  file and edits Markdown — build-inert — and the owner still runs
  `./scripts/verify-and-install.ps1` (full gate) on the merged `master` before
  release. Acceptable for v1; the tree-hash cache (Out of scope) would close it
  precisely.
- **`git merge --abort` after a failed `--no-commit` merge.** Confirm it fully
  restores the pre-merge state including a clean index when the merge had staged
  changes but no `MERGE_HEAD` yet — if `--abort` refuses in that state, fall
  back to `git merge --abort || git reset --hard HEAD` and re-check `git status`.
- **Worktree detection.** `git rev-parse --git-dir` vs `--git-common-dir`
  differ inside a linked worktree; verify the check on both a real worktree
  (`C:\fs-wt\task-nnn`) and the main checkout before relying on it.
- The doc gate may not map `scripts/` to a `docs/modules/` page; the
  `platform-and-build.md` edit is required by module-doc sync regardless and
  keeps the gate green.

### Assumptions

- The task worktree ran `./scripts/verify.ps1` on the branch tip as part of
  `close-task`; `/merge` trusts that on the fast-forward path and cannot verify
  it independently in v1.
- `/merge` is a repo-owner action in the main checkout on the base branch, never
  in a task worktree — same rule as `verify-and-install.ps1`.
- Integration stays a local sequential merge (no remote, no PR); the base branch
  is whatever `scripts/base-branch.txt` names.
- No app code or test changes are needed.

## Verification

- [x] Task gate: `./scripts/verify.ps1` (Windows) or `./scripts/verify`
  (Linux/macOS/CI) — doc gate + self-check + lint + unit + assemble. `JAVA_HOME`
  must point at a JDK 17 (JBR at `C:\AndroidStudio\jbr` works).
- [ ] Integration gate (repo owner, on the merged result): `./scripts/verify`
  after merge, then `./scripts/verify-and-install.ps1` to install and launch on
  the Galaxy XCover 4s. Carry anything still pending here into `## Outcome`.
- [x] Acceptance criteria checked.
- [x] Additional scenario-specific check: the five scratch-branch scenarios (FF,
  divergent PASS, divergent FAIL, conflict, refusals) exercised and their
  results recorded in `## Outcome`.

## Outcome

**What changed**

- **New `scripts/merge-task.ps1`** (Windows-only, no POSIX counterpart) —
  `scripts/merge-task.ps1 <TASK-NNN|task-nnn> [-NoInstallReminder]`. Normalises
  the arg to the branch name via `ConvertTo-TaskId` / `Get-WorktreeBranchName`;
  reads the base branch from `scripts/base-branch.txt` (`Get-BaseBranch`).
  Guards (each exits non-zero, base branch untouched): linked worktree
  (`Test-LinkedWorktree` after `Set-Location` to the script's own repo root),
  `HEAD` not on the base branch, dirty tree/index (`git status --porcelain`),
  unknown branch, already-merged branch (`git merge-base --is-ancestor`).
  Fast-forward path (`git merge-base --is-ancestor <base> <branch>` true):
  `git merge --ff-only`, no Gradle, prints the gate-skipped message. Divergent
  path: `git merge --no-ff --no-commit` → on conflict `git merge --abort` +
  non-zero "resolve manually"; else `& scripts/verify.ps1` in a try/catch →
  FAIL/throw ⇒ `git merge --abort` + non-zero; PASS ⇒
  `git commit --no-edit -m "merge: integrate TASK-NNN (task-nnn)"`.
  `Invoke-MergeAbort` falls back to `git reset --hard HEAD` if `MERGE_HEAD`
  survives. On success prints a reminder to run `verify-and-install.ps1` and
  `remove-worktree.ps1`; `-NoInstallReminder` suppresses only that line. No
  `adb`, no emulator.
- **New `.claude/commands/merge.md`** — thin pointer (front-matter `description:`
  + one "Follow …" line), matching `.claude/commands/verify.md`.
- **`.claude/settings.json`** — added `PowerShell(./scripts/merge-task.ps1 *)`
  and the backslash variant to the committed allow-list.
- **`AGENTS.md`** — "Agent procedures" gains the `/merge` line; "Agent scripts"
  gains the `scripts/merge-task.ps1` row (Windows-only, "repo owner, main
  checkout only — never in a worktree").
- **`docs/AI_DEVELOPMENT_WORKFLOW.md`** — "Parallel work" step 6 rewritten
  around `./scripts/merge-task.ps1` (`/merge`), the FF-skip vs divergent-gate
  split, the refusal conditions, and the separate owner install step. "Standard
  task" / "Task lifecycle" needed no change (they never described a manual
  post-merge `verify`).
- **`docs/modules/platform-and-build.md`** — module-doc sync: `/merge` added to
  the "Проверка" command list plus a paragraph on FF vs divergent behaviour and
  the refusal conditions.
- **`docs/agent-memory/workflows.md`** — appended a verified record
  "Integrating a closed task branch: `/merge` (fast-forward vs divergent)".
- No app source or test change.

**Closing commit:** `docs: close TASK-032 — /merge command with fast-forward
check (TASK-032)` (this commit; `git log -- docs/tasks/done/TASK-032*`), on top
of `320cdea` (`feat: add /merge command and scripts/merge-task.ps1 (TASK-032)`).

**Verification**

- Task gate `./scripts/verify.ps1` — PASS (doc coverage PASS, self-check PASS,
  `BUILD SUCCESSFUL`; JBR at `C:\AndroidStudio\jbr`, `JAVA_HOME` unset).
- Scratch-branch scenarios on `master` in the main checkout, 2026-09-01, all
  observed as specified:
  - no args → prints usage, exit 1.
  - Refusals, each exit 1 with `git rev-parse master` unchanged: unknown branch
    (`TASK-897`), already-merged branch (`TASK-998` == `master`), `HEAD` on
    `task-998` not `master`, dirty tree (untracked file), and the worktree's own
    copy of the script run from a linked worktree (`C:\fs-wt\task-999`).
  - FF: `task-999` one commit ahead → `master` fast-forwarded to the branch tip,
    output `Task gate skipped … no Gradle work ran`, no `gradlew`/`verify.ps1`
    line; `-NoInstallReminder` suppressed the reminder.
  - Divergent PASS: non-conflicting commit on `master` + build-inert commit on
    `task-999` → `verify.ps1` ran (`BUILD SUCCESSFUL`), left a `--no-ff` commit
    `merge: integrate TASK-999 (task-999)` with two parents.
  - Divergent FAIL: `task-999` adds an undocumented source file → `verify.ps1`
    threw at the doc self-check → `git merge --abort` → `git status` clean,
    `master` unchanged, no `.git/MERGE_HEAD`, exit 1.
  - Conflict: add/add on `CONFLICT.md` → `git merge --no-ff` conflict →
    `git merge --abort` → `master` unchanged, tree clean, no `MERGE_HEAD`,
    exit 1 with the "resolve … by hand, then re-run /merge" message.
  - All scratch branches/commits/files removed; `master` reset to the
    pre-test SHA; `git worktree list` back to the main checkout only.

**Not verified here**

- Integration gate (`./scripts/verify` on a real merged `master`, then
  `./scripts/verify-and-install.ps1` on the Galaxy XCover 4s) — the repo owner's
  separate step; this task changed no app code, so no on-device behaviour
  changed.
- Known v1 gap (documented in the task body and the memory record): on the FF
  path `/merge` trusts the worktree's `close-task` `verify.ps1` run and may
  fast-forward a tip one build-inert doc-only commit ahead of it. The owner's
  `verify-and-install.ps1` on the merged `master` is the backstop; a tree-hash
  verify cache is a deferred follow-up (Out of scope).
