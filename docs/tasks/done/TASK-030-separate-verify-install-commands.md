# TASK-030 — Split verification and device install into separate commands

## Goal

The repo owner has three focused commands instead of one broad `/run-verification`:

- `/verify` — build check only: `scripts/verify.ps1` (doc gate + self-check + lint + unit + assemble), no device.
- `/install` — take the already-built debug APK, install it on the device, and launch FieldScanner. No lint / unit / assemble.
- `/verify-install` — the existing full path: `scripts/verify-and-install.ps1` (task gate, then install and launch).

## Context

Today the only device/verification command is `/run-verification`
(`.claude/commands/run-verification.md`), a thin pointer to the AGENTS.md
"Definition of done" / "Gradle verification discipline" sections, which together
describe both the task gate and the integration gate.

Two gaps:

1. There is no one-step way to push the APK that `scripts/verify.ps1` /
   `scripts/verify-and-install.ps1` already built onto the device without
   re-running the whole gate. `scripts/verify-and-install.ps1` always runs the
   full task gate first.
2. The single `/run-verification` name does not signal which of the two gates it
   runs.

Splitting into `/verify`, `/install`, `/verify-install` makes each common action
one command, and gives `/install` a fast path for the "I just want the current
build on the phone" case.

## Requirements

- **New `scripts/install.ps1`** (Windows-only, consistent with
  `scripts/verify-and-install.ps1`; no POSIX counterpart):
  - Installs the debug APK at `app/build/outputs/apk/debug/app-debug.apk` via
    `adb` and launches the FieldScanner main activity.
  - Accepts `-Target usb|emulator` with the same meaning and default (`usb`) as
    `scripts/verify-and-install.ps1`.
  - Runs no Gradle tasks — no lint, unit, assemble, or doc gate.
  - If the APK is missing, exits non-zero with a message telling the user to run
    `/verify` or `/verify-install` first. If no device/emulator matches
    `-Target`, exits non-zero naming that.
  - Reuse the install + launch (and status-marker) logic already in
    `scripts/verify-and-install.ps1` rather than duplicating it — extract a
    shared helper if that is the smaller change.
- **`.claude/commands/verify.md`** — thin pointer, replaces
  `.claude/commands/run-verification.md`. Points at the task-gate procedure
  (`scripts/verify.ps1` / `scripts/verify`; AGENTS.md "Definition of done").
- **`.claude/commands/install.md`** — thin pointer to `scripts/install.ps1` and
  the AGENTS.md row/section describing it.
- **`.claude/commands/verify-install.md`** — thin pointer to
  `scripts/verify-and-install.ps1` (integration gate).
- **Delete `.claude/commands/run-verification.md`.** Migrate every
  `run-verification` reference to the new names:
  `docs/agents/IMPLEMENTER.md`, `docs/agents/VERIFIER.md`,
  `docs/AI_DEVELOPMENT_WORKFLOW.md`, `docs/agent-memory/workflows.md`, and the
  AGENTS.md "Agent procedures" list.
