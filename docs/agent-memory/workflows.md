# Workflows Memory

This file contains non-obvious workflows that should be followed consistently.
Each record includes evidence and a re-verification method.

**Append-only (parallel-work rule, TASK-019):** add a new record at the END of
this file as its own `---`-separated block. Do not renumber, reformat, or
rewrite existing records or this preamble. Parallel tasks in separate worktrees
all touch these memory files, so append-only edits keep merges conflict-free.
`docs/agent-memory/INDEX.md` changes only when a whole topic is added.

---

## Windows verification requires the final status marker

Status: verified

Last verified: 2026-08-28

Workflow:
After a complete Windows code change, run
`./scripts/verify-and-install.ps1` once. Report success only when
`app/build/verify-and-install-status.txt` contains `PASS`; the success marker
is written only after verification, installation, and activity launch.

Why this matters:
Long Gradle output can be truncated, so a successful-looking terminal excerpt
does not establish that the complete device scenario passed.

Evidence:

- `AGENTS.md` (Gradle verification discipline)
- `scripts/verify-and-install.ps1`
- `docs/modules/platform-and-build.md`

Verification:
Inspect the marker handling in `scripts/verify-and-install.ps1` and the
resulting `app/build/verify-and-install-status.txt` after a run.

---

## Task status is checkbox-only; closing a task moves the file

Status: verified

Last verified: 2026-08-28

Workflow:
There is no task tracker or index. A task's status is the `- [ ]` / `- [x]`
checkboxes inside its own `docs/tasks/TASK-NNN-*.md` file. Open tasks sit in
`docs/tasks/`; `docs/tasks/done/` is the archive. To close a task: tick the
checkboxes that hold, fill its `## Outcome` section (changed files/behavior,
closing commit SHA, anything still not verified), `git mv` the file to
`docs/tasks/done/`, and name the task id in the closing commit subject. One
file per task — a plan is a `## Plan` section, not a second file.

Why this matters:
A future agent that leaves finished tasks in `docs/tasks/` with stale
checkboxes, or that starts a second numbered file for a plan, breaks the only
signal of what work is still open (`ls docs/tasks/*.md`).

Evidence:

- `docs/AI_DEVELOPMENT_WORKFLOW.md` (Task lifecycle and status)
- `AGENTS.md` (Before starting work; Definition of done)
- `docs/tasks/TEMPLATE.md` (`## Outcome`)
- `docs/tasks/done/` (archived TASK-000, TASK-001, TASK-003)

Verification:
`ls docs/tasks/ docs/tasks/done/`; confirm archived tasks have a filled
`## Outcome` and that `docs/AI_DEVELOPMENT_WORKFLOW.md` still describes this
lifecycle.

---

## Documentation coverage gate: case-sensitive prefixes + completeness self-check

Status: verified

Last verified: 2026-08-30

Workflow:
`scripts/check-documentation` / `scripts/check-documentation.ps1` maps every
source area under `app/src/main/` and every root Gradle file to one
`docs/modules/` page via a prefix table (`$coverage` / `coverage=(...)`). Prefix
matching is **case-sensitive** (`String.StartsWith(.., Ordinal)` in PowerShell,
plain `grep`/glob in bash), which is why the pre-TASK-018 rule
`.../app/Display` matched only the root `Display*.kt` files and never the
`.../app/display/` package — each needs its own entry. Run
`scripts/check-documentation --self-check` (`.ps1`: `-SelfCheck`) to prove the
table is exhaustive: it walks tracked **and** untracked files under
`app/src/main/` plus the root Gradle files (`*.gradle`, `gradle.properties`,
`gradle/wrapper/`, `gradlew*`) and fails on any path that matches neither a
coverage rule nor the in-script `$selfCheckExclusions` list (launcher icon set,
`gradlew` wrapper scripts). `scripts/verify` and `scripts/verify.ps1` run this
self-check next to the normal change-gate.

The change-gate's base-ref mode — `./scripts/verify <ref>` /
`./scripts/verify.ps1 -BaseRef <ref>` → `check-documentation` — is a **union**
of the `<ref>...HEAD` merge-base range and the working tree (uncommitted +
untracked). Without a base ref the behaviour is unchanged (working tree only).
The worktree pipeline (TASK-019) is the intended caller of the base-ref mode.

