# TASK-012 — Единообразие слоёв: порты, изоляция БД, сеть вне View

## Goal

Привести весь код к тому же разделению слоёв, что уже действует в ядре
сканера: доменный/«бэкенд» слой скрыт за интерфейсами-портами, Room не виден
за пределами своего пакета, а сетевой и I/O-код не живёт внутри `View`.
Отдельным пунктом — зафиксировать эти правила в инструкциях, чтобы будущие
задачи их не нарушали.

## Context

Аудит структуры (2026-08-29) показал: ядро сканера/журнала/персистентности
разделено правильно — порты [`FindingStore`](../../app/src/main/java/com/fieldscanner/app/data/finding/FindingStore.kt),
[`ImageAnalyzer`](../../app/src/main/java/com/fieldscanner/app/data/analysis/ImageAnalyzer.kt),
[`SpeechSynthesizer`](../../app/src/main/java/com/fieldscanner/app/data/speech/SpeechSynthesizer.kt);
Room спрятан внутри `data/finding` (`FindingEntity` / `FindingDao` /
`FindingsDatabase` больше нигде не импортируются); [`ScannerViewModel`](../../app/src/main/java/com/fieldscanner/app/ScannerViewModel.kt)
не содержит типов `android.view.*`.

Но паттерн выдержан не везде:

1. **`DeviceLocationProvider` лежит не в том пакете.** Он в `data/finding`,
   но с TASK-009 его использует ещё и слой `display/`
   ([`OutdoorTemperatureSource`](../../app/src/main/java/com/fieldscanner/app/display/OutdoorTemperatureSource.kt)
   импортирует `com.fieldscanner.app.data.finding.DeviceLocationProvider`).
   Класс дисплея зависит от пакета персистентности находок. Получение
   геолокации — общая платформенная возможность.

2. **Левый дисплей делает сеть и I/O геолокации внутри `View`.**
   [`DisplayZonesView`](../../app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt)
   сам конструирует и держит `OutdoorTemperatureSource` и
   `DeviceIndicatorSource`. `OutdoorTemperatureSource` поднимает свой
   `ExecutorService`, ходит по HTTP через `HttpURLConnection` и на каждый
   fetch создаёт `DeviceLocationProvider`. Интерфейс-шва нет: `object
   OpenMeteoTemperatureClient` не подменяется в тестах, `LeftDisplayViewModel`
   отсутствует, состояние собирается мутацией поля в двух колбэках внутри
   `View`. Это прямое расхождение с архитектурой пути сканера.

3. **Три самописных HTTP-клиента** дублируют настройку соединения, таймауты,
   проверку кода ответа и чтение потока:
   [`OpenAiImageAnalyzer`](../../app/src/main/java/com/fieldscanner/app/data/analysis/remote/OpenAiImageAnalyzer.kt),
   [`OpenAiTtsProxy`](../../app/src/main/java/com/fieldscanner/app/data/speech/remote/OpenAiTtsProxy.kt),
   `OpenMeteoTemperatureClient`. Общего `internal`-хелпера нет; сетевой код
   размазан по трём файлам в двух пакетах.

Ограничения проекта сохраняются: Kotlin + Views/XML, `minSdk` 24, без новых
внешних зависимостей (в т.ч. без OkHttp/Retrofit), низкая сложность.

## Requirements

### 1. Переместить получение геолокации в общий пакет

- Перенести `DeviceLocationProvider` и `FindingLocation` из
  `com.fieldscanner.app.data.finding` в отдельный пакет
  (`com.fieldscanner.app.data.location` или `com.fieldscanner.app.platform` —
  выбрать один и обосновать в задаче).
- Обновить импорты в `FindingRepository` и `OutdoorTemperatureSource`.
- Поведение и сигнатуры не менять; существующие тесты не ослаблять.

### 2. Ввести порт для «бэкенда» левого дисплея и вынести его из View

