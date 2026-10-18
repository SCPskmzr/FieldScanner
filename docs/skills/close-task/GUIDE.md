---
name: close-task
description: A task's work is finished and its file should be archived.
kind: command
roles: [implementer]
---

# Close a task

The mechanics live in `docs/AI_DEVELOPMENT_WORKFLOW.md`, section
"Task lifecycle and status" — do not restate them elsewhere. In order:

1. **Re-check the work.** Read `git diff` and the task's `## Acceptance
   criteria`. Tick only the boxes that actually hold; a check that could not run
   is reported as not verified, never as passed.
2. **Run the task gate once** on the finished change — `./scripts/verify.ps1`
   (Windows) or `./scripts/verify` (Linux/macOS/CI) — and record the real
   result. In a task worktree this is the only gate; the integration gate is the
   repo owner's separate step on the merged result.
3. **Memory write-back.** Decide whether the task produced durable, verified
   knowledge and, if so, update `docs/agent-memory/` in this same change.
   Follow `docs/skills/memory-writeback/GUIDE.md`.
4. **Module docs.** If a covered source area changed, sync its `docs/modules/`
   page in this same change. Follow `docs/skills/module-doc-sync/GUIDE.md`.
5. **Fill `## Outcome`:** what changed (files/behavior), the closing commit SHA,
   and anything still not verified (carry it here, not in chat history).
6. **Archive the file.** `git mv` it from `docs/tasks/` to `docs/tasks/done/` in
   the same change.
7. **Name the task id in the closing commit subject**, e.g.
   `refactor: extract MainActivity wiring (TASK-003)`.
