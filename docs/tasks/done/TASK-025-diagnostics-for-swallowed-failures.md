# TASK-025 — Диагностика проглоченных ошибок

Независима от остальных задач этой серии (TASK-026…029) и делается первой:
без неё сбои на устройстве нечем разбирать, а правило «без видимого текста»
запрещает показать ошибку ребёнку.

## Goal

Любой сбой в анализе фото, синтезе речи, сохранении находки, камере и звонках
оставляет на устройстве запись, которую владелец может снять через adb и по
которой отличимы «нет сети», «ключ не настроен», «сервис вернул 4xx/5xx» и
«ответ не разобрался». Сейчас эта информация теряется полностью.

## Context

В `app/src/main` **нет ни одного вызова `Log.*`** и нет никакой обработки
необработанных исключений (`grep -rn "Log\." app/src/main --include=*.kt` —
пусто). Единственный сигнал о сбое — `Toast`, который в релизе к тому же
нарушает продуктовое правило «без видимого текста» (см. `decisions.md`).

Исключения гасятся до полной потери:

- [ScannerViewModel.kt:109-143](../../app/src/main/java/com/fieldscanner/app/ScannerViewModel.kt)
  — `analyze()` построен на вложенных `runCatching{}.onFailure{}`; в четырёх
  ветках `Throwable` не связывается с переменной и не логируется, наружу идёт
  только `ScannerEffect.AnalysisFailed`. `OpenAiImageAnalyzer` при этом
  различает как минимум: незаполненный `BuildConfig.OPENAI_API_KEY`
  (`require`), отсутствующий файл, неразобранное фото, `HttpException` со
  статусом и телом, отсутствие `output_text` в ответе
  ([OpenAiImageAnalyzer.kt:32-99](../../app/src/main/java/com/fieldscanner/app/data/analysis/remote/OpenAiImageAnalyzer.kt)).
  Ни одно из этих различий до владельца не доходит.
- [ScannerViewModel.kt:145-151](../../app/src/main/java/com/fieldscanner/app/ScannerViewModel.kt)
  — `savePhotoWithoutAnalysis`, `onFailure` без причины.
- [ScannerViewModel.kt:160-172](../../app/src/main/java/com/fieldscanner/app/ScannerViewModel.kt)
  — `deleteCurrentFinding`, `onFailure` без причины.
- [CameraController.kt:37-38](../../app/src/main/java/com/fieldscanner/app/camera/CameraController.kt)
  — `runCatching { providerFuture.get() }.getOrElse { onUnavailable() }` и
  `runCatching { bind(...) }.onFailure { onRearCameraUnavailable() }`:
  `ImageCaptureException`/`ExecutionException` отбрасываются.

Ограничения, которые задача обязана соблюсти:

- Тест-скоуп — только `junit` и `kotlinx-coroutines-test`, Robolectric/Mockito
  нет (`gotchas.md`, запись про `com.sun.net.httpserver`). Всё, что должно быть
  покрыто unit-тестом, обязано жить за чистым интерфейсом с ручным фейком.
- Логи не должны содержать секретов: `HttpException.body` и заголовок
  `Authorization` могут нести ключ и данные ответа. См. `gotchas.md`, запись
  «OpenAI settings must remain untracked».
- Устройство целевое слабое: файл лога обязан быть ограничен по размеру и не
  расти бесконечно (см. TASK-026 про хранилище).

## Requirements

- Ввести порт диагностики (например `com.fieldscanner.app.data.diagnostics.
  DiagnosticsLog`) с чистым интерфейсом: запись уровня, тега, сообщения и
  опционального `Throwable`. Реализация — в `remote/`-подобном подпакете,
  собирается в `AppContainer` по стандарту слоёв TASK-012.
- Реализация пишет и в `android.util.Log`, и в кольцевой файл в
  `filesDir` с жёстким верхним пределом (одна ротация, суммарно не более
  ~256 КБ). Запись не должна блокировать главный поток.
- Провести порт в места, где сейчас теряется причина: `ScannerViewModel`
  (`analyze`, `savePhotoWithoutAnalysis`, `deleteCurrentFinding`),
  `CameraController` (обе ветки `runCatching`), `OpenAiImageAnalyzer`,
  `OpenAiTtsProxy`, `FindingRepository`. `ViewModel` получает порт через
  конструктор/фабрику, `View`/`Activity` — из `AppContainer`.
