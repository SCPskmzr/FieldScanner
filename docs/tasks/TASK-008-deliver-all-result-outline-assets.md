# TASK-008 — Добавить SVG-контуры для всех архетипов результатов

## Goal

Каждая из 100 поддерживаемых визуальных категорий результата имеет локальный
SVG-контур, в который морфится нижняя линия после анализа; для неизвестного
объекта доступен отдельный fallback-контур.

## Context

TASK-004 и TASK-005 уже реализовали полный путь: результат AI выбирает
`visual_id`, приложение сохраняет его и загружает SVG из
`assets/scan_outlines/`. Сейчас поставлен только `cat.svg`; остальные
архетипы показывают процедурный fallback. Для полного набора анимаций нужны
доставленные файлы иллюстраций.

## Requirements

- Добавить SVG для каждого значения `ScanVisual` из 100-элементного каталога,
  используя строго производное имя: идентификатор в нижнем регистре + `.svg`.
- Добавить `unknown_object.svg` для терминального `UNKNOWN_OBJECT` fallback.
- Каждый файл соблюдает контракт `docs/artwork/README.md`: `viewBox="0 0 640 200"`,
  ровно один видимый открытый `<path id="outline">`, без текста, фона,
  растровой графики, рамок, заливок и эффектов.
- Контуры должны быть семантически узнаваемыми, размещёнными в границах
  шаблона и рассчитанными на морфинг в нижнем сегменте XCover 4s.
- Не менять каталог `ScanVisual`, логику выбора/fallback, сетевые запросы или
  сценарии камеры и журнала.

## Acceptance criteria

- [ ] В `app/src/main/assets/scan_outlines/` есть по одному корректному SVG
  для каждого из 100 идентификаторов `ScanVisual`, включая `unknown_object.svg`.
- [ ] Имя каждого файла точно совпадает с вычисляемым `ScanVisual.assetName`.
- [ ] `ScanOutlineAsset` может загрузить и сэмплировать каждый добавленный
  контур; при этом отсутствующие и некорректные файлы по-прежнему безопасно
  используют terminal fallback.
- [ ] На устройстве успешный анализ представителя нескольких разных
  архетипов запускает морфинг в соответствующий контур; неизвестный объект —
  в `unknown_object.svg`.
- [ ] В нижнем сегменте не появляется текст, фон или артефакты SVG.

## Out of scope

- Изменение перечня 100 архетипов и промпта AI.
- Изменение длительности, кривой или последовательности morph-анимации.
- Создание контуров во время выполнения либо загрузка визуальных ассетов из
  сети.

## Relevant areas

`app/src/main/assets/scan_outlines/`, `docs/artwork/README.md`,
`docs/artwork/bottom-status-outline-template.svg`,
`docs/artwork/scan-outline-catalog.md`,
`ScanOutlineAsset.kt`, `ScanOutlineCatalog.kt`, тесты визуальных ассетов и
`docs/modules/ui-and-input.md`.

## Verification

- [ ] Добавить автоматическую проверку полноты имён ассетов и загрузки каждого
  SVG через `ScanOutlineAsset` либо эквивалентный тестируемый контракт.
- [ ] `./scripts/verify-and-install.ps1` на Windows (или с `-Target emulator`)
  либо `./scripts/verify` на Linux/macOS/CI.
- [ ] Acceptance criteria checked.
- [ ] Вручную на устройстве: проверить морфинг для CAT, двух других
  архетипов и UNKNOWN_OBJECT; подтвердить отсутствие визуальных артефактов.

## Progress

- Partial delivery in `ea96266`: added `person.svg`, `key.svg`, and `headphones.svg`, each using the required `id="outline"` path. The remaining catalogue assets are still outstanding; this task is not complete.

## Outcome

Fill in when closing the task, then `git mv` this file to `docs/tasks/done/`:

- What actually changed (files/behavior) and the closing commit SHA.
- Anything still not verified (e.g. manual on-device checks) — carry it here, not in chat history.
