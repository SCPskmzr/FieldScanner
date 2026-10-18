# TASK-019 — Пайплайн параллельной работы агентов через git worktree

Заменяет TASK-011 (часть про сам пайплайн). Зависит от TASK-018 (гейт покрытия
документации). Робо-смоук старта приложения вынесен в TASK-020.

## Goal

Два агента (например Claude Code и Codex) ведут разные задачи в этом
репозитории одновременно, каждый в своём git worktree, без порчи рабочего
дерева, индекса и Gradle-стейта. Гейт завершения задачи — `scripts/verify`
(без устройства); установка и запуск на Galaxy XCover 4s — отдельный ручной шаг
владельца репозитория на слитом результате.

## Context

Сейчас процесс рассчитан на один рабочий каталог и одного активного агента:
[docs/AI_DEVELOPMENT_WORKFLOW.md:29](../AI_DEVELOPMENT_WORKFLOW.md) прямо
запрещает многоагентную оркестрацию, а общий рабочий tree и индекс приводят к
тому, что `git commit` одного агента захватывает незакоммиченные файлы другого;
фичер-ветки этого не решают, потому что checkout, `HEAD` и индекс в каталоге
одни на всех.

Отдельный worktree на агента устраняет гонку по файлам и индексу, оставляя
общий `.git`. [verify.ps1:8](../../scripts/verify.ps1) вычисляет
`GRADLE_USER_HOME` от корня своего каталога, а `app/build/` лежит внутри
worktree, поэтому Windows-путь верификации уже изолирован и
параллельно-безопасен. **POSIX-[scripts/verify](../../scripts/verify) — нет:**
он использует `${GRADLE_USER_HOME:-$HOME/.gradle}`, то есть общий кеш на все
worktree. Это ограничение нужно зафиксировать, а не «унаследовать» из
Windows-поведения.

Решение по устройству: установка/запуск убраны из цикла задачи. Это снимает
единственный монопольный ресурс из параллельного пути и убирает необходимость
в мьютексе и суффиксе `applicationId`. Плата — runtime-креши на старте и
проблемы merge манифеста/ресурсов ловятся при ручной установке владельцем
(см. `## Risks`); частично их закрывает TASK-020.

Уточнения, найденные при разборе TASK-011 и обязательные к учёту:

- **Базовая ветка — `master`.** Других веток и remote в репозитории нет
  (`git branch -a`, `git remote -v`). Ссылок на `main` как на ветку в
  документации тоже нет. Значит: базовую ветку надо один раз назвать в
  документации, а интеграция — локальный последовательный merge, **не PR**.
- **`git config` из linked worktree пишет в общий `.git/config`.**
  Per-worktree конфиг требует `extensions.worktreeConfig=true` и
  `git config --worktree`. Формулировка «включить `core.longpaths`/`gc.auto 0`
  для этого worktree» без этого — глобальная мутация репозитория, которую
  никто не откатывает.
- **`core.longpaths` не лечит MAX_PATH при сборке.** Он влияет только на сам
  git; пути пробивает Gradle/kapt/javac. Единственная реальная мера — короткий
  корень worktree.
- **`.gradle-user-home` сейчас занимает 1.1 ГБ.** Каждый worktree заводит свой,
  и `git worktree remove` его не удаляет.
- **Untracked-файлы между worktree не разделяются.** Прямо сейчас
  `docs/tasks/TASK-016-*.md` и `docs/tasks/TASK-017-*.md` не в индексе — новый
  worktree от `master` их не увидит и выдаст занятый id.
- **Главная конфликтная поверхность — не `ui-and-input.md`, а
  `docs/agent-memory/`.** Memory write-back обязателен в каждой задаче
  (`AGENTS.md`), поэтому `workflows.md`/`INDEX.md` правят все параллельные
  задачи, а `docs/modules/ui-and-input.md` — всего 62 строки, конфликт в нём
  дешёвый.

## Requirements

### База и правила

