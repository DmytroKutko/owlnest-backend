# Agent System Maintenance

## When to recalibrate

Re-run repository discovery and update the profile after a Spring/Java/Gradle upgrade, new feature root, new API/error convention, migration framework change, new Redis use, security model, integration, test framework, CI, or deployment target. Update evidence documents in the same change as the code/convention.

## Safe change sequence

1. Read root `AGENTS.md`, this guide, and official current Codex custom-agent/config documentation.
2. Record Git status and existing agent/skill files; preserve unrelated project configuration.
3. Update `docs/agent-system/project-profile.md` and affected convention doc from concrete code/migration/test evidence.
4. Update the narrow skill or agent prompt that owns the workflow; keep detailed prompts out of root `AGENTS.md`.
5. Preserve exactly the configured agent role set unless the team explicitly changes that contract. Agent names are API-like routing identifiers.
6. Do not pin `model` or `model_reasoning_effort`; agents inherit the active parent-session choice.
7. Keep read-only sandboxes for analysis/review. Grant workspace write only for the developer, test engineer, CI engineer, API/regression QA, and release verification roles, with source edit restrictions in instructions.
8. Run the validator, TOML parsing, skill frontmatter checks, project build/tests, diff checks, and full review.

## Codex compatibility

This bootstrap was checked against local `codex-cli 0.145.0-alpha.18` and the fetched official Codex manual. Required custom-agent keys are `name`, `description`, and `developer_instructions`; supported `sandbox_mode` is used. Project `.codex/config.toml` uses only `[agents].max_threads` and `max_depth`.

After a Codex upgrade, fetch current docs and run strict configuration startup where available. If a field becomes unsupported, remove or migrate it based on official documentation—do not guess new keys.

## Testing the system on a real feature

Choose a small, non-destructive feature idea and start a new Codex session so project config reloads. Verify:

1. informal wording routes to business and requirements gates;
2. only impact-relevant specialists are selected;
3. a new module triggers skeleton architecture review;
4. only one production writer is active;
5. independent review findings use the handoff contract;
6. fixes return to the writer and fresh QA reruns;
7. release is refused when mandatory evidence is missing.

Do not test orchestration by making an unneeded production change. A documentation-only dry run may stop after the technical package.

## Temporary simplification or disablement

- Simplify one task by explicitly asking the root to use the trivial route or named subset; retain mandatory security/data/release gates when impact requires them.
- Temporarily disable automatic routing by moving the orchestration section to `AGENTS.override.md` with a clearly dated minimal replacement, then remove the override to restore normal behavior. Do not delete agent/skill files casually.
- Project `.codex` layers load only for trusted repositories. Untrusting the repository disables project-local config, agents, and related project layers; root `AGENTS.md` may still be discovered separately by clients.
- To reduce concurrency, lower `agents.max_threads` with a documented machine/repository reason; keep `max_depth = 1`.

Always state that newly edited project instructions take effect reliably in a new session.
