# TASK-031 — Stop the recurring "Gradle fell back to Java 8" failure in the task gate

## Goal

An agent (or the repo owner) can run the task gate with no prior shell setup and
it either uses a JDK 17 automatically or fails immediately with a message that
says exactly what to set. Nobody has to rediscover `JAVA_HOME=C:\AndroidStudio\jbr`
by reading a failed AGP stack trace.

## Context

`scripts/verify.ps1` run standalone does not configure a JDK. On this
workstation the `PATH` `java` is Java 8, and AGP 8.0.2 needs 11+ (17 for this
project), so `:app:assembleDebug` fails deep in Gradle with an unhelpful error.
The fix each time is to point `JAVA_HOME` at a JDK 17 (`C:\AndroidStudio\jbr`).

`scripts/verify-and-install.ps1` and `scripts/install.ps1` already avoid this:
`scripts/device-install-common.ps1` `Resolve-AndroidAdbPath` sets
`$env:JAVA_HOME = 'C:\AndroidStudio\jbr'` when it is unset and the path exists
(lines 17-21). `verify.ps1` — the gate that actually runs Gradle and the only
one allowed inside a task worktree — has no equivalent.

This has cost time repeatedly. The current documentation of it lives only in
`docs/agent-memory/workflows.md` (lines 196-198, and 272) as a "trap found
while building the pipeline", which agents do not read before a first verify.

## Requirements

- `scripts/verify.ps1` resolves a JDK 17 before invoking Gradle:
  1. If `JAVA_HOME` is already set, use it unchanged (caller wins — matches the
     comment already in `verify.ps1` about not overriding `GRADLE_USER_HOME`).
  2. Otherwise, if `C:\AndroidStudio\jbr` exists, set `JAVA_HOME` to it and
     prepend its `bin` to `PATH` for the process, printing one line saying so.
  3. Otherwise fail fast, before the doc gate and Gradle, with a message naming
     `JAVA_HOME` and "JDK 17".
- The JDK-resolution logic is shared, not copy-pasted: factor the block out of
  `scripts/device-install-common.ps1` into a helper both it and `verify.ps1`
  dot-source (e.g. a `Resolve-Jdk17` function), or into an existing common
  dot-source file. `device-install-common.ps1` keeps its current behaviour.
- Optionally detect a too-old `java` on `PATH` when `JAVA_HOME` is unset and
  `C:\AndroidStudio\jbr` is missing, and name the found version in the error.
- POSIX `scripts/verify`: at minimum, document the JDK 17 requirement in a
  comment near the Gradle invocation. Auto-resolution there is out of scope
  unless trivial (no fixed JBR path on Linux/macOS/CI).
- `AGENTS.md` states the JDK 17 requirement where an agent sees it before
  running the gate — add it to "Gradle verification discipline" (one line: the
  gate needs a JDK 17; `verify.ps1` auto-uses `C:\AndroidStudio\jbr` if
  `JAVA_HOME` is unset, otherwise set `JAVA_HOME` to a JDK 17).
- `.claude/commands/verify.md` and `.claude/commands/verify-install.md` still
  point only to `AGENTS.md` — no change needed if the AGENTS.md line is added.
- `docs/modules/platform-and-build.md` already says "нужен JDK 17"; extend that
  sentence to mention the `verify.ps1` auto-fallback and the `JAVA_HOME`
  override so the module page matches the new script behaviour (covered module —
  module-doc-sync applies).
