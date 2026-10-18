# FieldScanner repository instructions

## Project map

FieldScanner is a Kotlin Android application for a Samsung Galaxy XCover 4s.

- Application code and Android resources: `app/src/main/`
- Local unit tests: `app/src/test/`
- Instrumented tests: `app/src/androidTest/`
- Gradle configuration: root `build.gradle`, `settings.gradle`, `app/build.gradle`
- Product-specific constraints: `app/AGENTS.md`
- Architecture and task records: `docs/`
- Shared agent memory: `docs/agent-memory/`

Keep generated build output, local properties, crash logs, and IDE files out of commits.

# Shared Agent Instructions

This repository uses a shared, vendor-neutral project memory.

The shared memory is stored in:

`docs/agent-memory/`

Both Claude Code and Codex use this memory. `CLAUDE.md` imports this file so
general repository rules remain defined in one place.

## Memory startup protocol

Before starting any non-trivial task:

1. Read `docs/agent-memory/INDEX.md` from disk.
2. Identify memory topics relevant to the current task.
3. Read only those topic files.
4. Verify important or potentially stale facts against the current source code,
   tests, configuration, migrations, or authoritative docs.

Do not assume the copy of memory already present in conversation context is
current. Read the index from disk for every substantial new task.

## Source-of-truth precedence

When information conflicts, use this order:

1. Current executable code, tests, configuration, schemas, and migrations.
2. Current authoritative project documentation and ADRs.
3. `docs/agent-memory/`.
4. Agent-specific/native memory.
5. Previous conversation context.

Shared memory is a navigation and knowledge layer, not a replacement for
verifying the repository.

## Memory write-back protocol

Write-back is part of finishing a task, not a separate favor: before declaring a
task done, decide whether it produced durable, verified knowledge that would
help a future agent, and if so update `docs/agent-memory/` in the same change.
The full procedure — when it applies, which topic file, the append-only rule,
and the evidence requirement — is `docs/skills/memory-writeback/GUIDE.md`.

## Stale memory

If shared memory conflicts with the current repository:

1. Follow the repository.
2. Correct or remove the stale memory during the same task.
3. Do not preserve outdated guidance for compatibility with previous agents.

## Memory structure

`docs/agent-memory/INDEX.md` is an index, not a knowledge dump.

Keep detailed information in focused topic files.

Prefer updating an existing topic over creating many tiny files.

## Before starting work

1. Read this file, `app/AGENTS.md`, `docs/agent-memory/INDEX.md`, the relevant task in `docs/tasks/` (open tasks only; `docs/tasks/done/` is the archive), and relevant architecture notes.
2. Inspect the existing implementation and run `git status` before editing.
3. Keep the change within task scope; do not include unrelated refactors or dependency upgrades.
4. Work that could run in parallel with another agent goes in its own git worktree (Windows: `./scripts/new-worktree.ps1 TASK-NNN`, branch `task-nnn` from the base branch `master` named in `scripts/base-branch.txt`). Never run two agents in the main checkout. See `docs/AI_DEVELOPMENT_WORKFLOW.md` "Parallel work".

The task lifecycle (checkboxes, `## Outcome`, `git mv` to `docs/tasks/done/`, task id in the closing commit subject) is described in `docs/AI_DEVELOPMENT_WORKFLOW.md`.

## Agent procedures

Every repeatable procedure is defined once. The canon is
`docs/skills/<name>/GUIDE.md` (procedures) and `docs/agents/*.md` (roles). Claude
Code projects each procedure as a thin `/command` or auto-skill whose body is
only a pointer to the canon; Codex follows the pointers below. When a procedure
already lives entirely in a section of this file or
`docs/AI_DEVELOPMENT_WORKFLOW.md`, no GUIDE is created and the pointer targets
that section directly.

