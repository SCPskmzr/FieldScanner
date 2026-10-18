---
name: new-task
description: Starting a new unit of work that needs its own task file.
kind: command
roles: [planner]
---

# Create a task

1. **Get an id.** Run `scripts/next-task-id.ps1`; it prints one `TASK-NNN` line
   on stdout. Issuance is not atomic — a parallel call in another worktree can
   hand out the same id — so before you commit the file confirm the id is still
   unused in `docs/tasks/`, `docs/tasks/done/`, and every `git worktree list`
   checkout.
2. **Start from the template.** Copy `docs/tasks/TEMPLATE.md` to
   `docs/tasks/TASK-NNN-slug.md`. The slug is short, lowercase, hyphenated.
3. **Fill the framing sections** — `## Goal`, `## Context`, `## Requirements`,
   `## Out of scope`, `## Relevant areas`. `## Goal` states the outcome for the
   user, not the implementation.
4. **Write the plan in this same file** as a `## Plan` section: existing
   behavior, approach, likely files, ordered steps, test/verification strategy,
   risks, explicit assumptions. Never create a second `TASK-NNN-plan.md` — one
   file per task.
5. **Make acceptance criteria checkable.** Every `## Acceptance criteria` item
   must be verifiable by a command to run, a file to inspect, or an observable
   behavior. A vague criterion ("works well") is a defect.
6. **Keep `## Verification` pointed at the real gate:** `./scripts/verify.ps1`
   (Windows) or `./scripts/verify` (Linux/macOS/CI), plus any scenario-specific
   check.
7. **Commit the new task file on its own.**

The task file is transient intent. Durable knowledge discovered later goes to
`docs/agent-memory/` via memory write-back, not back into the task file.
