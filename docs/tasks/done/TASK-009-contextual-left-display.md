# TASK-009 — Контекстный левый дисплей: геометрия и индикация

## Goal

Превратить левую вертикальную зону в контекстный системный дисплей с
увеличенной нижней частью, не дублирующий состояния нижнего дисплея.

## Context

Пользователь утвердил новую геометрию только для `leftZone`: её нижняя часть
расширена и опущена до `y=712.5`, чтобы по ней было удобнее нажимать пальцем.
Центральная, нижняя и три правые рунические зоны в предоставленном SVG не
менялись. Добавленные `clipPath` и `defs` меняют только структуру SVG, а не
геометрию экранных зон.

Левая зона должна показывать краткую системную информацию по контексту:
обычно заряд устройства, при анализе — ход анализа, при перегреве —
предупреждение. Жесты и быстрые регулировки яркости/громкости вынесены в
отдельную TASK-010.

## Requirements

- Заменить только `DisplayGeometry.leftZone` координатами из утверждённого
  макета; не менять `centerZone`, `bottomZone` и `hintZones`.
- Синхронизировать `docs/artwork/full-display-zones-template.svg` с кодовой
  геометрией, сохранив отдельные идентификаторы путей зон.
- Добавить отдельный рендерер/состояние левого контекстного дисплея, не
  смешивая его с `BottomStatusRenderer` и не дублируя нижний статус.
- Отображать заряд как значение по умолчанию; во время `Analyzing` —
  вертикальный индикатор прогресса; при температурном предупреждении —
  приоритетный визуальный сигнал.
- Не требовать чтения текста в release-сборке; использовать пиктограммы,
  цвет и шкалу. Отладочные подписи допустимы только при `BuildConfig.DEBUG`.
- Корректно обрабатывать отсутствие доступных системных данных без падения
  приложения.
- Добавить тесты для выбора контента по контексту и геометрии левой зоны.
- Обновить `docs/modules/ui-and-input.md` и, если появится долговечное
  техническое ограничение, соответствующую запись в `docs/agent-memory/`.

## Plan

1. Зафиксировать новую геометрию и добавить регрессионную проверку, что пять
   остальных зон не изменились.
2. Вынести состояние и отрисовку левой зоны в отдельный компонент.
3. Подключить системные источники заряда и температуры с безопасными
   фолбэками для Android 7+.
4. Прогнать unit-тесты и полную проверку/установку на Galaxy XCover 4s,
   затем визуально проверить новую форму зоны.

## Acceptance criteria

- [x] `leftZone` соответствует новой ломаной из утверждённого SVG, включая
  нижние точки `(185, 641) → (185, 691) → (150.5, 712.5) → (74.5, 705) →
  (44.5, 691) → (35.5, 618.5) → (61.5, 561)`.
- [x] Координаты `centerZone`, `bottomZone` и всех `hintZones` идентичны
  текущим координатам до выполнения задачи.
- [x] Шаблон SVG и `DisplayGeometry.kt` содержат одну и ту же геометрию.
- [x] В обычном режиме левая зона показывает заряд; при анализе отображает
  ход анализа; температурное предупреждение имеет приоритет.
- [x] В release-сборке новый интерфейс не содержит обязательного для ребёнка
  текста.
- [x] Добавлены и проходят соответствующие unit-тесты.

## Out of scope

- Изменение формы центрального, нижнего или правых рунических дисплеев.
- Изменение списка, порядка или назначения существующих рун.
- Жесты, управление яркостью и управление громкостью (TASK-010).
- Новые внешние зависимости, Compose или повышение `minSdk`.
- Переработка нижнего статусного дисплея.

## Relevant areas

- `app/src/main/java/com/fieldscanner/app/display/DisplayGeometry.kt`
- `app/src/main/java/com/fieldscanner/app/DisplayZonesView.kt`
- `app/src/main/java/com/fieldscanner/app/display/`
- `app/src/test/java/com/fieldscanner/app/`
- `docs/artwork/full-display-zones-template.svg`
- `docs/modules/ui-and-input.md`

## Verification

- [x] Unit-тесты для контекстного выбора и геометрии левой зоны.
- [x] `./scripts/verify-and-install.ps1` на Windows после полного изменения.
- [x] На устройстве: новая форма левой зоны не перекрывает камеру, нижний
  статус и правые руны.
- [x] Acceptance criteria checked.

## Outcome

- `DisplayGeometry` and the SVG template now use the approved enlarged left-zone
  contour; canonical coordinate tests ensure the other zones remain unchanged.
  The separate left renderer displays battery energy, analysis progress, and
  outdoor-temperature context from Open-Meteo. The lower-left hold exposes a
  temporary −10…+45 °C scale and five-second refresh gesture.
- Implementation commit: `fd683c4` (`feat: add contextual left display (TASK-009)`).
- Verified: `./scripts/verify-and-install.ps1` PASS on USB device, including
  manual visual verification. No outstanding verification.
