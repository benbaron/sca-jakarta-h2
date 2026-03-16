# TODO / Next Stages

## Stage A — Reliability + Build Execution

- Resolve Maven plugin bootstrap in restricted environments so CI/local tests run without external network dependence.
  - Preferred: pre-seeded plugin cache + reproducible local repo bootstrap script in CI.
  - Secondary: approved internal mirror settings profile.
- Add CI guard to fail fast with explicit diagnostics when plugin/artifact endpoints are unreachable.

## Stage B — Operational Workflow Persistence

- Promote UI runbook logs to first-class persisted domain records (DB tables + repository APIs).
- Add query views for lifecycle history by account/fund/workflow.
- Support runbook export (CSV/JSON) from each operational panel.

## Stage C — Privilege and Security Hardening

- Extend privilege model from coarse shell gating to panel action-level authorization checks.
- Add role-aware visibility in menus/nav (hide restricted actions where appropriate).
- Add audit events for denied actions and privileged transitions.

## Stage D — Interaction and Regression Testing

- Expand JavaFX interaction tests for:
  - gated menu/toolbar state transitions,
  - runbook action flows,
  - search -> jump -> inspector coherence,
  - import/export multi-panel traces.
- Add deterministic persistence tests for runbook/budget files with test-isolated paths.

## Stage E — Domain-service integration

- Connect operational runbook actions to domain services:
  - schedules/open-items lifecycle transitions,
  - depreciation posting workflow,
  - inventory movement with optional posting templates.
- Add end-to-end validation from action -> service -> projection -> inspector.

## Roadmap status snapshot

- [x] Explicit panel run-command contract and active selection context
- [x] Search query/filter + panel jump + journal context improvements
- [x] Phase 2/3 panel additions (approval/import-export/bank transactions)
- [x] Phase 4 operational runbooks and budget/report wiring
- [x] Phase 5 shell privilege gating + shared inspector presentation model
- [ ] Persistent domain-backed runbook/event history
- [ ] Fully executable Maven test pipeline in this restricted environment
