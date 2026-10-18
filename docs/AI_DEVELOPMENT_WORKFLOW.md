# AI development workflow

## Standard task

1. Create a task from `docs/tasks/TEMPLATE.md` as `docs/tasks/TASK-NNN-slug.md` (one file per task; a plan, if needed, is a `## Plan` section, not a second file).
2. For a non-trivial change, prepare a plan.
3. Implement only the approved scope and update tests.
4. Run `./scripts/verify-and-install.ps1` on Windows (or add `-Target emulator`), or `./scripts/verify` on Linux/macOS/CI. The Windows command verifies, installs and opens the fresh APK.
5. Have an independent verifier check the task, diff, and evidence.
6. Address findings, repeat verification, and only then close the task.

## Task lifecycle and status

Status is tracked only inside the task file — there is no separate tracker or index.

- **Open tasks** live in `docs/tasks/*.md`. `Acceptance criteria` and `Verification` use `- [ ]` / `- [x]` checkboxes.
- **Closing a task:** check the boxes that hold, fill the `## Outcome` section (what changed, closing commit SHA, anything still not verified), then `git mv` the file to `docs/tasks/done/`.
- The **closing commit** references the task id in its subject, e.g. `refactor: extract MainActivity wiring (TASK-003)`. This makes the task↔code link bidirectional (`git log -- docs/tasks/done/TASK-003*.md`).
- **List open tasks:** `ls docs/tasks/*.md`, or `grep -rL '\- \[ \]' docs/tasks/*.md` for ones whose boxes are all checked but not yet archived.

Tasks are transient intent ("do this"). Durable knowledge discovered while doing them goes to `docs/agent-memory/` as write-back, not into the archived task.

## Small task

For a genuinely local, low-risk change, use: task → implementer → verifier → CI. The verifier and reproducible checks remain required.

## Procedures and roles

Repeatable procedures are defined once under `docs/skills/<name>/GUIDE.md` — that
is the canon. Roles are defined once under `docs/agents/*.md`. Claude Code
projects each procedure into `.claude/commands/<name>.md` (an explicit
`/<name>`, no auto trigger) or `.claude/skills/<name>/SKILL.md` (a background
auto-skill keyed on its `description`); the body of every such file is **only** a
pointer to the canon — never a copy of the steps, so there is nothing to keep in
sync and no generator. Codex follows the same canon through pointers in
`AGENTS.md` ("Agent procedures", "Agent scripts"). When a procedure already
lives entirely in `AGENTS.md` or this file, no `GUIDE.md` is created and the
stub points straight at that section (as for `verify`, `install`,
`verify-install` and `worktree-parallel`).

## Parallel work (git worktree, Windows)

Two agents may run different tasks at the same time, each in **its own git
worktree** on **its own branch**, sharing one `.git`. One branch — one worktree.
Never run two agents in the main checkout: a shared working tree and index make
one agent's `git commit` capture the other's uncommitted files, and feature
branches alone do not fix that (checkout, `HEAD` and the index are one per
directory).

Base (integration) branch: **`master`**, named once in `scripts/base-branch.txt`
and read from there by every pipeline script. There is no remote and no `main`
branch — integration is a local sequential merge, not a pull request.

1. **Pick an id:** `./scripts/next-task-id.ps1` — scans `docs/tasks/` and
   `docs/tasks/done/` in every worktree (`git worktree list`), including
   untracked task files, and prints the next `TASK-NNN`. Issuance is **not
   atomic**: a simultaneous call in another worktree can return the same id, so
   confirm it is still free before you commit its task file.
2. **Create the worktree:** `./scripts/new-worktree.ps1 TASK-NNN` — adds branch
   `task-nnn` from `master` and checks it out at a **short** root
   (`C:\fs-wt\task-nnn`; the short path is the only real mitigation for the
   build's MAX_PATH, `core.longpaths` does not help there) and copies
   `local.properties`. Gradle distributions and dependency caches remain in the
   normal shared per-user Gradle User Home; do not seed or copy them. Each
   worktree still creates its own project-local `.gradle/` and `app/build/`.
   Once per parallel session, add `-ApplyRepoGitConfig` to
   set `gc.auto=0` + `core.longpaths=true` **repository-wide** (all worktrees
   share `.git/config`; the script reports it and how to revert).
3. **Work** in `C:\fs-wt\task-nnn`. Its own `app/build/` and `.gradle/` keep
   branch-specific build state separate; the shared per-user Gradle cache is
   synchronized by Gradle for local concurrent processes.
4. **Task gate:** `./scripts/verify.ps1` **in the worktree** — documentation
   gate (auto `-BaseRef master`, so a missed `docs/modules/` update is caught
   even after it is committed on the branch), completeness self-check,
   `:app:lintDebug`, `:app:testDebugUnitTest`, `:app:assembleDebug`. No device.
   `./scripts/verify-and-install.ps1` is **not** part of the per-task cycle and
   must not be run by two agents at once.
5. **Close the task** in the worktree the normal way (checkboxes, `## Outcome`,
   `git mv` to `docs/tasks/done/`, task id in the commit subject), then commit
   on `task-nnn`.
6. **Integration gate (repo owner, main checkout):** integrate green branches
   into `master` one at a time with `./scripts/merge-task.ps1 TASK-NNN`
   (`/merge`). It reads the base branch from `scripts/base-branch.txt` and:
   - **fast-forward** (`master` has not moved since the branch was cut): the
     merged tree is byte-identical to the branch tip the worktree already
     verified, so `/merge` fast-forwards and runs **no** Gradle work;
   - **divergent** (`master` moved, or several branches in one batch): `/merge`
     does a `--no-ff` merge and runs `./scripts/verify` on the result (it
     catches manifest / resource / shared-constant conflicts between two
     independently-green branches), keeping the merge commit only if the gate
     passes; on a conflict or a gate failure it `git merge --abort`s and leaves
     `master` untouched.
   `/merge` refuses to run from a linked worktree, off `master`, with a dirty
   tree, or for an unknown / already-merged branch, and never touches a device.
   Installing and launching on the Galaxy XCover 4s stays the owner's separate
   step on the merged `master` (`./scripts/verify-and-install.ps1`) after the
   batch — it is not part of closing an individual task.
7. **Clean up:** `./scripts/remove-worktree.ps1 TASK-NNN` — refuses an unmerged
   branch; on a merged one removes the worktree, any legacy
   `.gradle-user-home`, the branch, and prunes the registration. `-Force` for locked handles (Android
   Studio / antivirus).

The worktree helper scripts are **Windows-only** by design; there are no POSIX
equivalents (on Linux/macOS use a second clone). Practical ceiling is two agents
(two Gradle + two kapt + two lint on one workstation). Full procedure and
accepted compromises: `docs/agent-memory/workflows.md`.
