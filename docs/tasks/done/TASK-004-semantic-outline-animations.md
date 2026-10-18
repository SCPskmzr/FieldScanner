# TASK-004 — Semantic outline animations for recognized findings

## Goal

Turn the temporary cat morph into a production feature: after image analysis,
the lower display line morphs into a local SVG outline that matches the
recognized object or a safe category fallback.

## Context

The current result renderer always uses `scan_outlines/cat.svg` as a visual
prototype. External artwork is authored against
`docs/artwork/bottom-status-outline-template.svg`; the delivery contract is in
`docs/artwork/README.md`. The application must select and render those assets
without adding a second network request or relying on generated media at
runtime.

## Requirements

- Define a closed catalogue of supported `visual_id` values and map each value
  to one local SVG outline asset and category fallback.
- Extend image-analysis output and validation with `visual_id`; the analysis
  prompt must require one supported value or a fallback value.
- Persist `visual_id` with each finding, including a Room migration, so a
  stored finding preserves its visual identity.
- Replace the temporary unconditional cat selection with the finding's
  `visual_id`; keep a deterministic fallback when an asset is absent, malformed
  or from an older record.
- Update SVG loading to use the artist template's native `viewBox="0 0 640 200"`
  coordinates directly. Do not require artists to rescale or translate paths.
- Keep rendering local, allocation-conscious and compatible with minSdk 24 and
  the Galaxy XCover 4s.
- Preserve the existing result lifecycle: one morph per analysis result, a
  static outline while the result remains open, and a reset on camera/journal
  transitions.
- Keep artist-facing requirements in `docs/artwork/`; do not expose SVG
  details in the child-facing UI.

## Acceptance criteria

- [x] A successful analysis returns a validated `visual_id` from the catalogue
  or an explicit fallback.
- [x] A result morphs into the matching local asset; missing or invalid assets
  use the category or neutral fallback without crashing.
- [x] The same finding displays the same outline after process restart and in
  the journal where a visual result is shown.
- [x] SVG assets delivered in the documented `640 × 200` template coordinate
  system render inside the physical lower zone without manual artist scaling.
- [x] The cat prototype is retained as the `CAT` catalogue asset rather than a
  hard-coded result for every finding.
- [x] Unit tests cover catalogue lookup, response validation, persistence
  migration/default handling, fallback selection and morph geometry.

## Out of scope

- Creating the full illustration catalogue or commissioning artwork.
- Generating images or video at recognition time.
- Changing camera capture, speech, rune bindings or journal search behavior.

## Relevant areas

- `app/src/main/assets/prompts/FIELD_SCANNER_PROMPT_V5.md`
- `app/src/main/java/com/fieldscanner/app/data/analysis/`
- `app/src/main/java/com/fieldscanner/app/data/finding/`
- `app/src/main/java/com/fieldscanner/app/display/zones/`
- `app/src/main/assets/scan_outlines/`
- `docs/artwork/`

## Verification

- [x] `./scripts/verify-and-install.ps1` on Windows — `app/build/verify-and-install-status.txt`
  = `PASS` (lint + `testDebugUnitTest` + `assembleDebug`, then streamed install
  and `MainActivity` launch on the USB device, exit 0).
- [x] Acceptance criteria checked
- [ ] On-device check: analyse representative supported and unsupported
  objects, then confirm the matching/fallback outline and reset behavior — not
  performed (needs a live OpenAI key and real subjects).

## Outcome

### What changed

Catalogue and domain:

- `data/finding/ScanOutlineCatalog.kt` (new): closed `ScanVisual` catalogue
  (`ScanCategory` + specific ids + `<CATEGORY>_GENERIC` + `NEUTRAL`), each
  mapped to one `assets/scan_outlines/*.svg`. `resolve()` normalises any raw
  value (unknown/blank/legacy → `NEUTRAL`); `fallbackChain()` gives the
  deterministic requested → category-generic → `NEUTRAL` order.
