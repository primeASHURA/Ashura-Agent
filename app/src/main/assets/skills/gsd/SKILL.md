# GSD — spec-driven phase loop

Source: open-gsd/gsd-core (the original gsd-build/get-shit-done repository now redirects here).
GSD attacks context rot by running research, planning and execution in fresh subagents while the
main session stays small, and by persisting state in files that survive session boundaries.

## The loop (one phase at a time)

1. **Discuss** — settle implementation decisions before planning. Write them to `CONTEXT.md`.
2. **Plan** — research, decompose into tasks that each fit a fresh context window, then verify the
   plan is small enough. Write it to `PLAN.md`.
3. **Execute** — run plan tasks in parallel waves; each executor starts with a clean context and
   only its plan slice plus the files it needs.
4. **Verify** — walk the built result, reproduce the flow, and produce a fix plan for what fails
   before declaring the phase done.
5. **Ship** — open the PR/commit, archive the phase state, move to the next phase.

## State files

- `STATE.md` — current phase, what is done, what is blocked, decisions already made.
- `CONTEXT.md` — decisions from the discuss step, constraints, rejected options.
- `PLAN.md` — ordered tasks with acceptance checks per task.

Read them at the start of every session; append, never rewrite history. A fresh session must be
able to continue from these files alone.

## Rules that make it work

- One phase per run. Do not start the next phase before verify passes.
- Every task has an explicit acceptance check; "looks done" is not a check.
- Keep the orchestrator context lean: delegate bulk reading and bulk writing to subagents and keep
  only summaries in the main thread.
- If a phase fails verification, write the fix plan before editing anything.
- Persist decisions immediately; a decision that lives only in chat is lost.

## When to apply

Multi-step features, refactors across many files, migrations, anything that would otherwise run
longer than one context window. Skip the ceremony for a one-file fix.
