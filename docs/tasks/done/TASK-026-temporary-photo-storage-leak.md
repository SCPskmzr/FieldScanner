# TASK-026 — Утечка временных фото в хранилище устройства

Независима от TASK-025 и TASK-027…029, но правится в
`ScannerScreenCoordinator.kt` и `ScannerViewModel.kt`, которые трогают также
TASK-028 и TASK-029 — держать в отдельном worktree и мёрджить раньше них.

## Goal

После успешного анализа фото на устройстве не остаётся полноразмерного дубля
снимка, а каталог временных снимков не растёт неограниченно между запусками.

## Context

`CameraController.takePhoto()` пишет полноразмерный `SCAN_<yyyyMMdd_HHmmss>.jpg`
в `getExternalFilesDir(Environment.DIRECTORY_PICTURES)` (фолбэк — `filesDir`)
и отдаёт файл наружу
([CameraController.kt:49-64](../../app/src/main/java/com/fieldscanner/app/camera/CameraController.kt)).
Владелец файла дальше — `ScannerScreenCoordinator.capturedPhotoFile`
([ScannerScreenCoordinator.kt:40](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt)).

Судьба временного файла по веткам:

- **Отмена** — `discardPhotoAndReturnToInactiveCamera()` вызывает
  `capturedPhotoFile?.delete()`
  ([ScannerScreenCoordinator.kt:138](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt)). Корректно.
- **Сохранение без анализа** — `ScannerViewModel.savePhotoWithoutAnalysis()`
  делает `photo.delete()` после записи
  ([ScannerViewModel.kt:145-151](../../app/src/main/java/com/fieldscanner/app/ScannerViewModel.kt)). Корректно.
- **Успешный анализ — утечка.** `analyze()` копирует снимок в
  `filesDir/findings/<id>.jpg` через `FindingMediaStorage.savePhoto`
  ([FindingMediaStorage.kt:10](../../app/src/main/java/com/fieldscanner/app/data/finding/FindingMediaStorage.kt)),
  но исходный `SCAN_*.jpg` **не удаляется никогда**, и `capturedPhotoFile` не
  обнуляется: последующий `showCameraMode()` его не трогает
  ([ScannerScreenCoordinator.kt:117-121](../../app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt)).

Подтверждение: `grep -rn "\.delete()" app/src/main --include=*.kt` даёт 11
попаданий, ни одно из них не находится на пути успешного анализа; `listFiles`
в `app/src/main` не встречается ни разу, то есть каталог временных снимков не
инспектируется и не чистится ни при старте, ни где-либо ещё.

Почему это значимо именно здесь: целевой XCover 4s ограничен по памяти и
хранилищу (`app/AGENTS.md`, `ARCHITECTURE.md` → «Constraints»), а приложение по
продуктовому правилу не показывает текста, то есть предупредить о нехватке
места оно не может в принципе. Каждый удачный скан оставляет второй
полноразмерный JPEG навсегда.

Дополнительный класс утечки: файл создаётся до того, как пользователь принял
решение. Если процесс убит на экране просмотра снимка, `capturedPhotoFile`
теряется вместе с процессом, а файл остаётся — уборщика нет.

## Requirements

- Удалять временный снимок после того, как находка успешно сохранена
  (снимок скопирован в `filesDir/findings/`). Удаление не должно происходить
  раньше успешного копирования: при ошибке сохранения пользователь остаётся на
  просмотре снимка и должен иметь возможность повторить.
- Обнулять `capturedPhotoFile` во всех терминальных ветках, а не только в
  отмене и сохранении без анализа.
- Добавить уборку осиротевших временных снимков: при старте приложения удалить
  из каталога временных снимков файлы, не относящиеся к текущей сессии.
  Правило уборки (по имени/возрасту) вынести в чистую функцию, покрываемую
  unit-тестом без Android.
- Уборка выполняется вне главного потока и за портом/классом уровня данных, а
  не в `Activity`/`View` (стандарт слоёв TASK-012).
- Не удалять содержимое `filesDir/findings/` — это долговременные данные
  находок, ими владеет `FindingMediaStorage`.
- Обновить `docs/modules/scanner.md` (жизненный цикл временного снимка) и, если
  затронуто, `docs/modules/findings-and-journal.md`.
- Записать в `docs/agent-memory/gotchas.md` новым блоком в конце файла
  (правило append-only) факт о двух разных каталогах — временные снимки в
  `getExternalFilesDir(PICTURES)` против долговременных в
  `filesDir/findings/` — и о том, кто за какой отвечает.

## Acceptance criteria

- [x] После успешного анализа временный `SCAN_*.jpg` отсутствует, а
  `filesDir/findings/<id>.jpg` на месте.
- [x] При неуспешном сохранении находки временный снимок сохраняется и
  повторная попытка анализа возможна.
- [x] Ветки отмены и «сохранить без анализа» не изменили поведения.
- [x] Правило уборки осиротевших файлов покрыто unit-тестом (в том числе:
  файлы находок не подпадают под уборку).
- [x] Уборка не выполняется на главном потоке.
- [x] `./scripts/verify.ps1` проходит.
- [x] `docs/modules/scanner.md` и `docs/agent-memory/gotchas.md` обновлены.

## Out of scope

- Ограничение общего числа находок или квота на `filesDir/findings/`.
- Экспорт, синхронизация или выгрузка находок.
- Сжатие/переформатирование долговременных снимков.
- Восстановление состояния экрана после гибели процесса (это TASK-028).
- Изменение места, куда CameraX пишет снимок, без необходимости.

## Relevant areas

- `app/src/main/java/com/fieldscanner/app/camera/CameraController.kt`
- `app/src/main/java/com/fieldscanner/app/ScannerScreenCoordinator.kt`
- `app/src/main/java/com/fieldscanner/app/ScannerViewModel.kt`
- `app/src/main/java/com/fieldscanner/app/data/finding/FindingMediaStorage.kt`
  (границы ответственности; менять по необходимости)
- `app/src/main/java/com/fieldscanner/app/AppContainer.kt` (сборка уборщика)
- `app/src/test/java/com/fieldscanner/app/` (тест правила уборки)
- `docs/modules/scanner.md`, `docs/agent-memory/gotchas.md`

## Verification

- [x] Task gate: `./scripts/verify.ps1` (Windows) или `./scripts/verify`.
- [ ] Integration gate (владелец репозитория, на смёрдженном результате):
  `./scripts/verify`, затем `./scripts/verify-and-install.ps1`.
- [ ] На устройстве: снять три фото с успешным анализом, затем
  `adb shell ls /sdcard/Android/data/com.fieldscanner.app/files/Pictures`
  — каталог пуст; `adb shell run-as com.fieldscanner.app ls files/findings`
  содержит три `.jpg`.
- [ ] На устройстве: снять фото, убить процесс на экране просмотра, запустить
  приложение заново → осиротевший `SCAN_*.jpg` убран.
- [x] Acceptance criteria checked.

## Outcome

Fill in when closing the task, then `git mv` this file to `docs/tasks/done/`:

- Added background startup cleanup for stale `SCAN_*.jpg` files, routed temporary-photo directory lookup through the data component, and deleted the source photo after `FindingStore.saveFinding` succeeds. The coordinator now clears its source-photo reference when showing the result. Added cleanup-rule and ViewModel success/failure tests, matching module docs, documentation coverage, and append-only storage ownership memory. Closing commit SHA: 9308e509ca3127f3d29bd05db6546b4e4e39afab.
- Not verified: the two manual XCover 4s/ADB checks and the owner-only integration gate (`./scripts/verify`, then install/launch) remain pending on the merged result.
