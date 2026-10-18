# Project Gotchas

This file contains verified, recurring pitfalls. Each record identifies its
evidence and when it must be re-checked.

**Append-only (parallel-work rule, TASK-019):** add a new record at the END of
this file as its own `---`-separated block. Do not renumber, reformat, or
rewrite existing records or this preamble. Parallel tasks in separate worktrees
all touch these memory files, so append-only edits keep merges conflict-free.
`docs/agent-memory/INDEX.md` changes only when a whole topic is added.

---

## Telephony flow depends on role and runtime permission

Status: verified

Last verified: 2026-08-28

Fact:
Before placing a call, `CallUiController` checks the contact whitelist, default
dialer role, and `CALL_PHONE` runtime permission. The system Telecom behavior
also depends on device configuration and cannot be fully proven by local JVM
tests.

Reason:
A passing local build or unit test does not establish that calling works on the
target phone.

Evidence:

- `app/src/main/java/com/fieldscanner/app/calls/CallUiController.kt`
- `app/src/main/java/com/fieldscanner/app/calls/PhoneRoleManager.kt`
- `docs/modules/telephony.md`
- `docs/ARCHITECTURE.md`

Do not:
Assume dialer-role or phone-permission behavior works without suitable device
verification.

Reverify when:
The call flow, Android target/version, device configuration, or Telecom
integration changes.

---

## Android unit tests cannot see `com.sun.net.httpserver`

Status: verified

Last verified: 2026-08-29

Fact:
`:app:compileDebugUnitTestKotlin` compiles against the Android bootclasspath, so
`com.sun.net.httpserver.HttpServer` is an "Unresolved reference: sun" error even
though a normal JDK ships it. JVM tests that need a real HTTP endpoint (e.g. for
`data/net/HttpClient`) must hand-roll a `java.net.ServerSocket` that speaks
minimal HTTP/1.1 — see `app/src/test/java/com/fieldscanner/app/data/net/HttpClientTest.kt`.
Only `junit:junit` and `kotlinx-coroutines-test` are on the test classpath;
there is no Mockito/Robolectric, so seams must be plain interfaces with
hand-written fakes.

Reason:
A test file that imports `com.sun.*` fails the whole `verify` run at test
compilation, not at runtime.

Evidence:

- `app/src/test/java/com/fieldscanner/app/data/net/HttpClientTest.kt`
- `app/build.gradle` (`dependencies` — testImplementation lines)
- `docs/tasks/done/TASK-012-layering-consistency.md`

Reverify when:
The test dependencies or the Gradle/AGP toolchain change.

---

## Tool-written files get LF endings and break `verify` on Windows

Status: verified

Last verified: 2026-08-29

Fact:
This Windows checkout has `core.autocrlf=true` and **no `.gitattributes`**, so a
normal working tree is CRLF. Files created or rewritten by an editor/agent tool
often land as LF. `check-documentation.ps1` (run first inside `scripts/verify.ps1`)
does `git diff --name-only HEAD`, and for every LF file `git` prints
`warning: in the working copy of '<file>', LF will be replaced by CRLF …` to
stderr. Under `$ErrorActionPreference = 'Stop'` PowerShell 5.1 turns that stderr
line into a terminating error, so `verify-and-install-status.txt` becomes
`state=FAIL` with `reason=warning: … LF will be replaced by CRLF …` — before any
Gradle work runs.

Fix:
Normalise every file you created/modified to CRLF before running verify, e.g.
`python -c "..."` reading each path and writing
`data.replace(b'\r\n',b'\n').replace(b'\r',b'\n').replace(b'\n',b'\r\n')`
(UTF-8, no BOM). `git` still stores them as LF. Verify the tree is clean with
`git diff --name-only HEAD 2>&1 >/dev/null` producing no output.

Evidence:

- `scripts/check-documentation.ps1` (`git diff` line), `scripts/verify.ps1`
  (`$ErrorActionPreference = 'Stop'`)
- `git config --get core.autocrlf` → `true`; no `.gitattributes` in repo root
- `docs/tasks/done/TASK-015-voice-language-switching.md`

Reverify when:
A `.gitattributes` is added, `core.autocrlf` changes, or the verify scripts stop
using `ErrorActionPreference=Stop` around `git`.

---

## OpenAI settings must remain untracked

Status: verified

Last verified: 2026-08-28

Fact:
Image analysis and text-to-speech use a developer key supplied through
untracked `local.properties`.

Reason:
Hardcoding or committing API credentials exposes sensitive configuration.

Evidence:

- `app/build.gradle`
- `app/src/main/java/com/fieldscanner/app/data/analysis/remote/OpenAiImageAnalyzer.kt`
- `app/src/main/java/com/fieldscanner/app/data/speech/remote/OpenAiTtsProxy.kt`
- `docs/modules/ai-services.md`

Do not:
Add API keys, tokens, or real `local.properties` values to source, tests,
documentation, or shared memory.

Reverify when:
The AI configuration or provider integration changes.

