# UI и руническое управление

> **Структура секций зафиксирована (TASK-019).** Правьте свою `##`-секцию; новые секции добавляйте в конец. Не переставляйте и не переименовывайте существующие `##`-секции и не дробите файл на под-страницы — параллельные задачи в разных worktree правят эту страницу, и стабильные секции удешевляют слияние. Обязательный минимум секций: «Состояния сканера», «Центральная зона», «Нижний статус», «Левая зона», «Руны».

## Назначение

`MainActivity`, `MainScreenView`, `DisplayZonesView` и `activity_main.xml` образуют один fullscreen-интерфейс. `MainScreenView` централизует binding только живых виджетов: preview камеры, фото и `DisplayZonesView`; отдельные call/photo-review overlay, текстовые кнопки и текстовый `statusText` ошибки камеры удалены (TASK-029) — `activity_main.xml` не содержит ни одного `android:text`. `ScannerScreenCoordinator` владеет переходами scanner/photo/journal и эффектами ViewModel, получает общий `DiagnosticsLog` и `RuneActionRouter` маршрутизирует руны. Экран сканера он рисует из одного места — `render(ScannerScreenModel)`, где `scannerScreenModel(state)` — чистое отображение `ScannerUiState` в описание экрана, а `render` применяет его к `View` идемпотентно; подписка на `ScannerViewModel.state` заведена в `MainActivity.buildScreen()` рядом с `effects` (TASK-028, подробности в `docs/modules/scanner.md`). Ошибки для ребёнка бестекстовые: причина уходит в owner-only `DiagnosticsLog`, а на экране проигрывается короткий красный импульс над нижней зоной (`DisplayZonesView.playErrorSignal()`), общий для сбоя анализа, сохранения фото, удаления, неготовности/недоступности камеры и телефонии — это разовый эффект, а не часть `render`. Временный снимок живёт прямо в `ScannerUiState` (`PhotoReview`/`Analyzing`.`photo`), а не в поле координатора: отмена удаляет файл, сохранение без анализа отдаёт его в сохранение, а после сохранения долговременной копии состояние уходит в `AnalysisResult` и ссылка на временный файл больше нигде не держится. Activity связывает lifecycle и Activity Result API; CameraX, аудио, speech recognition, call permission/dialer flow и call-screen rendering вынесены в специализированные контроллеры. Сборка графа объектов экрана выполняется в отдельном методе `MainActivity.buildScreen()`, вызываемом из `onCreate`; коллабораторы создаются в порядке зависимостей (`callUi` до `scannerScreen`), поэтому ни один конструктор не читает ещё не присвоенное `lateinit`-поле, а перестановка строк ломает компиляцию, а не рантайм.

Старт экрана покрыт локальным смоук-тестом `MainActivityStartupSmokeTest` (TASK-020, Robolectric): он поднимает реальные `FieldScannerApplication` и `MainActivity` до `onResume` и падает, если `onCreate` бросает, разваливается граф `buildScreen()`, ломается инфляция `activity_main` или пропадает `View`. Шов — `internal var MainActivity.containerFactory` (по умолчанию `::AppContainer`): тест подставляет `TestAppContainer` с фейками портов, поэтому старт не открывает Room, не ходит в OpenAI и не трогает геолокацию. Порядок построения в `buildScreen()` и продакшн-поведение не меняются. Смоук **не** проверяет рантайм камеры/телефонии/TTS/распознавания и экранный рендер; см. `docs/modules/platform-and-build.md` (тест-зависимость и `testOptions`) и `docs/agent-memory/gotchas.md`.

`DisplayZonesView` остаётся Android-контейнером состояния, lifecycle-анимации и сенсорного ввода. Виртуальное пространство артворка `1286 × 745` проецируется на реальный размер View **единым равномерным (letterbox) масштабом** — см. секцию «Масштабирование дисплея». Неизменяемая разметка дисплея находится в `display/DisplayGeometry`; её проверяемые координаты — в `DisplayGeometryCoordinates`, а SVG-шаблон `docs/artwork/full-display-zones-template.svg` повторяет их. Центральная зона — в `display/zones/CentralZoneRenderer`, левая контекстная зона — в независимом `display/zones/LeftContextRenderer`, а подсказки правых рунических зон — в `display/zones/RuneHintsZoneRenderer`. Визуальные помехи центрального экрана изолированы в `display/effects/InterferenceEffect`.

