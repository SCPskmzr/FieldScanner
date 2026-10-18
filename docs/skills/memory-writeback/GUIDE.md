---
name: memory-writeback
description: Finishing a task that uncovered durable, reusable project knowledge.
kind: skill
roles: [implementer]
---

# Memory write-back

Write-back is part of finishing a task, done in the same change as the code,
tests, and module docs — not deferred until the user asks.

## Decide whether to write

Write only if the knowledge is both **durable** (useful to a future agent, not
just this task) and **verified** against the repository.

Good candidates:

- non-obvious architectural constraints;
- reasons behind surprising implementation choices;
- important cross-module relationships;
- recurring debugging traps;
- commands or workflows that are difficult to discover;
- compatibility constraints;
- external-system behavior relevant to implementation;
- decisions that affect future engineering work;
- corrections to assumptions agents are likely to make again.

Do not store:

- secrets, credentials, API keys, tokens, or private user data;
- guesses or unverified hypotheses;
- information easily and reliably derivable from the current code;
- large logs or command output;
- transient implementation details or temporary task status;
- duplicated documentation;
- generic programming knowledge.

## Where it goes

1. Pick the matching topic file under `docs/agent-memory/` — `architecture.md`,
   `decisions.md`, `workflows.md`, or `gotchas.md`.
2. Add the new record as its own block at the END of that file, separated by a
   `---` line. **Append-only** (TASK-019): do not renumber, reformat, or rewrite
   existing records or the file preamble, so parallel-worktree merges stay
   conflict-free.
3. Update `docs/agent-memory/INDEX.md` only when a whole new topic file is
   added.

## Evidence

Every non-obvious record must carry enough evidence for another agent to
re-check it: source file paths, test names, configuration paths, migrations,
ADRs, spec/issue references, and a concrete verification command or steps. If
evidence cannot be found, mark the statement `unverified` rather than presenting
it as fact.

## Stale memory

If a record conflicts with the current repository, follow the repository and
correct or remove the stale record in the same task.