Why this matters:
Before TASK-018 the entire left-display package had no working coverage rule, so
TASK-009/013/014 changed `display/` with the gate silent. Adding a new
top-level package or resource directory now fails the self-check until it is
either documented (new `$coverage` entry) or explicitly excluded with a reason.
Keep the PowerShell and bash implementations identical: same table, same
exclusions, same union semantics.

Evidence:

- `scripts/check-documentation`, `scripts/check-documentation.ps1`
  (`$coverage` / `coverage`, `$selfCheckExclusions` / `self_check_exclusions`,
  `--self-check` / `-SelfCheck`)
- `scripts/verify`, `scripts/verify.ps1` (self-check call, `-BaseRef`
  forwarding)
- `docs/modules/platform-and-build.md` ("Проверка", "Ресурсы и тема приложения")
- `docs/tasks/done/TASK-018-doc-coverage-gate-correctness.md`

Verification:
`./scripts/check-documentation --self-check` exits 0 on a clean checkout; create
`app/src/main/java/com/fieldscanner/app/tmpcheck/Probe.kt` and it exits 1
naming the file. Append a line to any file under `display/` without touching
`docs/modules/ui-and-input.md` and `./scripts/verify.ps1` fails.

---

## Parallel agents run in separate git worktrees (Windows only)

Status: verified

Last verified: 2026-08-30

Workflow:
Two agents work at once, each in its own worktree on its own branch, sharing
one `.git`. Scripts (all Windows-only PowerShell; no POSIX equivalents):

- `scripts/base-branch.txt` — the one place the integration branch (`master`)
  is named. `Get-BaseBranch` in `scripts/worktree-common.ps1` reads it.
- `scripts/next-task-id.ps1` — prints the next `TASK-NNN` (stdout, one line),
  scanning `docs/tasks/` + `docs/tasks/done/` and untracked task files in every
  `git worktree list` entry. Not atomic (stderr note): two simultaneous calls
  can return the same id.
- `scripts/new-worktree.ps1 TASK-NNN [-WorktreeRoot C:\fs-wt] [-ApplyRepoGitConfig]`
  — branch `task-nnn` from `master` at a short root (the only real MAX_PATH
  mitigation; `core.longpaths` only affects git, not the Gradle/kapt/javac
  build). Copies `local.properties`, never the caches. `-ApplyRepoGitConfig`
  sets `gc.auto=0` + `core.longpaths=true` **repository-wide** (all worktrees
  share `.git/config`; per-worktree config would need
  `extensions.worktreeConfig` + `git config --worktree`) and prints what it
  changed and how to revert. Re-run with an existing branch/dir fails before
  any side effect.
- `scripts/remove-worktree.ps1 TASK-NNN [-Force]` — refuses unless
  `git merge-base --is-ancestor task-nnn master` (a squash-merge does not
  count); on a merged branch removes the worktree, deletes its
  `.gradle-user-home/` (~1 GB; `git worktree remove` leaves it), prunes, and
  `git branch -d`. `-Force` for locked handles; it does not bypass the merged
  check.

Gate: `scripts/verify.ps1` **in the worktree** is the task gate. It detects a
linked worktree (`git rev-parse --git-dir` != `--git-common-dir`) and passes
`-BaseRef master` to `check-documentation.ps1` so a missed `docs/modules/`
update is caught even after it is committed on the branch. On detached HEAD or a
branch with no merge-base to `master` it warns and falls back to the working-tree
gate (still a real run of all three checks — never a silent pass). It warns when
run on `master` itself only if more than one worktree exists. The main checkout
with one worktree is unchanged.

Parallel-safety and accepted compromises:

- Only `scripts/verify.ps1` is parallel-safe by cache: `GRADLE_USER_HOME` and
  `app/build/` resolve under the worktree root. POSIX `scripts/verify` uses
  `${GRADLE_USER_HOME:-$HOME/.gradle}` — shared across worktrees, not
  parallel-safe (out of scope to fix; documented only).