`RuneHintPager` хранит временное состояние страниц подсказок, а `SignalAnalysis` содержит чистую логику анализа визуальной волны. Нижний статусный сегмент вместе с wave, capture flash и analysis-анимациями принадлежит `display/zones/BottomStatusRenderer`. Сенсорные области рун преобразуются в `Region` один раз при создании View, а не на каждое касание.

## Состояния сканера

Состояния сканера определены в `ScannerUiState`: `CameraInactive`, `Camera`, `PhotoReview`, `Analyzing`, `AnalysisResult`, `AnalysisResultDeleteConfirmation`, `JournalLoading`, `Journal`, `JournalDeleteConfirmation`. Каждое состояние самодостаточно для рендера (TASK-028): `PhotoReview` и `Analyzing` несут временный `photo: File`, `Journal` и `JournalDeleteConfirmation` — выбранную `finding: Finding?` (навигация по журналу и голосовой фильтр переизлучают `Journal` с новой находкой или с `null`, если совпадений нет). Экран целиком выводится чистой функцией `scannerScreenModel(state)` и идемпотентным `ScannerScreenCoordinator.render(model)`; ровно один коллектор `ScannerViewModel.state` заведён в `MainActivity`. Удаление находки — и из журнала, и из результата анализа — проходит через отдельное состояние подтверждения (TASK-029): `requestJournalFindingDeletion()` / `requestAnalysisResultDeletion()` только меняют состояние, `deleteCurrentFinding()` удаляет лишь из одного из этих двух состояний. `AnalysisResultDeleteConfirmation` несёт саму `Finding`, поэтому отмена (`cancelAnalysisResultDeletion()`) возвращает в `AnalysisResult` с тем же результатом. Соответствие в руны — `ScannerUiState.toRuneContext()`: `AnalysisResultDeleteConfirmation` → `RuneContext.ANALYSIS_RESULT_DELETE_CONFIRMATION`. Состояние контактов хранится как `ContactSelection`; состояние звонка поступает через `CallCoordinator` и приоритетнее состояния сканера.

## Центральная зона

Детский интерфейс не требует чтения: в release-сборке `DisplayZonesView` не рисует текст. Текстовые оверлеи журнала (заголовок и координаты находки) и звонка (статус, имя контакта, номер) в `DisplayZonesView.drawOverlayText()` выводятся только под `BuildConfig.DEBUG` как отладочная диагностика. В обычной сборке находку в журнале ребёнок опознаёт по её фотографии (`capturedPhoto`), а звонящего — по иконке `ContactSelection` (МАМА/ПАПА) в центральной зоне. Декоративные псевдосимволы результата анализа в `BottomStatusRenderer` не несут смысла и не считаются текстом.

## Нижний статус

Нижний графитовый сегмент — визуальная строка состояния для ребёнка: его контур светится многослойным янтарным светом, а всю поверхность пересекает тонкая сетка с крупными ячейками. Непериодическая линия входит через середину левого ребра, следует через центральный излом и выходит на верхнем правом вертикальном ребре, примерно на четверть его высоты выше стыка с нижней ломаной. Амплитуда в покое равна 28, в камере — 48; оба параметра линии, включая скорость морфинга, плавно переходят между режимами за три секунды. При съёмке вся зона получает медленно затухающую янтарную вспышку, после чего фиксируется точный контур линии, который был отрисован к моменту нажатия. Каждый вход в анализ начинает новый цикл с короткого спокойного состояния, затем тонкий янтарный сканер и яркая точка всегда проходят кривую слева направо; до пяти значимых локальных экстремумов получают краткий pulse, остаются помеченными на время удержания результата и затем плавно затухают. Под основной линией постоянно видны две очень слабые эхо-линии: рядом со сканером они схлопываются к сигналу, а вне его зоны мягко расходятся и почти незаметно «дышат». В результате анализа зафиксированная линия за 1,8 секунды morph-ится в открытый контур, соответствующий `visualId` находки, сохраняя тот же двухслойный янтарный светящийся штрих, и затем остаётся статичной. Выбор контура делает `BottomStatusRenderer.setResultVisual()` (вызывается из `DisplayZonesView.showAnalysisResultBottomStatus()`), а `ScanOutlineAsset` загружает первый доступный SVG из цепочки фолбэков `ScanVisual.fallbackChain()`: запрошенный архетип → `UNKNOWN_OBJECT` (единственный терминальный фолбэк; шага категории больше нет). Если ни один asset не читается, остаётся прежний результат с декоративными псевдосимволами. SVG берутся из `assets/scan_outlines/` в нативных координатах шаблона `viewBox="0 0 640 200"` — `ScanMorphGeometry` проецирует их прямо на bounding box нижней зоны без ручного масштабирования художником; на текущем этапе поставлен только `cat.svg`, остальные `visualId` попадают в процедурный фолбэк. Шаблон с точной асимметричной геометрией зоны — `docs/artwork/bottom-status-outline-template.svg`, технический контракт SVG — `docs/artwork/README.md`, каталог `visual_id` → файл → категория — `docs/artwork/scan-outline-catalog.md`. При загрузке журнала сегмент пустой. Режим `BottomStatusMode.DELETE_CONFIRMATION` (символ корзины) обслуживает подтверждение удаления и из журнала, и из результата анализа — метод `DisplayZonesView.showDeleteConfirmationBottomStatus()`. Поверх нижней зоны, независимо от режима, `DisplayZonesView.drawErrorPulse()` может проиграть короткий (≈0,9 с) затухающий красный импульс — единый бестекстовый сигнал сбоя (`playErrorSignal()`); смысла сверх «действие не завершилось» он не несёт, конкретная причина пишется в `DiagnosticsLog`. Отрисовка не хранит пользовательских данных и не влияет на доступные руны.

