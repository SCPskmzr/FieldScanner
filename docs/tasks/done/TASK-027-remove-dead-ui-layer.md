# TASK-027 — Удаление мёртвого UI-слоя (callOverlay и photoReview)

Выполняется **до TASK-028 и TASK-029**: обе переписывают
`ScannerScreenCoordinator`, и сначала выгоднее удалить то, что переписывать не
нужно. Все три задачи конфликтуют по одним файлам — вести последовательно.

## Goal

В `activity_main.xml` и коде экрана не остаётся виджетов и проводки, которые
никогда не показываются пользователю. Заодно исчезает большая часть текста,
попадающего в релизную сборку.

## Context

Весь UI приложения рисует `DisplayZonesView`. Два контейнера из
`activity_main.xml` остались от ранней версии и мертвы.

**`callOverlay`** — объявлен с `android:visibility="gone"`
([activity_main.xml:40-137](../../app/src/main/res/layout/activity_main.xml)),
содержит два `TextView` и шесть `Button` с захардкоженным русским текстом
(`ПРИНЯТЬ`, `ОТКЛОНИТЬ`, `ЗАВЕРШИТЬ`, `МИКРОФОН`, `ДИНАМИК`, `BLUETOOTH`).
`grep -rn "callOverlay" app/src/main --include=*.kt` показывает, что
`visibility` выставляется **только в `View.GONE`** — в
[CallScreenPresenter.kt:40](../../app/src/main/java/com/fieldscanner/app/calls/CallScreenPresenter.kt)
и [CallScreenPresenter.kt:52](../../app/src/main/java/com/fieldscanner/app/calls/CallScreenPresenter.kt);
в `View.VISIBLE` — нигде. Состояние звонка рисуется через
`displayZones.setCallDetails(...)` и `displayZones.setContactPicker(...)`.
Соответственно мертвы: `CallScreenPresenter.bindControls()`
([строки 22-36](../../app/src/main/java/com/fieldscanner/app/calls/CallScreenPresenter.kt)),
её вызов и блок из шести `findViewById` в
[MainActivity.kt:89-93](../../app/src/main/java/com/fieldscanner/app/MainActivity.kt),
поле `MainScreenView.callOverlay`.

**`photoReview`** — контейнер с `android:visibility="gone"`
([activity_main.xml:151-205](../../app/src/main/res/layout/activity_main.xml)),
и `R.id.photoReview`, `R.id.photoReviewActions`, `R.id.photoReviewTitle` в
Kotlin не упоминаются вообще. Значит `retakeButton` и `usePhotoButton`
недостижимы для пользователя, хотя код с ними работает как с живыми:

- `initialize()` вешает на них `setOnClickListener`
  ([ScannerScreenCoordinator.kt:52-53](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt));
- `analyzeAcceptedPhoto()` выключает их и ставит текст `R.string.analyzing_photo`
  ([строка 134-135](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt));
- `resetPhotoReviewActions()` возвращает их в исходное состояние и вызывается
  из трёх мест ([строка 185](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt)).

Реальный путь просмотра снимка идёт через руны и
`displayZones.showPhotoReviewBottomStatus()`.

Побочный эффект удаления: из релизной сборки уходят 9 из примерно 15
оставшихся нарушений правила «без видимого текста», перечисленных в
`docs/agent-memory/decisions.md` (запись «No visible text for children»).
Оставшиеся нарушения — `statusText`, `Toast`-ы и `AlertDialog` — закрывает
TASK-029.

`statusText` **живой**: `showCameraUnavailable()` делает его видимым
([ScannerScreenCoordinator.kt:69](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt)).
Его в этой задаче не трогать.

## Requirements

- Удалить из `activity_main.xml` контейнер `callOverlay` со всем содержимым и
  контейнер `photoReview` со всем содержимым.
- Удалить `CallScreenPresenter.bindControls()`, её вызов и соответствующий блок
  `findViewById` в `MainActivity.buildScreen()`; параметр `callOverlay`
  конструктора `CallScreenPresenter` и обе строки `callOverlay.visibility =
  View.GONE` в `render()`.
- Удалить из `MainScreenView` поля `usePhoto`, `retake`, `callOverlay` и их
  привязку в `bind()`.
