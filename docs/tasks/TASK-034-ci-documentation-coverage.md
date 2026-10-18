# TASK-034 — Restore Linux documentation coverage parity

## Goal

Let Android CI validate the current source tree without missing documentation mappings.

## Context

The Bash coverage table lacks three mappings already present in PowerShell. The
scanner and platform module pages already describe these source areas. GitHub
master is a separate snapshot history; this task starts at origin/master and
will be pushed there without merging the unrelated local history.

## Requirements

- Synchronize Bash mappings with the existing PowerShell table.
- Preserve all documentation checks and existing CI setup fixes.

## Plan

1. Verify existing module descriptions and PowerShell mappings.
2. Add the three missing Bash mappings; check table parity and both self-checks.
3. Run the task gate with origin/master as the explicit base, review, and push.

## Acceptance criteria

- [ ] Bash and PowerShell coverage tables have identical source/document pairs.
- [ ] Both documentation self-checks pass on the real source tree.
- [ ] Existing CI setup and executable-permission fixes remain intact.

## Out of scope

Application changes, SDK upgrades, device installation, unrelated local history.

## Relevant areas

scripts/check-documentation, scripts/check-documentation.ps1,
docs/modules/scanner.md, docs/modules/platform-and-build.md.

## Verification

- [ ] `./scripts/verify.ps1 -BaseRef origin/master`: documentation, self-check, lint, unit tests, APK.
- [ ] Bash documentation change gate and self-check; coverage table parity.
- [ ] Independent diff review and acceptance criteria checked.
- GitHub CI is checked after push; no device installation for this CI-only fix.

## Outcome

Pending.
