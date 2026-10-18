# TASK-020 — Смоук-тест старта приложения в unit-тестах

Выделено из TASK-011. Независимо от TASK-018/019, но именно этот тест
компенсирует часть сигнала, который теряется при выносе установки на устройство
из цикла задачи (TASK-019).

## Goal

`:app:testDebugUnitTest` падает, если экран сканера перестаёт подниматься:
`MainActivity.onCreate` бросает исключение, разваливается граф `AppContainer`,
ломается инфляция `activity_main` или пропадает `View`, которую ищет
`findViewById`. Сейчас ни `lintDebug`, ни `assembleDebug`, ни существующие
unit-тесты этого не видят.

## Context

`scripts/verify` проверяет lint, unit-тесты и сборку APK. Ни один из этих шагов
не выполняет код старта: сборка успешна, а приложение падает на первом экране.
Единственный текущий сигнал — ручная установка через
`scripts/verify-and-install.ps1`.

Что реально происходит на старте
([MainActivity.kt:39-49](../../app/src/main/java/com/fieldscanner/app/MainActivity.kt)):
`setContentView(R.layout.activity_main)` → `MainScreenView.bind(...)` →
`buildScreen()` → `enterFullscreen()` → `scannerScreen.initialize()`.
`buildScreen()` строит весь граф экрана в жёстко заданном порядке
(комментарий в коде явно требует его сохранять) и первым обращением к
`container` создаёт `AppContainer`, то есть `FindingRepository` (Room),
`OpenAiImageAnalyzer`, `OpenAiTtsProxy`, `SharedPrefsLanguagePreference`,
`LeftDisplayInputs` с `DeviceLocationProvider`. Дальше — `CameraController`,
`VoiceSearchController`, `LanguageChangeController`, `CallScreenPresenter` с
шестью `findViewById` кнопок звонка, `RuneActionRouter`,
`RuneInputDispatcher` и два `lifecycleScope.launch`.

Важные факты, снимающие часть опасений:

- Кастомного `Application` в проекте нет — у `<application>` в
  `AndroidManifest.xml` нет `android:name`, `FileProvider` тоже не объявлен.
  Проверять надо старт `MainActivity`, а не `Application.onCreate`.
- Камера на старте не запускается: `ScannerScreenCoordinator.initialize()`
  ставит слушатели и вызывает `showCameraInactive()`, а
  `ProcessCameraProvider.getInstance` вызывается только из `camera.start()` по
  результату запроса разрешения
  ([CameraController.kt:35](../../app/src/main/java/com/fieldscanner/app/camera/CameraController.kt)).

Что мешает написать тест прямо сейчас:

- **Нет шва для подмены графа.** `AppContainer` создаётся внутри активити
  (`private val container by lazy { AppContainer(applicationContext) }`), внешне
  подменить его нечем. Это же расходится со стандартом слоёв из TASK-012
  («`View`s and coordinators receive their dependencies from outside»).
- **Нет тест-рантайма для Android-кода.** В
  [app/build.gradle](../../app/build.gradle) тест-скоуп — только JUnit 4 и
  `kotlinx-coroutines-test`; `testOptions` отсутствует. Существующие тесты
  (`ScannerViewModelTest`, `SignalAnalysisTest`) чистые JVM-тесты.
- **Цена Robolectric.** Под AGP 8.0 / compileSdk 33 / Kotlin 1.8 нужна
  Robolectric 4.10+ и `testOptions { unitTests.includeAndroidResources = true }`.
  Robolectric скачивает `android-all-*.jar` в собственный кеш (не в
  `GRADLE_USER_HOME`), то есть первый прогон на новой машине или в новом
  worktree идёт в сеть. Это единственная новая сторонняя зависимость, которую
  задача допускает.

## Requirements

- Ввести шов для графа: `MainActivity` получает `AppContainer` извне
  (`internal` setter или фабрика с значением по умолчанию), сохраняя текущее
  поведение в продакшне и порядок построения в `buildScreen()`.