- Удалить `resetPhotoReviewActions()` и все её вызовы, оба
  `setOnClickListener` в `ScannerScreenCoordinator.initialize()` и установку
  текста кнопки в `analyzeAcceptedPhoto()`. Проверить, что переходы состояний
  при этом не меняются: остальные вызовы в этих методах должны сохраниться.
- Удалить из `strings.xml` строки, оставшиеся без пользователей
  (кандидаты: `photo_review_title`, `retake`, `use_photo`, `analyzing_photo`,
  и всё, что `lintDebug` пометит как неиспользуемое). Строки, которые ещё
  используются, не трогать.
- Проверить, что после удаления не осталось неиспользуемых `import`
  (`android.widget.Button` в `MainScreenView` и `CallScreenPresenter` и т. п.)
  и что `lintDebug` не даёт новых предупреждений.
- Обновить `docs/modules/ui-and-input.md` и `docs/modules/telephony.md`.
- Дополнить в `docs/agent-memory/decisions.md` новым блоком в конце
  (правило append-only) актуальный список остаточных нарушений правила
  «без видимого текста» — старую запись не переписывать.

## Acceptance criteria

- [x] `grep -rn "callOverlay\|photoReview\|usePhoto\|retakeButton" app/src` не
  даёт попаданий.
- [x] Экран звонка (входящий, исходящий, разговор) рендерится как раньше —
  через `DisplayZonesView`; руны `EMBER`/`FLUX` продолжают отвечать за приём,
  отклонение и завершение (`CallScreenPresenter.handleRune`).
- [x] Просмотр снимка, запуск анализа, отмена анализа и возврат к камере
  работают без изменений.
- [x] `statusText` и `showCameraUnavailable()` сохранены и работают.
- [x] В `activity_main.xml` не осталось элементов с `android:text`.
- [x] `:app:lintDebug` не даёт новых предупреждений, в том числе про
  неиспользуемые ресурсы.
- [x] `./scripts/verify.ps1` проходит.
- [x] `docs/modules/ui-and-input.md`, `docs/modules/telephony.md` и
  `docs/agent-memory/decisions.md` обновлены.

## Out of scope

- Замена `Toast`-ов, `statusText` и `AlertDialog` на бестекстовые сигналы —
  это TASK-029.
- Перевод рендеринга на функцию от состояния — это TASK-028.
- Любые изменения в `DisplayZonesView` и его зонах.
- Изменение логики Telecom, whitelist и ролей.

## Relevant areas

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/fieldscanner/app/MainScreenView.kt`
- `app/src/main/java/com/fieldscanner/app/MainActivity.kt`
- `app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt`
- `app/src/main/java/com/fieldscanner/app/calls/CallScreenPresenter.kt`
- `docs/modules/ui-and-input.md`, `docs/modules/telephony.md`,
  `docs/agent-memory/decisions.md`

## Verification

- [x] Task gate: `./scripts/verify.ps1` (Windows) или `./scripts/verify`.
- [ ] Integration gate (владелец репозитория, на смёрдженном результате):
  `./scripts/verify`, затем `./scripts/verify-and-install.ps1`.
- [ ] На устройстве: полный проход сценария скана (камера → снимок → анализ →
  результат → продолжить) без визуальных отличий от текущей сборки.
- [ ] На устройстве: входящий звонок с разрешённого контакта — экран звонка
  выглядит как раньше, приём и завершение по рунам работают.
- [x] Acceptance criteria checked.

## Outcome

Fill in when closing the task, then `git mv` this file to `docs/tasks/done/`:

- Removed the unreachable `callOverlay` and `photoReview` containers, their
  resources and Kotlin wiring. Calls and photo review continue through
  `DisplayZonesView` and rune actions; the live camera-error `statusText` is
  unchanged. Updated UI, telephony and platform module documents and appended
  the remaining visible-text exceptions to decision memory. Implementation
  commit: `7f3597468a3de95f8b237fd7b4274fd662f819f3` (`TASK-027 Remove dead UI layer`).
- Verified: `./scripts/verify.ps1` with `JAVA_HOME=C:\AndroidStudio\jbr`
  (OpenJDK 17.0.6): documentation gate, self-check, `:app:lintDebug`,
  `:app:testDebugUnitTest`, and `:app:assembleDebug` all passed. Still not
  verified: integration gate after merge, APK installation, and manual device
  scenarios for scanning and telephony.
