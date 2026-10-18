# Architecture Memory

This file contains non-obvious architectural facts that are useful across
multiple engineering tasks. Each record states its verification status, date,
evidence, and a way to re-check it.

**Append-only (parallel-work rule, TASK-019):** add a new record at the END of
this file as its own `---`-separated block. Do not renumber, reformat, or
rewrite existing records or this preamble. Parallel tasks in separate worktrees
all touch these memory files, so append-only edits keep merges conflict-free.
`docs/agent-memory/INDEX.md` changes only when a whole topic is added.

---

## MainActivity is a host; feature controllers own behavior

Status: verified

Last verified: 2026-08-28

Fact:
`MainActivity` owns lifecycle and Activity Result integration. Scanner flow,
camera, journal, audio, voice search, and call UI behavior are delegated to
specialized coordinators and controllers. The collaborator object graph is
assembled in `MainActivity.buildScreen()` (called from `onCreate`), and the
collaborators are constructed in strict dependency order — `callUi` before
`scannerScreen`, `scannerScreen` before `camera`/`voice`/`callScreen` — so no
constructor or method reference reads a `lateinit` field before it is assigned.

Why this matters:
Adding feature behavior to the activity bypasses the established ownership
boundaries and makes lifecycle-sensitive flows harder to test and maintain.
Before TASK-003 the wiring lived inline in `onCreate` and passed
`{ callUi.isPickerVisible }` into `ScannerScreenCoordinator` while `callUi` was
still assigned further down; it only worked because the lambda is not invoked
during construction. Reordering `buildScreen()` statements, or feeding a
constructor a bound method reference / eager read of a not-yet-assigned
`lateinit` field, reintroduces a latent `UninitializedPropertyAccessException`
with no compiler warning.

Evidence:

- `app/src/main/java/com/fieldscanner/app/MainActivity.kt` (`buildScreen()`)
- `app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt`
- `app/src/main/java/com/fieldscanner/app/camera/CameraController.kt`
- `docs/tasks/done/TASK-003-mainactivity-wiring-cleanup.md`
- `docs/ARCHITECTURE.md`
- `docs/modules/ui-and-input.md`

Verification:
Inspect `MainActivity.buildScreen()` construction order and calls into the
named coordinator and controllers; compare ownership with
`docs/ARCHITECTURE.md`.

---

## Result-outline visual identity: catalogue + 640×200 template

Status: verified

Last verified: 2026-08-29

Fact:
The lower-zone result morph is driven by a finding's `visualId`, a validated
member of the closed `ScanVisual` catalogue in
`app/src/main/java/com/fieldscanner/app/data/finding/ScanOutlineCatalog.kt`. As
of TASK-005 this is a flat `enum class ScanVisual` of exactly 100 archetypes in
a normative order, `UNKNOWN_OBJECT` last; identifier == enum name, asset name ==
`name.lowercase(Locale.US) + ".svg"` under
`app/src/main/assets/scan_outlines/`. There is no `ScanCategory` and no
`<CATEGORY>_GENERIC` tier any more. `AnalysisResult.validated()` collapses
unknown/blank/legacy values (including the retired `NEUTRAL` /
`<CATEGORY>_GENERIC`) to `UNKNOWN_OBJECT` via `ScanVisual.resolve`; the prompt
(`FIELD_SCANNER_PROMPT_V5.md`) requires one of the 100 archetypes.
Persistence: `FindingEntity.visualId`, Room schema version 3 (unreleased, so
TASK-005 edited `MIGRATION_2_3` in place, no new migration),
`FindingsDatabase.MIGRATION_2_3`
(`visualId TEXT NOT NULL DEFAULT 'UNKNOWN_OBJECT'`). Rendering:
`ScanOutlineAsset` walks `ScanVisual.fallbackChain()` — `[this, UNKNOWN_OBJECT]`
for a specific archetype, `[UNKNOWN_OBJECT]` for `UNKNOWN_OBJECT` — and falls
back to the procedural wave/symbols if no asset loads. Artist SVGs use the
template artboard `viewBox="0 0 640 200"` directly; `ScanMorphGeometry` projects
that artboard onto the bounding box of `DisplayGeometry.bottomZone`
(≈317.1,513.1 … 632.8×198.2), i.e. the template is a ~1:1 normalisation of the
physical lower zone — no artist rescale/translate.

Why this matters:
Adding a new outline is a catalogue entry plus an SVG file, not renderer code.
Only `cat.svg` is shipped; every other `visualId` is expected to hit the
fallback until artwork lands. Changing the target rect in `ScanMorphGeometry`
silently desyncs delivered artwork from the physical zone.

