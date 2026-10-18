---
name: module-doc-sync
description: A change touches a source area that a docs/modules/ page covers.
kind: skill
roles: [implementer]
---

# Keep module docs in sync

The documentation gate (`scripts/check-documentation` /
`scripts/check-documentation.ps1`, run by `scripts/verify`) maps every source
area under `app/src/main/` and every root Gradle file to exactly one
`docs/modules/` page through its coverage table (`$coverage` / `coverage=(...)`).
Prefix matching is case-sensitive.

1. From your diff, list the changed paths under `app/src/main/` and the root
   Gradle files.
2. For each, find its `docs/modules/*` page in the coverage table in
   `scripts/check-documentation.ps1`.
3. In every matched page, rewrite the `##` sections whose subject your change
   affected so the prose matches the code again — new behavior, renamed types,
   changed flow, removed features.
4. The gate only checks that the page file changed in the same diff. A cosmetic
   edit that does not reflect the code is a violation of this procedure, not a
   way around the gate. Do not weaken the gate to pass.

If a change adds a new top-level package or resource directory with no coverage
rule, the completeness self-check (`--self-check` / `-SelfCheck`) fails until the
area is either documented (a new coverage entry) or explicitly excluded with a
reason in the script.
