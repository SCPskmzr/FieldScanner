# TASK-022 — Проверка add/add-конфликта worktree: вариант агента A

## Goal

Создать первую из двух параллельных тестовых веток для ручной проверки
worktree-пайплайна из TASK-019. После слияния второй тестовой ветки Git должен
потребовать разрешить конфликт `add/add`.

## Context

TASK-023 создаётся от той же точки `master` и намеренно добавляет тот же
отсутствующий в базовой ветке файл, но с другим содержимым. Первая ветка
сольётся чисто; при слиянии второй ветки конфликт неизбежен.

## Requirements

- Работать только в отдельном worktree на ветке `task-022`, созданном через
  `./scripts/new-worktree.ps1 TASK-022`.
- Создать `docs/worktree-merge-playground.md` с точным содержимым:

  ```md
  # Worktree merge playground

  Winner: agent A
  ```
- Не менять продуктовый код, Gradle-конфигурацию, модули документации или
  файлы другой тестовой задачи.
- Закрыть задачу по обычному жизненному циклу после успешной проверки.

## Acceptance criteria

- [x] В ветке `task-022` добавлен только вариант файла с `Winner: agent A`.
- [x] Изменение закоммичено отдельным коммитом с `TASK-022` в теме.
- [x] Acceptance criteria checked.

## Out of scope

- Разрешение merge-конфликта: это выполняет интегратор при слиянии TASK-023.
- Установка APK или проверка на устройстве.

## Relevant areas

- `docs/worktree-merge-playground.md` (новый временный тестовый файл)
- `scripts/new-worktree.ps1`, `scripts/verify.ps1`

## Verification

- [x] Task gate: `./scripts/verify.ps1` в worktree `task-022`.
- [ ] Integration gate: после слияния ветки в `master` интегратор запускает
  `./scripts/verify`.
- [x] Additional scenario-specific check: ветка `task-022` сливается в
  `master` без конфликта, если `docs/worktree-merge-playground.md` ещё
  отсутствует в `master`.
- [x] Acceptance criteria checked.

## Outcome

- Added `docs/worktree-merge-playground.md` with the agent-A fixture. Commit:
  `f3bacbd` (`docs: add agent A merge fixture (TASK-022)`).
- `./scripts/verify.ps1` passed in `C:\fs-wt\task-022`: documentation gates,
  lint, unit tests, and debug APK assembly completed. The initial Gradle-wrapper
  download stalled, so the existing local Gradle 8.0 distribution was seeded
  into this worktree's isolated cache before the successful run.
- `git merge-tree --write-tree master f3bacbd` succeeded while the fixture is
  absent from `master`, confirming the first merge is clean.
- Still not verified: the integration gate and the deliberate add/add conflict
  when TASK-023 is merged; both are explicitly assigned to the integrator.