- Зафиксировать `master` как базовую (интеграционную) ветку и назвать её явно
  в `AGENTS.md` и `docs/AI_DEVELOPMENT_WORKFLOW.md`. Скрипты берут её из одного
  места (константа или `git symbolic-ref`), не дублируя строку.
- Интеграцию описать как локальный последовательный merge в `master` одним
  интегратором. Формулировок про PR/remote не вводить — их в репозитории нет.

### Скрипты (Windows-only, PowerShell)

- `scripts/new-worktree.ps1 <TASK-ID>`:
  - создаёт worktree на новой ветке `task-nnn` от `master`; падает без
    побочных эффектов, если ветка или каталог уже существуют;
  - располагает worktree по короткому корню (например `C:\fs-wt\task-nnn`), не
    рядом с глубоким основным каталогом — это и есть мера против MAX_PATH;
  - если репозиторий использует `core.longpaths`/`gc.auto`, эти настройки
    ставятся **осознанно на весь репозиторий** один раз, с явным сообщением
    пользователю о том, что настройка глобальная, либо через
    `extensions.worktreeConfig` + `git config --worktree`. Не описывать
    репо-глобальную настройку как «для этого worktree»;
  - копирует `local.properties` (и, при наличии, другие невидимые для git
    локальные файлы сборки), не копируя `.gradle-user-home/` и `build/`;
  - печатает путь и следующий шаг (`cd`) без лишнего вывода.
- `scripts/remove-worktree.ps1 <TASK-ID>`:
  - удаляет worktree и ветку **только** если ветка слита в `master`; иначе —
    понятный отказ;
  - выполняет `git worktree prune` и удаляет `<worktree>/.gradle-user-home`
    (иначе каждый брошенный worktree — около гигабайта);
  - на залоченных хендлах (Studio/антивирус/остаточные `*.lock`) даёт понятное
    сообщение и опциональный `-Force`, не оставляя полу-удалённого состояния.
- `scripts/next-task-id.ps1`:
  - сканирует `docs/tasks/` и `docs/tasks/done/` **во всех worktree**
    (`git worktree list`), а не только в своём, и учитывает untracked-файлы;
  - нормализует формат до трёх цифр (`TASK-012`, не `TASK-12`);
  - печатает следующий свободный id и явно предупреждает, что выдача не
    атомарна: одновременный вызов двумя агентами может вернуть один id.
- Скрипты worktree — только Windows. Явно записать это в документации; POSIX-
  аналоги не создавать.

### Гейт верификации

- Гейт задачи — `scripts/verify` в своём worktree. `scripts/verify-and-install.ps1`
  из per-task цикла исключается и не запускается двумя агентами параллельно.
- В worktree `scripts/verify.ps1` определяет базовую точку сравнения для гейта
  документации (`master`) и пробрасывает её как `-BaseRef` (механика — из
  TASK-018, где режим `-BaseRef` объединяет коммиты ветки с незакоммиченным).
  В основном каталоге на `master` поведение не меняется.
- Обработать случаи, когда база не определяется (detached HEAD, ветка не от
  `master`): понятная ошибка или явный откат к режиму без `-BaseRef`, но не
  тихий холостой проход.
- Предупреждение при запуске `verify.ps1` на базовой ветке добавлять только
  если существует более одного worktree (`git worktree list`), чтобы не
  шуметь на каждом обычном прогоне владельца.
- После каждого merge в `master` прогонять `scripts/verify` на слитом
  результате — интеграционная проверка конфликтов манифеста, ресурсов и общих
  констант между независимо зелёными ветками.

### Конфликтные поверхности документации

- `docs/agent-memory/workflows.md`, `gotchas.md`, `architecture.md`,
  `decisions.md`: ввести и задокументировать правило append-only — новая запись
  добавляется в конец файла отдельным блоком через `---`, существующие записи
  и преамбула не переформатируются. `INDEX.md` правится только при появлении
  нового топика.
