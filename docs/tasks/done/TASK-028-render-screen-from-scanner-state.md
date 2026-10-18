# TASK-028 — Рендеринг экрана как функция ScannerUiState

Выполняется **после TASK-027** (иначе придётся переносить в новый рендер
мёртвые виджеты) и **после TASK-026** (обе правят владение
`capturedPhotoFile`). Самая объёмная задача серии.

## Goal

Экран сканера рисуется из одного места — функции от `ScannerUiState`,
подписанной на `ScannerViewModel.state`. Добавление или изменение состояния
перестаёт требовать «не забыть семь вызовов View» в каждой ветке, а
рассинхронизация состояния модели и состояния экрана становится невозможной по
конструкции.

## Context

`ScannerViewModel` объявляет `StateFlow<ScannerUiState>` и присваивает
`_state.value` в 17 местах
([ScannerViewModel.kt](../../app/src/main/java/com/fieldscanner/app/ScannerViewModel.kt)).
**Коллекторов у этого потока нет.** `MainActivity` подписывается только на
`effects` ([MainActivity.kt:98](../../app/src/main/java/com/fieldscanner/app/MainActivity.kt)),
а `state` читается исключительно как `.value` для точечных вопросов —
`activeRuneContext()`, `playAnalysisResultAudio()`, `handleFindingDeleted()`
([ScannerScreenCoordinator.kt:66, 153, 169](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt)).
Проверяется командой:
`grep -rn "state.collect\|\.state\.value" app/src/main --include=*.kt`.

Рендеринг вместо этого выполняется императивно в `ScannerScreenCoordinator`:
каждый переход и уведомляет `ViewModel`, и сам дёргает набор свойств `View`.
Например `showCameraMode()`
([строки 117-121](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt))
делает восемь вызовов подряд, `showCameraInactive()` — семь, и оба дублируют
друг друга частично. `refreshRuneHints()` приходится вызывать вручную из **14**
мест.

Чем это плохо конкретно:

- Два источника правды. `ScannerUiState` и фактическое состояние `View`
  синхронны только потому, что каждая ветка вручную это поддерживает.
- Ничего из рендеринга не тестируется: нет функции, которой можно подать
  состояние и проверить результат.
- Восстановление невозможно: после гибели процесса `MainActivity.onCreate` →
  `initialize()` → `showCameraInactive()` сбрасывает экран в исходное,
  независимо от того, что было.

Правильный образец **уже есть в этом же репозитории**:
`CallScreenPresenter.render(state: FieldCallUiState?)`
([CallScreenPresenter.kt:38-69](../../app/src/main/java/com/fieldscanner/app/calls/CallScreenPresenter.kt))
— чистая функция от состояния, подписанная в `MainActivity` на
`CallCoordinator.state`. Задача — распространить этот приём на экран сканера,
а не изобретать новый.

Ограничения:

- `ScannerScreenCoordinator` держит состояние, которого нет в `ScannerUiState`:
  `capturedPhotoFile`, `analysisAudio`, `audioPermissionTarget`. Часть из них
  относится к состоянию экрана и должна переехать в `ScannerUiState`; часть —
  нет. Решение принимается явно и фиксируется в `## Outcome`.
- Некоторые действия — одноразовые (проигрывание аудио, `Toast`, вспышка
  затвора, запрос разрешения) и обязаны остаться в `effects`, а не в
  рендеринге: перерисовка не должна повторно проигрывать звук.
- Подписка обязана быть привязана к жизненному циклу и не течь.
- Тест-скоуп — только `junit` и `kotlinx-coroutines-test` (`gotchas.md`).
  Значит проверяемая часть рендера должна быть чистой: состояние → описание
  того, что показать, без обращения к `View`.

## Requirements

- Ввести чистое представление того, что должно быть на экране (например
  `ScannerScreenModel`), вычисляемое из `ScannerUiState` чистой функцией без
  Android-типов, и покрыть эту функцию unit-тестами по всем ветвям
  `ScannerUiState`.
- В `ScannerScreenCoordinator` (или выделенном презентере) сделать
  `render(model)`, который применяет представление к `View` идемпотентно:
  повторный вызов с тем же значением не должен давать видимых побочных
  эффектов.
- Подписать рендер на `ScannerViewModel.state` в `MainActivity` рядом с
  существующей подпиской на `effects`, с корректной привязкой к жизненному
  циклу.
- Убрать ручной вызов `refreshRuneHints()` из всех мест, где подсказки рун
  выводимы из состояния; оставить его только там, где источник контекста
  внешний (`contactsVisible()`, `languageChange.isActive()`) — и явно
  зафиксировать, почему.
- Перенести в `ScannerUiState` то состояние экрана, которое сейчас лежит в
  полях координатора и определяет картинку; оставшиеся поля прокомментировать.
- Одноразовые действия оставить в `ScannerEffect`. Убедиться, что перерисовка
  не проигрывает аудио повторно.