- Добавить Robolectric в `testImplementation` и `testOptions {
  unitTests.includeAndroidResources = true }` в `app/build.gradle`. Версию
  зафиксировать явно, `@Config(sdk = [33])` — в тесте, а не в глобальном
  `robolectric.properties`, если это не требуется.
- Добавить смоук-тест, который поднимает `MainActivity` до `onCreate`
  (по возможности до `onResume`) с тестовым контейнером и проверяет, что:
  - исключения не выброшено;
  - `activity_main` инфлицирован и `MainScreenView.bind` нашёл свои `View`;
  - найдены все `View`, которые запрашивает `CallScreenPresenter.bindControls`.
- Тестовый контейнер не должен ходить в сеть, в реальную БД и в геолокацию:
  использовать фейковые реализации портов (`FindingStore`, `ImageAnalyzer`,
  `SpeechSynthesizer`, `LanguagePreference`) — они уже интерфейсы по TASK-012.
- Тест обязан быть детерминированным и не требовать устройства/эмулятора,
  укладываться в разумное время и работать в составе `:app:testDebugUnitTest`
  без дополнительных флагов.
- Если поднять полноценный `MainActivity` в Robolectric не удаётся,
  зафиксировать это в `## Outcome` и сузить сцену (инфляция `activity_main`
  плюс построение графа экрана без активити), не удаляя проверку целиком и не
  ослабляя остальные тесты.
- Обновить `docs/modules/platform-and-build.md` (новая тест-зависимость и
  `testOptions`) и `docs/modules/ui-and-input.md` либо `scanner.md` — что
  именно покрывает смоук.
- Записать в `docs/agent-memory/` (workflows или gotchas): что смоук-тест
  покрывает и чего не покрывает, и что Robolectric тянет `android-all` по сети
  на первом прогоне.

## Acceptance criteria

- [x] `:app:testDebugUnitTest` содержит смоук-тест старта и проходит на чистом
  дереве.
- [x] Намеренная поломка старта (например, исключение в конструкторе
  `AppContainer` или удаление `View`, которую ищет `bindControls`) валит
  `:app:testDebugUnitTest`. — проверено `error(...)` в `buildScreen()`:
  `1 test completed, 1 failed`; после отката — зелёный.
- [x] Тест не выполняет сетевых запросов, не открывает реальную Room-базу и не
  обращается к геолокации. — `TestAppContainer` фейкает все порты
  (`FindingStore`, `ImageAnalyzer`, `SpeechSynthesizer`, `LanguagePreference`,
  оба порта левого дисплея) и переопределяет `startBackgroundMaintenance()`.
- [x] Продакшн-поведение `MainActivity` не изменилось: порядок построения
  графа в `buildScreen()` сохранён, приложение запускается на устройстве. —
  порядок не тронут; шов — `internal var` с дефолтом `::AppContainer`. Запуск
  на устройстве в этом цикле **не** проверялся (worktree-объём), см. Outcome.
- [x] `./scripts/verify` проходит; новая зависимость — только в тест-скоупе.
- [x] `docs/modules/platform-and-build.md` и память обновлены.

## Out of scope

- Инструментальные тесты на устройстве и возврат `verify-and-install.ps1` в
  автоматический цикл задачи.
- Скриншотное/визуальное тестирование.
- Покрытие тестами сценариев камеры, звонков, TTS и распознавания речи.
- Рефакторинг `MainActivity` сверх минимального шва для контейнера.
- Compose, повышение `minSdk`, любые не тестовые зависимости.

## Relevant areas

- `app/src/main/java/com/fieldscanner/app/MainActivity.kt` (шов для контейнера)
- `app/src/main/java/com/fieldscanner/app/AppContainer.kt`
- `app/src/test/java/com/fieldscanner/app/` (новый тест и фейки портов)
- `app/build.gradle` (`testImplementation`, `testOptions`)
- `docs/modules/platform-and-build.md`, `docs/modules/scanner.md` или
  `ui-and-input.md`