- On-device install/launch is **not** part of closing a task. The repo owner
  merges green branches into `master` one at a time, re-runs `scripts/verify`
  on each merge (integration check for manifest/resource/shared-constant
  conflicts between independently-green branches), then runs
  `scripts/verify-and-install.ps1` on the merged result. Runtime crashes on
  start and manifest/resource merge breakage surface there (TASK-020 smoke test
  covers part of it).
- Practical ceiling is two agents (two Gradle + two kapt + two lint per
  workstation); no governor.
- `gc.auto` / `core.longpaths` set by `-ApplyRepoGitConfig` are repository-wide
  and never unset by the scripts.
- Each worktree is another on-disk copy of `local.properties`, which carries
  `OPENAI_API_KEY`.

Traps found while building this:

- In a worktree freshly checked out under `core.autocrlf=true` every file is
  CRLF; `git commit -am` with `-c core.autocrlf=false` then rewrites **all**
  ~170 files as CRLF blobs. Stage the specific files and commit normally.
- The task gate needs JDK 17 (AGP 8.0.2); the `PATH` `java` on the reference
  workstation is Java 8. As of TASK-031 `scripts/verify.ps1` resolves this
  itself via `scripts/jdk-common.ps1` (`Resolve-Jdk17`): existing `JAVA_HOME`
  wins, else it falls back to the Android Studio JBR (`C:\AndroidStudio\jbr`),
  else it stops with a clear message before Gradle. `verify-and-install.ps1` /
  `install.ps1` get the same fallback through `device-install-common.ps1`
  (non-fatal there). Set `JAVA_HOME` manually only on a machine with no
  `C:\AndroidStudio\jbr`.
- PowerShell array-splat of a named parameter (`& script @('-BaseRef', $x)`)
  binds `$BaseRef` to the literal `'-BaseRef'`; call `-BaseRef $x` explicitly.

Why this matters:
A shared working tree + index means one agent's `git commit` captures the
other's files; feature branches do not help. The old rule "do not automate
multi-agent orchestration" is replaced by this per-worktree procedure.

Evidence:

- `scripts/new-worktree.ps1`, `scripts/remove-worktree.ps1`,
  `scripts/next-task-id.ps1`, `scripts/worktree-common.ps1`,
  `scripts/base-branch.txt`, `scripts/verify.ps1` (worktree-awareness block)
- `docs/AI_DEVELOPMENT_WORKFLOW.md` ("Parallel work (git worktree, Windows)")
- `AGENTS.md` ("Before starting work" item 4; "Definition of done" two gates)
- `docs/tasks/done/TASK-019-parallel-worktree-pipeline.md`

Verification:
`./scripts/next-task-id.ps1` prints `TASK-NNN` on stdout only.
`./scripts/new-worktree.ps1 TASK-999` then, in `C:\fs-wt\task-999`, commit a
one-line change under `display/` without the doc and run `./scripts/verify.ps1`:
it prints "comparing against 'master'" and the doc gate fails. Re-run
`new-worktree.ps1 TASK-999` — fails, no side effect.
`./scripts/remove-worktree.ps1 TASK-999` refuses (unmerged); on a commit-free
worktree it removes everything including `.gradle-user-home`.

---

## Seed a new worktree with the verified Gradle cache

Status: verified

Last verified: 2026-08-31

Workflow:
For a new task worktree, use
`./scripts/new-worktree.ps1 TASK-NNN -SeedGradleCache` to seed only its
`.gradle-user-home/` from a quiescent, successfully verified base checkout.
Do not copy the base checkout's `app/build/`: build outputs are branch-specific.
Each worktree keeps the copied cache private for subsequent Gradle writes.
Exclude transient `.tmp/` and `daemon/` directories from the seed.

Measured result:
On this workstation, copying a 1.03 GiB cache from the warmed `master` checkout
took 11.9 seconds. With an empty `app/build/`, a new `task-024` worktree then
passed `./scripts/verify.ps1` in 1m 41s (`BUILD SUCCESSFUL`; 50 tasks
executed). This replaces the earlier 5–12 minute cold-cache runs, but is not a
substitute for a warm branch-local `app/build/` (a no-change main checkout run
was about 12 seconds).

