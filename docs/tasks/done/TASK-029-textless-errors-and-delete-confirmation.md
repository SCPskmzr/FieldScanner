# TASK-029 — Бестекстовое подтверждение удаления и остаточный текст в релизе

Выполняется **после TASK-027** (тот убирает 9 из ~15 нарушений вместе с мёртвой
разметкой) и желательно **после TASK-025** (диагностика даёт куда девать текст
ошибки, когда его нельзя показать). Правит `ScannerScreenCoordinator`, как и
TASK-028 — вести последовательно.

## Goal

Релизная сборка не показывает текста нигде, включая пути ошибок и
подтверждение удаления результата анализа. Информация, которая сейчас уходит в
текст, либо становится бестекстовым сигналом, либо уходит в диагностический
лог.

## Context

Главное продуктовое правило приложения — «без видимого текста для ребёнка»
(TASK-002, `docs/agent-memory/decisions.md`, запись «No visible text for
children»). `DisplayZonesView.onDraw()` уже соблюдает его: `drawOverlayText()`
вызывается только под `BuildConfig.DEBUG`
([DisplayZonesView.kt:173](../../app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt)).
Это единственное место в `app/src/main`, где стоит проверка `BuildConfig.DEBUG`.

Оставшиеся нарушения на **живых** путях (после того как TASK-027 уберёт мёртвую
разметку):

1. **`AlertDialog` с захардкоженным русским текстом** —
   [ScannerScreenCoordinator.kt:168](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt):
   `setTitle("Удалить запись?")`, `setMessage("Фото, описание и озвучка будут
   удалены без возможности восстановления.")`, кнопки «Оставить» / «Удалить».
   Показывается ребёнку при удалении результата анализа
   (`requestDeleteCurrentFinding()` → `confirmAnalysisResultDeletion()`,
   [строки 154-161](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt)).
   Это худшее из нарушений: диалог, текст и системные кнопки одновременно.
2. **`Toast`-ы** — `toast(R.string.analysis_failed, …)`,
   `R.string.photo_save_error` (трижды), `R.string.camera_not_ready`
   ([строки 105-109, 127, 188](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt))
   и `showPhoneError(message)` в
   [MainActivity.kt:108](../../app/src/main/java/com/fieldscanner/app/MainActivity.kt).
3. **`statusText`** — `showCameraUnavailable(message)` делает `TextView`
   видимым с текстом ошибки камеры
   ([ScannerScreenCoordinator.kt:69](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt)),
   вызывается из трёх мест, включая отказ в разрешении на камеру.

Ключевой факт: **бестекстовый механизм подтверждения уже написан и работает.**
Удаление из журнала идёт через состояние
`ScannerUiState.JournalDeleteConfirmation` и
`displayZones.showJournalDeleteConfirmationBottomStatus()`
(`BottomStatusMode.DELETE_CONFIRMATION`,
[DisplayZonesView.kt:150](../../app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt)),
подтверждение и отмена — руны `ConfirmJournalFindingDeletion` /
`CancelJournalFindingDeletion`. Путь результата анализа просто не переведён на
этот же механизм, а получил диалог.

`ScannerViewModel.requestJournalFindingDeletion()` сейчас возвращает `false`
для состояния `AnalysisResult`
([ScannerViewModel.kt:94-98](../../app/src/main/java/com/fieldscanner/app/ScannerViewModel.kt)),
из-за чего координатор и уходит в ветку диалога.

## Requirements

- Перевести подтверждение удаления результата анализа на существующий
  бестекстовый механизм: отдельное состояние подтверждения (по образцу
  `JournalDeleteConfirmation`), режим нижнего статуса и подтверждение/отмена
  рунами. Удалить `confirmAnalysisResultDeletion()` и `AlertDialog` целиком,
  вместе с импортом `androidx.appcompat.app.AlertDialog`.
- Возврат по отмене должен вести обратно в `AnalysisResult` с тем же
  результатом, а не в исходное состояние.
- Заменить все `Toast` на бестекстовый сигнал (например режим нижнего статуса
  или короткая анимация в `DisplayZonesView`), а текстовую причину направить в
  диагностический лог из TASK-025. Набор различимых сигналов согласовать с
  существующими `ScannerEffect`, не плодя новые ради каждого сообщения.
- Заменить `statusText` на бестекстовое отображение недоступности камеры.
  Если `statusText` после этого не нужен — удалить `TextView` из разметки и
  поле из `MainScreenView`.
