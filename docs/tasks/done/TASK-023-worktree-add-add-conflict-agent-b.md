# TASK-023 — Проверка add/add-конфликта worktree: вариант агента B

## Goal

Создать вторую из двух параллельных тестовых веток для ручной проверки
worktree-пайплайна из TASK-019. При слиянии после TASK-022 Git должен
потребовать разрешить конфликт `add/add`.

## Context

TASK-022 создаётся от той же точки `master` и намеренно добавляет тот же
отсутствующий в базовой ветке файл, но с другим содержимым. После слияния
TASK-022 этот файл уже существует в `master`, поэтому изменение данной ветки
не может быть применено автоматически.

## Requirements

- Работать только в отдельном worktree на ветке `task-023`, созданном через
  `./scripts/new-worktree.ps1 TASK-023`.
- Создать `docs/worktree-merge-playground.md` с точным содержимым:

  ```md
  # Worktree merge playground

  Winner: agent B
  ```
- Не менять продуктовый код, Gradle-конфигурацию, модули документации или
  файлы другой тестовой задачи.
- Закрыть задачу по обычному жизненному циклу после успешной проверки.

## Acceptance criteria

- [x] В ветке `task-023` добавлен только вариант файла с `Winner: agent B`.
- [x] Изменение закоммичено отдельным коммитом с `TASK-023` в теме.
- [x] Acceptance criteria checked.

## Out of scope

- Разрешение merge-конфликта: это выполняет интегратор после слияния TASK-022.
- Установка APK или проверка на устройстве.

## Relevant areas

- `docs/worktree-merge-playground.md` (новый временный тестовый файл)
- `scripts/new-worktree.ps1`, `scripts/verify.ps1`

## Verification

- [x] Task gate: `./scripts/verify.ps1` в worktree `task-023`.
- [ ] Integration gate: после слияния ветки в `master` интегратор запускает
  `./scripts/verify`.
- [ ] Additional scenario-specific check: после предварительного слияния
  TASK-022 команда слияния TASK-023 останавливается на конфликте `add/add` в
  `docs/worktree-merge-playground.md`.
- [x] Acceptance criteria checked.

## Outcome

- **Что изменилось:** добавлен новый временный тестовый файл
  `docs/worktree-merge-playground.md` с содержимым `# Worktree merge playground`
  / пустая строка / `Winner: agent B` (CRLF, как остальное рабочее дерево на
  Windows). Продуктовый код, Gradle-конфигурация и модули `docs/modules/` не
  затронуты. Файл задачи перемещён в `docs/tasks/done/` тем же изменением.
- **Ветка/worktree:** `task-023` от `master`; worktree `C:\fs-wt\task-023`,
  создан `./scripts/new-worktree.ps1 TASK-023`.
- **Closing commit SHA:** `01a5abc5a99d47cc93dbb2bef5c80e666df9b9f7`
  (`docs: add worktree add/add-conflict agent-B variant (TASK-023)`); эта строка
  дописана follow-up коммитом, как для TASK-018 и TASK-019.
- **Task gate:** `./scripts/verify.ps1` в worktree — PASS
  (`BUILD SUCCESSFUL in 11m 35s`, exit 0). Documentation coverage PASS +
  self-check PASS; `:app:lintDebug`, `:app:testDebugUnitTest`,
  `:app:assembleDebug` — все прошли. Только deprecation-варнинги в
  существующем коде, не ошибки.
- **Не проверено здесь (по замыслу задачи — выполняет интегратор):**
  - Integration gate: `./scripts/verify` на результате слияния `task-023` в
    `master`.
  - Сценарный чек `add/add`: после предварительного слияния TASK-022 (тот же
    файл с `Winner: agent A`) слияние `task-023` должно остановиться на
    конфликте `add/add` в `docs/worktree-merge-playground.md`.
