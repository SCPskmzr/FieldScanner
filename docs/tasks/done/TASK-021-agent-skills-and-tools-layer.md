# TASK-021 — Слой процедур и каталог скриптов для двух агентов (Claude Code + Codex)

Зависимость от TASK-019 снята: задача закрыта
(`docs/tasks/done/TASK-019-parallel-worktree-pipeline.md`), `master` как база,
`scripts/next-task-id.ps1` / `new-worktree.ps1` / `remove-worktree.ps1`,
правило append-only для `docs/agent-memory/*` и раздел «Parallel work» уже в
репозитории.

Переписана 2026-08-31: объём сокращён — генератор проекций, режим `-Check` и
новый шаг гейта вынесены из v1 (см. `## Out of scope`).

## Goal

Каждая повторяемая процедура описана в репозитории **ровно один раз**. Claude
Code получает её через свой родной механизм (слэш-команда или авто-скилл),
Codex — через указатель в `AGENTS.md`. Исполняемые точки входа собраны в одну
таблицу, и Claude не спрашивает разрешение на каждый их запуск.

Ключевое свойство решения: файлы в `.claude/**` содержат **только ссылку** на
канон, а не его копию. Расходиться с каноном там нечему, поэтому ни генератор,
ни гейт против расхождения не нужны.

## Context

Проверенное состояние на момент написания:

- **Инструкции уже вендор-нейтральны.** `AGENTS.md` — общий слой, `CLAUDE.md`
  импортирует его через `@AGENTS.md`, Codex читает `AGENTS.md` нативно;
  `docs/agent-memory/` — общая память с протоколами startup/write-back.
  Процедуры и роли этот приём пока не используют.
- **`AGENTS.md` — 211 строк, и грузится в каждую сессию целиком.** Заметная его
  часть — ритуалы, нужные лишь в момент выполнения: «Memory write-back
  protocol», операционная часть «Definition of done», «Gradle verification
  discipline». Вынос их в загружаемые по требованию процедуры сокращает
  постоянный контекст, а не только наводит порядок.
- **Роли есть, но не подключены и противоречат воркфлоу.**
  `docs/agents/PLANNER.md` требует «Create a neighboring `TASK-XXX-plan.md`»,
  тогда как `docs/AI_DEVELOPMENT_WORKFLOW.md` (шаг 1 «Standard task»)
  предписывает план как секцию `## Plan` в самом файле задачи. Все три файла
  ролей — 5–7 строк, явного контракта (входы, запреты, формат ответа) в них
  нет.
- **`docs/prompts/` пуст и в git не существует** (`git ls-files docs/prompts/`
  — пусто; git не хранит пустые каталоги). Это локальная заготовка, а не
  элемент репозитория.
- **`.claude/` не отслеживается**: `git ls-files .claude/` пусто. В
  `.gitignore` исключён **только** `.claude/settings.local.json`, значит
  `.claude/settings.json` можно закоммитить, не трогая `.gitignore`. Личный
  allow-лист сейчас разрознен (два варианта пути к `verify-and-install.ps1`,
  штучные `git`-команды) и второй машине/агенту недоступен.
- **Тулы нигде не собраны.** Точки входа — `scripts/verify`, `verify.ps1`,
  `verify-and-install.ps1`, `check-documentation(.ps1)`, `next-task-id.ps1`,
  `new-worktree.ps1`, `remove-worktree.ps1`. Единого списка, который агент
  видит до того, как полезет в `scripts/` руками, нет.
- **Гейт документации этой задачи не касается.**
  `scripts/check-documentation.ps1` ограничен `$auditPathspecs` =
  `app/src/main`, `*.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`,
  `gradle/wrapper`. Изменения в `docs/`, `scripts/` и `.claude/` под покрытие
  не попадают, поэтому обновление `docs/modules/*` здесь не требуется.

### Принятые решения по форме

- **Канон живёт в `docs/`, а не в `.claude/skills/`.** Иначе общий канон
  оказался бы в вендорном каталоге, да ещё расщеплённым надвое
  (`.claude/commands/` vs `.claude/skills/`), и Codex читал бы процедуры из
  каталога Claude.
- **Третьего дома для знания не заводим.** GUIDE создаётся только для
  процедуры, у которой сегодня нет единого дома. Если процедура уже целиком
  описана в `AGENTS.md` или `docs/AI_DEVELOPMENT_WORKFLOW.md`, стаб ссылается
  прямо на этот раздел, и GUIDE не создаётся.
