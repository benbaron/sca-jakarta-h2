# Development workflow

## Container start

1. Run the repository bootstrap commands from `AGENTS.md`.
2. Confirm branch, remotes, Java, Maven, and working tree state.
3. Read `AGENTS.md`, `doc/PLAN.md`, and the selected phase required reading.
4. Inspect required implementation/test/migration files before editing.

## Branching and scope

- Work one selected phase and one selected slice at a time.
- If starting from `main`, create a focused `codex/<phase>-<slice>-<description>` branch.
- If a remote is unavailable in the container, record that limitation in the plan and PR notes and proceed from the provided current branch only when the working tree is clean.
- Never discard unexplained user work.

## Validation

- Establish a baseline before code phases, normally `mvn -DskipTests compile`.
- Run focused tests while developing.
- Before handoff, run `mvn clean verify` unless dependency/network limitations prevent Maven resolution.
- Run `git diff --check` and inspect the final diff.
- For documentation-only P00 changes, verify Markdown links/paths by script or targeted shell checks.

## Pull request handoff

Every implementation run should leave:

- selected phase/slice;
- branch;
- PR identifier or local PR placeholder when no remote exists;
- head commit;
- completed and remaining work;
- validation results and known failures;
- next exact action.

After creating or updating a PR version, run Maven tests again and report results. Perform a code review, report issues found, and offer to fix review or test issues.