- `showPhoneError` в `MainActivity` привести к тому же правилу.
- Удалить из `strings.xml` строки, оставшиеся без пользователей.
- Ввести автоматический барьер против регресса: тест или проверка, падающая,
  если в релизной конфигурации появляется показ текста (например: в
  `activity_main.xml` нет `android:text`, в `app/src/main` нет вызовов
  `Toast.makeText` и `AlertDialog`, кроме как под `BuildConfig.DEBUG`).
  Механизм барьера выбрать посильный: unit-тест по исходникам, правило lint
  или проверка в `scripts/`.
- Обновить `docs/modules/ui-and-input.md` и `docs/modules/scanner.md`.
- Дополнить `docs/agent-memory/decisions.md` новым блоком в конце
  (правило append-only): правило соблюдается полностью, остаточных нарушений
  нет, и чем оно теперь защищено от регресса.

## Acceptance criteria

- [x] `grep -rn "Toast\|AlertDialog" app/src/main --include=*.kt` не даёт
  попаданий вне веток под `BuildConfig.DEBUG`. (не даёт попаданий вовсе)
- [x] В `app/src/main/res/layout/` нет атрибутов `android:text`.
- [x] Удаление результата анализа подтверждается рунами и бестекстовым нижним
  статусом; отмена возвращает в `AnalysisResult` с тем же результатом.
- [x] Удаление из журнала не изменило поведения. (ветка журнала в
  `deleteCurrentFinding` не тронута; `ScannerViewModelTest` зелёный)
- [x] Сбой анализа, сбой сохранения фото, неготовность камеры, отказ в
  разрешении на камеру и ошибка телефонии дают бестекстовый сигнал
  (`DisplayZonesView.playErrorSignal()` — общий красный импульс), а причина
  видна в `DiagnosticsLog`. См. примечание про «различимые» в Outcome.
- [x] Барьер против регресса краснеет при намеренном добавлении `Toast` или
  `android:text` и зеленеет после отката. (проверено вручную: probe → оба
  теста `ReleaseTextBarrierTest` FAILED; откат → PASS в `verify`)
- [x] `./scripts/verify.ps1` проходит. (JDK 17 `C:\AndroidStudio\jbr`;
  BUILD SUCCESSFUL: doc gate + self-check + lintDebug + testDebugUnitTest +
  assembleDebug)
- [x] `docs/modules/` и `docs/agent-memory/decisions.md` обновлены.

## Out of scope

- Отладочный текст под `BuildConfig.DEBUG` (`drawOverlayText`) — он остаётся.
- Изменение состава рун и их назначения сверх необходимого для подтверждения
  удаления.
- Редизайн зон `DisplayZonesView` и новая графика сверх минимально
  необходимого сигнала.
- Озвучивание ошибок голосом (потребовало бы сети — ровно в тот момент, когда
  её может не быть).

## Relevant areas

- `app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt`
- `app/src/main/java/com/fieldscanner/app/ScannerViewModel.kt`
- `app/src/main/java/com/fieldscanner/app/MainActivity.kt`,
  `MainScreenView.kt`
- `app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt` и
  `display/zones/BottomStatusRenderer.kt`
- `app/src/main/java/com/fieldscanner/app/runes/` (биндинги подтверждения)
- `app/src/main/res/layout/activity_main.xml`,
  `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/fieldscanner/app/` (барьер и тесты состояния)
- `docs/modules/ui-and-input.md`, `docs/modules/scanner.md`,
  `docs/agent-memory/decisions.md`

## Verification

- [x] Task gate: `./scripts/verify.ps1` — BUILD SUCCESSFUL (JDK 17
  `C:\AndroidStudio\jbr`; окружением по умолчанию `java` = 1.8, гейту нужен
  JDK 17).
- [ ] Integration gate (владелец репозитория, на смёрдженном результате):
  `./scripts/verify`, затем `./scripts/verify-and-install.ps1`. — не выполнялось.
- [ ] На устройстве в релизной сборке: удалить результат анализа —
  подтверждение бестекстовое, отмена возвращает результат. — не выполнялось.
- [ ] На устройстве: выключить сеть и запустить анализ — сигнал бестекстовый,
  причина есть в логе. — не выполнялось.
- [ ] На устройстве: отозвать разрешение камеры — сигнал бестекстовый. —
  не выполнялось.
- [x] Ручной прогон барьера: `Toast` + `android:text` добавлены пробно →
  оба теста `ReleaseTextBarrierTest` FAILED; откат → зелёные в `verify`.
- [x] Acceptance criteria checked.

## Outcome

### Что изменилось