- **Различие механизмов.** Осознанная процедура (запускают по команде) →
  `.claude/commands/<name>.md`, вызов `/<name>`, авто-триггера нет. Фоновая
  процедура (агент подхватывает сам) → `.claude/skills/<name>/SKILL.md`, где
  срабатывание определяется полем `description`.
- **Codex в v1 обслуживается только `AGENTS.md`.** Проектные `.codex/prompts/`
  не создаются: их поддержка установленным Codex не подтверждена, и без
  генератора выигрыш от них мал. Открытый вопрос уходит с критического пути.
- **Авто-скилл — не средство принуждения.** Срабатывание по `description`
  эвристично. Принуждение остаётся за гейтом: `check-documentation` валит
  верификацию, если покрытый модуль изменён без правки страницы
  `docs/modules/*`. Скилл влияет на качество исполнения, не на факт.

## Plan

- **Существующее состояние.** `AGENTS.md` — 211 строк, вся ритуальная проза
  памяти в нём. `.claude/` не в git (только игнорируемый `settings.local.json`).
  `docs/skills/` нет, `docs/agents/*` — 5–7 строк без контракта, PLANNER
  противоречит воркфлоу. `docs/prompts/` — пустой локальный каталог, не в git.
- **Подход.** Канон в `docs/skills/<name>/GUIDE.md` (+ `docs/agents/*`), в
  `.claude/**` только стабы-ссылки, без генератора. Дублирующую прозу из
  `AGENTS.md` перенести в GUIDE, добавить таблицу скриптов и блок указателей так,
  чтобы файл не стал длиннее.
- **Шаги.** (1) 4 GUIDE: `new-task`, `close-task` (command), `module-doc-sync`,
  `memory-writeback` (skill). (2) 4 `.claude/commands/` + 2 `.claude/skills/` —
  фронтматтер + одна строка-ссылка. (3) `AGENTS.md`: перенести «Memory write-back
  protocol»/«What belongs»/«Evidence requirement» в GUIDE, добавить «Agent
  procedures» и «Agent scripts». (4) `.claude/settings.json` — allow на скрипты
  таблицы; `settings.local.json` не трогать. (5) `docs/agents/*` — явный контракт
  (входы, запреты, процедуры, формат ответа), план как `## Plan`, верный гейт.
  (6) `docs/AI_DEVELOPMENT_WORKFLOW.md` — раздел «Procedures and roles». (7)
  `docs/agent-memory/workflows.md` — append-only блок. (8) удалить локальный
  `docs/prompts/`.
- **Проверка.** `./scripts/verify.ps1` в worktree `task-021`. Изменения вне
  `$auditPathspecs`, правка `docs/modules/*` не требуется. `wc -l AGENTS.md` ≤ 211.
- **Риски/допущения.** Перенос трёх секций памяти (не только «write-back
  protocol») — в духе «перенести процедурную прозу», бюджет длины это требует.
  Ручные проверки в чистой сессии Claude Code выполняет владелец.

## Requirements

### Канон процедур

- Создать `docs/skills/<name>/GUIDE.md` для четырёх процедур, у которых нет
  единого дома. Каждый GUIDE самодостаточен при чтении целиком, с
  YAML-фронтматтером: `name`, `description` (**только** условие применения, без
  перечисления скриптов), `kind: command | skill`, опционально `args`, `roles`.
  Скрипты называются в шагах прозой.
  - `new-task` (`command`): id из `scripts/next-task-id.ps1` (выдача не
    атомарна — подтвердить, что id всё ещё свободен); файл из
    `docs/tasks/TEMPLATE.md`; план — секция `## Plan`, не второй файл; критерии
    приёмки формулируются проверяемыми.
  - `close-task` (`command`): отметить только реально выполненные критерии;
    заполнить `## Outcome` (изменённые файлы/поведение, SHA закрывающего
    коммита, непроверенное); прогнать `memory-writeback`; `git mv` в
    `docs/tasks/done/`; task id в сабджекте коммита. Механику не пересказывать
    — ссылаться на `docs/AI_DEVELOPMENT_WORKFLOW.md`, раздел «Task lifecycle
    and status».
  - `module-doc-sync` (`skill`): по диффу определить страницу(ы)
    `docs/modules/*` из таблицы покрытия в `scripts/check-documentation.ps1` и
    переписать затронутые `##`-секции по смыслу; гейт проверяет лишь факт
    правки файла, поэтому формальная правка — нарушение, а не обход.
  - `memory-writeback` (`skill`): durable + verified? → нужный топик
    `docs/agent-memory/`; новая запись — отдельный блок в конец через `---`
    (append-only, TASK-019); `INDEX.md` — только под новый топик; обязателен
    evidence (пути, имена тестов, команда проверки); недоказанное помечать
    `unverified`.
