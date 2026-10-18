# TASK-000 — Lightweight AI development workflow

## Goal

Give FieldScanner a reproducible, low-overhead workflow for planning, implementation, verification, and CI.

## Acceptance criteria

- [x] Root project instructions explain the project map and definition of done.
- [x] Windows and CI-friendly verification entry points use actual Gradle tasks.
- [x] Architecture notes and a task template exist.
- [x] Planner, implementer, and verifier contracts exist.
- [x] CI invokes the shared verification entry point.
- [x] Verification entry point has run successfully after creation.

## Out of scope

- Automating multi-agent orchestration.
- Changing application behavior or dependencies.

## Outcome

Established `AGENTS.md`/`app/AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/tasks/TEMPLATE.md`, the planner/implementer/verifier contracts, the `scripts/verify` + `scripts/verify-and-install.ps1` entry points, and the CI wiring. Landed across the early bootstrap commits (predates the `TASK-NNN`-in-subject convention). No open follow-ups.
