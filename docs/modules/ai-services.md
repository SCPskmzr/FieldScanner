# Анализ изображения и озвучивание

## Анализ изображения

`OpenAiImageAnalyzer` читает локальный prompt `assets/prompts/FIELD_SCANNER_PROMPT_V5.md`, уменьшает фото до максимальной стороны 1280 px, JPEG quality 80 и отправляет его в OpenAI Responses API. Ответ должен быть распознан как JSON `AnalysisResult` с заголовком, описанием, необязательной уверенностью и полем `visual_id`.

Язык пользовательских текстов ответа не зашит: в prompt-шаблоне стоит плейсхолдер `{{OUTPUT_LANGUAGE}}` (`OpenAiImageAnalyzer.OUTPUT_LANGUAGE_PLACEHOLDER`), который перед каждым запросом заменяется на `AppLanguage.promptDescriptor` (эндоним + BCP-47) от `languageProvider`. Enum-значения и `scientific_name` плейсхолдер не затрагивает. Сам шаблон читается один раз (`promptTemplate by lazy`), подстановка выполняется на каждый вызов, поэтому смена языка (TASK-015) действует со следующего скана без пересборки графа.

`visual_id` — служебный идентификатор контурного рисунка для нижнего дисплея, не показывается ребёнку. Prompt требует ровно одно значение из закрытого плоского каталога 100 архетипов (`ScanVisual` в `data/finding/ScanOutlineCatalog.kt`): конкретный архетип или `UNKNOWN_OBJECT`. Обобщённых значений категорий больше нет. `AnalysisResult.validated()` нормализует значение через `ScanVisual.resolve()`; всё неизвестное, пустое или устаревшее (включая старое `NEUTRAL` и `<CATEGORY>_GENERIC`) становится `UNKNOWN_OBJECT`. Схема каталога для художников — `docs/artwork/scan-outline-catalog.md`.

## Озвучивание

`OpenAiTtsProxy` отправляет текст находки в OpenAI Speech API, получает MP3 во временный cache-файл, после чего репозиторий присоединяет его к находке. Стилевая инструкция голоса (`voiceInstructions`) параметризована: `Speak <englishName> …` по `AppLanguage` от `languageProvider`. Уже синтезированное аудио ранее сохранённых находок не переозвучивается — оно остаётся на своём языке (TASK-015).

## Язык приложения

`LanguagePreference` (порт в пакете `voice/`, реализация `voice/remote/SharedPrefsLanguagePreference` поверх `SharedPreferences "field_scanner_prefs"`, ключ `app_language`) — единственный источник выбранного языка. Закрытый набор — `AppLanguage`: ровно шесть языков `ru, pl, be, en, uk, he`, дефолт `ru`. `AppContainer.languagePreference` раздаёт `languageProvider` в `OpenAiImageAnalyzer`, `OpenAiTtsProxy`, `VoiceSearchController` (локаль `SpeechRecognizer`) и `ScannerViewModel` (`localeProvider` для сопоставления заголовков журнала).

Переключение (TASK-015) — без экранного меню и **без распознавания речи**: долгое нажатие руны `ASTRA` в журнале вводит в режим `RuneContext.LANGUAGE_CHANGE`, приложение озвучивает эндоним текущего языка, дальше это карусель по клавишам — `ASTRA` следующий язык (проговаривается), `EMBER` применить, `DRIFT` выход. `LanguageChangeController` зависит только от `LanguagePreference` и `LanguageVoice`. Детали ввода — в `docs/modules/ui-and-input.md`. Порт `SpeechRecognitionSession` (`voice/remote/AndroidSpeechRecognitionSession`) остался только для журнального голосового поиска (`VoiceSearchController`), локаль которого тоже берётся из `LanguagePreference`.

`AndroidLanguageVoice` озвучивает эндонимы через `SpeechSynthesizer` (тот же `OpenAiTtsProxy`, что и для находок), а не через системный `TextToSpeech`: у устройства обычно нет голосов для `pl/be/uk/he`, и они бы молчали. Каждый эндоним синтезируется один раз и кэшируется на диск (`cacheDir/language-voice/endonym-<code>.mp3`), `LanguageChangeController.start()` заранее прогревает все шесть (`voice.prewarm`). Промах кэша без сети/ключа → короткий тон `ToneGenerator`, без текста.

## Сеть

`OpenAiImageAnalyzer`, `OpenAiTtsProxy` и клиент Open-Meteo не открывают `HttpURLConnection` сами: общий `internal`-хелпер `com.fieldscanner.app.data.net.HttpClient` централизует метод, заголовки, таймауты, запись тела, проверку `responseCode in 200..299` (иначе `HttpClient.HttpException` с телом ошибки) и гарантированный `disconnect()`. Специфика запроса — JSON тела, парсинг ответа — остаётся в вызывающих классах. Отмена анализа сохранена: `HttpClient.execute` отдаёт живое соединение через `onConnect`, и `OpenAiImageAnalyzer.cancel()` рвёт активный запрос его `disconnect()`.

## Конфигурация и безопасность

- API-ключ и модель берутся из неотслеживаемого `local.properties` в `OPENAI_API_KEY` и `OPENAI_MODEL`.
- Секреты, реальные запросы и ответы не попадают в Git и документацию.
- Для обоих сетевых сервисов установлен 30-секундный timeout; ошибки переводят UI в ошибочный результат, не должны удалять ранее сохранённые находки.
- Реальная доступность зависит от сети и валидного ключа, поэтому JVM-тесты не подтверждают работу API.
- Ошибки анализа классифицируются для журнала владельца как `NOT_CONFIGURED`, `NETWORK`, `SERVICE_STATUS`, `RESPONSE_PARSE` или `OTHER`. `OpenAiImageAnalyzer` и `OpenAiTtsProxy` передают исходное исключение в порт `DiagnosticsLog`; статус и безопасно усечённое тело ответа остаются доступны через adb, но ключ и `Authorization` редактируются.