---

## Temporary scans and finding media use separate directories

Status: verified

Last verified: 2026-08-31

Fact:
CameraX writes disposable `SCAN_*.jpg` files to `getExternalFilesDir(Environment.DIRECTORY_PICTURES)` (or `filesDir` only when external app storage is unavailable). `TemporaryPhotoCleanup` owns startup cleanup of that location, while `FindingMediaStorage` owns durable copies in `filesDir/findings/`; cleanup must never traverse or delete the findings directory.

Evidence:

- `app/src/main/java/com/fieldscanner/app/camera/CameraController.kt`
- `app/src/main/java/com/fieldscanner/app/data/camera/TemporaryPhotoCleanup.kt`
- `app/src/main/java/com/fieldscanner/app/data/finding/FindingMediaStorage.kt`
- `app/src/test/java/com/fieldscanner/app/data/camera/TemporaryPhotoCleanupRuleTest.kt`

Reverify when:
Camera output storage, finding-media storage, or startup cleanup changes.

---

## App-startup smoke test: what it does and does not prove (TASK-020)

Status: verified

Last verified: 2026-09-01

Fact:
`app/src/test/java/com/fieldscanner/app/MainActivityStartupSmokeTest.kt` runs on
Robolectric inside `:app:testDebugUnitTest`. It brings the real
`FieldScannerApplication` and `MainActivity` up to `onResume` with a
`TestAppContainer` (all ports faked) injected through
`MainActivity.containerFactory`, and asserts `activity_main` inflated and the
`MainScreenView` views resolve.

Covers: `MainActivity.onCreate`/`buildScreen()` throwing, a collapsed screen
graph, broken `activity_main` inflation, a missing `findViewById` target, and
`FieldScannerApplication.onCreate`. Does NOT cover: camera/telephony/TTS/
speech-recognition runtime, real rendering, coroutine effects past the first
frame, or anything a device-only path would catch. On-device install
(`verify-and-install.ps1`) is still the only full-startup signal.

The seam: `AppContainer` is now `open` with `open` lazy ports and an overridable
`startBackgroundMaintenance()`; a subclass swaps ports for fakes and skips the
`TemporaryPhotoCleanup` disk work. Production behaviour is unchanged (ports still
built on first access, cleanup still runs from `init`).

Robolectric 4.10.3 is the only new test-scope dependency. On the first run of a
fresh machine or a new worktree it downloads `android-all-13-robolectric-*.jar`
from Maven Central into its own cache (`~/.m2`-style, NOT `GRADLE_USER_HOME`), so
that run needs network even though `scripts/verify` otherwise does not.
`android.testOptions { unitTests { includeAndroidResources = true } }` in
`app/build.gradle` is required for resource/theme inflation.

Evidence:

- `app/src/test/java/com/fieldscanner/app/MainActivityStartupSmokeTest.kt`
- `app/src/test/java/com/fieldscanner/app/support/TestAppContainer.kt`
- `app/src/main/java/com/fieldscanner/app/MainActivity.kt` (`containerFactory`)
- `app/src/main/java/com/fieldscanner/app/AppContainer.kt` (`open` ports, `startBackgroundMaintenance`)
- `app/build.gradle` (`robolectric`, `testOptions`)
- `docs/modules/platform-and-build.md`, `docs/modules/ui-and-input.md`, `docs/modules/scanner.md`
- `docs/tasks/done/TASK-020-app-startup-smoke-test.md`

Reverify when:
`MainActivity` startup, `AppContainer` wiring, the app theme/layout, or the
Robolectric version changes.

---

## `NewApi` lint needs the SDK_INT guard to lexically enclose the API call

Status: verified

Last verified: 2026-09-02

Fact:
`:app:lintDebug` runs inside `scripts/verify` and `abortOnError` is on (no lint
baseline), so a single `NewApi` error fails the task gate. minSdk is 24, so any
call to an API added later must sit **lexically inside** an
`if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.<X>) { ... }` block (or a
`@RequiresApi`-annotated method called only from such a block). A guarded
*assignment* whose result is used on later lines does NOT satisfy lint: in
TASK-033 `val cutout = if (SDK_INT >= P) rootWindowInsets?.displayCutout else null`
followed by `cutout?.safeInsetLeft` on the next lines produced
`Call requires API level 28 (current min is 24) [NewApi]` four times. Fix was to
read all four `DisplayCutout.safeInset*` values inside the `if` body and pass
plain `Int`s out.

Why this matters:
The failure only surfaces at the end of a 1-2 minute `verify` run, not at
compile time (Kotlin is happy). Structure API-level-gated code so every
newer-API member access is under the version check, not just its data source.

Evidence:

- `app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt` (`recomputeTransform()`)
- `app/build.gradle` (no `lint { baseline }` block)
- `docs/tasks/done/TASK-033-aspect-safe-display-scaling.md` ("## Outcome", Verification)

Reverify when:
A lint baseline is added to `app/build.gradle`, or `abortOnError` is disabled.