- `AnalysisResult` gains `visualId`; `validated()` normalises it via
  `ScanVisual.resolve`.
- `OpenAiImageAnalyzer` reads `visual_id` from the response JSON.
- `FIELD_SCANNER_PROMPT_V5.md`: new `analysis.visual` block, `visual_id` in the
  output schema/rules, and a `final_check` item; the model must return one
  catalogue value.

Persistence:

- `Finding` / `FindingEntity` gain `visualId`; mappers updated.
- Room schema `version = 3`; `FindingsDatabase.MIGRATION_2_3` adds
  `visualId TEXT NOT NULL DEFAULT 'NEUTRAL'` (SQL exposed as
  `MIGRATION_2_3_SQL` for testing). `FindingRepository` persists
  `validAnalysis.visualId`; photo-without-analysis uses `NEUTRAL`.

Rendering:

- `CatOutlineAsset` → `ScanOutlineAsset`, `CatMorphGeometry` → `ScanMorphGeometry`
  (files `git mv`d). `ScanOutlineAsset.loadSamples(context, visual, count)`
  walks the fallback chain and returns `null` when no asset loads, so the
  renderer keeps its procedural wave/symbols fallback.
- `ScanMorphGeometry` consumes the artist template's native
  `viewBox="0 0 640 200"` directly, projecting it onto the bounding box of
  `DisplayGeometry.bottomZone` (`TARGET_* = 317.1, 513.1, 632.8, 198.2`) — no
  artist rescale/translate.
- `BottomStatusRenderer.setResultVisual(ScanVisual)` selects the outline and
  invalidates the cached samples on change; `drawResultMorph` replaces the
  unconditional `drawCatMorph`.
- `DisplayZonesView.showAnalysisResultBottomStatus(visual)` and
  `ScannerScreenCoordinator.showAnalysisResult` pass `finding.visualId`
  (resolved) into the renderer.
- `assets/scan_outlines/cat.svg` re-authored in `0 0 640 200` coordinates; it
  is now the `CAT` catalogue entry, not a hard-coded default. No other outline
  files are shipped (out of scope); other `visualId`s use the procedural
  fallback until artwork lands.

Docs / memory:

- `docs/artwork/scan-outline-catalog.md` (new) — `visual_id` → file → category;
  `docs/artwork/README.md` updated for the 640×200 contract and per-file
  delivery. `docs/modules/ai-services.md`, `findings-and-journal.md`,
  `ui-and-input.md` updated. New record in `docs/agent-memory/architecture.md`.

Tests (all green in `:app:testDebugUnitTest`):

- `ScanOutlineCatalogTest` (new) — resolve, per-category fallback, chain order,
  uniqueness, `CAT` is a catalogue entry.
- `FindingsDatabaseMigrationTest` (new) — 2→3 version bounds and the ADD COLUMN
  SQL / `NEUTRAL` default; legacy default resolves to a real entry.
- `ScanOutlineGeometryTest` (was `CatMorphGeometryTest`) — 640×200 projection,
  centre mapping, interpolation, path extraction.
- `AnalysisResultTest`, `FindingMappingTest`, `ScannerViewModelTest` extended
  for `visualId`.

### Closing commit

`705c9c582b07cab50a620f1c0bb381c5132946e0` —
*feat: catalogue-driven result outline morph (TASK-004)*
(this SHA recorded in a follow-up docs commit).

### Not verified

- End-to-end on-device recognition (supported vs unsupported subject → matching
  vs fallback outline, and reset on camera/journal transitions) was not run;
  requires a live key and physical subjects. The morph path itself, the
  fallback chain and the 640×200 geometry are covered by unit tests, and the
  full build installs and launches on the device.
- Actual Room `2→3` migration on an existing on-device database (unit test
  covers the statement and version bounds only; no instrumented migration test
  harness exists in this project).
