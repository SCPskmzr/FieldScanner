# TASK-015 — Смена языка приложения голосом

## Goal

Дать возможность переключать язык приложения (озвучивание находок, язык
ответа анализатора, распознавание речи, сопоставление текста) без экранного
меню — голосом, так чтобы это оставалось доступным ребёнку и не нарушало
запрет видимого текста в release-сборке (TASK-002).

## Context

Сейчас язык жёстко зашит как русский в нескольких местах:

- `app/src/main/assets/prompts/FIELD_SCANNER_PROMPT_V5.md` требует ответы
  анализатора на русском (строки ~8 и ~354);
- `OpenAiTtsProxy` озвучивает текст находки как есть — язык неявно следует из
  текста;
- `VoiceSearchController` жёстко задаёт `EXTRA_LANGUAGE = "ru-RU"`
  (`app/src/main/java/com/fieldscanner/app/voice/VoiceSearchController.kt:36`);
- `ScannerViewModel` использует `Locale("ru", "RU")` для нормализации и
  сопоставления заголовков находок
  (`app/src/main/java/com/fieldscanner/app/ScannerViewModel.kt:80`).

Никакого хранимого параметра языка и точки его распространения нет. Экранное
меню настроек не подходит из-за запрета текста для ребёнка, поэтому
переключение должно быть голосовым.

## Requirements

- Ввести единый источник выбранного языка (например
  `LanguagePreference`/`AppLocale`) с безопасным дефолтом `ru-RU` и
  персистентностью между запусками (простые prefs/DataStore, без новых
  тяжёлых зависимостей).
- Закрытый список поддерживаемых языков (утверждён): русский `ru`, польский
  `pl`, белорусский `be`, английский `en`, украинский `uk`, иврит `he`.
  Дефолт — `ru`. Иврит — RTL: убедиться, что отладочные подписи и любые
  глифы корректно ведут себя в RTL; в release-UI текста нет, поэтому влияние
  минимально.
- Распространить выбранный язык на: язык ответа анализатора (параметризовать
  prompt вместо жёсткого «на русском»), язык озвучивания TTS, локаль
  `SpeechRecognizer`, `Locale` для нормализации/сопоставления в
  `ScannerViewModel`, числовые/строковые форматирования, где это влияет на
  результат.
- Реализовать голосовое переключение по утверждённому подходу
  «push-to-talk + эндоним» (см. раздел ниже). Без обязательного видимого
  текста; обратная связь — озвучиванием и/или пиктограммой/цветом языка в
  левой зоне.
- Триггер сессии (утверждён): долгое нажатие руны анализа/аудио `ASTRA` в
  контексте `RuneContext.JOURNAL`. Короткое нажатие `ASTRA` в журнале
  остаётся `PlayJournalAudio`; долгое — новое действие запуска смены языка.
  Это требует новой ветки ввода: `RuneInputDispatcher` сейчас знает только
  `onRunePressed`, нужен путь долгого нажатия и запись в `RuneBindings` для
  `JOURNAL` (по образцу того, как значения рун держатся только в
  `RuneBindings`, а не во View).
- Смена языка не должна удалять или ломать ранее сохранённые находки и их
  озвучку; уже синтезированное аудио остаётся на своём языке.
- Сетевые/распознавательные ошибки при попытке смены языка безопасно
  откатываются к текущему языку с понятной непереводной обратной связью.
- Обновить `docs/modules/ui-and-input.md` и `docs/modules/ai-services.md`,
  добавить/обновить тесты для чистой логики разбора голосовой команды,
  выбора локали и распространения параметра.

## Подход: push-to-talk + эндоним (вариант 1, утверждён)