- Объявить интерфейс(ы) для источников данных левого дисплея (например
  `OutdoorTemperatureProvider` и `DeviceIndicatorProvider`, либо один
  агрегирующий `LeftDisplayInputs`) в слое `display/`.
- Конкретные реализации (Open-Meteo, sticky-broadcast батареи) должны быть за
  этим портом; `OpenMeteoTemperatureClient` — тоже за подменяемым seam-ом, не
  голым `object`.
- `DisplayZonesView` больше не конструирует источники напрямую — они
  передаются снаружи (через `AppContainer` и место сборки View/экрана).
- Сборку графа для левого дисплея добавить в
  [`AppContainer`](../../app/src/main/java/com/fieldscanner/app/AppContainer.kt),
  по образцу `findings` / `analyzer` / `speech`.
- Приоритетная логика уже чистая
  ([`LeftDisplayState`](../../app/src/main/java/com/fieldscanner/app/display/zones/LeftDisplayState.kt))
  — сохранить её и, если появляется сборка состояния из нескольких сигналов,
  вынести её в такой же чистый компонент, а не оставлять в `View`.
- Управление жизненным циклом (start/stop на attach/detach) сохранить.

### 3. Общий internal HTTP-хелпер

- Добавить один `internal` помощник (например
  `com.fieldscanner.app.data.net.HttpClient` или `HttpRequest`) поверх
  `HttpURLConnection`: метод, заголовки, таймауты, запись тела, проверка
  `responseCode in 200..299`, чтение `inputStream`/`errorStream`, гарантия
  `disconnect()`.
- Перевести на него `OpenAiImageAnalyzer`, `OpenAiTtsProxy` и клиент
  Open-Meteo. Специфика запроса (JSON тела, парсинг ответа, поддержка
  `cancel()` у анализатора) остаётся в вызывающих классах.
- Никаких новых зависимостей.

### 4. Зафиксировать стандарты в инструкциях

- В `AGENTS.md`, раздел **Development rules**, добавить явные правила слоёв:
  - доступ к персистентности и внешним сервисам — только через
    интерфейс-порт; конкретные реализации живут в подпакете `remote/` (или
    аналогичном) и не импортируются UI/ViewModel напрямую;
  - типы Room (`*Entity`, `*Dao`, `*Database`) не покидают свой пакет; наружу
    отдаётся доменная модель через мапперы;
  - `View`/`Activity` не выполняют сетевой и файловый I/O и не владеют
    фоновыми исполнителями — это делает слой за портом, собранный в
    `AppContainer`;
  - graph wiring — в `AppContainer`; `View` и координаторы получают
    зависимости снаружи;
  - новый сетевой код использует общий `internal` HTTP-хелпер, а не свой
    `HttpURLConnection`.
- В `docs/agent-memory/decisions.md` добавить пункт «Layering standard» с
  ссылками на порты-образцы и на этот task.
- В `docs/agent-memory/architecture.md` обновить запись про левый дисплей так,
  чтобы она отражала порт и сборку в `AppContainer` (а не прямое владение
  источниками во `View`).
- Обновить затронутые `docs/modules/` (`ui-and-input.md`, `ai-services.md`,
  `findings-and-journal.md` — по факту диффа).

## Acceptance criteria

- [x] `DeviceLocationProvider` / `FindingLocation` вне пакета `data.finding`
  (перенесены в `com.fieldscanner.app.data.location`);
  `grep -rn "data.finding.DeviceLocationProvider" app/src` пусто.
- [x] `grep -rn "androidx.room" app/src/main` — только файлы в
  `app/src/main/java/com/fieldscanner/app/data/finding/`.
- [x] `grep -rn "FindingEntity\|FindingDao\|FindingsDatabase" app/src/main`
  вне `data/finding` пусто.