## Левая зона

Левая вертикальная зона имеет расширенную нижнюю часть: после `(185, 641)` её контур проходит через `(185, 691)`, `(150.5, 712.5)`, `(74.5, 705)`, `(44.5, 691)`, `(35.5, 618.5)` и `(61.5, 561)`. Она не повторяет нижний статус. Её бэкенд `DisplayZonesView` не создаёт: порты `OutdoorTemperatureProvider` и `DeviceIndicatorProvider` вместе с чистым агрегатором `LeftDisplayInputs` (сливает заряд и температуру в один `DeviceIndicators`) собираются в `AppContainer.leftDisplay` и передаются через `DisplayZonesView.setLeftDisplayInputs(...)` из `MainActivity.buildScreen()`; View лишь пробрасывает attach/detach в `start()/stop()`. `DeviceIndicatorSource` читает безопасный sticky broadcast `ACTION_BATTERY_CHANGED` только пока View присоединён к окну, а `LeftDisplayState` выбирает безтекстовую индикацию. По умолчанию сама зона является индикатором батареи: янтарная энергия, близкая к свечению нижнего сегмента, заполняет её снизу вверх; отдельной пиктограммы батареи нет. Анализ **не меняет** левую зону — во время скана она остаётся индикатором батареи (по требованию пользователя; отменяет TASK-014, `LeftDisplayContent.ANALYSIS_PROGRESS` и `LeftContextRenderer.drawProgress` удалены). Для температурного предупреждения `OutdoorTemperatureSource` раз в 30 минут, пока экран виден, получает последнюю доступную локацию через общий `DeviceLocationProvider` (пакет `data.location`) и запрашивает только `current=temperature_2m` у Open-Meteo по HTTPS через общий `data/net/HttpClient`; сам вызов Open-Meteo скрыт за подменяемым `TemperatureClient` (`OpenMeteoTemperatureClient`). Координаты и ответ не сохраняются. Наружная температура ≤0 °C либо ≥30 °C имеет приоритет и показывает красный предупреждающий знак. При отсутствии разрешения, локации или сети приложение остаётся стабильным: после сохранённого значения предупреждение исчезает, а базовым контентом становится заряд.

Удержание в нижней расширенной части левой зоны (от `y=561`) дольше 0,6 секунды временно заменяет обычный индикатор температурной шкалой. Шкала имеет диапазон −10…+45 °C, отметки и текущую температуру: круглый маркер занимает пропорциональную позицию, а числовое значение выводится только пока пользователь сознательно удерживает эту область. После отпускания зона немедленно возвращается к обычному контексту. Если удерживать более 5 секунд, `OutdoorTemperatureSource.refreshNow()` выполняет новый запрос; полученное значение за 0,6 секунды анимированно перемещает маркер.

## Руны