**Бестекстовое подтверждение удаления результата анализа** (вместо `AlertDialog`):
- `ScannerViewModel`: новое состояние `AnalysisResultDeleteConfirmation(operationId, finding)`;
  `requestAnalysisResultDeletion()` / `cancelAnalysisResultDeletion()`;
  `deleteCurrentFinding()` теперь удаляет из любого из двух состояний подтверждения
  (из результата анализа → `CameraInactive`).
- `runes/`: `RuneContext.ANALYSIS_RESULT_DELETE_CONFIRMATION`; действия
  `ConfirmJournalFindingDeletion`/`CancelJournalFindingDeletion` переименованы в общие
  `ConfirmFindingDeletion`/`CancelFindingDeletion` и используются в обоих контекстах
  подтверждения; `toRuneContext()` расширен.
- `ScannerScreenCoordinator`: `requestDeleteCurrentFinding()` уводит в общий бестекстовый
  нижний статус (`showDeleteConfirmationBottomStatus()`, режим `DELETE_CONFIRMATION`,
  символ корзины — не менялся); `cancelFindingDeletion()` из результата анализа
  перерисовывает `showAnalysisResultBottomStatus(visual)` и остаётся в `AnalysisResult`
  с тем же результатом. `confirmAnalysisResultDeletion()` и `AlertDialog` удалены,
  импорт `androidx.appcompat.app.AlertDialog` убран.
- `DisplayZonesView.showJournalDeleteConfirmationBottomStatus()` →
  `showDeleteConfirmationBottomStatus()`.

**Бестекстовые ошибки** (вместо `Toast` и `statusText`):
- `DisplayZonesView.playErrorSignal()` + `drawErrorPulse()`: один общий ≈0.9 с
  затухающий красный импульс над нижней зоной (`ERROR_PULSE_MILLIS`, `ERROR_PULSE_CYCLES`).
- `ScannerScreenCoordinator` получил параметр `DiagnosticsLog`; `handleEffect`
  (`AnalysisFailed`, `PhotoSaveFailed`, `FindingDeleteFailed`), `takePhoto` (камера не готова
  / ошибка съёмки) и `showCameraUnavailable(reason)` пишут причину в лог и вызывают
  `playErrorSignal()`. Причины сбоев анализа/сохранения/удаления уже логировались в
  `ScannerViewModel` (TASK-025) — не дублировалось.
- `MainActivity.showPhoneError(reason)` → `container.diagnostics.error(...)` + `playErrorSignal()`;
  импорт `android.widget.Toast` убран. Строки причин камеры в `buildScreen` заменены на
  простые диагностические литералы.
- `statusText` удалён: из `activity_main.xml`, из `MainScreenView` (поле `status` и импорт
  `TextView`), и все `screen.status.*` из координатора.
- `strings.xml`: сведён к `app_name` + `captured_photo` (accessibility). Удалены и
  осиротевшие этой задачей строки (`camera_*`, `photo_save_error`, `analysis_failed`), и
  ранее мёртвые (`photo_saved`, `requesting_location`, `speech_*`, `api_key_missing` и т.п.).

**Тесты**: `ScannerViewModelTest` (+3: подтверждение/отмена/запрет вне результата),
`RuneBindingsTest` (переименования + новый контекст).

Закрывающий коммит: `290b7f0` (`feat: textless errors and delete confirmation (TASK-029)`).

### Барьер против регресса

`app/src/test/java/com/fieldscanner/app/ReleaseTextBarrierTest.kt` — unit-тест по исходникам,
входит в `:app:testDebugUnitTest` (то есть в `verify`). Ловит:
1. любой `android:text=` в `app/src/main/res/layout/**.xml`;
2. любое упоминание `Toast` или `AlertDialog` в `app/src/main/**.kt` в файле, где нет
   `BuildConfig.DEBUG` (debug-диагностика типа `drawOverlayText` остаётся разрешённой).

Проверено вручную: пробное добавление `import android.widget.Toast` и `android:text="probe"`
→ оба теста FAILED; после отката `verify` зелёный.

### Не проверено

- Integration gate (`./scripts/verify` + `./scripts/verify-and-install.ps1` на смёрдженном
  результате) — шаг владельца репозитория.
- Все четыре ручных прогона на устройстве (удаление результата и отмена; анализ без сети;
  отзыв разрешения камеры; вид импульса/корзины в release) — не выполнялись, нужно железо.
- Точная эргономика красного импульса и морфа результата при возврате по отмене — только
  логика и unit-тесты; визуально на устройстве не смотрелось.