- [x] `DisplayZonesView` не импортирует `java.net.*`, не создаёт
  `Executors`/`ExecutorService` и не конструирует `OutdoorTemperatureSource` /
  `DeviceIndicatorSource` напрямую; источники приходят через `internal fun
  setLeftDisplayInputs(...)` из `AppContainer.leftDisplay`.
- [x] `OpenMeteoTemperatureClient` доступен через подменяемый интерфейс
  `TemperatureClient`; в тестах левого дисплея источник температуры мокается
  без сети (`LeftDisplayInputsTest` с fake-провайдерами).
- [x] `OpenAiImageAnalyzer`, `OpenAiTtsProxy` и клиент Open-Meteo используют
  общий `internal` HTTP-хелпер `data/net/HttpClient`; в них не остаётся прямых
  `URL(...).openConnection()`.
- [x] `cancel()` у `OpenAiImageAnalyzer` продолжает разрывать активный запрос
  (`HttpClient.execute(onConnect = { activeConnection = it })`, затем
  `activeConnection?.disconnect()`).
- [x] Новых внешних зависимостей нет (`git diff app/build.gradle` пуст в части
  `dependencies`).
- [x] `AGENTS.md` содержит раздел с правилами слоёв; `decisions.md` и
  `architecture.md` обновлены; затронутые `docs/modules/` изменены.
- [x] Существующие unit-тесты не ослаблены; добавлены тесты на новый
  HTTP-хелпер (`HttpClientTest`) и на подменяемый источник температуры
  (`LeftDisplayInputsTest`).

## Out of scope

- Введение DI-фреймворка (Hilt/Koin/Dagger) — `AppContainer` остаётся ручным.
- Замена `HttpURLConnection` на OkHttp/Retrofit или любую сетевую библиотеку.
- Перевод на Compose, изменение `minSdk`, апгрейды Kotlin/Gradle/AGP.
- Функциональные изменения сканера, журнала, телефонии и левого дисплея —
  задача чисто структурная, поведение сохраняется.
- Рефакторинг `calls/` (там singleton `CallCoordinator` вынужден системным
  `InCallService` и уже задокументирован).
- `filterJournal()` и место `AnalysisResult` — отмечены в аудите как
  косметика, не входят в этот task.

## Relevant areas

- `app/src/main/java/com/fieldscanner/app/AppContainer.kt`
- `app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt`
- `app/src/main/java/com/fieldscanner/app/display/OutdoorTemperatureSource.kt`,
  `display/DeviceIndicatorSource.kt`, `display/zones/LeftDisplayState.kt`
- `app/src/main/java/com/fieldscanner/app/data/finding/DeviceLocationProvider.kt`,
  `data/finding/FindingRepository.kt`
- `app/src/main/java/com/fieldscanner/app/data/analysis/remote/OpenAiImageAnalyzer.kt`
- `app/src/main/java/com/fieldscanner/app/data/speech/remote/OpenAiTtsProxy.kt`
- `app/src/test/java/com/fieldscanner/app/display/` и `.../data/`
- `AGENTS.md`, `docs/agent-memory/decisions.md`,
  `docs/agent-memory/architecture.md`, `docs/modules/`

## Verification

- [x] `./scripts/verify-and-install.ps1` на Windows после полного изменения —
  `app/build/verify-and-install-status.txt` = `state=PASS` (lint + 16 unit-suites
  без падений, streamed install + запуск `MainActivity` на USB-устройстве).
- [x] Все `grep`-инварианты из Acceptance criteria проверены (см. ниже).
- [ ] На устройстве: левый дисплей по-прежнему показывает заряд, ход анализа и
  температурное предупреждение; наружная температура обновляется. — приложение
  установлено и запущено скриптом, но живой прогон левого дисплея (заряд →
  анализ → предупреждение → обновление температуры) визуально не подтверждён.
- [ ] Сканирование фото → анализ → озвучка → сохранение в журнал работает
  сквозняком (HTTP-хелпер не сломал запросы к OpenAI). — не прогонялось вручную
  (нужен валидный ключ и сеть); JVM-тесты API не подтверждают.
