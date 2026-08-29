# P17-C12 — Documentation authority reconciliation owner verification

P17-C12 changes governing documentation only. It does not intentionally change Java production behavior, persistence, migrations, accounting calculations, or reachable commands.

## What changed

The governing documentation is reconciled to the production architecture already merged through P17-C11:

- one canonical Journal workspace; Ledger Register and Transaction Editor are compatibility aliases only;
- `ProductionWorkspaceWindow` is the production shell and the deprecated `MainWindow` owns no production commands;
- no Schedules destination and no generic Import/Export Jobs workflow;
- format-specific COA CSV/JSON, OFX/QFX/CSV, SCLX, and whole-database transfer boundaries;
- current reconciliation/cleared-state authority;
- current period-close range authority;
- current fixed-asset and inventory persistence/reporting authority;
- factual `AuditEvent` authority rather than a user-facing approval workflow;
- current JavaFX test expectations for the unified Journal and format-specific import/review flows.

## Owner checks

Because this slice is documentation-only, manual verification is a consistency check rather than a new product acceptance test.

1. Open the application and confirm there is one Journal destination and no separate Ledger Register or Transaction Editor navigation destination.
2. Confirm no Schedules or Import/Export Jobs destination is present.
3. Confirm File/Banking import actions route to the current format-specific preview/review surfaces rather than a generic job list.
4. In Journal, confirm bank state is displayed as read-only `Not bank`, `Uncleared`, `Cleared`, or `Mixed` as applicable.
5. Confirm reconciliation remains the workflow that changes matching/cleared state.
6. Confirm Period Close uses the current calculated/custom range workflow and the configured accounting-period start day.
7. Confirm fixed-asset and inventory history/report surfaces remain present as documented.
8. Confirm no Approval queue/workflow is exposed merely because historical approval records remain in the database.

## Regression boundary

If any owner check above disagrees with current production, do not rewrite the documentation to hide the disagreement. Record the live defect as a new corrective slice in `doc/PLAN.md` and keep P17-C12 limited to documentation authority.

## Automated validation

The final branch head must pass the repository Maven PR Tests, including clean verification, repeat tests, and production JavaFX route compliance. A green workflow proves that documentation reconciliation did not accidentally disturb production source; it does not substitute for the owner consistency checks above.
