# TASK-005 — Final visual archetype catalogue (100 values)

## Goal

Replace the placeholder `ScanVisual` catalogue introduced by TASK-004 with the
final, product-approved set of 100 visual archetypes, so that a recognised
finding morphs into the outline for its real archetype and every unrecognised
or unsupported case resolves to a single explicit `UNKNOWN_OBJECT` fallback.

## Context

TASK-004 shipped the outline-morph pipeline (catalogue → validated `visual_id`
→ persisted `Finding.visualId` → `BottomStatusRenderer` morph, with a
procedural fallback). Its catalogue (`CAT`, `BIRD`, `MUG`, …, plus
`<CATEGORY>_GENERIC` + `NEUTRAL`) was an explicit placeholder.

The product now defines the real archetype list: **100 flat archetypes**, the
last of which, `UNKNOWN_OBJECT`, is the catch-all. There is no
per-category-generic tier in the final model. The canonical list is the
appendix of this task file.

Only `cat.svg` is currently delivered as artwork; every other archetype is
expected to fall back until its SVG lands (creating artwork stays out of
scope, as in TASK-004).

## Requirements

- Rewrite `data/finding/ScanOutlineCatalog.kt` as a flat `enum class ScanVisual`
  with exactly the 100 values in the appendix, in that order, the identifier
  equal to the enum name.
- Derive the asset name as `name.lowercase(Locale.US) + ".svg"`
  (`CAT` → `cat.svg`, `PEN_PENCIL` → `pen_pencil.svg`). Assets live in
  `app/src/main/assets/scan_outlines/`.
- Remove `ScanCategory`, `isCategoryFallback`, `categoryFallback()` and the
  `<CATEGORY>_GENERIC` entries — they have no counterpart in the final model.
- `UNKNOWN_OBJECT` is the terminal fallback (it replaces `NEUTRAL` everywhere):
  - `ScanVisual.resolve(raw)` returns `UNKNOWN_OBJECT` for unknown / blank /
    `null` / legacy input;
  - `fallbackChain()` is `[this, UNKNOWN_OBJECT]` for any specific archetype and
    `[UNKNOWN_OBJECT]` for `UNKNOWN_OBJECT` itself.
- Update every `ScanVisual.NEUTRAL` reference: `AnalysisResult.visualId`
  default, `FindingRepository` photo-without-analysis, `BottomStatusRenderer`
  initial `resultVisual`, `DisplayZonesView.showAnalysisResultBottomStatus`
  default parameter.
- Room: the schema is still at version 3 from TASK-004 and unreleased, so change
  `MIGRATION_2_3` / `MIGRATION_2_3_SQL` default from `'NEUTRAL'` to
  `'UNKNOWN_OBJECT'` in place. If, by the time this task runs, version 3 has
  been released to any real device, add `MIGRATION_3_4` instead and bump to
  version 4. State which path was taken in the Outcome.
- `AnalysisResult.validated()` keeps normalising `visualId` through
  `ScanVisual.resolve`; no behavioural change beyond the new fallback value.
- Update `assets/prompts/FIELD_SCANNER_PROMPT_V5.md`: the `analysis.visual`
  block, the `output.schema` `visual_id` line, the `output.rules` line and the
  `final_check` item must list the 100 archetypes and drop all
  `<CATEGORY>_GENERIC` wording. The model must return exactly one archetype or
  `UNKNOWN_OBJECT`.
