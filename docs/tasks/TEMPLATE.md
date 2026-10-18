# TASK-XXX — Title

## Goal

Describe the outcome for the user.

## Context

Why this task exists and what it affects.

## Requirements

- ...

## Acceptance criteria

- [ ] ...

## Out of scope

- ...

## Relevant areas

Likely modules or components. This is a guide, not permission to ignore other affected code.

## Verification

- [ ] Task gate: `./scripts/verify.ps1` (Windows) or `./scripts/verify` (Linux/macOS/CI) — doc gate + self-check + lint + unit + assemble. Run it in the task worktree; there it auto-compares the doc gate against the base branch.
- [ ] Integration gate (repo owner, on the merged result): `./scripts/verify` after merge, then `./scripts/verify-and-install.ps1` to install and launch on the Galaxy XCover 4s. Carry anything still pending here into `## Outcome`.
- [ ] Acceptance criteria checked
- [ ] Additional scenario-specific check: ...

## Outcome

Fill in when closing the task, then `git mv` this file to `docs/tasks/done/`:

- What actually changed (files/behavior) and the closing commit SHA.
- Anything still not verified (e.g. manual on-device checks) — carry it here, not in chat history.