> **Финальная реализация (после итераций с пользователем) отличается от плана
> ниже.** Распознавание речи в переключателе языка убрано полностью. Долгое
> нажатие `ASTRA` в журнале открывает режим `RuneContext.LANGUAGE_CHANGE` —
> **карусель по клавишам**: при входе озвучивается эндоним *текущего* языка,
> `ASTRA` — следующий язык (проговаривается), `EMBER` — применить, `DRIFT` —
> выход. Ребёнок ничего не говорит. `RECORD_AUDIO` режиму не нужен;
> `LanguageChangeController` зависит только от `LanguagePreference` и
> `LanguageVoice`. Удалены: `LanguageCommandMatcher`, `VoiceTextNormalizer`,
> `Levenshtein`, `ConfirmationMatcher` и «прогрев»/откат распознавания.
> `SpeechRecognitionSession` остался только для журнального голосового поиска.
> План ниже сохранён как история.

Ребёнок один раз произносит название языка — на самом этом языке («English»,
«Deutsch») или на текущем («английский», «немецкий»); оба принимаются. Чтобы
это было надёжно на XCover 4s (Android 7, распознаватель Google произвольной
версии, локаль обычно `ru-RU`, языковые модели могут быть не скачаны),
реализуем так:

1. **Одна короткая сессия записи, без повторов.** Долгое нажатие `ASTRA` в
   журнале запускает одну сессию распознавания. Ребёнок говорит один раз.
   Нужно разрешение `RECORD_AUDIO` — переиспользовать существующий запрос из
   `startJournalVoiceSearch`.

2. **Локале-независимый матчер (чистый класс, покрыт тестами).**
   `LanguageCommandMatcher` сопоставляет все гипотезы из
   `RESULTS_RECOGNITION` (не только первую) с локалью по таблице алиасов
   каждого поддерживаемого языка: эндоним + экзонимы на всех остальных
   поддерживаемых языках + частые ошибочные транскрипции, включая
   кириллическую (`Deutsch` → «дойч/дёйч»). Нормализация перед сравнением:
   lowercase по `Locale.ROOT`, обрезка, свёртка диакритики, транслитерация
   кириллица↔латиница; совпадение нечёткое (расстояние Левенштейна с
   порогом). Не зависит от того, какие модели распознавания есть на
   устройстве.

3. **Оппортунистическое авто-определение.** Если сборка распознавателя
   понимает `RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES`, передаём список
   поддерживаемых BCP-47 и читаем определённый язык из `EXTRA_LANGUAGE`
   результатов как дополнительный сигнал для матчера. Feature-detect, без
   жёсткой зависимости от поддержки.

4. **Подтверждение клавишей (уточнено при реализации; ранее было голосовое
   да/нет).** Долгое нажатие `ASTRA` вводит в отдельный режим
   `RuneContext.LANGUAGE_CHANGE` с тремя рунами: `EMBER` — применить
   озвученный язык, `ASTRA` — следующий кандидат (каждый озвучивается),
   `DRIFT` — выход без изменений. Матчер выдаёт кандидата, приложение
   озвучивает его эндоним, ребёнок подтверждает руной. Второй голосовой
   раунд убран как ненадёжный на XCover 4s; `ConfirmationMatcher` удалён.
   Язык применяется и сохраняется только после нажатия `EMBER`.

5. **Мягкая деградация без тупика.** Если распознавание недоступно
   (`isRecognitionAvailable == false`, нет сети, `ERROR_*`) или речь не
   разобрана, режим сразу начинает перебор языков теми же тремя рунами
   (`ASTRA` листает, `EMBER` применяет). Тупика нет никогда.

6. **Проверка целевой модели.** После применения языка — короткий «прогрев»
   распознавания в новой локали; при ошибке недоступности модели озвучиваем
   непереводной сигнал ошибки и откатываемся к предыдущему языку.

7. **Переиспользуемая сессия.** Выделить из `VoiceSearchController` общий
   компонент сессии распознавания (локаль + конфиг → гипотезы); журнальный
   голосовой поиск и смена языка используют его. Матчер и нормализация —
   отдельные чистые классы в пакете `voice/`, покрытые unit-тестами по
   образцу `AnalysisResult.validated()`.