- [x] Acceptance criteria checked.

## Outcome

Closing commit: `c44bea0` — "refactor: enforce layering standard across ports,
DB, and network (TASK-012)".

### Что изменилось

1. **Геолокация — общий пакет.** `git mv` `DeviceLocationProvider` /
   `FindingLocation` из `data/finding` в новый
   `com.fieldscanner.app.data.location` (остаётся в слое `data`, рядом с
   `data.analysis` / `data.speech`; `platform` не заводили). Импорты обновлены в
   `FindingRepository` и `OutdoorTemperatureSource`; поведение и сигнатуры
   класса не тронуты.
2. **Порт левого дисплея + сборка в `AppContainer`.** Новый
   `display/LeftDisplayInputs.kt`: интерфейсы `OutdoorTemperatureProvider` и
   `DeviceIndicatorProvider` + чистый агрегатор `LeftDisplayInputs` (сливает
   заряд и температуру в один `DeviceIndicators`, проксирует start/stop/refresh;
   без Android/IO-типов — покрыт JVM-тестом). `OutdoorTemperatureSource` и
   `DeviceIndicatorSource` реализуют порты; вызов Open-Meteo скрыт за
   `TemperatureClient` (не голый `object`). `DisplayZonesView` больше не
   конструирует источники — `internal fun setLeftDisplayInputs(...)`,
   инъекция из `MainActivity.buildScreen()` через `AppContainer.leftDisplay`.
   Жизненный цикл attach/detach сохранён.
3. **Общий `internal` HTTP-хелпер** `com.fieldscanner.app.data.net.HttpClient`
   поверх `HttpURLConnection` (метод, заголовки, таймауты, тело, проверка
   2xx → `HttpException`, чтение `inputStream`/`errorStream`, гарантированный
   `disconnect()`, `onConnect` для отмены). На него переведены
   `OpenAiImageAnalyzer`, `OpenAiTtsProxy`, `OpenMeteoTemperatureClient`;
   прямых `openConnection()` в них не осталось. Зависимостей не добавлено.
   `cancel()` анализатора сохранён через `onConnect`.
4. **Стандарты слоёв зафиксированы:** `AGENTS.md` → блок «Layering standard»;
   `docs/agent-memory/decisions.md` и `architecture.md` обновлены; модульные
   доки `ui-and-input.md`, `ai-services.md`, `findings-and-journal.md`,
   `scanner.md` обновлены.

### Тесты

Добавлены `HttpClientTest` (хелпер против самодельного localhost
`ServerSocket` — `com.sun.net.httpserver` недоступен на classpath
Android-юнит-тестов) и `LeftDisplayInputsTest` (fake-провайдеры, слияние +
жизненный цикл). Существующие `OpenMeteoTemperatureClientTest`,
`LeftDisplayStateTest` не менялись. `./scripts/verify-and-install.ps1` → PASS.

### Grep-инварианты (свидетельства)

- `grep -rn "data.finding.DeviceLocationProvider" app/src` → пусто.
- `grep -rn "androidx.room" app/src/main` → только `data/finding/`.
- `grep -rn "FindingEntity\|FindingDao\|FindingsDatabase" app/src/main` вне
  `data/finding` → пусто.
- `DisplayZonesView.kt`: нет `java.net` / `Executors` / `OutdoorTemperatureSource(` /
  `DeviceIndicatorSource(`.
- `openConnection()` в `data/analysis`, `data/speech`,
  `display/OutdoorTemperatureSource.kt` → пусто.
- `git diff app/build.gradle` → 0 строк.

### Не подтверждено

- Живой визуальный прогон левого дисплея на устройстве (заряд → анализ →
  температурное предупреждение → обновление наружной температуры).
- Сквозной прогон OpenAI (анализ + TTS) с реальным ключом — структурно
  эквивалентен прежнему коду, но вручную не выполнялся.