- `docs/modules/ui-and-input.md`: **не дробить**. Зафиксировать стабильную
  структуру `##`-секций (как минимум левый дисплей, нижний статус, центральная
  зона, руны/ввод) и записать в шапке файла правило «правим свою секцию, новые
  секции добавляем в конец». Все существующие ссылки на файл продолжают
  работать без изменений.

### Документация и память

- Переписать раздел «Parallel work» в `docs/AI_DEVELOPMENT_WORKFLOW.md`:
  worktree-процедура, правило «одна ветка — один worktree», выдача id через
  `scripts/next-task-id.ps1`, `scripts/verify` как гейт задачи, ручная
  установка владельцем как отдельный шаг, последовательная интеграция и
  повторный `verify` на слитом результате.
- Обновить «Definition of done» в `AGENTS.md` и чекбокс Verification в
  `docs/tasks/TEMPLATE.md`: развести «task gate» (`scripts/verify`) и
  «integration gate» (ручная установка/запуск на устройстве владельцем).
- В том же проходе исправить в `AGENTS.md` устаревшее имя маркера:
  скрипт пишет `app/build/verify-and-install-status.txt`
  ([verify-and-install.ps1:17](../../scripts/verify-and-install.ps1)), а не
  `verify-and-install-success.txt`. Сверить формулировку в
  `docs/agent-memory/workflows.md`.
- Добавить в `AGENTS.md` правило: потенциально параллельные задачи ведутся в
  отдельном worktree через `scripts/new-worktree.ps1`; двух агентов в основном
  каталоге не запускать.
- Записать в `docs/agent-memory/workflows.md`: процедуру worktree; факт, что
  параллельно-безопасен только Windows-`verify.ps1`, а POSIX-`verify` делит
  `$HOME/.gradle`; что on-device — ручной шаг владельца; принятые компромиссы
  из `## Risks`; правило append-only для файлов памяти.
- Не менять поведение основного каталога без worktree: `./scripts/verify` и
  `./scripts/verify-and-install.ps1` без новых обязательных аргументов
  работают как раньше.

## Risks

### Интеграция

- **Поломка на merge без автоматического сигнала.** Две зелёные ветки при
  слиянии дают битый APK: merge манифеста, коллизия ресурсов, рассинхрон общих
  констант, граф `AppContainer`. (Повторный `scripts/verify` на слитом
  результате; смоук-тест из TASK-020; on-device перед следующим merge.)
- **Runtime-креши на старте не ловятся `assembleDebug`/lint/unit.**
  (Частично TASK-020; остальное — принятый компромисс.)

### Среда Windows

- **MAX_PATH** при сборке в глубоком worktree. (Короткий корень; `core.longpaths`
  здесь не помогает.)
- **Холодный Gradle-кеш × N worktree** — 1.1 ГБ на каждый, забитый диск
  проявляется странными ошибками сборки. (Удаление кеша в `remove-worktree.ps1`;
  спайк по общему `GRADLE_USER_HOME` с `--no-daemon` — вне этой задачи.)
- **Ресурсная гонка**: два Gradle + два kapt + два lint на слабой станции →
  своп/OOM. Практический потолок — два агента; governor'а нет.
- **`git worktree remove`** падает на залоченных хендлах.

### Координация

- **Аллокация id не атомарна** даже со скриптом; untracked-задачи не видны из
  другого worktree.
- **Ничто не заставляет использовать worktree** — агент по привычке стартует в
  основном каталоге. (Правило в `AGENTS.md`; условное предупреждение в
  `verify.ps1`.)
- **Стейл `local.properties` в worktree**: копируется один раз, при смене SDK
  или ключа worktree молча держит старое. (Пере-создавать worktree.)
- **Секрет расползается по каталогам**: `local.properties` содержит
  `OPENAI_API_KEY`, который `app/build.gradle` кладёт в `buildConfigField`.
  Каждый worktree — ещё одна копия ключа на диске.