- GUIDE **не** создаётся для двух процедур — стабы ссылаются на существующий
  дом:
  - `run-verification` → `AGENTS.md`, разделы «Definition of done» и «Gradle
    verification discipline».
  - `worktree-parallel` → `docs/AI_DEVELOPMENT_WORKFLOW.md`, раздел «Parallel
    work».
- Единственное допустимое дублирование во всей схеме — строка `description`
  (она нужна и в GUIDE, и в `SKILL.md`, потому что там это триггер). Держать её
  одним предложением.
- Удалить локальный пустой `docs/prompts/`. В git его нет, коммит-артефакта не
  будет; отдельный `README.md` там не заводить.

### Устранение дублей в `AGENTS.md`

- Процедурную прозу **перенести**, а не скопировать. Из `AGENTS.md` уходит
  операционная детализация «Memory write-back protocol»; остаётся короткое
  правило «write-back — часть завершения задачи» и указатель на
  `docs/skills/memory-writeback/GUIDE.md`.
- Разделы «Definition of done» и «Gradle verification discipline» остаются в
  `AGENTS.md` как есть: это правила, которые Codex обязан видеть без вызова
  команды, и на них ссылается стаб `run-verification`.
- Итог измерим: после задачи `AGENTS.md` не длиннее, чем до неё, при том что в
  него добавлены таблица скриптов и блок указателей.

### Проекции для Claude Code

- `.claude/commands/<name>.md` для `new-task`, `close-task`,
  `run-verification`, `worktree-parallel` — вызов `/<name>`; имя файла, имя
  каталога GUIDE и имя вызова совпадают, алиасов нет.
- `.claude/skills/module-doc-sync/SKILL.md` и
  `.claude/skills/memory-writeback/SKILL.md` — фронтматтер (`name`,
  `description`) плюс строка-указатель.
- Каждый такой файл содержит **только** фронтматтер и одну строку вида «Follow
  `docs/skills/<name>/GUIDE.md`» (или ссылку на раздел существующего
  документа). Никаких императивных шагов в теле: тело — это путь, а не
  процедура. Правило действует вместо снятого гейта расхождения.
- Файлы пишутся руками и коммитятся; генератор не заводится.

### Каталог скриптов и разрешения

- Раздел «Agent scripts» в `AGENTS.md`: таблица всех точек входа — имя Windows
  / POSIX, аргументы, назначение, и явно — какие запускать в worktree, а какие
  только в основном каталоге (`verify-and-install.ps1` — только владелец, на
  слитом результате).
- Коммитируемый `.claude/settings.json` с `permissions.allow` на скрипты из
  таблицы. Записи из личного `.claude/settings.local.json`, относящиеся к этим
  скриптам, переносятся в общий файл, а не дублируются; сам
  `settings.local.json` не изменяется и остаётся в `.gitignore`.

### Роли

- Расширить `docs/agents/PLANNER.md`, `IMPLEMENTER.md`, `VERIFIER.md` до явного
  контракта: что обязан прочитать, что делать нельзя, какие процедуры
  использует, точный формат ответа (`DONE` / `PARTIALLY VERIFIED` / `BLOCKED`;
  `VERDICT: PASS | FAIL`).
- Исправить противоречие: PLANNER пишет план секцией `## Plan` в файле задачи,
  а не соседним `TASK-XXX-plan.md`.
- Назвать в ролях правильный гейт: задачный — `scripts/verify.ps1` (в worktree
  — только он), интеграционный — ручной шаг владельца на слитом результате.
- Привязать процедуры к ролям: PLANNER → `new-task`; IMPLEMENTER →
  `run-verification`, `module-doc-sync`, `memory-writeback`, `close-task`,
  `worktree-parallel`; VERIFIER → `run-verification`.

### Документация и память

- `docs/AI_DEVELOPMENT_WORKFLOW.md`: назвать `docs/skills/` каноном процедур,
  `docs/agents/` — каноном ролей, и зафиксировать правило «стаб содержит только
  ссылку». Раздел «Parallel work» не переписывать.
- `docs/agent-memory/workflows.md` — новый блок в конец через `---`
  (append-only): модель «канон в `docs/skills` + `docs/agents`, в `.claude/**`
  только стабы-ссылки, генератора нет», правило «третьего дома не заводим»,
  различие command-vs-skill, и почему принуждение остаётся за гейтом
  документации, а не за авто-скиллом. С evidence (пути к файлам).

## Acceptance criteria

