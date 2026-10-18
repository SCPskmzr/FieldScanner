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

- [x] Bash and PowerShell coverage tables have identical source/document pairs.
- [x] Both documentation self-checks pass on the real source tree.
- [x] Existing CI setup and executable-permission fixes remain intact.

## Out of scope

Application changes, SDK upgrades, device installation, unrelated local history.

## Relevant areas

scripts/check-documentation, scripts/check-documentation.ps1,
docs/modules/scanner.md, docs/modules/platform-and-build.md.

## Verification

- [x] `./scripts/verify.ps1 -BaseRef origin/master`: documentation, self-check, lint, unit tests, APK.
- [x] Bash documentation change gate and self-check; coverage table parity.
- [x] Independent diff review and acceptance criteria checked.
- GitHub CI is checked after push; no device installation for this CI-only fix.

## Outcome

- Added three Bash mappings for data/camera, data/diagnostics, and FieldScannerApplication.
- All 38 source/document pairs match PowerShell; module pages already describe them.
- Bash documentation gate and self-check: PASS. Full Windows task gate: PASS
  (`BUILD SUCCESSFUL`, lintDebug, testDebugUnitTest, assembleDebug; exit 0).
- Independent read-only verifier: PASS; no findings. git diff --check: PASS.
- Closing commit: `23152f05e2d6a24a9fce95cd5ab36b3121cf45fb`.
- Memory write-back: existing workflows memory already requires table parity;
  no new durable knowledge and no covered application-module changes.
- GitHub CI after push is not yet verified. Device installation is outside scope.