- `docs/agent-memory/workflows.md` или `gotchas.md`

## Verification

- [ ] `./scripts/verify-and-install.ps1` на Windows — тесты зелёные, APK
  ставится и запускается (шов не сломал продакшн-старт). — **не выполнялось**:
  задача закрыта в worktree `task-020`, установка на устройство — шаг владельца
  репозитория при интеграционном мёрдже.
- [x] Ручной прогон: временно бросить исключение в `AppContainer` →
  `:app:testDebugUnitTest` краснеет; вернуть → зеленеет.
- [x] Прогон `:app:testDebugUnitTest` дважды подряд — стабильный результат и
  приемлемое время. — `--rerun-tasks` ×2: BUILD SUCCESSFUL, 31s и 27s.
- [x] Acceptance criteria checked.

## Outcome

Изменения:

- `app/build.gradle` — `testImplementation 'org.robolectric:robolectric:4.10.3'`
  (единственная новая зависимость, только тест-скоуп) и
  `android.testOptions { unitTests { includeAndroidResources = true } }`.
- `AppContainer.kt` — класс стал `open`; порты (`diagnostics`, `findings`,
  `languagePreference`, `analyzer`, `speech`, `leftDisplay`) — `open`-свойства
  с `by lazy`; разовая уборка вынесена в `protected open
  startBackgroundMaintenance()`, вызываемую из `init`. Продакшн-поведение то же
  (порты строятся при первом обращении, уборка идёт из `init`).
- `MainActivity.kt` — добавлен шов `internal var containerFactory: (Context) ->
  AppContainer = ::AppContainer`; `container by lazy { containerFactory(...) }`.
  Порядок `buildScreen()` не менялся.
- `app/src/test/java/com/fieldscanner/app/MainActivityStartupSmokeTest.kt` —
  новый Robolectric-смоук (`@Config(sdk=[33])`): поднимает реальные
  `FieldScannerApplication` + `MainActivity` до `onResume` с
  `TestAppContainer`, проверяет отсутствие исключений и инфляцию
  `activity_main` / `MainScreenView` (`cameraPreview`, `displayZones`,
  `capturedPhoto`).
- `app/src/test/java/com/fieldscanner/app/support/TestAppContainer.kt` — новый:
  подкласс `AppContainer` со всеми фейковыми портами и пустой
  `startBackgroundMaintenance()`.
- Модульные доки: `platform-and-build.md` (тест-зависимость, `testOptions`,
  подраздел про смоук), `ui-and-input.md` (шов и что покрывает смоук),
  `scanner.md` (`AppContainer` теперь `open`/lazy).
- Память: `docs/agent-memory/gotchas.md` — запись про смоук (что покрывает /
  чего нет) и про сетевую загрузку `android-all` Robolectric-ом на первом
  прогоне.

Проверка: `./scripts/verify.ps1` в worktree `C:\fs-wt\task-020` — BUILD
SUCCESSFUL (doc gate + self-check + lint + 97 unit-тестов, 0 падений +
assembleDebug), 52 задачи executed. Негативная проверка и двойной прогон — см.
чекбоксы выше.

Closing commit SHA: cd199e4 (branch `task-020`).

Не проверено:

- Запуск APK на реальном Galaxy XCover 4s (`verify-and-install.ps1`) — шаг
  владельца при интеграционном мёрдже ветки `task-020` в `master`.
- Требование «найдены все `View`, которые запрашивает
  `CallScreenPresenter.bindControls`» **устарело**: с рефакторингом call-UI
  метода `bindControls` и `findViewById` кнопок звонка больше нет (звонок
  рисуется на канве `DisplayZonesView`). Смоук покрывает единственные
  оставшиеся стартовые `findViewById` — три в `MainScreenView.bind`.
- Реальный `FieldScannerApplication.onCreate` создаёт синглтон
  `AndroidDiagnosticsLog` (только конструирует `File`-хендлы в песочный
  `filesDir` Robolectric; сети/БД/геолокации нет).