Why this matters:
Sharing one writable `GRADLE_USER_HOME` between concurrent worktrees defeats
the pipeline's cache isolation and risks lock/contention problems. A copy-on-
create seed removes network and dependency-resolution delay without sharing
live cache state. It costs roughly 1 GiB of temporary disk per active worktree.
If a Gradle run is forcibly interrupted, wait for Java processes to exit and run
`./gradlew.bat clean --no-daemon --console=plain` in that worktree before
retrying; the experiment otherwise hit a locked
`app/build/intermediates/incremental/debug-mergeJavaRes/zip-cache` directory.

Evidence:

- Experiment on `master` / temporary `C:\fs-wt\task-024`, 2026-08-31:
  worktree creation 0.8s; cache copy 11.9s; task gate PASS in 1m 41s.
- `scripts/verify.ps1` sets `GRADLE_USER_HOME` below each worktree root.
- `scripts/new-worktree.ps1 -SeedGradleCache` copies only the cache after
  `git worktree add` succeeds; it rejects a missing source before any branch or
  worktree is created and removes the new worktree/branch if copying fails.

Verification:
After a successful base verification and with no Gradle process running, create
a throwaway worktree with `-SeedGradleCache`, confirm the copied cache excludes
`.tmp/` and `daemon/`, make a harmless documented source change, and run
`./scripts/verify.ps1` (it resolves JDK 17 itself since TASK-031; setting
`JAVA_HOME=C:\AndroidStudio\jbr` is now optional). Confirm a full PASS and that
the new worktree's `app/build/` was not copied.

---

## Shared Gradle User Home for local worktrees

Status: verified

Last verified: 2026-08-31

Supersedes the repository-local-cache policy above (TASK-019) and the
seed-cache procedure above (TASK-024): `scripts/verify.ps1` and
`scripts/verify-and-install.ps1` no longer set `GRADLE_USER_HOME`, so they use
the normal per-user Gradle User Home unless a caller explicitly sets one.
`scripts/new-worktree.ps1` has no cache-seeding option. Do not copy Gradle
caches into new worktrees.

Why this matters:
Gradle's dependency cache uses file locks and supports concurrent local Gradle
processes that can communicate. Sharing it avoids roughly 1 GiB per worktree
and the cold-download delay. A worktree still has its own project-local
`.gradle/` and `app/build/`, so branch-specific incremental state and outputs
remain isolated. With limited CPU/RAM, limit simultaneous verification runs to
two; serialization is a performance choice, not a cache-correctness requirement.

Evidence:

- `scripts/verify.ps1`, `scripts/verify-and-install.ps1`, and
  `scripts/new-worktree.ps1` after the TASK-024 amendment.
- Gradle User Manual, "Dependency Caching" → "Cache locking": local
  concurrent access is safe through file-based locking; "Directory layout":
  `.gradle/` and `build/` are project-local generated state.
  https://docs.gradle.org/current/userguide/dependency_caching.html
  https://docs.gradle.org/current/userguide/directory_layout.html

Verification:
With two worktrees on the same host, run `./scripts/verify.ps1` in each and
confirm both use the same per-user Gradle User Home while retaining distinct
`<worktree>/.gradle/` and `<worktree>/app/build/` directories.

---

## Retrieving owner diagnostics from a device

Status: verified

Last verified: 2026-08-31

FieldScanner writes owner diagnostics into the app-private files directory as
`field-scanner-diagnostics.log` and at most one rotated predecessor
`field-scanner-diagnostics.1.log`. Retrieve both without exposing them in the
child UI:

`adb shell run-as com.fieldscanner.app sh -c 'cat files/field-scanner-diagnostics.log; cat files/field-scanner-diagnostics.1.log 2>/dev/null' > field-scanner-diagnostics.txt`

The implementation redacts the configured OpenAI key and Authorization values;
do not put the exported file in version control.

Evidence:

- `app/src/main/java/com/fieldscanner/app/data/diagnostics/remote/FileDiagnosticsLog.kt`
- `app/src/main/java/com/fieldscanner/app/data/diagnostics/remote/AndroidDiagnosticsLog.kt`

Verification:
Trigger a known failure on a debuggable installed build, run the command above,
and confirm the resulting file has a classified reason while containing no API key.

---

## Agent procedures: single canon, pointer-only `.claude` projections

Status: verified

Last verified: 2026-08-31