- Create a task — `docs/skills/new-task/GUIDE.md`
- Close a task — `docs/skills/close-task/GUIDE.md`
- Sync `docs/modules/*` after a covered change — `docs/skills/module-doc-sync/GUIDE.md`
- Memory write-back — `docs/skills/memory-writeback/GUIDE.md`
- Run the task gate — `/verify`: "Definition of done" and "Gradle verification discipline" in this file
- Install the already-built APK on the device — `/install`: the `scripts/install.ps1` row in "Agent scripts" below
- Run the gate then install (integration gate) — `/verify-install`: `scripts/verify-and-install.ps1`; "Definition of done" in this file
- Integrate a closed task branch — `/merge`: "Parallel work" in `docs/AI_DEVELOPMENT_WORKFLOW.md`
- Parallel worktrees — `docs/AI_DEVELOPMENT_WORKFLOW.md`, "Parallel work (git worktree, Windows)"

## Agent scripts

All executable entry points. Run them from the repository root. The "Run in"
column says where each is allowed.

| Windows | POSIX | Arguments | Purpose | Run in |
| --- | --- | --- | --- | --- |
| `scripts/verify.ps1` | `scripts/verify` | `[-BaseRef <ref>]` / `[<base-ref>]` | Task gate: doc gate + self-check + `:app:lintDebug` + `:app:testDebugUnitTest` + `:app:assembleDebug`. No device. | task worktree or main checkout |
| `scripts/verify-and-install.ps1` | — | `[-Target usb\|emulator]` | Task gate, then install and launch the APK on the device. | repo owner, merged result only — never in a worktree, never two at once |
| `scripts/install.ps1` | — | `[-Target usb\|emulator]` | Install and launch the already-built debug APK, no gate. | repo owner, merged result — never in a worktree, never two at once |
| `scripts/check-documentation.ps1` | `scripts/check-documentation` | `[-BaseRef <ref>]` / `[<ref>]`, `[-SelfCheck]` / `--self-check` | Documentation coverage gate; normally invoked by `verify`. | task worktree or main checkout |
| `scripts/next-task-id.ps1` | — | none | Print the next free `TASK-NNN` on stdout (issuance is not atomic). | any checkout |
| `scripts/new-worktree.ps1` | — | `TASK-NNN [-WorktreeRoot <dir>] [-ApplyRepoGitConfig]` | Create branch `task-nnn` and its worktree from `master`. | main checkout |
| `scripts/remove-worktree.ps1` | — | `TASK-NNN [-Force]` | Remove a merged task worktree and its branch. | main checkout |
| `scripts/merge-task.ps1` | — | `<TASK-NNN\|task-nnn> [-NoInstallReminder]` | Integrate a closed task branch into the base branch; runs the task gate only when the merge is not a fast-forward. | repo owner, main checkout only — never in a worktree |
| `scripts/base-branch.txt` | same | data file | The one place the integration branch (`master`) is named. | — |

The worktree helper scripts are Windows-only by design; on Linux/macOS use a
second clone. Full parallel-work procedure and accepted compromises:
`docs/AI_DEVELOPMENT_WORKFLOW.md` and `docs/agent-memory/workflows.md`.

## Development rules

- Use Kotlin and traditional Android Views/XML unless explicitly approved otherwise.
- Preserve minSdk 24 and optimize for the XCover 4s's limited resources.
- Prefer Android/AndroidX APIs and do not add dependencies without a concrete need.
- Add or update tests when changing observable logic.
- When a covered module changes, analyze the diff and update the matching `docs/modules/` summary yourself; the verification scripts enforce that the page changed.
- Do not weaken tests, lint, or build checks to make verification pass.
- Never perform destructive device, bootloader, firmware, or data operations without explicit approval.

### Layering standard (see TASK-012)

- Persistence and external services are reached only through an interface port
  (`FindingStore`, `ImageAnalyzer`, `SpeechSynthesizer`,
  `OutdoorTemperatureProvider`, `DeviceIndicatorProvider`). Concrete
  implementations live in a `remote/` subpackage (or equivalent) and are never
  imported by UI/`View`/`ViewModel` code directly.
- Room types (`*Entity`, `*Dao`, `*Database`) never leave their own package; the
  domain model is exposed across the boundary via mappers.
- `View`/`Activity` classes perform no network or file I/O and own no background
  executors — a layer behind a port does that, assembled in `AppContainer`.