| Контекст | Доступные действия |
| --- | --- |
| Неактивный сканер | Ember (зелёная) — включить камеру; Astra (белая) — журнал; Boreal (фиолетовая) — контакты |
| Камера | Ember — снять фото; Drift (синяя) — выйти на главный экран |
| Проверка фото | Ember — сохранить без анализа и продолжить сканирование; Astra — анализировать; Drift — не сохранять и выйти на главный экран |
| Анализ | Flux (красная) — отменить анализ и вернуться к фото |
| Результат анализа | Ember — продолжить сканирование; Astra — повторить озвучку; Drift — главный экран; Flux — удалить находку с подтверждением |
| Подтверждение удаления результата анализа | Ember (зелёная) — удалить; Drift (синяя) — отменить и вернуться к тому же результату; нижний сегмент показывает только символ корзины |
| Загрузка журнала | Все руны выключены до завершения загрузки |
| Журнал | Cipher (оранжевая)/Boreal — предыдущая/следующая; Drift — главный экран; Astra — аудио; Ember — голосовой поиск; Flux — удалить запись с подтверждением |
| Подтверждение удаления из журнала | Ember (зелёная) — удалить; Drift (синяя) — отменить; нижний сегмент показывает только символ корзины |
| Контакты | Ember — позвонить; Boreal — следующий контакт (циклично); Drift — главный экран |
| Смена языка | Ember — применить озвученный язык; Astra — следующий язык (озвучивается); Drift — выйти без изменений |
| Входящий звонок | Ember — ответить; Flux — отклонить |
| Активный/исходящий звонок | Flux — завершить |

Источник назначений — `runes/RuneBindings.kt`; не добавляйте значения рун непосредственно во View или listener. `RuneInputDispatcher` — общая точка для сенсорной эмуляции и будущего аппаратного ввода. Подтверждение/отмена удаления в обоих контекстах — общие действия `RuneAction.ConfirmFindingDeletion` / `CancelFindingDeletion` (TASK-029).

### Долгое нажатие руны

`RuneBindings` держит и обычные, и long-press-назначения (`resolveLongPress`, карта `longPressBindings`). Пока задан один hold: `JOURNAL` + `ASTRA` → `RuneAction.StartLanguageChange` (вход в режим смены языка приложения, TASK-015); короткое нажатие `ASTRA` в журнале остаётся `PlayJournalAudio`, и long-press-назначения не входят в порядок подсказок. `DisplayZonesView` в `ACTION_DOWN` над слотом подсказки запускает таймер `RUNE_LONG_PRESS_MILLIS = 1000` мс; при срабатывании рисует короткую белую вспышку по слоту, гасит последующий `ACTION_UP` и вызывает `onHintRuneLongPressed`; `RuneInputDispatcher.onRuneLongPressed` резолвит через `resolveLongPress`. Оба пути (`onRunePressed`, `onRuneLongPressed`) идут в `RuneActionRouter.execute`.

### Режим смены языка (`RuneContext.LANGUAGE_CHANGE`)

Долгое нажатие `ASTRA` в журнале вводит в отдельный контекст рун: `ScannerScreenCoordinator.activeRuneContext()` возвращает `RuneContext.LANGUAGE_CHANGE`, пока `LanguageChangeController.isActive()`, поэтому строка подсказок сразу показывает три руны — `EMBER` (`ConfirmLanguageChange`), `ASTRA` (`CycleLanguageChange`), `DRIFT` (`CancelLanguageChange`). Пока режим активен, `ScannerScreenCoordinator.execute` направляет эти действия в контроллер и не выполняет обычные сценарии.

**Голос не используется** — это карусель по клавишам. При входе режим озвучивает эндоним *текущего* языка и стоит на нём; `ASTRA` — следующий язык (каждый проговаривается), `EMBER` — применить озвученный (сохраняется в `LanguagePreference`, произносится ещё раз как подтверждение), `DRIFT` — выход без изменений. Ребёнок ничего не говорит. 25-секундный сторожевой таймаут перезапускается при каждом действии. `RECORD_AUDIO` режиму не нужен; `LanguageChangeController` зависит только от `LanguagePreference` и `LanguageVoice`.

`AndroidLanguageVoice` озвучивает эндонимы через **тот же облачный TTS**, что и сканер (`SpeechSynthesizer`/`OpenAiTtsProxy`), а не через системный `TextToSpeech` — иначе языки без установленного на устройстве голоса (обычно `pl/be/uk/he`) молчали бы. Каждый эндоним синтезируется один раз и кэшируется в `cacheDir/language-voice/endonym-<code>.mp3`, поэтому последующие прокрутки играют мгновенно и офлайн. `LanguageChangeController.start()` вызывает `voice.prewarm(all)` — фоновой синтез всех шести на входе в режим. Если синтез не удался (нет ключа/сети и нет кэша) — короткий тон `ToneGenerator`.

Порядок рун на экране также задаётся централизованно в `RuneBindings.kt`; он не выводится из порядка enum или из цвета руны. Для списка из более чем трёх действий этот порядок определяет и страницы подсказок.

Цвета сохраняют устойчивую роль между контекстами: Ember/зелёная — главное действие, Flux/красная — отмена или остановка, Drift/синяя — возврат, Boreal/фиолетовая и Cipher/оранжевая — движение вперёд/назад, Astra/белая — дополнительное действие. Переход к контактам с главного экрана через Boreal — согласованное исключение.