Workflow:
Every repeatable procedure is defined once. The canon is
`docs/skills/<name>/GUIDE.md` for procedures and `docs/agents/*.md` for roles.
Claude Code reaches a procedure through a thin projection in `.claude/`:
`.claude/commands/<name>.md` for a deliberate `/<name>` command (no auto
trigger), or `.claude/skills/<name>/SKILL.md` for a background auto-skill that
fires on its `description`. The body of every projection file is **only** a
pointer line ("Follow `docs/skills/<name>/GUIDE.md`" or a section reference) —
never a copy of the steps. Nothing is duplicated, so there is no sync generator
and no anti-drift gate. Codex uses the same canon via pointers in `AGENTS.md`
("Agent procedures", "Agent scripts").

Rules that hold:

- No third home for knowledge. A `GUIDE.md` is created only for a procedure with
  no existing single home. `verify`, `install`, `verify-install` and
  `worktree-parallel` have no GUIDE: the first three point at `AGENTS.md`
  ("Definition of done", "Gradle verification discipline") plus the
  `scripts/install.ps1` row in "Agent scripts", and `worktree-parallel` points at
  `docs/AI_DEVELOPMENT_WORKFLOW.md` ("Parallel work").
- command vs skill: a procedure a human starts on purpose is a
  `.claude/commands/` command; a procedure an agent should pick up on its own is
  a `.claude/skills/` skill whose `description` is the trigger.
- The only text allowed to repeat across the scheme is the one-line
  `description` (needed in the GUIDE and, as the trigger, in `SKILL.md`).
- Enforcement stays with the documentation gate, not the auto-skill. Skill
  triggering on `description` is heuristic and only improves execution quality;
  `check-documentation` is what actually fails a build when a covered module
  changed without its `docs/modules/*` page.

Why this matters:
A future agent editing a procedure must edit the GUIDE (or the named `AGENTS.md`
/ workflow section), not the `.claude/` stub. Adding steps to a stub silently
recreates the drift this layer was built to remove.

Evidence:

- `docs/skills/new-task/GUIDE.md`, `docs/skills/close-task/GUIDE.md`,
  `docs/skills/module-doc-sync/GUIDE.md`,
  `docs/skills/memory-writeback/GUIDE.md`
- `.claude/commands/new-task.md`, `close-task.md`, `verify.md`, `install.md`,
  `verify-install.md`, `worktree-parallel.md`;
  `.claude/skills/module-doc-sync/SKILL.md`,
  `.claude/skills/memory-writeback/SKILL.md`
- `scripts/install.ps1`, `scripts/device-install-common.ps1` (install + launch
  helper shared with `scripts/verify-and-install.ps1`) — added by TASK-030
- `AGENTS.md` ("Agent procedures", "Agent scripts")
- `.claude/settings.json` (committed script allow-list;
  `.claude/settings.local.json` stays git-ignored)
- `docs/agents/PLANNER.md`, `IMPLEMENTER.md`, `VERIFIER.md`
- `docs/AI_DEVELOPMENT_WORKFLOW.md` ("Procedures and roles")
- `docs/tasks/done/TASK-021-agent-skills-and-tools-layer.md`

Verification:
Read all eight files under `.claude/commands/` and `.claude/skills/`: each is
frontmatter plus a single pointer line. `wc -l AGENTS.md` is <= its
pre-TASK-021 length (211; currently 193). `git check-ignore
.claude/settings.local.json` still matches; `git ls-files .claude/settings.json`
lists it.

---

## Integrating a closed task branch: `/merge` (fast-forward vs divergent)

Status: verified

Last verified: 2026-09-01

Workflow:
The repo owner integrates a closed `task-nnn` branch into the base branch
(`scripts/base-branch.txt`, currently `master`) with
`./scripts/merge-task.ps1 <TASK-NNN|task-nnn> [-NoInstallReminder]` (`/merge`),
run in the **main checkout on the base branch**. It branches on whether the
merge changes the tree:

- **Fast-forward** (`git merge-base --is-ancestor <base> task-nnn` and the base
  is not already an ancestor of nothing new): the merged tree is byte-identical
  to the branch tip the task worktree already verified, so `/merge` does
  `git merge --ff-only` and runs **no** Gradle work at all (no `gradlew`, no
  `verify.ps1`). It prints that the gate was skipped.
