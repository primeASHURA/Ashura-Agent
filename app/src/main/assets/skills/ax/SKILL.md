# AX — declarative agent orchestration

Source: google/ax (Apache-2.0, alpha). AX runs agent workloads at scale on Kubernetes through three
declarative primitives; the model transfers to any multi-task agent setup even without a cluster.

## Primitives

- **Task** (`ax.io/v1alpha1`, kind `Task`) — one agent run: goal text, model, workspace reference,
  optional `debug: true`. Tasks start Suspended; `ax resume` schedules and starts the actor.
  Lifecycle verbs: `ax apply`, `ax resume`, `ax suspend`, `ax describe`, `ax delete`.
- **Workspace** — pre-wired environment: git repos at a ref, injected files (for example
  `AGENTS.md`), MCP servers. Every task starts warm instead of cloning at runtime.
- **Model** — which LLM the platform uses, with credentials sourced from a Kubernetes secret rather
  than baked into the image.

## Operating model worth copying

1. Declare the task in YAML, not in a shell script: goal, workspace, model, image pinned by digest.
2. Keep state per task and checkpoint it: suspend pauses an idle agent, resume continues exactly
   where it stopped.
3. Inspect running work over `ax ssh <task> -- <cmd>` instead of guessing from logs.
4. Isolate untrusted agent code in a sandbox with CPU/memory limits; a runaway loop costs money.
5. Promote a task (`ax promote --to prod`) instead of recreating it by hand.
6. Pin container images by digest; tags drift and break reproducibility.

## Applying it here

- Treat each big request as a task: goal + workspace + model, with `AGENTS.md` as the injected
  instruction file.
- Prefer `STATE.md`-style checkpoint files so a suspended or crashed run can resume.
- Shell into the workspace (`shell_exec`, `ssh_exec`) to observe, rather than re-running blind.
- Budget the loop: stop when a run makes no state change (see the read-only streak guard).

## Defence line

Sandbox isolation plus per-task resource limits are what stop a prompt-injected task from touching
the host or burning unbounded tokens.
