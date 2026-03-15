# Next Codex Instance Prompt

Continue from `docs/progress-report-next-pass.md` section **132**.

## Primary objective
Execute the remaining **Phase 1** follow-ups before any Phase 2 work:
1. Add an explicit run command contract for active panels (avoid generic save dispatch for Post/Validate).
2. Upgrade Search from snapshot-only to query/filter + panel-jump behavior.
3. Improve Journal inspector to prefer active selection context and gracefully fall back to most recent transaction.

## Implementation constraints
- Keep behavior deterministic and testable.
- Avoid placeholder copy in newly touched flows.
- Prefer in-panel/inspector feedback over modal alerts for primary workflows.

## Testing requirements
- Add or update focused tests for each changed behavior.
- Run `mvn -B -ntp test` and report results.
- If Maven/network blocks execution, report exact blocker and still summarize added/updated tests.

## Process requirements
- Update `docs/progress-report-next-pass.md` with a new numbered entry describing implementation details.
- Commit changes on the current branch.
- Call `make_pr` after commit with concise title/body.

## Final response requirements
- Summary with file citations.
- Testing section with ✅/⚠️/❌ command prefixes.
- Brief code review and offer to fix follow-up issues.