Отклонённые альтернативы (для истории): фиксированная русскоязычная команда
«говори по-английски» — язык команды жёстко привязан; call-and-response
перебор как основной режим — медленнее (оставлен только как fallback п. 5);
per-scan переозвучка — требует перевода текста на N языков и лишних сетевых
вызовов.

## Acceptance criteria

- [x] Выбранный язык хранится между запусками и по умолчанию `ru`.
  (`SharedPrefsLanguagePreference` + `AppLanguage.DEFAULT`; `AppLanguageTest`.
  Персистентность именно между перезапусками на устройстве не проверялась.)
- [x] Поддержаны ровно шесть языков: `ru`, `pl`, `be`, `en`, `uk`, `he`.
- [x] Долгое нажатие `ASTRA` в журнале запускает сессию смены языка;
  короткое нажатие `ASTRA` в журнале по-прежнему проигрывает аудио находки.
  (`RuneBindings.resolveLongPress`, `RuneBindingsTest`; сам жест удержания
  1000 мс в `DisplayZonesView` на устройстве не проверялся.)
- [x] Язык можно переключить минимум между `ru` и `en` (и любой другой из
  шести), без экранного меню и без обязательного текста в release. Финальная
  реализация — карусель по клавишам без распознавания речи; логика покрыта
  `LanguageChangeControllerTest`. Фактический звук на устройстве не проверялся.
- [x] Вход в режим озвучивает **текущий** язык и стоит на нём; `ASTRA` —
  следующий (проговаривается), `EMBER` — применить, `DRIFT` — выход без
  изменений (`LanguageChangeControllerTest`, `RuneBindingsTest`).
  (Распознавание речи и голосовое подтверждение убраны по требованию
  пользователя; `LanguageCommandMatcher`/`VoiceTextNormalizer`/`Levenshtein`/
  `ConfirmationMatcher` удалены.)
- [x] Тупика нет: режим — это карусель, `EMBER` всегда применяет текущий
  озвученный язык (`LanguageChangeControllerTest`).
- [ ] После переключения на новом языке идут: озвучивание новых находок,
  ответ анализатора, распознавание речи и сопоставление заголовков.
  (Распространение параметра реализовано и покрыто `ScannerViewModelTest`;
  сквозная проверка на устройстве не выполнялась.)
- [x] Ранее сохранённые находки и их аудио не повреждаются; старое аудио
  сохраняет свой язык. (Миграций и изменений `FindingStore`/БД нет.)
- [x] Ошибка/таймаут в режиме безопасно завершают его непереводным тоном без
  смены языка (`LanguageChangeControllerTest`: timeout; `AndroidLanguageVoice`
  `ToneGenerator`). Сетевого шага в переключателе больше нет.
- [x] Приоритетные состояния левой зоны (температурное предупреждение)
  не нарушены: индикатор языка в левой зоне не добавлялся, а анимация прогресса
  анализа из левой зоны удалена (см. TASK-014) — во время скана там штатный
  заряд.
- [x] Соответствующие автоматические тесты проходят (`verify` PASS).

## Out of scope

- Полная локализация строк отладочного UI и `docs/`.
- Перевод исторических находок задним числом.
- Смена провайдера анализа/TTS и модели приватности Open-Meteo.
- Экранное меню настроек с выбором языка.
- Новые тяжёлые зависимости, Compose, повышение `minSdk`.

## Открытые вопросы

- Порог нечёткого совпадения и стартовый состав таблицы алиасов в
  `LanguageCommandMatcher` (шесть языков → эндонимы + взаимные экзонимы +
  кириллические/латинские мисрекогниции; иврит потребует и латинской
  транслитерации «иврит/hebrew/иврис» и т. п.).
- Индикация активного языка в левой зоне: цвет + пиктограмма на слот; или
  2-буквенный код только при `BuildConfig.DEBUG`.