- Update tests: `ScanOutlineCatalogTest` (rewrite for the flat model + a
  "catalogue has exactly 100 entries, `UNKNOWN_OBJECT` last, ids == names,
  asset names unique and derived" guard), `FindingsDatabaseMigrationTest`,
  `AnalysisResultTest`, `FindingMappingTest`, `ScannerViewModelTest`.
  `ScanOutlineGeometryTest` is geometry-only and should not need changes.
- Update docs: `docs/artwork/scan-outline-catalog.md` (all 100 rows,
  id → file, note `UNKNOWN_OBJECT` is terminal, only `cat.svg` shipped),
  `docs/artwork/README.md` naming wording, `docs/modules/ai-services.md`,
  `docs/modules/findings-and-journal.md`, `docs/modules/ui-and-input.md` (the
  fallback is now just requested → `UNKNOWN_OBJECT`, no category step), and the
  TASK-004 record in `docs/agent-memory/architecture.md`.
- Keep `cat.svg` as the `CAT` entry; do not add other outline files.

## Acceptance criteria

- [x] `ScanVisual` has exactly the 100 appendix values in order; `UNKNOWN_OBJECT`
  is the last entry and the only fallback.
- [x] A successful analysis returns a validated `visual_id` that is one of the
  100 archetypes; anything else is stored as `UNKNOWN_OBJECT`.
- [x] A finding whose `visualId` has a delivered asset morphs into it; every
  other finding (including `UNKNOWN_OBJECT` and legacy rows) uses
  `unknown_object.svg` if present, otherwise the procedural fallback, without
  crashing.
- [x] The persisted `visualId` round-trips unchanged across process restart.
- [x] `ScanCategory` and the `<CATEGORY>_GENERIC` entries no longer exist in
  the codebase.
- [x] The analysis prompt enumerates the 100 archetypes and no longer mentions
  category-generic values.
- [x] Unit tests cover catalogue size/order, `resolve` normalisation and
  fallback, `fallbackChain`, migration default, validation and mapping.
- [x] `./scripts/verify-and-install.ps1` reports `PASS`.

## Out of scope

- Authoring outline SVGs for archetypes other than `cat.svg`.
- Changing the morph animation, capture/analysis lifecycle, `ScanMorphGeometry`
  projection or the `640 × 200` artist template.
- Changing the prompt's separate `category` field or any non-visual analysis
  output.
- Journal display behaviour (the lower strip is still hidden in the journal).

## Relevant areas

- `app/src/main/java/com/fieldscanner/app/data/finding/ScanOutlineCatalog.kt`
- `app/src/main/java/com/fieldscanner/app/data/finding/AnalysisResult.kt`,
  `Finding.kt`, `FindingEntity.kt`, `FindingRepository.kt`, `FindingsDatabase.kt`
- `app/src/main/java/com/fieldscanner/app/display/zones/BottomStatusRenderer.kt`,
  `ScanOutlineAsset.kt`
- `app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt`,
  `ScannerScreenCoordinator.kt`
- `app/src/main/assets/prompts/FIELD_SCANNER_PROMPT_V5.md`
- `app/src/main/assets/scan_outlines/`
- `docs/artwork/`, `docs/modules/`, `docs/agent-memory/architecture.md`
- Tests under `app/src/test/java/com/fieldscanner/app/data/finding/` and
  `ScannerViewModelTest.kt`

## Verification

- [x] `./scripts/verify-and-install.ps1` on Windows (or `-Target emulator`) or
  `./scripts/verify` on Linux/macOS/CI
- [x] Acceptance criteria checked
- [ ] On-device check (if a live key is available): analyse a subject expected
  to hit `CAT`, a subject expected to hit another archetype, and an
  unrecognisable subject; confirm cat morph / procedural fallback / procedural
  fallback respectively, and reset on camera and journal transitions.

## Outcome

### What changed

- `data/finding/ScanOutlineCatalog.kt` rewritten as a flat `enum class ScanVisual`
  with exactly the 100 appendix archetypes in normative order, `UNKNOWN_OBJECT`
  last. Removed `ScanCategory`, `isCategoryFallback`, `categoryFallback()` and
  every `<CATEGORY>_GENERIC` entry. `id` == enum name (computed);
  `assetName` == `name.lowercase(Locale.US) + ".svg"` (computed).
  `resolve()` returns `UNKNOWN_OBJECT` for unknown/blank/null/legacy input;
  `fallbackChain()` is `[this, UNKNOWN_OBJECT]` for a specific archetype and
  `[UNKNOWN_OBJECT]` for `UNKNOWN_OBJECT`.
- `ScanVisual.NEUTRAL` → `ScanVisual.UNKNOWN_OBJECT` at every reference:
  `AnalysisResult.visualId` default, `FindingRepository.savePhotoWithoutAnalysis`,
  `BottomStatusRenderer.resultVisual`, `DisplayZonesView.showAnalysisResultBottomStatus`
  default. `ScannerScreenCoordinator` already used `ScanVisual.resolve` — no change.
- `AnalysisResult.validated()` behaviour unchanged apart from the new fallback
  value.
- Room: schema version 3 (from TASK-004) is unreleased, so **`MIGRATION_2_3` /
  `MIGRATION_2_3_SQL` were edited in place** — default now
  `'UNKNOWN_OBJECT'`. No `MIGRATION_3_4`, version stays 3.
- `assets/prompts/FIELD_SCANNER_PROMPT_V5.md`: `analysis.visual` block now lists
  all 100 archetypes as a single `values` list with rewritten `rule`/`rules`;
  `output.schema` `visual_id`, `output.rules` line and the `final_check` item
  updated; all `<CATEGORY>_GENERIC` / `NEUTRAL` wording removed. Model must
  return exactly one archetype or `UNKNOWN_OBJECT`.
- Tests: `ScanOutlineCatalogTest` rewritten for the flat model (100-entry guard,
  ids == names, derived + unique asset names, `resolve`/`fromId`/`fallbackChain`,
  no `_GENERIC`/`NEUTRAL`). `FindingsDatabaseMigrationTest`, `AnalysisResultTest`,
  `FindingMappingTest`, `ScannerViewModelTest` updated to `UNKNOWN_OBJECT`.
  `ScanOutlineGeometryTest` untouched.
- Docs: `docs/artwork/scan-outline-catalog.md` (all 100 rows id → file, terminal
  `UNKNOWN_OBJECT`, only `cat.svg` shipped), `docs/artwork/README.md` naming
  wording, `docs/modules/ai-services.md`, `docs/modules/findings-and-journal.md`,
  `docs/modules/ui-and-input.md`, and the TASK-004 record in
  `docs/agent-memory/architecture.md`.
- No outline SVGs added; `cat.svg` remains the only shipped asset.

### Room path

In-place `MIGRATION_2_3` edit (schema version 3 unreleased). No new migration.

### Closing commit

`025fbb8c67459002d0b3eb111dea51261cb5b707` — *feat: final 100-archetype visual
catalogue (TASK-005)*. `./scripts/verify-and-install.ps1` reported `PASS` on a
USB device (build, unit tests, lint, install and activity launch).

### Not verified

- On-device analysis checks (`CAT` morph / procedural fallback / reset on
  camera & journal transitions) — require a live OpenAI key and the device.

## Appendix — canonical archetype list

Order is normative. Identifier = enum name; asset = lower-case identifier +
`.svg`. Source: `fieldscanner_visual_archetypes_100.txt` (product-provided).

| # | `visual_id` | Значение |
| --- | --- | --- |
| 1 | `PERSON` | человек |
| 2 | `FACE` | лицо |
| 3 | `HAND` | рука |
| 4 | `DOG` | собака |
| 5 | `CAT` | кошка |
| 6 | `BIRD` | птица |
| 7 | `FISH` | рыба |
| 8 | `FARM_ANIMAL` | сельскохозяйственное животное |
| 9 | `SMALL_MAMMAL` | мелкое млекопитающее |
| 10 | `INSECT` | насекомое |
| 11 | `BUTTERFLY` | бабочка |
| 12 | `SPIDER` | паук |
| 13 | `SNAIL` | улитка |
| 14 | `REPTILE_AMPHIBIAN` | рептилия / земноводное |
| 15 | `BALL` | мяч |
| 16 | `TOY_CAR` | игрушечная машинка |
| 17 | `TOY_FIGURE` | игрушечная фигурка |
| 18 | `PLUSH_TOY` | мягкая игрушка |
| 19 | `BUILDING_BLOCKS` | конструктор / кубики |
| 20 | `TOY_WEAPON` | игрушечное оружие |
| 21 | `BOARD_GAME` | настольная игра |
| 22 | `CARD` | карточка / игровая карта |
| 23 | `PUZZLE` | пазл |
| 24 | `PHONE` | телефон |
| 25 | `LAPTOP` | ноутбук |
| 26 | `HEADPHONES` | наушники |
| 27 | `ELECTRONIC_DEVICE` | электронное устройство |
| 28 | `CAR` | автомобиль |
| 29 | `TRUCK` | грузовик |
| 30 | `BUS` | автобус |
| 31 | `BICYCLE` | велосипед |
| 32 | `MOTORCYCLE` | мотоцикл |
| 33 | `TRAIN` | поезд |
| 34 | `CONSTRUCTION_VEHICLE` | строительная техника |
| 35 | `AIRCRAFT` | летательный аппарат |
| 36 | `BOAT` | лодка / корабль |
| 37 | `LAMP` | лампа / светильник |
| 38 | `DOOR` | дверь |
| 39 | `WINDOW` | окно |
| 40 | `MIRROR` | зеркало |
| 41 | `FURNITURE` | мебель |
| 42 | `BUILDING` | здание |
| 43 | `CUP` | чашка / стакан |
| 44 | `BOTTLE` | бутылка |
| 45 | `PLATE_BOWL` | тарелка / миска |
| 46 | `BOX` | коробка |
| 47 | `BAG` | сумка / пакет |
| 48 | `BACKPACK` | рюкзак |
| 49 | `KEY` | ключ |
| 50 | `CLOCK_WATCH` | часы |
| 51 | `GLASSES` | очки |
| 52 | `UMBRELLA` | зонт |
| 53 | `COIN` | монета |
| 54 | `SHOE` | обувь |
| 55 | `HAT` | головной убор |
| 56 | `SHIRT` | футболка / рубашка |
| 57 | `PANTS` | брюки / штаны |
| 58 | `JACKET` | куртка |
| 59 | `BOOK` | книга |
| 60 | `PAPER` | бумага / лист бумаги |
| 61 | `PEN_PENCIL` | ручка / карандаш |
| 62 | `ART_CRAFTS` | принадлежности для творчества / поделок |
| 63 | `HAND_TOOL` | ручной инструмент |
| 64 | `METAL_PART` | металлическая деталь |
| 65 | `FRUIT` | фрукт |
| 66 | `BREAD` | хлеб / выпечка |
| 67 | `SWEET` | сладость |
| 68 | `DRINK` | напиток |
| 69 | `PREPARED_FOOD` | готовая еда / блюдо |
| 70 | `CUTLERY` | столовые приборы |
| 71 | `POT_PAN` | кастрюля / сковорода |
| 72 | `REFRIGERATOR` | холодильник |
| 73 | `VACUUM_CLEANER` | пылесос |
| 74 | `TOOTHBRUSH` | зубная щётка |
| 75 | `JEWELRY` | украшение |
| 76 | `SPORTS_EQUIPMENT` | спортивный инвентарь |
| 77 | `MUSICAL_INSTRUMENT` | музыкальный инструмент |
| 78 | `PLAYGROUND_EQUIPMENT` | оборудование детской площадки |
| 79 | `HELMET` | шлем |
| 80 | `BALLOON` | воздушный шар |
| 81 | `STREET_SIGN` | дорожный знак / уличный указатель |
| 82 | `TRASH_LITTER` | мусор |
| 83 | `BATTERY` | батарейка / аккумулятор |
| 84 | `CABLE_CHARGER` | кабель / зарядное устройство |
| 85 | `TOILET` | унитаз |
| 86 | `ROCK` | камень |
| 87 | `LEAF` | лист |
| 88 | `FLOWER` | цветок |
| 89 | `STICK_BRANCH` | палка / ветка |
| 90 | `TREE_PLANT` | дерево / растение |
| 91 | `MUSHROOM` | гриб |
| 92 | `FIRE` | огонь |
| 93 | `WATER` | вода |
| 94 | `SNOW_ICE` | снег / лёд |
| 95 | `SAND_SOIL` | песок / земля / грунт |
| 96 | `GLASS` | стекло |
| 97 | `BARK_WOOD` | кора / древесина |
| 98 | `SHELL` | ракушка |
| 99 | `BONE` | кость |
| 100 | `UNKNOWN_OBJECT` | неизвестный / неопределённый объект (терминальный фолбэк) |