## Ограничения

- UI построен на Android Views/XML, не на Compose.
- Вызовы рун в телефонном состоянии обрабатываются до обычного dispatcher.
- Фото и звонки управляются только состоянием `DisplayZonesView` и рунами; скрытых XML-кнопок или overlay для этих режимов нет.
- В release-сборке текста нет нигде на живых путях: `Toast`, `AlertDialog` и текстовые виджеты в `app/src/main` запрещены. Барьер — unit-тест `ReleaseTextBarrierTest` (падает при `android:text` в любой разметке `res/layout/` или при `Toast`/`AlertDialog` в `.kt` вне файла, где также есть `BuildConfig.DEBUG`). Единственный оставшийся текст в release — `DisplayZonesView.drawOverlayText()` под `BuildConfig.DEBUG` (намеренно, вне scope TASK-029).

## Масштабирование дисплея

Весь детский интерфейс рисуется в фиксированном виртуальном пространстве
`DisplayGeometry.WIDTH × HEIGHT` (`1286 × 745`). Раньше `DisplayZonesView`
проецировал его на View **неравномерным** масштабом (`width / WIDTH` по X и
`height / HEIGHT` по Y), поэтому артворк слегка растягивался уже на XCover 4s и
по-разному искажался на других соотношениях сторон; обработки выреза камеры не
было. TASK-033 заменил это на **единый равномерный letterbox-фит внутри
cutout-safe области**.

Математика вынесена в чистый помощник
`com.fieldscanner.app.display.DisplayViewport` (тестируется без View):
`DisplayViewport.of(viewW, viewH, insetLeft, insetTop, insetRight, insetBottom)`
считает безопасный бокс (View минус инсеты), масштаб
`scale = min(safeW / WIDTH, safeH / HEIGHT)` и центрирующие сдвиги
`offsetX/offsetY`, возвращая `DisplayTransform(scale, offsetX, offsetY)` с
методами `toVirtualX/Y`, `toScreenX/Y` и `contains(px, py)` (точка на артворке, а
не в чёрной кайме). Один и тот же `DisplayTransform` используется и в `onDraw`
(`canvas.translate(offsetX, offsetY); canvas.scale(scale, scale)`), и в
`onTouchEvent` — второй копии арифметики нет.

`DisplayZonesView` кэширует `transform` и пересчитывает его в `onSizeChanged`,
`onApplyWindowInsets` и `onAttachedToWindow` через `recomputeTransform()`, которая
на API 28+ берёт safe-инсеты `rootWindowInsets.displayCutout` (иначе нулевые).
Тема `values-v27/themes.xml` выставляет
`android:windowLayoutInDisplayCutoutMode = shortEdges`, разрешая окну заходить под
вырез, тогда как артворк остаётся внутри safe-области. Касание в чёрной
letterbox-кайме (`!transform.contains(...)`) гасится и не запускает ни руническую,
ни температурную жесты — виртуальные координаты за пределами `0..WIDTH` /
`0..HEIGHT` не выдаются. Тексты на канве остаются в виртуальных единицах и
масштабируются вместе с артворком (для дизайна с фиксированным артворком это
корректно; в `sp` не переводятся). На устройстве с соотношением, равным
`1286:745`, поведение не меняется (кроме субпиксельного округления). Покрытие —
`DisplayViewportTest`.

## Артефакты вне кода

Documentation gate (`scripts/check-documentation`) закрепляет за этой страницей не только код экрана и рун, но и ресурсы: разметку `res/layout/activity_main.xml`, растровую графику рун и панелей в `res/drawable-nodpi/`, контуры результата анализа в `assets/scan_outlines/` и иконки контактов `assets/call_contacts_family*`. Изменение любого из них требует правки этой страницы в том же коммите. Корневые `MainScreenView.kt`, `ScannerScreenCoordinator.kt`, `RuneActionRouter.kt`, `RuneHintPager.kt`, `SignalAnalysis.kt`, `Display*.kt` и пакет `display/` тоже сопоставлены сюда.

`MainActivity` получает единый `DiagnosticsLog` из `AppContainer` и передаёт его `ScannerScreenCoordinator`, а также контроллерам камеры и звонков; это не добавляет на экран диагностический текст. Бывшие текстовые ошибки (`showPhoneError`, `showCameraUnavailable`, сбой съёмки) теперь пишут причину в этот лог и вызывают `DisplayZonesView.playErrorSignal()`.