- Жест долгого нажатия рун: порог удержания и как показать ребёнку, что
  удержание засчитано (короткая подсветка руны / звук).
- Нужна ли переозвучка уже сохранённых находок при смене языка (по умолчанию
  нет — старое аудио сохраняет свой язык).

## Relevant areas

- `app/src/main/java/com/fieldscanner/app/voice/VoiceSearchController.kt`
- `app/src/main/java/com/fieldscanner/app/runes/RuneBindings.kt`,
  `RuneModels.kt` (новое действие и путь долгого нажатия для `JOURNAL`)
- `app/src/main/java/com/fieldscanner/app/ScannerViewModel.kt`
- `app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt`
- `app/src/main/java/com/fieldscanner/app/data/speech/`
- `app/src/main/java/com/fieldscanner/app/data/` (анализатор, prompt-загрузка)
- `app/src/main/assets/prompts/FIELD_SCANNER_PROMPT_V5.md`
- `app/src/main/java/com/fieldscanner/app/display/zones/LeftContextRenderer.kt`
  (индикация активного языка, если выбрана)
- `app/src/test/java/com/fieldscanner/app/`
- `docs/modules/ui-and-input.md`, `docs/modules/ai-services.md`

## Verification

- [x] `./scripts/verify-and-install.ps1` on Windows — `state=PASS`
  (`app/build/verify-and-install-status.txt`), lint + 94 unit-теста + сборка +
  установка + запуск на USB-устройстве `R58R11G9FMH`.
- [x] Acceptance criteria checked (частично: пункты, требующие живого
  распознавания речи и жестов на XCover 4s, отмечены `[ ]` и перенесены ниже).
- [ ] На физическом XCover 4s: в журнале зажать `ASTRA`, `ASTRA` долистать до
  `Polski`, `EMBER` — применить; отсканировать объект и проверить язык
  озвучивания и ответа анализатора; короткое нажатие `ASTRA` в журнале всё ещё
  играет аудио находки; перезапустить приложение — язык сохранился; во время
  анализа слева просто заряд. **Не выполнено** — ручная проверка на устройстве.

## Outcome

Closing commit SHA: _pending — изменения готовы и проверены `verify` PASS,
коммит ещё не сделан._

### Что изменилось

Новый пакет `com.fieldscanner.app.voice`. **Итоговая реализация после
доработок с пользователем: переключатель языка — карусель по клавишам, без
распознавания речи и без сети.**

- `AppLanguage` — закрытый набор 6 языков (`ru, pl, be, en, uk, he`), дефолт
  `ru`; `AppLanguageTest`.
- Порты + реализации в `voice/remote/`: `LanguagePreference`
  (`SharedPrefsLanguagePreference`, prefs `field_scanner_prefs`/`app_language`),
  `LanguageVoice` (`AndroidLanguageVoice` — on-device `TextToSpeech` для
  эндонимов + `ToneGenerator` для тона таймаута), `SpeechRecognitionSession`
  (`AndroidSpeechRecognitionSession`, выделен из `VoiceSearchController`) —
  используется **только** журнальным голосовым поиском.
- `LanguageChangeController` — режим `RuneContext.LANGUAGE_CHANGE`: при входе
  озвучивает эндоним текущего языка и стоит на нём; `ASTRA` — следующий язык
  (проговаривается), `EMBER` — применить (`LanguagePreference.update` + ещё раз
  озвучить), `DRIFT` — выход. 25-секундный сторожевой таймаут перезапускается
  при каждом действии. Зависит только от `LanguagePreference` + `LanguageVoice`;
  покрыт `LanguageChangeControllerTest` на фейках.