- Graph wiring lives in `AppContainer`; `View`s and coordinators receive their
  dependencies from outside (constructor or an `internal` setter).
- New network code uses the shared `internal` helper
  `com.fieldscanner.app.data.net.HttpClient`, not its own `HttpURLConnection`.
- Cross-cutting platform capabilities (e.g. geolocation in
  `com.fieldscanner.app.data.location`) belong in a shared package, not inside a
  feature package that happens to have used them first.

## Definition of done

For code changes there are two gates:

- **Task gate** — closes an individual task, and the only gate that runs inside a task worktree:
  - Windows: `./scripts/verify.ps1`
  - Linux/macOS/CI: `./scripts/verify`

  Both run the documentation gate, its completeness self-check, `:app:lintDebug`, `:app:testDebugUnitTest` and `:app:assembleDebug`. No device. In a task worktree `verify.ps1` compares the documentation gate against the base branch automatically; pass `-BaseRef <ref>` / `$1` to override.
- **Integration gate** — the repo owner's step on the merged result, not part of closing a task: merge the closed `task-nnn` branch into `master`, re-run `./scripts/verify` on the merge, then `./scripts/verify-and-install.ps1` (USB device by default; `-Target emulator` for an emulator) to install and launch the fresh APK on the Galaxy XCover 4s.

`verify-and-install.ps1` runs the full task gate first, then installs and opens the APK; it is not run inside a worktree or by two agents at once. Use `verify.ps1` alone when there is no target device, when the change is worktree-scoped, or when the user explicitly requests a check without installation.

On Windows the script keeps `app/build/verify-and-install-status.txt` current: it writes `state=PASS` only after the verification, APK installation, and activity launch all succeed (`state=RUNNING` before, `state=FAIL` with `reason=` on error). If a terminal host truncates the final output of a long run, inspect this file (and `app/build/verify-and-install.log`) before reporting success; anything other than `state=PASS` means the full scenario is not verified.

Memory write-back and, when a covered module changed, module-doc sync are part
of task completion — done in this same change, not deferred until the user asks.
See "Agent procedures" above.

Before reporting completion, review `git diff`, verify the acceptance criteria,
and state actual command results. A check that could not run must be reported as
not verified, not as passed.

To close the task, tick the checkboxes that hold, fill its `## Outcome` section
(changed files/behavior, closing commit SHA, anything still not verified), and
`git mv` the file from `docs/tasks/` to `docs/tasks/done/` in the same change.
Reference the task id in the closing commit subject.

## Gradle verification discipline

- Run the task gate (`./scripts/verify.ps1`, or `./scripts/verify-and-install.ps1` when integrating with a device) once, after the full change is ready; do not run preliminary Gradle tasks by default.
- The gate builds with AGP 8.0.2 and needs a **JDK 17**. `verify.ps1` uses `JAVA_HOME` when set, otherwise falls back to the Android Studio JBR (`C:\AndroidStudio\jbr`); with neither available it stops with a clear message before Gradle runs. If you have no Android Studio install, set `JAVA_HOME` to a JDK 17 first. The `java` on `PATH` on the reference workstation is Java 8 and will not build.
- The scripts use the normal per-user Gradle User Home (normally `C:\Users\…\.gradle`); Gradle synchronizes the dependency cache for concurrent local processes. Each worktree still has its own project-local `.gradle/` and `app/build/`. Do not copy Gradle caches into worktrees.
- `app/build/verify-and-install-status.txt` is the source of truth: `RUNNING` means the process has no known final outcome, `PASS` means the full scenario completed, and `FAIL` includes the reported reason. The ignored `app/build/verify-and-install.log` preserves the detailed output.
- If terminal output is truncated, inspect the status file and log before considering another run. Retry only after identifying and fixing a reported failure.
- Do not capture or inspect device screenshots as routine verification; do so only when the user explicitly asks for a screenshot or visual review.

## Completion report

Report: changed files/behavior; verification commands with PASS/FAIL; acceptance criteria with PASS/FAIL; known limitations; and anything not verified.
