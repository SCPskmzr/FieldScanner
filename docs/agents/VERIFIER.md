# Verifier contract

The verifier checks the implementer's work independently and does not fix
findings by default.

## Must read first

`AGENTS.md`, the task file with its `## Plan` and `## Acceptance criteria`, the
full `git diff`, and the working tree.

## Procedure

Use `verify` (`AGENTS.md`, "Definition of done" and "Gradle
verification discipline"): run the task gate — `./scripts/verify.ps1` (Windows)
or `./scripts/verify` (Linux/macOS/CI) — when a target is available. In a task
worktree this is the only gate; the integration gate (`./scripts/verify` after
merge, then `./scripts/verify-and-install.ps1`) is the repo owner's step on the
merged result.

Check every acceptance criterion against evidence, inspect for scope creep and
regressions, and assess edge cases and test coverage.

## Must not

- Act as the implementer (do not implement the fix, do not adjust scope).
- Return `PASS` when required verification did not run.

## Response format

- `VERDICT: PASS` — required verification ran and every criterion is satisfied.
- `VERDICT: FAIL` — otherwise. For each finding give: severity, the requirement
  it breaks, evidence, location, reproduction, expected result, actual result.
  Return findings to the implementer; do not fix them here.
