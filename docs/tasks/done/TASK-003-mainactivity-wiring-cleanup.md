# TASK-003 — Fix MainActivity wiring fragility and readability

## Goal

Remove the order-dependent object wiring in `MainActivity.onCreate` and make the wiring block easier to read, without introducing a DI framework or any new dependency.

## Context

`MainActivity` correctly delegates all behavior to feature controllers/coordinators — this task does not change that split. The problem is narrower: `onCreate` builds ~10 collaborators in one dense block, and `ScannerScreenCoordinator` is constructed with a lambda `{ callUi.isPickerVisible }` that reads the `lateinit var callUi` before it is assigned later in the same method. It only works today because the lambda isn't invoked synchronously during construction; reordering these lines during a future change could throw `UninitializedPropertyAccessException` with no compiler warning.

## Requirements

- Remove the forward reference to `callUi` from `ScannerScreenCoordinator`'s construction — either construct `callUi` before `scannerScreen`, or pass the picker-visibility check through a mechanism that doesn't depend on initialization order (e.g. a small mutable holder, or restructuring the dependency).
- Reduce the density of `onCreate`: split the semicolon-chained one-liners in `MainActivity.kt` into normal statements, and consider extracting the object-graph construction into a separate private method (e.g. `buildScreen()`), called from `onCreate`, purely for readability — no new classes/files/dependencies required unless it clearly helps.
- Do not introduce a DI framework (Hilt/Koin/Dagger) — out of scope and against the project's low-dependency-count principle (`app/AGENTS.md`).

## Acceptance criteria

- [x] No collaborator construction reads a `lateinit var` before it is assigned, anywhere in `MainActivity.onCreate` (or its extracted method).
- [x] `MainActivity.kt` has no behavior change — camera, journal, call, and rune flows work identically to before.
- [x] `onCreate`/wiring code is split into readable statements (no multi-statement `;`-chained lines beyond what's idiomatic for one-liners like `override fun onDestroy()`).

## Out of scope

- Cleaning up the semicolon-dense style anywhere outside `MainActivity.kt` (it appears elsewhere, e.g. `DisplayZonesView.kt`, `ScannerScreenCoordinator.kt` — separate task if desired).
- Any DI framework adoption.
- Changing controller/coordinator responsibilities themselves.

## Relevant areas

`app/src/main/java/com/fieldscanner/app/MainActivity.kt`, `ScannerScreenCoordinator.kt`, `calls/CallUiController.kt`.

## Verification

- [x] `./scripts/verify-and-install.ps1` on Windows (or `-Target emulator`) or `./scripts/verify` on Linux/macOS/CI
- [x] Acceptance criteria checked
- [ ] Additional scenario-specific check: manually exercise camera capture, journal open, and an outgoing call on-device to confirm no regression from the wiring change

## Outcome

`onCreate` wiring extracted into a private `MainActivity.buildScreen()`; semicolon-chained lines expanded into statements (idiomatic one-liner overrides `onDestroy`/`onWindowFocusChanged`/`onNewIntent`/`enterFullscreen` left as-is, per the task). `callUi` is now constructed before `scannerScreen`, so the `{ callUi.isPickerVisible }` callback no longer forward-references an unassigned `lateinit var`; every collaborator is built after its dependencies. Only reorder is `CallUiController`'s side-effect-free constructor moving earlier — no behavior change.

Changed: `app/src/main/java/com/fieldscanner/app/MainActivity.kt`, `docs/modules/ui-and-input.md`, `docs/agent-memory/architecture.md` (memory write-back), `.gitignore`.

Verified: `./scripts/verify-and-install.ps1` → `verify-and-install-status.txt` `state=PASS` (Gradle build, `testDebugUnitTest`, lint, APK install, `MainActivity` launch on device `R58R11G9FMH`), exit 0.

Closing commit: `de9f3b4`.

Not verified: manual on-device walk-through of camera capture / journal open / outgoing call — needs hands-on hardware interaction (rune touch zones, camera, Telecom). Regression risk low (pure construction-order reorder), but the three flows have not been manually exercised.