- Сохранить существующее поведение экрана без видимых изменений — это
  рефакторинг, а не редизайн.
- Обновить `docs/ARCHITECTURE.md` («Main flows»), `docs/modules/scanner.md`,
  `docs/modules/ui-and-input.md`.
- Записать в `docs/agent-memory/decisions.md` новым блоком в конце
  (правило append-only) решение «экран сканера рисуется из состояния;
  одноразовое — через effects» с указанием, где живёт чистая функция.

## Acceptance criteria

- [x] `ScannerViewModel.state` имеет ровно один коллектор, и он рисует экран.
  (`MainActivity.buildScreen()`: `scanner.state.collect { scannerScreen.render(scannerScreenModel(it)) }`.)
- [x] Чистая функция состояние → представление покрыта unit-тестами на все
  подтипы `ScannerUiState`. (`scannerScreenModel`; `ScannerScreenModelTest`, 12 тестов.)
- [x] `render` идемпотентен: повторный вызов с тем же представлением не
  проигрывает аудио, не показывает `Toast` и не сбрасывает анимации.
  (`render` выходит на `previous == model` и дальше трогает только изменившиеся
  поля; аудио/вспышка/импульс — в `ScannerEffect`, не в `render`. На устройстве
  не проверено — см. интеграционный гейт.)
- [x] `refreshRuneHints()` не вызывается вручную из веток переходов состояния.
  (Только из `render` при смене рунического контекста и вручную для внешних
  источников: `startLanguageChange`, `languageChange.onFinished`, контакты в
  `RuneActionRouter`.)
- [ ] Поведение экрана на устройстве не изменилось ни в одном из режимов:
  ожидание, камера, просмотр снимка, анализ, результат, журнал, подтверждение
  удаления, смена языка, звонок. (Ожидает on-device проверки владельцем —
  интеграционный гейт.)
- [x] Подписка не течёт: при пересоздании активити коллектор один.
  (`lifecycleScope.launch` в `buildScreen()`, отменяется в `onDestroy`, как и
  соседние коллекторы `effects` / `CallCoordinator.state`. На устройстве не
  проверено.)
- [x] `./scripts/verify.ps1` проходит. (`BUILD SUCCESSFUL in 52s`; doc gate +
  self-check + lint + 17 `ScannerViewModelTest` + 12 `ScannerScreenModelTest`.)
- [x] `docs/ARCHITECTURE.md`, `docs/modules/` и `docs/agent-memory/decisions.md`
  обновлены.

## Out of scope

- Восстановление состояния после гибели процесса (`SavedStateHandle`) —
  отдельная задача, если понадобится; здесь только фундамент для неё.
- Compose и любая замена View/XML.
- Изменение набора состояний, рун, действий или визуального оформления.
- Рефакторинг вложенных `runCatching` в `ScannerViewModel.analyze()`.
- Экран звонка: `CallScreenPresenter` уже соответствует целевому приёму.

## Relevant areas

- `app/src/main/java/com/fieldscanner/app/ScannerViewModel.kt`
- `app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt`
- `app/src/main/java/com/fieldscanner/app/MainActivity.kt`
- `app/src/main/java/com/fieldscanner/app/MainScreenView.kt`,
  `DisplayZonesView.kt` (только вызовы, не внутренности)
- `app/src/main/java/com/fieldscanner/app/journal/JournalCoordinator.kt`
- `app/src/test/java/com/fieldscanner/app/` (новые тесты рендер-модели)
- `docs/ARCHITECTURE.md`, `docs/modules/scanner.md`,
  `docs/modules/ui-and-input.md`, `docs/agent-memory/decisions.md`

## Verification

- [x] Task gate: `./scripts/verify.ps1` (Windows) или `./scripts/verify`.
  `BUILD SUCCESSFUL in 52s` (JDK `C:\AndroidStudio\jbr`), 2026-09-01.
- [ ] Integration gate (владелец репозитория, на смёрдженном результате):
  `./scripts/verify`, затем `./scripts/verify-and-install.ps1`.
- [ ] На устройстве пройти все режимы поимённо: ожидание → камера → снимок →
  анализ → результат → воспроизведение аудио → журнал → перелистывание →
  голосовой поиск → подтверждение удаления → смена языка → входящий звонок.
- [ ] На устройстве: проиграть аудио результата, затем вызвать перерисовку
  (поворот/возврат из фона) — аудио не запускается повторно.
- [x] Acceptance criteria checked (device-only пункты остаются открытыми).

## Outcome

Closing commit SHA: `43ba867` (feat: render the scanner screen from ScannerUiState (TASK-028)).

### Что изменилось