- **Общий `.git` и авто-gc**: gc в одном worktree берёт лок на `packed-refs` и
  транзиентно роняет ref-операцию в другом.

### Принятые компромиссы

- On-device верификация перестаёт быть частью завершения задачи;
  ответственность — на владельце репозитория.
- Runtime-регрессии вне смоук-теста обнаруживаются на интеграции.
- Параллельность ограничена ресурсами одной рабочей станции (два агента).
- Настройки git (`gc.auto`, `core.longpaths`) — репозиторные, не
  per-worktree, если не включён `extensions.worktreeConfig`.

## Acceptance criteria

- [x] `master` названа базовой веткой в `AGENTS.md` и
  `docs/AI_DEVELOPMENT_WORKFLOW.md`; скрипты берут её из одного места
  (`scripts/base-branch.txt` → `Get-BaseBranch` в `scripts/worktree-common.ps1`).
- [x] `scripts/next-task-id.ps1` печатает следующий свободный `TASK-NNN` с
  учётом `docs/tasks/`, `docs/tasks/done/`, untracked-файлов и всех worktree;
  неатомарность выдачи проговорена (в stderr и в документации). Проверено:
  `TASK-022`; с untracked `TASK-030-*` → `TASK-031`.
- [x] `scripts/new-worktree.ps1 <TASK-ID>` создаёт worktree на ветке `task-nnn`
  от `master` по короткому пути (`C:\fs-wt\task-nnn`) со скопированным
  `local.properties`; повторный запуск падает без побочных эффектов; про
  глобальность git-настроек сообщается явно (`-ApplyRepoGitConfig` печатает что
  и как откатить; без него — hint при пустом `gc.auto`).
- [x] `scripts/remove-worktree.ps1 <TASK-ID>` отказывается удалять неслитую
  ветку (`git merge-base --is-ancestor`), удаляет слитую вместе с каталогом,
  `.gradle-user-home` и записью worktree (`git worktree prune`); `-Force` и
  явное сообщение для залоченных хендлов, без полу-удалённого состояния.
- [x] В двух worktree одновременно проходит `./scripts/verify.ps1` без
  взаимного влияния на `app/build/` и `.gradle-user-home/` — изоляция
  структурная (`$repositoryRoot` от `$PSScriptRoot` каждого worktree). Полный
  зелёный прогон `verify.ps1` в worktree подтверждён; строго одновременный
  прогон двух — проверка владельца на его железе.
- [x] `./scripts/verify.ps1` в worktree ловит пропущенное обновление
  `docs/modules/` **после коммита** в ветке (авто `-BaseRef master`), а в
  основном каталоге на `master` ведёт себя как раньше (обе стороны проверены).
- [x] При недоступной базовой точке `verify.ps1` не проходит вхолостую:
  detached HEAD / ветка без merge-base к `master` → предупреждение + прогон
  всех трёх проверок по рабочему дереву (не тихий проход).
- [x] Предупреждение о базовой ветке появляется только при существующих
  дополнительных worktree (проверено: молчит при одном, предупреждает при двух).
- [x] `docs/agent-memory/*` содержит правило append-only (преамбулы всех
  четырёх файлов), а `docs/modules/ui-and-input.md` — зафиксированную структуру
  `##`-секций (шапка + секции «Состояния сканера», «Центральная зона», «Нижний
  статус», «Левая зона», «Руны»); файл не раздроблен, внешние ссылки на него не
  меняются.
- [x] `AGENTS.md`, `docs/AI_DEVELOPMENT_WORKFLOW.md`, `docs/tasks/TEMPLATE.md`,
  `docs/agent-memory/workflows.md` описывают worktree-пайплайн, разведённые
  task/integration гейты и ручную on-device установку владельцем.
- [x] Имя маркера в `AGENTS.md` совпадает с тем, что пишет
  `verify-and-install.ps1` (`app/build/verify-and-install-status.txt`,
  `state=PASS`).
