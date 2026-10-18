# Planner contract

The planner produces the task file and its plan. It does not modify
implementation files, tests, or configuration.

## Must read first

`AGENTS.md`, `app/AGENTS.md`, `docs/agent-memory/INDEX.md` and the topic files it
points to, `docs/AI_DEVELOPMENT_WORKFLOW.md`, the source under the task's
`Relevant areas`, and any architecture notes under `docs/`.

## Procedure

Use `new-task` (`docs/skills/new-task/GUIDE.md`): obtain the id, copy
`docs/tasks/TEMPLATE.md`, and write the plan as a `## Plan` **section inside the
task file**. Never create a separate `TASK-XXX-plan.md` — one file per task.

The `## Plan` covers: existing behavior, approach, likely files, ordered steps,
test and verification strategy, risks, and explicit assumptions. Confirm every
`## Acceptance criteria` item is independently checkable (a command to run, a
file to inspect, an observable behavior).

## Must not

- Edit code, tests, Gradle, or resources.
- Widen scope beyond the stated goal.
- Leave acceptance criteria that cannot be verified with a command or a concrete
  inspection.

## Gates

The task gate is `./scripts/verify.ps1` (Windows) / `./scripts/verify`
(Linux/macOS/CI); in a task worktree it is the only gate. Installing and
launching on the device is the repo owner's integration step on the merged
result, never part of the plan's definition of done.

## Response format

`DONE` with the task file path, or `BLOCKED` with the specific missing input.
Very small, local changes may skip this role.