- `AndroidLanguageVoice` озвучивает эндонимы через облачный `SpeechSynthesizer`
  (`OpenAiTtsProxy`), а не системный `TextToSpeech` — иначе `pl/be/uk/he` без
  установленного голоса молчали бы (замечено пользователем: озвучивались только
  `ru/en`). Каждый клип синтезируется один раз и кэшируется на диск
  (`cacheDir/language-voice/endonym-<code>.mp3`); `start()` вызывает
  `voice.prewarm(all)`. Промах кэша без сети/ключа → тон `ToneGenerator`.
  `LanguageChangeController.create` теперь принимает `SpeechSynthesizer`
  (передаётся `container.speech` из `MainActivity`).
- **Удалены** (были в первых итерациях, не нужны для карусели):
  `LanguageCommandMatcher`, `VoiceTextNormalizer`, `Levenshtein`,
  `ConfirmationMatcher` + их тесты; «прогрев»/откат распознавания;
  `EXTRA_SUPPORTED_LANGUAGES`/чтение `EXTRA_LANGUAGE`;
  `RecognitionConfig.supportedBcp47`; `RECORD_AUDIO` для смены языка не нужен.

Распространение языка:

- `FIELD_SCANNER_PROMPT_V5.md`: жёсткое «на русском» → плейсхолдер
  `{{OUTPUT_LANGUAGE}}`; `OpenAiImageAnalyzer` держит сырой шаблон в `by lazy` и
  подставляет `AppLanguage.promptDescriptor` на каждый вызов.
- `OpenAiTtsProxy`: `voiceInstructions(language)` вместо жёсткой английской
  строки «Speak Russian…».
- `VoiceSearchController` переписан на `SpeechRecognitionSession` +
  `languageProvider` (локаль `SpeechRecognizer`).
- `ScannerViewModel`: `localeProvider` вместо `Locale("ru","RU")` в
  `filterJournal`; набор синонимов «показать всё» расширен под 6 языков.
- `AppContainer.languagePreference` раздаёт `languageProvider`/`localeProvider`;
  `ScannerViewModelFactory` прокидывает локаль.

Ввод:

- `RuneContext.LANGUAGE_CHANGE` + `RuneAction.StartLanguageChange` (long-press
  триггер) / `ConfirmLanguageChange` / `CycleLanguageChange` /
  `CancelLanguageChange` (руны режима). `RuneBindings`: `hintOrder` +
  `bindingFor` для нового контекста (`EMBER`/`ASTRA`/`DRIFT`), `longPressBindings`
  + `resolveLongPress` (`JOURNAL`+`ASTRA`). `RuneInputDispatcher.onRuneLongPressed`;
  `DisplayZonesView` — таймер удержания 1000 мс на слоте подсказки + короткая
  белая вспышка, `onHintRuneLongPressed`; `MainActivity` проброс.
  `ScannerScreenCoordinator.activeRuneContext()` отдаёт `LANGUAGE_CHANGE` пока
  `languageChange.isActive()`, `execute` направляет три руны режима в контроллер.

Левая зона: анимация прогресса анализа удалена (см. TASK-014) — во время скана
там штатный заряд. `LeftDisplayContent.ANALYSIS_PROGRESS` и
`LeftContextRenderer.drawProgress` убраны; `LeftDisplayState.contentFor` и
`LeftContextRenderer.draw` больше не принимают `isAnalyzing`.

Документация: `docs/modules/ai-services.md`, `docs/modules/ui-and-input.md`,
`docs/modules/scanner.md`, `docs/agent-memory/decisions.md`, `docs/agent-memory/gotchas.md`.

### Не проверено (нужна ручная проверка на XCover 4s)

- Фактическое озвучивание эндонимов системным `TextToSpeech` (иврит RTL,
  отсутствие голосовых данных → деградация в `ToneGenerator`).
- Ответ анализатора и озвучивание новых находок на выбранном языке после
  переключения.
- Жест удержания `ASTRA` 1000 мс и его вспышка на экране.
- Сохранение выбранного языка между перезапусками приложения.
- Левая зона показывает просто заряд во время анализа.