- [x] `./scripts/verify` и `./scripts/verify-and-install.ps1` в основном
  каталоге работают без новых обязательных аргументов (`verify.ps1` без
  аргументов — зелёный прогон в основном каталоге; `verify-and-install.ps1`
  зовёт `verify.ps1` без аргументов, поведение не менялось).
- [x] Принятые компромиссы из `## Risks` отражены в
  `docs/agent-memory/workflows.md`.

## Out of scope

- Автоматический оркестратор, раздающий задачи агентам и мержащий ветки.
- Возврат on-device установки в автоматический пайплайн; параллельная
  установка на несколько устройств.
- CI облачного раннера.
- Робо-смоук старта приложения (TASK-020) и правки гейта покрытия
  документации (TASK-018).
- Дробление `docs/modules/ui-and-input.md` на под-страницы.
- Приведение POSIX-`scripts/verify` к репо-локальному `GRADLE_USER_HOME`
  (только задокументировать ограничение).
- Спайк по общему `GRADLE_USER_HOME` между worktree.
- Реализация самих TASK-007/008/010/013/016.
- Переход на отдельные `git clone`; новые сторонние зависимости.

## Relevant areas

- `scripts/new-worktree.ps1`, `scripts/remove-worktree.ps1`,
  `scripts/next-task-id.ps1` (новые)
- `scripts/verify.ps1` (определение базовой точки, условное предупреждение)
- `docs/AI_DEVELOPMENT_WORKFLOW.md`, `AGENTS.md`, `docs/tasks/TEMPLATE.md`
- `docs/modules/ui-and-input.md` (структура секций, без дробления)
- `docs/agent-memory/workflows.md`, `docs/agent-memory/INDEX.md`

## Verification

- [~] `./scripts/verify-and-install.ps1` в основном каталоге — не запускался
  (нет подключённого устройства). Обёрнутый им `./scripts/verify.ps1` без
  аргументов проходит зелёным в основном каталоге (`Documentation coverage:
  PASS`, `self-check: PASS`, lint/unit/assemble `BUILD SUCCESSFUL`, exit 0) —
  регрессий базового пути в проверяемой части нет.
- [x] Ручной прогон: `next-task-id.ps1` (`TASK-022`) → `new-worktree.ps1` для
  `TASK-999`/`TASK-998`/`TASK-997` → `./scripts/verify.ps1` в worktree
  (последовательно, не строго одновременно) → `remove-worktree.ps1 TASK-999`
  неслитой (отказ), `remove-worktree.ps1 TASK-998` слитой (удаление вместе с
  `.gradle-user-home` и записью worktree).
- [x] Проверка гейта документации в worktree: коммит правки
  `display/DisplayGeometry.kt` без правки страницы → `./scripts/verify.ps1`
  печатает «comparing against 'master'» и падает на
  `Documentation update required: docs/modules/ui-and-input.md`; с добавленным
  коммитом правки страницы — гейт проходит.
- [x] `next-task-id.ps1` видит untracked задачи (проверено: временный
  `docs/tasks/TASK-030-scratch.md` → выдаёт `TASK-031`); скрипт обходит все
  записи `git worktree list`, включая основной каталог.
- [x] Acceptance criteria checked.

## Outcome

Closing commit SHA: `b1af488` — `feat: parallel-agent git-worktree pipeline (TASK-019)`

### Что изменилось

Новые скрипты (Windows-only PowerShell):

- `scripts/base-branch.txt` — единственное место, где названа интеграционная
  ветка (`master`).
- `scripts/worktree-common.ps1` — общие хелперы (`Get-BaseBranch`,
  `Get-MainWorktreePath`, `Test-LinkedWorktree`, `Test-GitRef`,
  `ConvertTo-TaskId`, `Get-TaskNumber`, `Get-WorktreeBranchName`).
- `scripts/next-task-id.ps1` — следующий `TASK-NNN` на stdout (одна строка);
  обход `docs/tasks/` + `docs/tasks/done/` + untracked во всех
  `git worktree list`; неатомарность — в stderr.