- [x] `docs/skills/<name>/GUIDE.md` существуют для `new-task`, `close-task`,
  `module-doc-sync`, `memory-writeback`; каждый с фронтматтером (`name`,
  `description`, `kind`) и самодостаточным телом.
- [x] Для `run-verification` и `worktree-parallel` GUIDE **не** создан, а стабы
  ссылаются на существующие разделы `AGENTS.md` и
  `docs/AI_DEVELOPMENT_WORKFLOW.md`.
- [x] В `.claude/commands/` четыре команды, в `.claude/skills/` два скилла;
  тело каждого файла — фронтматтер плюс одна строка-ссылка, без шагов
  процедуры.
- [x] `AGENTS.md` содержит таблицу «Agent scripts» со всеми точками входа и
  блок указателей на фоновые процедуры; при этом не стал длиннее, чем до
  задачи (211 → 190 строк; дублирующая проза перенесена в GUIDE, а не
  скопирована).
- [x] Коммитируемый `.claude/settings.json` даёт `permissions.allow` на скрипты
  из таблицы; `.claude/settings.local.json` не изменён и по-прежнему
  игнорируется git (`git check-ignore` совпадает, файла нет в worktree).
- [x] `docs/agents/*.md` содержат явный контракт (входы, запреты, процедуры,
  формат ответа), план как секция `## Plan`, и правильный задачный гейт.
- [x] Локального `docs/prompts/` больше нет.
- [x] `docs/agent-memory/workflows.md` пополнен append-only блоком про этот
  слой, с evidence.
- [x] Ни один файл в `.claude/**` не содержит текста процедуры, который
  пришлось бы синхронизировать с каноном (проверяется чтением всех шести
  файлов — их суммарный объём это позволяет).
- [x] Задачный гейт `./scripts/verify.ps1` проходит (`BUILD SUCCESSFUL in
  1m 59s`, doc coverage + self-check PASS, exit 0).

## Out of scope

- **Генератор `scripts/sync-agent-config` и его режим `-Check` в
  `scripts/verify`.** Схема со стабами-ссылками не создаёт расхождения, а две
  реализации генератора (PowerShell + POSIX с требованием идентичного вывода)
  дороже управляемого ими объёма. Возвращаться к нему только если появится
  реальное дублирование текста между каноном и проекциями либо второй среде
  понадобятся генерируемые файлы.
- **`.codex/prompts/`, `.codex/config.toml` и любые проектные файлы Codex.** В
  v1 Codex обслуживается указателями в `AGENTS.md`.
- **`.claude/agents/*` (сабагенты) для ролей.** Дало бы механизм, которого нет
  у Codex, и приблизилось бы к автооркестратору, запрещённому в
  `docs/AI_DEVELOPMENT_WORKFLOW.md`. Роли остаются каноном в `docs/agents/`.
- **MCP-сервер `tools/device-mcp`, реестр `docs/agent-tools.json`, генерация
  `.mcp.json`.** Точки входа уже выражены скриптами; MCP добавляет процесс и
  вендорный конфиг, не давая новой возможности. Заводить вместе с первым
  сценарием, который скриптом не выражается.
- Переписывание раздела «Parallel work» (TASK-019) и правка таблицы покрытия
  `check-documentation`.
- Изменения кода приложения, Gradle, зависимостей, `minSdk`, Compose.
- Скилл `layering-review` и вынос «memory startup» из `AGENTS.md` в отдельную
  процедуру — позже, если понадобится повторяемый проход.

## Relevant areas

- `docs/skills/` (новое), `docs/agents/*.md` (расширить), `docs/prompts/`
  (удалить)
- `AGENTS.md` (таблица скриптов, блок указателей, перенос write-back прозы)
- `docs/AI_DEVELOPMENT_WORKFLOW.md`, `docs/agent-memory/workflows.md`
- `.claude/settings.json` (новый, коммитится), `.claude/commands/`,
  `.claude/skills/`

## Verification

- [x] Задачный гейт: `./scripts/verify.ps1` — зелено (`BUILD SUCCESSFUL in
  1m 59s`; 50 tasks executed; `Documentation coverage: PASS`, self-check PASS;
  `JAVA_HOME=C:\AndroidStudio\jbr`). Изменения затрагивают только `docs/`,
  `AGENTS.md` и `.claude/`, вне `$auditPathspecs` гейта документации, поэтому
  правка `docs/modules/*` не требуется и не выполнялась.
- [ ] Интеграционный гейт (владелец, на слитом результате): `./scripts/verify`
  после merge. Устройство здесь не нужно — кода приложения задача не меняет;
  `verify-and-install.ps1` в объём задачи не входит. **Не выполнено — шаг
  владельца.**
