# TASK-018 — Починить гейт покрытия документации

## Goal

`scripts/check-documentation` действительно ловит изменение исходников без
обновления соответствующей страницы `docs/modules/`: мёртвых правил нет,
непокрытых областей нет, а режим сравнения с базовой ветвью не теряет
незакоммиченные изменения.

## Context

Гейт покрытия документации — единственная автоматическая защита от
расхождения `docs/modules/` с кодом, и сейчас он частично не работает.

- **Мёртвое правило.** В таблице покрытия
  ([check-documentation.ps1:15](../../scripts/check-documentation.ps1),
  [check-documentation:15](../../scripts/check-documentation)) записан префикс
  `app/src/main/java/com/fieldscanner/app/Display` с большой буквы, а реальный
  пакет — `app/src/main/java/com/fieldscanner/app/display/`
  (`DeviceIndicatorSource.kt`, `DisplayGeometry.kt`, `LeftDisplayInputs.kt`,
  `OutdoorTemperatureSource.kt`, `zones/`, `effects/`). И `String.StartsWith`
  в PowerShell, и `grep -q "^..."` в bash регистрозависимы, поэтому правило
  ловит только корневые `DisplayState.kt`, `DisplayZoneId.kt`,
  `DisplayZonesView.kt` и **ни одного файла пакета левого дисплея**. Задачи
  TASK-009/013/014 правили именно `display/`, и гейт при этом молчал.
- **Непокрытые области.** Ни под одно правило не попадают пакеты `voice/`,
  `journal/`, `audio/`, `camera/`, `data/net/`, `data/location/`, каталоги
  ресурсов `res/values/`, `res/values-night/`, `res/drawable*/`, `res/xml/`, а
  также корневые `MainScreenView.kt`, `ScannerScreenCoordinator.kt`,
  `RuneActionRouter.kt`, `RuneHintPager.kt`, `SignalAnalysis.kt` (префикс
  `runes/` не матчит корневые `Rune*.kt`, префикс `MainActivity.kt` не матчит
  `MainScreenView.kt`).
- **Режим `-BaseRef` теряет незакоммиченное.** Скрипт выбирает *либо*
  `git diff --name-only "${BaseRef}...HEAD"`, *либо* `git diff HEAD` +
  untracked ([check-documentation.ps1:7-11](../../scripts/check-documentation.ps1)).
  Три точки уже дают семантику `merge-base`, отдельный вызов `git merge-base`
  не нужен, но при передаче базы гейт перестаёт видеть рабочее дерево. Любое
  будущее включение этого режима по умолчанию молча ослабит проверку в
  основном каталоге.
- **Асимметрия точек входа.** POSIX-[scripts/verify](../../scripts/verify)
  пробрасывает `${1:-}` в `check-documentation`, а
  [verify.ps1:11](../../scripts/verify.ps1) вызывает скрипт без аргументов и
  сам параметров не принимает.

Задача независима от параллельной работы агентов и должна быть выполнена до
продолжения TASK-008/010/013 — иначе они снова пройдут гейт, не обновив
страницу модуля.

## Requirements

- Исправить префикс пакета левого дисплея на фактический
  (`app/src/main/java/com/fieldscanner/app/display/`) в обеих реализациях
  таблицы покрытия; корневые `Display*.kt` должны остаться покрытыми.
- Закрыть страницами `docs/modules/` все перечисленные в `## Context`
  непокрытые исходные области. Распределение по страницам определить по
  фактическому содержанию модулей, не создавая новых страниц без нужды.
- Сделать сопоставление префиксов явно регистрозависимым и сверенным с
  файловой системой: добавить в `check-documentation` режим самопроверки,
  который обходит `app/src/main/`, корневые Gradle-файлы и падает, если найден
  файл, не попадающий ни под одно правило покрытия и не входящий в явный
  список исключений (иконки `mipmap-*`, сгенерированные ресурсы и т.п.).
  Список исключений — в самом скрипте, с комментарием, почему путь исключён.
- Запускать самопроверку из `scripts/verify` и `scripts/verify.ps1` вместе с
  обычной проверкой покрытия.
- Изменить режим `-BaseRef`: сравнение должно быть **объединением**
  `"${BaseRef}...HEAD"` и незакоммиченных + untracked-файлов, а не
  альтернативой. Поведение без `-BaseRef` не меняется.
- Дать `scripts/verify.ps1` необязательный параметр `-BaseRef`, пробрасываемый
  в `check-documentation.ps1`, — паритет с POSIX-`scripts/verify`. Без
  параметра поведение основного каталога остаётся прежним.
- Держать обе реализации (`.ps1` и POSIX) в согласованном виде: одинаковая
  таблица, одинаковые исключения, одинаковая семантика объединения.
- Обновить затронутые страницы `docs/modules/` (как минимум ту, что получает
  ранее непокрытые области) и `docs/agent-memory/` — записать, что гейт
  покрытия имеет самопроверку полноты и регистрозависим.

## Acceptance criteria

- [x] Изменение любого файла в `app/src/main/java/com/fieldscanner/app/display/`
  без правки его страницы модуля заваливает `scripts/check-documentation` и
  `scripts/check-documentation.ps1`.
- [x] Ни один файл под `app/src/main/` и ни один корневой Gradle-файл не
  остаётся без правила покрытия или явного исключения; режим самопроверки это
  доказывает и падает при добавлении нового непокрытого каталога.