- **Новый `app/src/main/java/com/fieldscanner/app/ScannerScreenModel.kt`** — чистая
  `data class ScannerScreenModel` (нижний статус, `centerShowsCameraFeed`,
  `cameraPreviewActive`, `photo`, `journalDetails`, `runeContext`) и тотальная
  функция `scannerScreenModel(state): ScannerScreenModel` без Android-типов и без
  времени. Покрыта `app/src/test/java/com/fieldscanner/app/ScannerScreenModelTest.kt`
  (12 тестов, все 9 подтипов `ScannerUiState` + фолбэк визуала + равенство модели).
- **`ScannerScreenCoordinator`** — добавлен идемпотентный `render(model)`: хранит
  `renderedModel`, выходит на `previous == model`, дальше применяет только
  изменившиеся поля (`applyPhoto` / `applyBottomStatus` / центр-камера / hints /
  `applyCameraPreview`). Методы-переходы рун свелись к «толкнуть состояние»:
  `showCameraMode` = `audio.release(); scanner.showCamera()`, `analyzeAcceptedPhoto`
  берёт файл из `PhotoReview.photo`, `cancelFindingDeletion` только вызывает
  `scanner.cancel*`, `showLoadedJournal` — `scanner.showCameraInactive()` лишь на
  пустом журнале. `handleEffect` больше не рисует: `AnalysisFailed`/`PhotoSaveFailed`/
  `FindingDeleteFailed` → только `playErrorSignal()`, `AnalysisResultReady` → только
  проигрыш аудио. Убраны `handleFindingDeleted`, `showJournalEntry`,
  `cancelAnalysisAndReturnToPhoto`, `openJournal`.
- **`ScannerViewModel`** — `PhotoReview`/`Analyzing` теперь несут `photo: File`;
  `Journal`/`JournalDeleteConfirmation` — `finding: Finding?`. `beginAnalysis(photo)`,
  `showPhotoReview(photo)`. `moveJournal`/`filterJournal`/`showJournal`/
  `cancelJournalFindingDeletion`/`deleteCurrentFinding` (ветка журнала) переизлучают
  `Journal` с текущей находкой через `publishJournalFinding()`. `ScannerEffect.FindingDeleted`
  удалён (удаление теперь видно через переизлучённое состояние).
- **`MainActivity.buildScreen()`** — один новый коллектор рядом с `effects` и
  `CallCoordinator.state`: `scanner.state.collect { scannerScreen.render(scannerScreenModel(it)) }`.
  `initialize()` по-прежнему принудительно ставит `CameraInactive` на каждый
  `onCreate` (восстановление после гибели процесса — вне scope).
- **`scripts/check-documentation(.ps1)`** — новая строка покрытия
  `ScannerScreenModel.kt` → `docs/modules/ui-and-input.md`.
- **Тесты** — `ScannerViewModelTest`: `beginAnalysis(File(...))`, +3 теста
  (переизлучение `Journal` при навигации, пустой фильтр → `finding == null`,
  фото переносится в `Analyzing`/`PhotoReview`). Итого 17.
- **Документация** — `docs/ARCHITECTURE.md` (structure + Main flows),
  `docs/modules/scanner.md`, `docs/modules/ui-and-input.md`,
  `docs/agent-memory/decisions.md` (новый блок в конце).

Наблюдаемое поведение экрана не менялось намеренно: сопоставление всех девяти
состояний со старыми императивными вызовами (нижний статус, центр-камера, preview
CameraX через все состояния кроме `CameraInactive`/`JournalLoading`/`Journal*`,
видимость фото, `bringToFront` при появлении фото, отладочный оверлей журнала)
разобрано по переходам и сохранено.

### Поля координатора

- `capturedPhotoFile` → **переехало** в `ScannerUiState.PhotoReview.photo` /
  `Analyzing.photo`: это то, что рисуется на экране в этих режимах, и держать его
  вторым источником правды больше незачем.
- `analysisAudio` → **удалено**: это разовый хэндл проигрывания, не состояние
  экрана. Первый проигрыш идёт из `ScannerEffect.AnalysisResultReady.audio`,
  повтор (`PlayAnalysisResultAudio`) читает `AnalysisResult.finding.audioUri` —
  прежний фолбэк, теперь единственный путь.
- `audioPermissionTarget` → **осознанно осталось полем**: это отложенное
  продолжение запроса `RECORD_AUDIO`, а не то, что видно на экране.
- Находка журнала переехала из внутренних `journalFindings`/`journalIndex`
  `ScannerViewModel` в `ScannerUiState.Journal.finding` (и `JournalDeleteConfirmation.finding`)
  — без неё чистая функция не могла бы отрисовать фото и координаты записи.

### Не проверено

- On-device прогон всех режимов поимённо и проверка «перерисовка не
  переигрывает аудио» — за владельцем репозитория на интеграционном гейте
  (`./scripts/verify-and-install.ps1` на смёрдженном результате). Идемпотентность
  и единственность коллектора обеспечены по конструкции и unit-тестами, но не
  подтверждены на устройстве.