- `docs/agent-memory/workflows.md` lines ~196-198 and ~272 become stale once
  `verify.ps1` sets the fallback itself. Correct them in this same change per
  the stale-memory rule (they currently say "Only `verify-and-install.ps1` sets
  that JBR fallback").

## Acceptance criteria

- [x] In a fresh PowerShell with no `JAVA_HOME` and Java 8 first on `PATH`,
      `./scripts/verify.ps1` prints a line naming the JBR it selected and then
      runs the gate (no AGP Java-version failure). — full gate run with
      `JAVA_HOME=''` / `FIELDSCANNER_JBR=''`: `BUILD SUCCESSFUL in 1m 43s`,
      `VERIFY_EXIT=0`.
- [x] With `C:\AndroidStudio\jbr` temporarily hidden (env var the script reads)
      and `JAVA_HOME` unset, `./scripts/verify.ps1` exits non-zero **before** the
      documentation gate with a message containing `JAVA_HOME` and `17`. — with
      `FIELDSCANNER_JBR='C:\nope\jdk'` the script threw
      "No JDK 17 found. Set JAVA_HOME to a JDK 17 … AGP 8.0.2 requires JDK 17 …"
      before any "Documentation coverage" output.
- [x] With `JAVA_HOME` already set to a valid JDK 17, `./scripts/verify.ps1`
      leaves it unchanged. — `Resolve-Jdk17` with `JAVA_HOME='C:\some\preset\jdk17'`
      returned it unmodified.
- [x] The literal fallback path appears in exactly one file (the shared helper).
      — `AndroidStudio\jbr` as a code default is only
      `scripts/jdk-common.ps1:33`; other hits there are the doc-comment and the
      error message. `device-install-common.ps1` no longer contains it.
- [x] `./scripts/verify-and-install.ps1` still resolves adb and JDK as before
      (its device-install path is unchanged). — `Resolve-AndroidAdbPath` returns
      the adb path both with the JBR present (sets `JAVA_HOME`) and with it
      missing (leaves `JAVA_HOME` empty, no throw). Full integration gate on the
      device is the repo owner's step — see Verification.
- [x] `AGENTS.md` "Gradle verification discipline" contains the JDK 17 line.
- [x] `docs/modules/platform-and-build.md` mentions the `verify.ps1` JDK 17
      auto-fallback and the `JAVA_HOME` override.
- [x] `docs/agent-memory/workflows.md` no longer claims only
      `verify-and-install.ps1` sets the JBR fallback.
- [x] Task gate passes: `./scripts/verify.ps1` — `BUILD SUCCESSFUL in 1m 43s`,
      50 tasks executed, `VERIFY_EXIT=0` (worktree `C:\fs-wt\task-031`).

## Out of scope

- Bundling or committing a JDK (the gitignored `.tooling/jdk17/` stays as-is).
- Gradle toolchain / `org.gradle.java.home` changes in `gradle.properties` or
  `app/build.gradle`.
- Emulator / instrumented-test setup.
- Any change to `install.ps1` / `verify-and-install.ps1` behaviour beyond
  consuming the shared JDK helper.
- CI provisioning of a JDK (CI already provides one; this is the local-dev trap).

## Relevant areas

- `scripts/verify.ps1` — add JDK resolution before the doc gate / Gradle call.
- `scripts/device-install-common.ps1` — source of the existing JBR fallback to
  factor out.
- A shared dot-source helper (existing `scripts/worktree-common.ps1` is already
  conditionally sourced by `verify.ps1`, or add a small `scripts/jdk-common.ps1`).
- `scripts/verify` (POSIX) — comment only.
- `AGENTS.md`, `docs/modules/platform-and-build.md`,
  `docs/agent-memory/workflows.md`.

This is a guide, not permission to ignore other affected code.

## Plan

**Existing behavior.** `verify.ps1` sets `$ErrorActionPreference='Stop'`, cd's
to repo root, optionally dot-sources `scripts/worktree-common.ps1` for base-branch
awareness, runs `check-documentation.ps1` twice, then
`./gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug`. It
never touches `JAVA_HOME`. `device-install-common.ps1` `Resolve-AndroidAdbPath`
does the `C:\AndroidStudio\jbr` fallback inline as a side effect of resolving adb.

**Approach.** Extract the JDK-17 fallback into a named function in a new tiny
`scripts/jdk-common.ps1` (functions only, nothing on load — same convention as
`device-install-common.ps1` / `worktree-common.ps1`):

```
function Resolve-Jdk17 {
    param([string]$PreferredJbr = 'C:\AndroidStudio\jbr')
    if ($env:JAVA_HOME) { return }                      # caller wins
    if (Test-Path -LiteralPath $PreferredJbr) {
        $env:JAVA_HOME = $PreferredJbr
        $env:Path = "$PreferredJbr\bin;$env:Path"
        Write-Host "Using JDK at $PreferredJbr (JAVA_HOME was unset)."
        return
    }
    throw "No JDK 17 found: set JAVA_HOME to a JDK 17 (AGP 8.0.2 needs 17; the PATH 'java' here is too old)."
}
```

- `verify.ps1`: dot-source `scripts/jdk-common.ps1` and call `Resolve-Jdk17`
  immediately after `Set-Location $repositoryRoot`, before the doc gate.
- `device-install-common.ps1`: dot-source `jdk-common.ps1` and replace the
  inline `if (-not $env:JAVA_HOME -and (Test-Path ...))` block in
  `Resolve-AndroidAdbPath` with `Resolve-Jdk17` wrapped so a missing JBR there
  is non-fatal only if adb can still be found — simplest: call `Resolve-Jdk17`
  in a `try` and swallow the throw (device path historically only *preferred*
  the JBR, it did not require it). Confirm adb resolution still works with
  `JAVA_HOME` unset on a machine that has the JBR.
- Make `-PreferredJbr` overridable for the test (env var `FIELDSCANNER_JBR` or a
  script param on `verify.ps1`) so the "JBR hidden" acceptance case is runnable
  without renaming a system directory.
- `verify` (POSIX): one comment line above the `./gradlew` call — "requires a
  JDK 17 on JAVA_HOME / PATH; AGP 8.0.2 rejects older".
- Docs: `AGENTS.md` one line in "Gradle verification discipline";
  `platform-and-build.md` extend the existing JDK-17 sentence;
  `workflows.md` correct the two stale spots.

**Likely files:** `scripts/jdk-common.ps1` (new), `scripts/verify.ps1`,
`scripts/device-install-common.ps1`, `scripts/verify`, `AGENTS.md`,
`docs/modules/platform-and-build.md`, `docs/agent-memory/workflows.md`.

**Ordered steps.**
1. Add `scripts/jdk-common.ps1` with `Resolve-Jdk17`.
2. Wire it into `verify.ps1`; add the JBR-override hook.
3. Refactor `device-install-common.ps1` to consume it; verify device path
   behaviour unchanged.
4. Add the comment to POSIX `scripts/verify`.
5. Update `AGENTS.md`, `platform-and-build.md`, `workflows.md`.
6. Run the four script scenarios in the acceptance criteria.
7. Run the task gate.

**Test / verification strategy.** Manual scenario runs in a clean shell (no unit
test can cover process-env / PATH behaviour meaningfully); then
`./scripts/verify.ps1` for the gate. The gate run itself exercises the happy
path (criterion 1) since it will use the auto-resolved JBR.

**Risks / assumptions.**
- Assumes `C:\AndroidStudio\jbr` is the canonical JDK 17 on the repo owner's
  workstation (it is what `verify-and-install.ps1` / `device-install-common.ps1`
  already assume).
- Prepending to `PATH` for the process only — no persistence, no user-env
  writes.
- `device-install-common.ps1` refactor must not turn the previously-optional JBR
  preference into a hard failure on the device path; the `try`/swallow keeps it
  optional.
- If a future machine has no Android Studio, the new hard `throw` in `verify.ps1`
  is the intended behaviour (fail fast with a clear message beats an AGP trace).

## Verification

- [x] Task gate: `./scripts/verify.ps1` in worktree `C:\fs-wt\task-031` —
      `BUILD SUCCESSFUL in 1m 43s`, 50 tasks executed, `VERIFY_EXIT=0`; doc gate
      + self-check + lint + unit + assemble all passed. Run with `JAVA_HOME=''`
      so the run also exercised the new auto-fallback.
- [ ] Integration gate (repo owner, on the merged result): `./scripts/verify`
      after merge, then `./scripts/verify-and-install.ps1` to install and launch
      on the Galaxy XCover 4s — confirms the `device-install-common.ps1` refactor
      did not break adb/JDK resolution on the real device path. **Not run**
      (no device in this worktree session).
- [x] Acceptance criteria checked — all 9 boxes above, with evidence.
- [x] Additional scenario-specific checks: the `scripts/verify.ps1` shell
      scenarios (JAVA_HOME unset → JBR auto-selected; JBR hidden → fail-fast
      before doc gate; JAVA_HOME preset → unchanged; single-source-of-path
      grep) — all pass; details in "Acceptance criteria".

## Outcome

**What changed**

- `scripts/jdk-common.ps1` (new) — shared `Resolve-Jdk17` helper: honours an
  existing `JAVA_HOME`; else falls back to `C:\AndroidStudio\jbr` (override with
  `FIELDSCANNER_JBR`), setting `JAVA_HOME` and prepending its `bin` to `PATH`
  for the process; else throws naming `JAVA_HOME` and "JDK 17". `-Quiet` switch
  suppresses the info line.
- `scripts/verify.ps1` — dot-sources `jdk-common.ps1` and calls `Resolve-Jdk17`
  right after `Set-Location`, before the documentation gate. A shell with no
  JDK 17 now stops here with a clear message instead of failing deep in AGP.
- `scripts/device-install-common.ps1` — dot-sources `jdk-common.ps1`; the
  inline `C:\AndroidStudio\jbr` block in `Resolve-AndroidAdbPath` is replaced by
  `try { Resolve-Jdk17 -Quiet } catch { }` (a missing JBR stays non-fatal on the
  device path, as before). The literal fallback path now lives only in
  `jdk-common.ps1`.
- `scripts/verify` (POSIX) — comment above the `./gradlew` line stating the
  JDK 17 requirement; no behaviour change (no fixed JBR path on Linux/macOS/CI).
- `AGENTS.md` — "Gradle verification discipline" gains a bullet on the JDK 17
  requirement and `verify.ps1`'s auto-fallback.
- `docs/modules/platform-and-build.md` — the "нужен JDK 17" sentence now
  describes the `verify.ps1` auto-fallback, the `JAVA_HOME` precedence, the
  `FIELDSCANNER_JBR` override, and the POSIX difference.
- `docs/agent-memory/workflows.md` — corrected the stale trap that said only
  `verify-and-install.ps1` sets the JBR fallback; softened the `-SeedGradleCache`
  verification recipe's explicit `JAVA_HOME=` (now optional).

**Closing commit SHA:** `261945bc784272b50152fac9f73ee389b5592886`
(task file created earlier in `b218673` on `master`).

**Not verified**

- Integration gate on the Galaxy XCover 4s (`verify-and-install.ps1` end to end
  on the real device) — repo owner's step on the merged result. The
  `device-install-common.ps1` refactor was checked in isolation only
  (`Resolve-AndroidAdbPath` returns the adb path with the JBR present and with it
  absent; `verify-and-install.ps1` / `install.ps1` were not run against a
  device).
- POSIX `scripts/verify` on Linux/macOS/CI — comment-only change, not executed
  in this session.
