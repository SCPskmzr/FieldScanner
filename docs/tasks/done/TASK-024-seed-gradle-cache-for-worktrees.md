# TASK-024 — Seed Gradle cache for new worktrees

## Goal

Make a new task worktree start with a safe, local seed of Gradle distributions
and resolved dependencies, avoiding cold downloads while preserving isolation
between concurrent agents.

## Context

The TASK-022/023 worktree experiment spent 5–12 minutes on cold Gradle caches.
On 2026-08-31, copying the warmed `master` `.gradle-user-home` (1.03 GiB) into
a new worktree took 11.9 seconds; with an empty `app/build`, the full task gate
then passed in 1m 41s. The existing `scripts/new-worktree.ps1` deliberately
copies only `local.properties`, so this repeatable optimization must be added
there without sharing a writable cache between worktrees.

## Requirements

- Add an explicit opt-in seed-cache option to `scripts/new-worktree.ps1`.
- Copy the base checkout's `.gradle-user-home` into the new worktree only after
  `git worktree add` succeeds.
- Keep the copied cache private to the new worktree; never set two worktrees to
  one writable `GRADLE_USER_HOME`.
- Exclude transient `.tmp` and `daemon` directories from the seed.
- Never copy `app/build/` or any other branch-specific build output.
- Fail clearly without leaving a partially registered worktree if the requested
  seed source is missing or the cache copy fails.
- Document the option, disk cost, and recovery after an interrupted Gradle run
  in the workflow documentation.

## Acceptance criteria

- [x] `new-worktree.ps1` offers a documented, opt-in seed-cache option and
  preserves existing behavior when it is omitted.
- [x] A seeded worktree gets its own `.gradle-user-home`; no `app/build` is
  copied from the base checkout.
- [x] A seeded worktree passes `./scripts/verify.ps1` after a minimal,
  documented source change.
- [x] A missing or invalid seed source fails with a clear message and leaves no
  partially created worktree or branch.
- [x] Documentation and shared memory state the measured trade-off and the
  recovery procedure after a forcibly interrupted Gradle run.
- [x] Acceptance criteria checked.

## Out of scope

- Sharing a writable Gradle user home or build cache across concurrent
  worktrees.
- Copying or reusing `app/build/` output between branches.
- Changing Gradle, AGP, Kotlin, or dependency versions.
- JBR fallback in `verify.ps1`, CRLF handling, and docs-only verification gates;
  those are separate improvements.

## Relevant areas

- `scripts/new-worktree.ps1`
- `scripts/remove-worktree.ps1`
- `scripts/verify.ps1`
- `docs/AI_DEVELOPMENT_WORKFLOW.md`
- `docs/agent-memory/workflows.md`

## Verification

- [x] Task gate: `./scripts/verify.ps1` (Windows) in the task worktree — doc
  gate + self-check + lint + unit + assemble.
- [ ] Integration gate (repo owner): after merge, run `./scripts/verify` on
  `master`, then `./scripts/verify-and-install.ps1` to install and launch.
- [x] Scenario check: create a throwaway worktree with the seed option; confirm
  its cache is private, `.tmp`/`daemon` and `app/build` were not copied, and a
  full task gate passes.
- [x] Failure check: request seeding from an unavailable source and confirm the
  script leaves neither a registered worktree nor a `task-nnn` branch.
- [x] Acceptance criteria checked.

## Outcome

- **What changed:** `scripts/new-worktree.ps1` now supports explicit
  `-SeedGradleCache`. It checks the main worktree cache before side effects,
  copies it only after `git worktree add`, excludes `.tmp` and `daemon`, and
  cleans up the exact target/branch if post-create setup fails. Each destination
  remains a private `.gradle-user-home`; no `app/build` is copied. The workflow
  and shared-memory instructions document the option, ~1 GiB disk cost and
  interrupted-Gradle recovery.
- **Verification:** created `C:\fs-wt\task-997` with `-SeedGradleCache`;
  confirmed a private cache with no `.tmp`, `daemon` or `app/build`, made a
  harmless documented change and passed `scripts/verify.ps1` (lint report,
  18 unit XML reports with 0 failures/errors, and debug APK produced). The
  temporary worktree and branch were removed. Temporarily hiding the source
  cache made `TASK-996 -SeedGradleCache` fail before creating either its target
  path or branch. `scripts/verify.ps1` on this change also passed.
- **Not verified:** the integration gate on a merged result and device install
  are owner steps. Robocopy's mid-copy failure path was code-reviewed but not
  fault-injected; its cleanup deletes the exact preflight-checked target and
  temporary branch.
- **Closing commit SHA:** `057704ecd98ce3c87951454487bb37d68cb264af`
  (`feat: seed Gradle cache for worktrees (TASK-024)`); recorded in this
  follow-up commit, per the established TASK-018/019/023 convention.

## Amendment — shared Gradle User Home

On 2026-08-31 the repository switched from per-worktree
`.gradle-user-home` caches to Gradle's normal shared per-user Gradle User Home.
The `-SeedGradleCache` option was removed from `new-worktree.ps1`, because
Gradle's local dependency-cache locking supports concurrent processes on this
host and the copied-cache approach consumed about 1 GiB per worktree. The
branch-specific `.gradle/` and `app/build/` directories remain local. See the
append-only correction in `docs/agent-memory/workflows.md` and the follow-up
commit `153501966dc050964ac7cf2cd6de4b377f66b929`
(`fix: share Gradle user home across worktrees (TASK-024)`).