Evidence:

- `app/src/main/java/com/fieldscanner/app/data/finding/ScanOutlineCatalog.kt`
- `app/src/main/java/com/fieldscanner/app/display/zones/ScanOutlineAsset.kt`,
  `ScanMorphGeometry.kt`, `BottomStatusRenderer.setResultVisual()`
- `app/src/main/java/com/fieldscanner/app/data/finding/FindingsDatabase.kt`
- `docs/artwork/scan-outline-catalog.md`
- `docs/tasks/done/TASK-004-semantic-outline-animations.md`,
  `docs/tasks/done/TASK-005-final-visual-archetype-catalogue.md`
- Tests: `ScanOutlineCatalogTest`, `ScanOutlineGeometryTest`,
  `FindingsDatabaseMigrationTest`, `AnalysisResultTest`

Verification:
`./gradlew :app:testDebugUnitTest`; inspect the catalogue enum and
`ScanMorphGeometry.TARGET_*` against `DisplayGeometry.bottomZone` bounds.

---

## Left display is independent from the bottom status renderer

Status: verified

Last verified: 2026-08-29

Fact:
`DisplayZonesView` renders the left system display with `LeftContextRenderer`, but no longer constructs its data sources. Since TASK-012 the backend sits behind ports in `com.fieldscanner.app.display`: `OutdoorTemperatureProvider` and `DeviceIndicatorProvider`, aggregated by the pure `LeftDisplayInputs` (merges battery + temperature into one `DeviceIndicators`, forwards start/stop/refresh). The graph — `OutdoorTemperatureSource(DeviceLocationProvider)` + `DeviceIndicatorSource` wrapped in `LeftDisplayInputs` — is assembled in `AppContainer.leftDisplay` and injected via `DisplayZonesView.setLeftDisplayInputs(...)` from `MainActivity.buildScreen()`; the View only forwards attach/detach to `LeftDisplayInputs.start()/stop()`. The Android sticky `ACTION_BATTERY_CHANGED` broadcast supplies battery percentage without a runtime permission while the View is attached. `OutdoorTemperatureSource` uses the shared best-effort `DeviceLocationProvider` (now in `com.fieldscanner.app.data.location`), then requests Open-Meteo's `current=temperature_2m` over HTTPS every 30 minutes while visible through the shared `HttpClient`; the Open-Meteo call is behind the swappable `TemperatureClient` seam (`OpenMeteoTemperatureClient`), not a bare object; no location or weather data is persisted. `LeftDisplayState` is pure and uses this priority: outdoor temperature ≤0 °C or ≥30 °C, analysis progress, battery, then unavailable. `LeftTemperatureGesture` keeps the temperature scale isolated from rune input: a 0.6-second hold in the lower expanded left zone shows it only while held, and a five-second hold forces a refresh. The numerical scale is intentionally limited to this explicit held interaction. This rendering does not alter `BottomStatusRenderer` or rune bindings.

Why this matters:
The left area is a system-context display, not a second rendering of the analysis signal. Future gesture controls belong to TASK-010 and must preserve this rendering ownership.

Evidence:

- `app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt`
- `app/src/main/java/com/fieldscanner/app/display/LeftDisplayInputs.kt`
- `app/src/main/java/com/fieldscanner/app/display/DeviceIndicatorSource.kt`
- `app/src/main/java/com/fieldscanner/app/display/OutdoorTemperatureSource.kt`
- `app/src/main/java/com/fieldscanner/app/AppContainer.kt` (`leftDisplay`)
- `app/src/main/java/com/fieldscanner/app/data/net/HttpClient.kt`
- `app/src/main/java/com/fieldscanner/app/data/location/DeviceLocationProvider.kt`
- `app/src/main/java/com/fieldscanner/app/display/zones/LeftContextRenderer.kt`
- `app/src/main/java/com/fieldscanner/app/display/zones/LeftTemperatureGesture.kt`
- `app/src/main/java/com/fieldscanner/app/display/zones/LeftDisplayState.kt`
- `app/src/test/java/com/fieldscanner/app/display/LeftDisplayInputsTest.kt`,
  `app/src/test/java/com/fieldscanner/app/data/net/HttpClientTest.kt`
- `app/src/test/java/com/fieldscanner/app/display/zones/LeftDisplayStateTest.kt`,
  `OpenMeteoTemperatureClientTest.kt`

Verification:
Run `./scripts/verify-and-install.ps1`, then inspect the device while idle, analysing, and with a battery-temperature warning source.