- Ввести редакцию секретов: значение `BuildConfig.OPENAI_API_KEY` и заголовок
  `Authorization` никогда не попадают в лог; тело ошибки от сервиса
  усекается до разумного предела.
- Классифицировать сбой анализа в перечисление причин (нет конфигурации / сеть
  / статус сервиса / разбор ответа / прочее) так, чтобы классификация была
  чистой функцией и покрывалась unit-тестом без Android.
- Добавить перехват необработанных исключений процесса
  (`Thread.setDefaultUncaughtExceptionHandler`) с записью в тот же файл и
  корректной передачей управления предыдущему обработчику.
- Задокументировать в `docs/agent-memory/workflows.md`, как снять лог с
  устройства (конкретная команда `adb`), и обновить
  `docs/modules/platform-and-build.md` плюс `docs/modules/ai-services.md`.

## Acceptance criteria

- [x] Ни одна ветка `onFailure`/`getOrElse` в перечисленных файлах не теряет
  `Throwable` молча.
- [x] Классификатор причин сбоя анализа покрыт unit-тестами на все ветки.
- [x] Unit-тест доказывает, что ключ API и заголовок `Authorization` не
  появляются в отформатированной записи лога.
- [x] Файл лога не превышает заданный предел после искусственной серии записей
  (покрыто unit-тестом на чистой файловой абстракции или на `tmp`-каталоге).
- [x] Необработанное исключение попадает в файл лога, и прежний обработчик
  по-прежнему вызывается.
- [x] Запись в лог не выполняется на главном потоке.
- [x] `./scripts/verify.ps1` проходит; новых сторонних зависимостей нет.
- [x] `docs/agent-memory/workflows.md` содержит команду снятия лога;
  `docs/modules/` обновлены.

## Out of scope

- Отправка отчётов в облако, Crashlytics и любые сторонние SDK.
- Показ ошибок пользователю (текстом или иначе) — правило «без видимого
  текста» здесь не ослабляется; замена `Toast` на бестекстовый сигнал — это
  TASK-029.
- Ретраи и офлайн-очередь анализа.
- Рефакторинг вложенных `runCatching` в `analyze()` сверх минимального,
  необходимого, чтобы связать и залогировать причину.

## Relevant areas

- `app/src/main/java/com/fieldscanner/app/data/diagnostics/` (новый пакет)
- `app/src/main/java/com/fieldscanner/app/AppContainer.kt`,
  `ScannerViewModel.kt`, `ScannerViewModelFactory.kt`
- `app/src/main/java/com/fieldscanner/app/camera/CameraController.kt`
- `app/src/main/java/com/fieldscanner/app/data/analysis/remote/OpenAiImageAnalyzer.kt`
- `app/src/main/java/com/fieldscanner/app/data/speech/remote/OpenAiTtsProxy.kt`
- `app/src/main/java/com/fieldscanner/app/data/finding/FindingRepository.kt`
- `app/src/test/java/com/fieldscanner/app/` (новые тесты)
- `docs/modules/platform-and-build.md`, `docs/modules/ai-services.md`,
  `docs/agent-memory/workflows.md`

## Verification

- [x] Task gate: `./scripts/verify.ps1` (Windows) или `./scripts/verify`.
- [ ] Integration gate (владелец репозитория, на смёрдженном результате):
  `./scripts/verify`, затем `./scripts/verify-and-install.ps1`.
- [ ] На устройстве: временно убрать `OPENAI_API_KEY` из `local.properties`,
  выполнить анализ → в логе видна причина «не настроено», а не общий сбой.
- [ ] На устройстве: выключить сеть, выполнить анализ → в логе видна сетевая
  причина; ключ в логе отсутствует.
- [ ] Лог снимается командой, записанной в `workflows.md`.
- [x] Acceptance criteria checked.

## Outcome

- Added `DiagnosticsLog`, async Logcat/file implementation with 128 KiB + one
  rotation, secret redaction, analysis classification, and crash-handler
  delegation. Logged camera, scanner, OpenAI, persistence and telecom failures.
  Unit tests cover classification, redaction, rotation, crash delegation and
  off-main-thread writing. Closing implementation commit: `0db82d8`.
- Verified: `$env:JAVA_HOME='C:\AndroidStudio\jbr'; ./scripts/verify.ps1`.
  Not verified: integration gate and the three manual on-device scenarios
  (missing key, offline network, and adb retrieval against a physical device).
