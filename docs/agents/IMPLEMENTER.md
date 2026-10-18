# Implementer contract

The implementer makes the change described by an approved task and closes it.

## Must read first

`AGENTS.md`, `app/AGENTS.md`, the task file including its `## Plan`,
`docs/agent-memory/INDEX.md` and the relevant topic files, and the existing code
in the affected area. Run `git status` before editing.

## Procedures

- `verify` / `verify-install` — `AGENTS.md`, "Definition of done" and "Gradle
  verification discipline".
- `module-doc-sync` — `docs/skills/module-doc-sync/GUIDE.md`, when a covered
  source area changed.
- `memory-writeback` — `docs/skills/memory-writeback/GUIDE.md`.
- `close-task` — `docs/skills/close-task/GUIDE.md`.
- `worktree-parallel` — `docs/AI_DEVELOPMENT_WORKFLOW.md`, "Parallel work",
  when the work could run beside another agent's.

## Must not

- Change anything outside the task scope; no drive-by refactors or dependency
  upgrades.
- Weaken tests, lint, or gate checks to make verification pass.
- Add dependencies without a concrete, stated need.
- Report `DONE` when a verification failure was caused by the change, or when an
  acceptance criterion is unmet.

## Gates

Run the task gate once on the finished change: `./scripts/verify.ps1` (Windows)
or `./scripts/verify` (Linux/macOS/CI). In a task worktree this is the only
gate. `./scripts/verify-and-install.ps1` and on-device checks are the repo
owner's integration step on the merged result.

## Response format

- `DONE` — scope met, task gate ran and passed, acceptance criteria satisfied,
  `## Outcome` filled, file `git mv`d to `docs/tasks/done/`.
- `PARTIALLY VERIFIED` — change complete but a required check could not run;
  name it.
- `BLOCKED` — cannot proceed; state the specific reason.

Always report actual command results; a check that could not run is "not
verified", not "passed".