- [x] `check-documentation` с `-BaseRef` видит и коммиты ветки, и
  незакоммиченные/untracked изменения одновременно.
- [x] `check-documentation` без `-BaseRef` ведёт себя ровно как раньше.
- [x] `./scripts/verify` и `./scripts/verify.ps1` без аргументов работают как
  раньше; `-BaseRef`/`$1` пробрасывается в обеих реализациях.
- [x] PowerShell- и bash-версии дают одинаковый вердикт на одном и том же
  наборе изменений.

## Out of scope

- Дробление `docs/modules/ui-and-input.md` на под-страницы.
- Проверка того, что обновлена именно нужная `##`-секция страницы, а не файл
  целиком.
- Автоматическое определение базовой ветки и worktree-режим (TASK-019).
- Любые изменения продакшн-кода приложения.

## Relevant areas

- `scripts/check-documentation.ps1`, `scripts/check-documentation`
- `scripts/verify.ps1`, `scripts/verify`
- `docs/modules/*.md`
- `docs/agent-memory/workflows.md` (или `gotchas.md`), `docs/agent-memory/INDEX.md`

## Verification

- [x] `./scripts/verify.ps1` на Windows (`JAVA_HOME=C:\AndroidStudio\jbr`, JDK 17)
  — `Documentation coverage: PASS`, `Documentation coverage self-check: PASS`,
  `:app:lintDebug` / `:app:testDebugUnitTest` / `:app:assembleDebug`
  `BUILD SUCCESSFUL`, exit 0. Регрессий базового пути нет.
  `./scripts/verify-and-install.ps1` (установка на устройство) не запускался —
  вне scope TASK-018 и без подключённого устройства.
- [x] Ручной прогон: изменить файл в `display/`, `voice/`, `camera/`,
  `res/values/`, `res/xml/`, `assets/scan_outlines/` по одному — гейт падает без
  правки страницы (`display/` → `ui-and-input.md`; `voice/` → `ai-services.md`;
  `camera/` → `scanner.md`; `res/values/` + `res/xml/` → `platform-and-build.md`)
  и проходит с ней.
- [x] Ручной прогон самопроверки: `app/src/main/java/com/fieldscanner/app/tmpcheck/Probe.kt`
  → `check-documentation --self-check` и `.ps1 -SelfCheck` падают, называя файл.
- [x] Сравнение вердиктов `.ps1` и POSIX-версии на одинаковом рабочем дереве —
  совпадают (clean → PASS; `display/` без доки → `ui-and-input.md`; `-BaseRef`
  union → коммит ветки + рабочее дерево).
- [x] Acceptance criteria checked.

## Outcome

Closing commit SHA: `40513f8` — `fix: correct and complete the documentation coverage gate (TASK-018)`

### Что изменилось

- `scripts/check-documentation` и `scripts/check-documentation.ps1`:
  - таблица покрытия расширена с 15 до 34 правил; каждая область под
    `app/src/main/` и корневые Gradle-файлы (`*.gradle`, `gradle.properties`,
    `gradle/wrapper/`) теперь сопоставлены со страницей `docs/modules/`;
  - мёртвое правило `.../app/Display` (не ловило пакет `display/`) заменено на
    пару правил: `.../app/Display` (корневые `Display*.kt`) + `.../app/display/`
    (пакет). Сопоставление префиксов сделано явно регистрозависимым
    (`StringComparison.Ordinal` / plain `grep`);
  - добавлен режим `--self-check` / `-SelfCheck`: обходит tracked + untracked
    файлы под `app/src/main/` и корневые Gradle-файлы, падает на любом пути без
    правила покрытия или записи в списке исключений (`res/mipmap-*`,
    `ic_launcher_*`, `gradlew*` — с комментариями в скрипте);
  - режим base ref (`-BaseRef` / `$1`) стал **объединением** `<ref>...HEAD` и
    рабочего дерева (незакоммиченное + untracked), а не альтернативой. Без
    аргумента поведение прежнее.
- `scripts/verify.ps1`: добавлен необязательный `-BaseRef`, пробрасываемый в
  `check-documentation.ps1`; после обычного гейта запускается `-SelfCheck`.
  `scripts/verify` (bash) уже пробрасывал `$1`; добавлен вызов `--self-check`.
- `docs/modules/platform-and-build.md`: новая секция «Ресурсы и тема
  приложения» (`res/values*`, `res/xml/`, `gradle.properties`, `gradle/wrapper/`,
  исключения иконки/`gradlew`); секция «Проверка» описывает self-check и base ref.
- `docs/modules/ui-and-input.md`: новая секция «Артефакты вне кода» —
  закреплённые за страницей ресурсы (`res/layout/`, `res/drawable-nodpi/`,
  `assets/scan_outlines/`, `assets/call_contacts_family*`) и корневые файлы.
- `docs/agent-memory/workflows.md`: запись о регистрозависимости гейта,
  self-check и union-семантике base ref.

### Не проверено

- `./scripts/verify-and-install.ps1` с установкой на XCover 4s — вне scope
  TASK-018; продакшн-код приложения не менялся, поэтому регрессий поведения
  устройства эта задача не вносит.
- POSIX `scripts/verify` / `scripts/check-documentation` фактически исполнялись
  через Git Bash на Windows; отдельный прогон на Linux/CI не выполнялся (логика
  зеркальна PowerShell-версии и совместима с bash 3.2).
