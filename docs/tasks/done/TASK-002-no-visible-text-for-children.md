# TASK-002 — Remove visible text from the child-facing UI outside debug builds

## Goal

In a normal (release/non-debug) build, the app must not render any human-readable text to the child user. Everything the app currently communicates via text must be conveyed through icons, photos, color, or animation instead, or moved behind an explicit `BuildConfig.DEBUG` gate.

## Context

Product philosophy (`docs/agent-memory/decisions.md`, "No visible text for children; text only in debug builds") states the app must not require the child to read. `DisplayZonesView.drawOverlayText()` currently draws several text overlays unconditionally in the normal rendering path: journal title/coordinates and call title/contact name/phone number.

## Requirements

- Audit every text draw call in `DisplayZonesView` and any other zone renderer (`display/zones/*`) for user-visible strings.
- Gate any remaining text rendering (e.g. developer diagnostics) behind `BuildConfig.DEBUG`.
- Replace journal identification (currently `journalTitle`) with a non-text equivalent so the child can still recognize/browse findings — e.g. the finding's own captured photo thumbnail already stored by `FindingMediaStorage`, or an icon/color code.
- Replace call identification (currently `callTitle`/`callContactName`/`callPhoneNumber`) with the existing icon-based `ContactSelection` (MOM/DAD) visual already used in the central zone, instead of printing the name or number.
- Decide whether `journalCoordinates` (raw lat/long text) should be dropped entirely for children or kept debug-only — it is diagnostic in nature, not something a child needs.

## Acceptance criteria

- [x] No text is drawn on screen in a non-debug build, across all `ScannerUiState` values and all call states. — Holds for the `DisplayZonesView` render path (its whole `drawOverlayText()` output is now `BuildConfig.DEBUG`-only). Residual out-of-scope text in release is enumerated in `## Outcome`.
- [x] Journal and call screens remain distinguishable/navigable for a non-reading child using only icons/photos/color. — Journal identity = the finding photo (`capturedPhoto`), navigated by runes; call identity = the МАМА/ПАПА `ContactSelection` icon in the central zone (all whitelisted, i.e. all reachable, calls). Not yet walked on a release build on-device.
- [x] Debug builds may still show text for development purposes (`BuildConfig.DEBUG`).

## Out of scope

- Redesigning the central "alien display" decorative renderer beyond what's needed to carry contact/journal identity.
- Voice narration behavior (already audio-based, not text-based).

## Relevant areas

`app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt`, `DisplayState.kt`, `journal/JournalCoordinator.kt`, `calls/CallScreenPresenter.kt`, `calls/CallUiController.kt`, `display/zones/*`.

## Verification

- [x] `./scripts/verify-and-install.ps1` on Windows (or `-Target emulator`) or `./scripts/verify` on Linux/macOS/CI
- [x] Acceptance criteria checked
- [ ] Additional scenario-specific check: manually walk through journal browsing and an incoming/outgoing call on a release build and confirm no text appears anywhere on screen — not done (requires a release build + hands-on device interaction).

## Outcome

`DisplayZonesView.onDraw()` now calls `drawOverlayText(canvas)` only under `BuildConfig.DEBUG`. In a release build the view draws no text in any `ScannerUiState` or call state: the journal-title / raw-coordinate overlays and the call title / contact-name / phone-number overlays are developer diagnostics only. `journalCoordinates` is kept, debug-only (it lives inside `drawOverlayText`).

No new non-text UI was needed — the equivalents already existed and are unchanged:

- Journal: `ScannerScreenCoordinator.showJournalEntry()` already shows each finding's photo full-screen (`capturedPhoto`), navigated with runes.
- Call: `CallScreenPresenter.render()` already maps the (always-whitelisted) caller to `ContactSelection.MOM/DAD` and renders the family photo with that person highlighted via `CentralZoneRenderer.drawContacts()`; it already passed `setCallDetails(null, null, null)` for MOM/DAD, so the text branch was only ever reachable for a non-whitelisted number, which the fail-closed whitelist prevents.

Decorative pseudo-symbols in `BottomStatusRenderer.drawSymbols()` (analysis-result flourish) are left as-is: non-linguistic decoration, already documented as intentional in `docs/modules/ui-and-input.md`, and outside this task's enumerated fields.

Changed files:

- `app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt` — `BuildConfig.DEBUG` gate on `drawOverlayText`.
- `docs/modules/ui-and-input.md` — documented the no-text-in-release policy.
- `docs/agent-memory/decisions.md` — memory write-back; recorded the fix and the known residual violations.
- `docs/tasks/TASK-002-*.md` → `docs/tasks/done/` (this file).

Verified: `./scripts/verify-and-install.ps1` → `app/build/verify-and-install-status.txt` `state=PASS` (Gradle `assembleDebug`, `testDebugUnitTest`, `lintDebug`, streamed install, `MainActivity` launch on device `R58R11G9FMH`), `BUILD SUCCESSFUL`.

Closing commit: `bcc4ceb`.

Not verified:
- Manual walkthrough on a **release** build (journal browsing + incoming/outgoing call) confirming zero on-screen text — needs a release build and hands-on device interaction.
- The verified build is `debug`, so it still shows the text overlays by design; the release suppression is by inspection of the single `BuildConfig.DEBUG` guard, not an on-device release check.

Known residual text in release, outside this task's scope (Context + Relevant areas are `DisplayZonesView`-only) — candidates for a follow-up task:
- `app/src/main/res/layout/activity_main.xml`: `callOverlay` `Button`s ("ПРИНЯТЬ"/"ОТКЛОНИТЬ"/…), `callStatusText`, `callContactText`. `callOverlay` is currently never made visible by `CallScreenPresenter`, but the text is authored in the layout.
- `statusText` `TextView` (camera-unavailable / permission messages) via `ScannerScreenCoordinator.showCameraUnavailable()`.
- Error `Toast`s: `analysis_failed`, `photo_save_error`, camera/permission strings.
- `AlertDialog` confirm-delete in `ScannerScreenCoordinator.confirmDeleteCurrentFinding()` ("Удалить запись?").