- [ ] Ручная проверка в чистой сессии Claude Code: `/new-task`, `/close-task`,
  `/run-verification`, `/worktree-parallel` доступны и подтягивают канон.
  **Не выполнено — требует новой сессии Claude Code.**
- [ ] Ручная проверка авто-скилла: в чистой сессии правка покрытого модуля
  провоцирует `module-doc-sync`. Срабатывание эвристично — при неудаче
  переформулировать `description`, но не считать это блокером: принуждение
  обеспечивает гейт документации. **Не выполнено — требует новой сессии.**
- [ ] Итоговый тест смысла: в чистой сессии задача закрывается по
  `/close-task`, без чтения ритуальной прозы из `AGENTS.md`. **Не выполнено —
  требует новой сессии.**
- [x] Acceptance criteria checked.

## Outcome

Закрыто на ветке `task-021` (worktree `C:\fs-wt\task-021`, от `master` 80733f9).
Closing commit SHA: `4b95e6f` — `docs: add pointer-only agent skills and script
catalog (TASK-021)`.

### Что изменилось

**Канон процедур (новое) — `docs/skills/`:**

- `new-task/GUIDE.md`, `close-task/GUIDE.md` (`kind: command`),
  `module-doc-sync/GUIDE.md`, `memory-writeback/GUIDE.md` (`kind: skill`).
  Фронтматтер `name` / `description` (только условие) / `kind` / `roles`; тело
  самодостаточно, скрипты названы прозой. GUIDE для `run-verification` и
  `worktree-parallel` **не создавались** — их проекции ссылаются на разделы
  `AGENTS.md` и `docs/AI_DEVELOPMENT_WORKFLOW.md`.

**Проекции для Claude Code (новое) — `.claude/`:**

- `commands/new-task.md`, `commands/close-task.md`, `commands/run-verification.md`,
  `commands/worktree-parallel.md`; `skills/module-doc-sync/SKILL.md`,
  `skills/memory-writeback/SKILL.md`. Каждый файл — фронтматтер плюс одна строка
  `Follow …` / ссылка на раздел; шагов процедуры нет.
- `settings.json` (новый, коммитится) — `permissions.allow` на скрипты из
  таблицы «Agent scripts» (22 записи, PowerShell + POSIX варианты).
  `settings.local.json` не тронут, по-прежнему в `.gitignore` и отсутствует в
  worktree.

**`AGENTS.md` — 211 → 190 строк:**

- «Memory write-back protocol» сжат до правила + указателя на GUIDE; секции
  «What belongs / does NOT belong in shared memory» и «Evidence requirement»
  перенесены (не скопированы) в `docs/skills/memory-writeback/GUIDE.md`.
- Добавлены разделы «Agent procedures» (блок указателей на канон/разделы) и
  «Agent scripts» (таблица всех точек входа: Windows/POSIX, аргументы,
  назначение, где запускать).
- В «Definition of done» дублирующий абзац про write-back заменён отсылкой к
  «Agent procedures».

**Роли — `docs/agents/*.md`:** PLANNER / IMPLEMENTER / VERIFIER переписаны в
явный контракт (Must read first, Procedure(s), Must not, Gates, Response
format: `DONE` / `PARTIALLY VERIFIED` / `BLOCKED`, `VERDICT: PASS | FAIL`).
Исправлено противоречие: план — секция `## Plan` в файле задачи, не
`TASK-XXX-plan.md`. Назван верный задачный гейт (`scripts/verify.ps1`, в
worktree — единственный) и интеграционный шаг владельца. Процедуры привязаны к
ролям.

**Документация и память:**

- `docs/AI_DEVELOPMENT_WORKFLOW.md` — новый раздел «Procedures and roles»
  (канон в `docs/skills/` + `docs/agents/`, в `.claude/**` только
  стабы-ссылки, генератора нет).
- `docs/agent-memory/workflows.md` — append-only блок «Agent procedures:
  single canon, pointer-only `.claude` projections» с evidence.
- Пустой локальный `docs/prompts/` удалён из основного checkout (в git его не
  было — коммит-артефакта нет).

### Не проверено

- Интеграционный гейт (`./scripts/verify` после merge в `master`) — шаг
  владельца.
- Ручные проверки в чистой сессии Claude Code: доступность `/new-task`,
  `/close-task`, `/run-verification`, `/worktree-parallel`; срабатывание
  авто-скилла `module-doc-sync`; закрытие задачи по `/close-task` без чтения
  ритуальной прозы. Требуют новой сессии Claude Code.