- `scripts/new-worktree.ps1 TASK-NNN [-WorktreeRoot C:\fs-wt]
  [-ApplyRepoGitConfig]` — ветка `task-nnn` от `master` в коротком корне,
  копирует `local.properties`, не копирует кеши/`build/`; pre-flight без
  побочных эффектов; `-ApplyRepoGitConfig` ставит `gc.auto=0` +
  `core.longpaths=true` репо-глобально и печатает откат.
- `scripts/remove-worktree.ps1 TASK-NNN [-Force]` — только для слитой в
  `master` ветки; удаляет worktree, его `.gradle-user-home`, ветку, `prune`;
  залоченные хендлы → понятное сообщение + `-Force`, без полу-состояния.

Изменения:

- `scripts/verify.ps1` — необязательный `-BaseRef`; в linked worktree
  автоматически сравнивает гейт документации с `master` (детект по
  `git rev-parse --git-dir` != `--git-common-dir`); detached HEAD / ветка без
  merge-base → предупреждение + прогон по рабочему дереву (не тихий проход);
  предупреждение о запуске на базовой ветке — только при >1 worktree.
  **Исправлен баг из TASK-018**: `verify.ps1` пробрасывал `-BaseRef` через
  array-splat `@('-BaseRef', $x)`, из-за чего `$BaseRef` становился строкой
  `'-BaseRef'` и git читал её как `-B` («break-rewrites expects <n>/<m>
  form»), а гейт тихо проходил. Теперь вызов явный.
- `scripts/check-documentation.ps1` — добавлен `exit 0` на успешном пути (иначе
  `$LASTEXITCODE` от внутреннего `git` протекал в вызывающий скрипт).
- `docs/AI_DEVELOPMENT_WORKFLOW.md` — раздел «Parallel work (git worktree,
  Windows)» переписан целиком.
- `AGENTS.md` — маркер `verify-and-install-status.txt` (был
  `...-success.txt`); «Before starting work» п.4 (worktree); «Definition of
  done» разведены task gate и integration gate; «Gradle verification
  discipline» — про per-worktree кеш.
- `docs/tasks/TEMPLATE.md` — Verification: task gate / integration gate.
- `docs/agent-memory/architecture.md|decisions.md|gotchas.md|workflows.md` —
  правило append-only в преамбуле; `workflows.md` — запись про worktree-пайплайн
  (процедура, parallel-safety, компромиссы из `## Risks`, найденные ловушки:
  CRLF-в-worktree при `-am`, `JAVA_HOME`/JBR для standalone `verify.ps1`,
  array-splat именованного параметра).
- `docs/modules/ui-and-input.md` — шапка с правилом «правь свою секцию, новые в
  конец, не дробить»; прежний `## Состояния` разнесён на `## Состояния
  сканера` / `## Центральная зона` / `## Нижний статус` / `## Левая зона` без
  изменения текста.

### Не проверено / отложено

- `./scripts/verify-and-install.ps1` и строго одновременный прогон
  `./scripts/verify.ps1` в двух worktree — на владельце (нет устройства; 2×
  Gradle — нагрузочная проверка его станции). Изоляция кешей структурная и
  подтверждена одиночным зелёным прогоном в worktree.
- Залоченные хендлы в `remove-worktree.ps1` — путь `-Force` и сообщение
  закодированы и проходят по логике, но реальный залоченный хендл не
  воспроизводился.
- POSIX `scripts/verify` не получил worktree-режим (по дизайну — worktree-часть
  Windows-only); его общий `$HOME/.gradle` задокументирован как не
  parallel-safe.
- `scripts/verify.ps1` в standalone-режиме требует `JAVA_HOME` (JDK 17); на
  этой станции `PATH` `java` — Java 8. Записано в `workflows.md`; правку самого
  `verify.ps1` (фолбэк на JBR, как в `verify-and-install.ps1`) не делал — вне
  scope TASK-019.