- **Divergent** (base moved since the branch was cut, or several branches in one
  batch): `git merge --no-ff --no-commit`, then `./scripts/verify.ps1` on the
  merged tree. On PASS it `git commit --no-edit -m "merge: integrate TASK-NNN
  (task-nnn)"`. On a merge conflict, or on gate FAIL, it runs `git merge
  --abort` (single rollback for both) — base branch, index and working tree
  unchanged, no `MERGE_HEAD` — and exits non-zero.

Refusals (exit non-zero, base branch untouched): run from a linked worktree
(the worktree's own copy of the script detects `git rev-parse --git-dir` !=
`--git-common-dir` after `Set-Location` to its own root), `HEAD` not on the base
branch, dirty tree/index (`git status --porcelain` non-empty), unknown branch,
or already-merged branch (`git merge-base --is-ancestor task-nnn <base>`).
`/merge` never runs `adb` or starts an emulator; installing and launching on the
XCover 4s stays the owner's separate `./scripts/verify-and-install.ps1` step on
the merged base branch, and worktree cleanup stays `./scripts/remove-worktree.ps1`.

Known v1 gap: `close-task` runs `verify.ps1` on the finished change, but the
`git mv` + `## Outcome` commit usually lands after it, so on the FF path `/merge`
fast-forwards a tip one build-inert doc-only commit ahead of the last verified
tree. The owner's `verify-and-install.ps1` on the merged base branch is the
backstop. A tree-hash verify cache (skip the gate on the divergent path too when
the post-merge tree already has a PASS record) is a deferred follow-up.

Why this matters:
Before this, integration was a manual `git merge` per branch with a manual
`./scripts/verify` "after each merge" (`docs/AI_DEVELOPMENT_WORKFLOW.md` step 6).
Nothing enforced the post-merge gate, so a clean-but-broken combination of two
independently-green branches (same manifest entry, same string/resource, same
shared constant, overlapping `AppContainer` wiring, duplicate test names,
version-catalog edits) could reach the base branch unnoticed; and on a plain
fast-forward the re-run was pure duplication of what the worktree already did.

Evidence:

- `scripts/merge-task.ps1` (guards → FF check `git merge-base --is-ancestor` →
  `--ff-only` no-gate path / `--no-ff --no-commit` + `verify.ps1` path;
  `Invoke-MergeAbort` fallback to `git reset --hard HEAD` if `MERGE_HEAD`
  survives), added by TASK-032.
- `.claude/commands/merge.md` (thin pointer), `.claude/settings.json`
  (`scripts/merge-task.ps1 *` in the allow-list).
- `AGENTS.md` ("Agent procedures" `/merge` line; "Agent scripts" table row).
- `docs/AI_DEVELOPMENT_WORKFLOW.md` "Parallel work" step 6 (rewritten).
- `docs/modules/platform-and-build.md` ("Проверка", `/merge` paragraph).
- `docs/tasks/done/TASK-032-merge-command-with-ff-check.md`.
- Scenarios exercised on scratch branches 2026-09-01: no-args usage/exit 1;
  refusals (unknown branch, already-merged, HEAD off base, dirty tree, from a
  linked worktree) each exit 1 with base branch unchanged; FF advances the base
  branch to the branch tip with no Gradle run; divergent-PASS runs `verify.ps1`
  and leaves a `--no-ff` commit `merge: integrate TASK-999 (task-999)` with two
  parents; divergent-FAIL (undocumented source file → doc self-check) and
  conflict (add/add) both `git merge --abort` and leave a clean tree with no
  `MERGE_HEAD`.

Verification:
On the main checkout on the base branch: `powershell -NoProfile -File
scripts/merge-task.ps1` prints usage and exits 1. Create `task-999` one commit
ahead of the base branch, run `./scripts/merge-task.ps1 TASK-999` — the base
branch fast-forwards, output says the gate was skipped, no `gradlew` line
appears. Add a non-conflicting commit on the base branch, re-point `task-999`,
re-run — `verify.ps1` runs and a `--no-ff` merge commit lands. Reset the base
branch and delete the scratch branch afterwards.