- **AGENTS.md "Agent scripts" table** — add a `scripts/install.ps1` row
  (Windows column only; args `[-Target usb|emulator]`; purpose "install and
  launch the already-built debug APK, no gate"; Run in "repo owner, merged
  result — never in a worktree, never two at once").
- **AGENTS.md "Agent procedures" list** — list `/verify`, `/install`,
  `/verify-install`; drop `run-verification`.
- **`docs/modules/platform-and-build.md`** — document the three commands and
  `scripts/install.ps1`.
- No new `docs/skills/*/GUIDE.md` files: the procedures stay defined in AGENTS.md
  sections and the scripts; the commands remain thin pointers.

## Acceptance criteria

- [x] `.claude/commands/verify.md`, `.claude/commands/install.md`, and
  `.claude/commands/verify-install.md` exist; `.claude/commands/run-verification.md`
  does not. Each new file is a thin pointer (front-matter `description:` plus a
  one-line "Follow …" body), matching `.claude/commands/new-task.md` in style.
- [x] `scripts/install.ps1` exists. With no debug APK present it exits non-zero
  and prints a message naming the missing APK and pointing at `/verify` /
  `/verify-install`. (Verified: APK moved aside, exit code 1, message printed.)
- [x] `scripts/install.ps1` runs no Gradle task (verifiable by inspection: no
  `gradlew` / `assemble` / `lint` / `test` invocation).
- [~] `grep -rn "run-verification" AGENTS.md docs/ .claude/` returns nothing —
  all live references migrated; remaining hits are historical records in
  `docs/tasks/done/TASK-021-*.md` (append-only archive) and this task file
  itself (moves to `docs/tasks/done/` on close). See `## Outcome`.
- [x] AGENTS.md "Agent scripts" table has a `scripts/install.ps1` row; "Agent
  procedures" lists verify / install / verify-install and not run-verification.
- [x] `docs/modules/platform-and-build.md` describes `/verify`, `/install`,
  `/verify-install`, and `scripts/install.ps1`.
- [x] Task gate `./scripts/verify.ps1` passes.
- [ ] Repo-owner manual check (carry result to `## Outcome`): with the debug APK
  built and the XCover 4s attached, `./scripts/install.ps1` installs the APK and
  launches FieldScanner without running the gate.

## Out of scope

- Changing what `scripts/verify.ps1` or `scripts/verify-and-install.ps1` do.
- A `-SkipVerify` flag on `scripts/verify-and-install.ps1`.
- A POSIX `scripts/install` (the on-device path is Windows repo-owner-only, like
  `scripts/verify-and-install.ps1`).
- Instrumented tests; any app source change.
- Converting the command pointers into `docs/skills/*/GUIDE.md`.

## Relevant areas

- `scripts/install.ps1` (new), `scripts/verify-and-install.ps1` (source of the
  install/launch logic to reuse), `scripts/verify.ps1`.
- `.claude/commands/` — `verify.md`, `install.md`, `verify-install.md` (new),
  `run-verification.md` (removed).
- `AGENTS.md` — "Agent procedures" list, "Agent scripts" table.
- `docs/AI_DEVELOPMENT_WORKFLOW.md`, `docs/agents/IMPLEMENTER.md`,
  `docs/agents/VERIFIER.md`, `docs/modules/platform-and-build.md`,
  `docs/agent-memory/workflows.md` — reference migration + module-doc sync.

## Plan

### Existing behavior

- `.claude/commands/run-verification.md` → "Follow AGENTS.md, sections
  'Definition of done' and 'Gradle verification discipline'."
- `scripts/verify.ps1` / `scripts/verify` — doc gate + self-check +
  `:app:lintDebug` + `:app:testDebugUnitTest` + `:app:assembleDebug`. No device.
- `scripts/verify-and-install.ps1` — runs the full task gate, then installs and
  launches the APK on `-Target usb|emulator` (default `usb`), maintaining
  `app/build/verify-and-install-status.txt` (`RUNNING` / `PASS` / `FAIL …`) and
  `app/build/verify-and-install.log`.
- `run-verification` is referenced from AGENTS.md "Agent procedures",
  `docs/agents/IMPLEMENTER.md`, `docs/agents/VERIFIER.md`,
  `docs/AI_DEVELOPMENT_WORKFLOW.md`, `docs/agent-memory/workflows.md`.

### Approach

Add `scripts/install.ps1` as an install-and-launch-only entry point that reuses
the device logic from `scripts/verify-and-install.ps1` (extract the install +
launch + status-marker steps into a shared helper, e.g. dot-sourced from
`scripts/`, if that is cleaner than copying). Replace the single
`/run-verification` command with three thin pointer commands and migrate all
references. Update the build/platform module doc and shared memory.

### Likely files

- New: `scripts/install.ps1`, possibly `scripts/device-install-common.ps1`
  (shared helper).
- New: `.claude/commands/verify.md`, `.claude/commands/install.md`,
  `.claude/commands/verify-install.md`.
- Removed: `.claude/commands/run-verification.md`.
- Edited: `scripts/verify-and-install.ps1` (only if factoring the helper),
  `AGENTS.md`, `docs/AI_DEVELOPMENT_WORKFLOW.md`, `docs/agents/IMPLEMENTER.md`,
  `docs/agents/VERIFIER.md`, `docs/modules/platform-and-build.md`,
  `docs/agent-memory/workflows.md`.

### Ordered steps

1. Read `scripts/verify-and-install.ps1` end to end; identify the APK path, the
   `-Target` handling, the `adb install` + activity-launch commands, and the
   status-marker writes.
2. Factor the install + launch (+ marker) portion so it can be called without
   the gate; keep `verify-and-install.ps1` behaviour identical.
3. Write `scripts/install.ps1`: parse `-Target`, check the APK exists (fail fast
   with guidance), check a matching device is present, call the shared
   install/launch helper.
4. Add the three `.claude/commands/*.md` pointer files; delete
   `run-verification.md`.
5. Update `AGENTS.md` — "Agent procedures" list and "Agent scripts" table.
6. Migrate `run-verification` references in `docs/AI_DEVELOPMENT_WORKFLOW.md`,
   `docs/agents/IMPLEMENTER.md`, `docs/agents/VERIFIER.md`,
   `docs/agent-memory/workflows.md`.
7. Update `docs/modules/platform-and-build.md` (module-doc sync) to describe the
   three commands and `scripts/install.ps1`.
8. Memory write-back: update `docs/agent-memory/workflows.md` command inventory
   with evidence (commit SHA / file list).
9. Run `./scripts/verify.ps1`.

### Test / verification strategy

- `./scripts/verify.ps1` — doc gate (the module-doc change must be picked up),
  self-check, lint, unit, assemble.
- Static checks for the acceptance criteria: the `grep` for `run-verification`,
  file existence, table/list contents, no Gradle call inside `install.ps1`.
- `scripts/install.ps1` no-APK path: run from a clean tree (or with the APK
  temporarily moved) and confirm the non-zero exit and message.
- Repo-owner on-device check: build once (`/verify` or `/verify-install`), then
  `./scripts/install.ps1` and confirm the app installs and opens. Record in
  `## Outcome`.

### Risks

- Refactoring the shared helper could change `verify-and-install.ps1` behaviour
  or its status-marker contract (`app/build/verify-and-install-status.txt`,
  described in AGENTS.md "Gradle verification discipline"). Mitigation: keep the
  marker writes owned by `verify-and-install.ps1`; the helper only does
  install + launch, or takes the marker path as a parameter.
- The doc gate may not map `scripts/` to a `docs/modules/` page; the
  `platform-and-build.md` edit is still required by module-doc sync and keeps
  the gate green regardless.
- `adb` not on `PATH` in a bare shell — mirror however `verify-and-install.ps1`
  resolves `adb`.

### Assumptions

- Debug APK path is `app/build/outputs/apk/debug/app-debug.apk` (confirm against
  `scripts/verify-and-install.ps1`).
- `/install` is a repo-owner action on the merged result, never run in a task
  worktree — same rule as `verify-and-install.ps1`.
- No app code or test changes are needed.

## Verification

- [x] Task gate: `./scripts/verify.ps1` (Windows) — doc gate + self-check + lint + unit + assemble. PASS (run in the main checkout; see `## Outcome` re: worktree). `JAVA_HOME` must point at a JDK 17 (JBR at `C:\AndroidStudio\jbr` works).
- [ ] Integration gate (repo owner, on the merged result): `./scripts/verify` after merge, then `./scripts/verify-and-install.ps1` to install and launch on the Galaxy XCover 4s. Carry anything still pending here into `## Outcome`.
- [x] Acceptance criteria checked
- [~] Additional scenario-specific check: the no-APK run fails fast with guidance — VERIFIED. `./scripts/install.ps1` on the XCover 4s installing and launching without the gate — NOT verified (no device in this session); repo-owner check.

## Outcome

### What changed

- **New `scripts/device-install-common.ps1`** — dot-source helper (functions only,
  nothing runs on load) holding the install/launch logic previously inline in
  `verify-and-install.ps1`: `Resolve-AndroidAdbPath` (JBR fallback + SDK adb
  resolve), `Assert-AdbDeviceReady` (`-d`/`-e` selector for `-Target`, throws
  naming the target when no device is ready), `Install-AndLaunchFieldScanner`
  (`adb install -r` + `am start -n com.fieldscanner.app/.MainActivity`),
  `Get-FieldScannerDebugApkPath`. The status-marker contract stays owned by
  `verify-and-install.ps1`; the helper writes no marker.
- **New `scripts/install.ps1`** (`-Target usb|emulator`, default `usb`) — dot-sources
  the helper, checks `app/build/outputs/apk/debug/app-debug.apk` exists (else exits
  1 with a message pointing at `/verify` / `/verify-install`), asserts a matching
  device, then installs + launches. Runs no Gradle work. Does not touch the status
  marker.
- **`scripts/verify-and-install.ps1`** — refactored to call the shared helper;
  behaviour, ordering (resolve adb → assert device → `verify.ps1` → install/launch
  → `PASS`), messages, and the `app/build/verify-and-install-status.txt` /
  `.log` contract are unchanged.
- **`.claude/commands/`** — `run-verification.md` renamed to `verify.md` (content
  identical: pointer to AGENTS.md "Definition of done" / "Gradle verification
  discipline"); new thin pointers `install.md` and `verify-install.md`.
- **`.claude/settings.json`** — added `scripts/install.ps1` to the committed
  PowerShell allow-list (same four-line pattern as the other scripts).
- **`AGENTS.md`** — "Agent procedures" now lists `/verify`, `/install`,
  `/verify-install` (was a single "Run the gate"); "Agent scripts" table has a new
  `scripts/install.ps1` row.
- **Reference migration** off `run-verification`: `docs/agents/IMPLEMENTER.md`,
  `docs/agents/VERIFIER.md`, `docs/AI_DEVELOPMENT_WORKFLOW.md`,
  `docs/agent-memory/workflows.md` ("Agent procedures" memory section — rules,
  evidence, and the file-count / `wc -l AGENTS.md` numbers refreshed to 8 files /
  193 lines).
- **`docs/modules/platform-and-build.md`** (module-doc sync) — "Проверка" section
  rewritten to describe the three commands, `scripts/install.ps1`, its fail-fast
  behaviour, and `scripts/device-install-common.ps1`.

### Verification

- Task gate `./scripts/verify.ps1` — **PASS** (doc gate + self-check + `:app:lintDebug`
  + `:app:testDebugUnitTest` + `:app:assembleDebug`). Requires `JAVA_HOME` set to a
  JDK 17; the first run failed only because the shell had no `JAVA_HOME` (Gradle
  fell back to Java 8) — resolved by pointing it at `C:\AndroidStudio\jbr`, same
  JBR `verify-and-install.ps1` uses.
- `scripts/install.ps1` no-APK path — **VERIFIED**: with the APK moved aside the
  script exits 1 and prints `Debug APK not found: …` plus the `/verify` /
  `/verify-install` hint, before touching adb.
- PowerShell parse check on all three scripts — OK.

### Closing commit SHA

`48892be` — "TASK-030 Split verify/install into /verify, /install, /verify-install"
(single squashed commit on `master`; a trailing `--amend` to fold in this SHA
note may shift the hash by one — see `git log` for the true tip).

### Not verified / carried forward

- **Repo-owner on-device check**: `./scripts/install.ps1` installing + launching on
  the attached Galaxy XCover 4s without running the gate. No device in this
  session. Part of the integration step.
- **Integration gate**: `./scripts/verify` on the merge, then
  `./scripts/verify-and-install.ps1` — repo owner, on the merged result.
- **`grep run-verification` is not empty**: live docs are all migrated; the
  remaining matches are in `docs/tasks/done/TASK-021-agent-skills-and-tools-layer.md`
  (append-only archive of a closed task — historical record, deliberately not
  rewritten) and this file (moves into `docs/tasks/done/` on close). The literal
  "returns nothing" criterion cannot hold without editing archived task records;
  marked `[~]`.
- **Worktree**: this doc/script-only change was made in the main checkout on
  `master` (no parallel agent touching these files). `verify.ps1` warns because 7
  worktrees exist; the run is still valid for this change.
