# P21 — Activity and Event Accounting

## Purpose

P21 makes the existing company-owned `Activity` dimension usable as production master data and then provides a read-only event/accounting workspace over canonical Journal transactions linked through `TxnSplit.activity`.

This phase extends existing H2/JPA authority. It does not introduce an event ledger, event posting model, schedule subsystem, approval workflow, or parallel persistence.

## Current-main findings

Current `main` already provides the durable accounting foundation:

- `Activity` is a company-owned H2/JPA entity with stable database ID, company-scoped unique code, name, and active state.
- `TxnSplit.activity` is the canonical optional transaction-line relationship for an event, project, or occasion.
- `TransactionEntryService` validates company ownership when a Journal line selects an activity.
- `TransactionReferenceDataService` already supplies active activities to the Journal editor.
- SCLX exports/imports activities and preserves activity-linked transaction data.
- Sample-company and SCLX paths can create activities, but current production JavaFX has no operator-facing Activity maintenance service/editor.
- `AppPanelId`, `PanelFactory`, and `NavigationPane` expose no Activity or Event Accounting production destination.

Therefore the first missing vertical behavior is not another accounting model. It is governed Activity master-data maintenance. Event reporting follows only after that authority is operator-manageable.

## Donor assessment

`benbaron/NonprofitAccounting` contains a useful `EventAccountingService`/`EventAccountingPanel` concept that summarizes Activity-linked transactions into income, expense, net, deposits/refunds, linked transaction rows, and closeout checklist items.

The donor is reference only. Do not copy it blindly:

- the current repository's canonical transaction model and company ownership rules win;
- the donor's use of `AccountType.BANK` is obsolete for this repository;
- current banking classification is `AccountType.ASSET` plus `AccountFunction.BANK`, with ordinary deposit accounts normally using `AccountSubtype.CASH`;
- event/accounting calculations must read current canonical `Txn`/`TxnSplit`, not donor/legacy records;
- any Journal drill-through must use the existing production routing/coordinator rather than create a second editor;
- P21 must use the P20 authorization model at both JavaFX presentation and authoritative service mutation boundaries.

## Product decisions

1. **Activity remains the sole event/project/occasion accounting dimension.** Do not add an `Event` entity merely to support a workspace label.
2. **Stable ID is identity.** Activity code and name are editable business data; code remains unique within a company.
3. **Lifecycle is deactivate/reactivate, not destructive deletion of used history.** A never-used Activity may only be physically deleted if current repository lifecycle conventions and referential evidence make that safe; otherwise the UI must visibly explain why physical deletion is unavailable. S1 must make this decision explicit from current usage evidence before implementing a Delete action.
4. **Activity maintenance requires `BOOKKEEPING_WRITE`.** VIEWER remains read-only. ACCOUNTANT, MANAGER, and ADMIN follow the fixed reserved-role permission union already established by P20.
5. **Reads remain company-scoped.** No cross-company Activity list, edit, or event summary is permitted.
6. **Event Accounting is a read model.** It calculates from canonical Journal data and does not create, post, approve, reconcile, or close accounting transactions.
7. **Bank/deposit classification uses current banking authority.** Event cash/deposit/refund presentation must identify canonical bank ledger accounts through the existing account/bank classification services or equivalent current model evidence; it must not resurrect `AccountType.BANK`.
8. **Budget Category remains separate from Activity.** P21 must not merge event/project identity with budget classification.
9. **SCLX authority is preserved.** Activity administration must remain compatible with existing company-scoped SCLX activity identity and imports/exports; no second portable-identity scheme is introduced.
10. **No schema change is assumed.** Add a migration only if implementation inspection proves the current `activity` schema cannot satisfy the adopted lifecycle contract.

## P21-S1 — Activity administration

### Goal

Provide complete durable production maintenance for the existing company-owned Activity master data so operators can create and maintain the same Activity choices already consumed by Journal entry and SCLX.

### Required reading

Before implementation read:

- root `AGENTS.md`;
- `doc/PLAN.md`;
- this document;
- `doc/interface-operation-matrix.md`;
- `doc/ui_design_rules.md`;
- `doc/ui/editor-guidelines.md`;
- `doc/accounting/transaction-lifecycle.md`;
- `doc/data-exchange/sclx.md`;
- `doc/P20-S1-authentication-authorization-boundary.md`;
- `doc/P20-S3-runtime-authorization.md`;
- `doc/P20-S3-javafx-permission-gating.md`.

### Required inspection

Inspect current `main` before design or edits:

- `Activity`, `TxnSplit`, `Txn`, `BudgetCategory`, and company ownership mappings;
- all Flyway migrations affecting `activity` and activity foreign keys/uniqueness;
- `TransactionEntryService` and `TransactionReferenceDataService`;
- `CompanyOwnershipService` and current durable-record admin service patterns (`FundAdminService`, `AccountAdminService`, `BudgetCategoryService` or their current equivalents);
- SCLX activity snapshot, preview, portable-identity, and commit paths;
- `WorkspaceServices`, `UiServiceRegistry`, `PanelFactory`, `AppPanelId`, `NavigationPane`, `ProductionWorkspaceWindow`, and `UiPermissionGate`;
- current production table-state/compliance tests and JavaFX source-route tests;
- donor `NonprofitAccounting` Activity/Event Accounting classes only as reference after the current repository inspection.

### Implementation contract

S1 should normally include:

- a typed Activity command/view model using stable database ID for update identity;
- a company-scoped Activity administration service with query plus create/update and governed lifecycle operation(s);
- validation for nonblank code/name, company ownership, company-scoped code uniqueness, and current lifecycle/reference constraints;
- central `BOOKKEEPING_WRITE` authorization on service-owned mutations, current authenticated actor usage for any factual audit write required by established lifecycle rules, and durable denial facts through the existing guard;
- production JavaFX maintenance using the existing panel/editor design rules, table-state ownership, dirty-state and global New/Save semantics;
- a real lifecycle control or a visible factual explanation when physical deletion is not allowed;
- production composition through existing `WorkspaceServices`/`UiServiceRegistry`/`PanelFactory` authority;
- immediate refresh of the same H2 Activity choices consumed by newly opened/refreshed Journal editing without a parallel cache;
- interface-operation-matrix and governing-doc updates describing the reachable production behavior;
- focused JUnit/H2 and JavaFX/source-route tests.

### Explicit exclusions

S1 does not implement event income/expense summaries, event closeout, event-specific posting, donor management, approval queues, attachments, or a replacement Journal editor.

### Completion gate

S1 is complete only when:

- Activity create/update/lifecycle behavior operates on stable IDs in the active company;
- duplicate/cross-company/invalid writes fail atomically;
- used Activity history is preserved under the adopted lifecycle rule;
- direct VIEWER service mutation fails closed and authorized roles succeed according to the fixed P20 policy;
- company/session switching changes authorization immediately without stale state;
- JavaFX exposes truthful New/Save/lifecycle behavior and conforms to the current UI design rules;
- Journal reference data reflects active Activity maintenance through the existing H2 query path;
- existing SCLX Activity import/export behavior remains compatible;
- relevant focused tests plus exact-head Maven PR Tests and production JavaFX route compliance pass;
- owner desktop acceptance is recorded before S1 is marked DONE.

## P21-S2 — Event Accounting workspace

### Dependency

Blocked until P21-S1 is merged and accepted. S2 may not create a substitute Activity maintenance authority.

### Goal

Provide a read-only production workspace for one Activity/event/project/occasion using canonical posted Journal data.

### Planned behavior

The workspace should provide, subject to implementation inspection:

- active/inactive Activity selection and factual identity/status display;
- income, expense, and net totals calculated from Activity-linked canonical splits using current account classification;
- linked Journal transaction rows with date, payee/counterparty where available, memo, account, fund, and amount context sufficient to explain totals;
- bank/deposit/refund presentation based on canonical configured bank/account classification, not `AccountType.BANK`;
- drill-through to the existing Journal destination for a selected transaction;
- a factual closeout checklist that reports review state without inventing a posting, approval, reconciliation, or period-close authority;
- company/date/fund scoping only where current reporting/period semantics can be reused without creating a second date-range authority;
- read access for VIEWER and the other reserved roles under the established P20 permission policy.

### Required inspection before S2 design

In addition to the S1 required reading, inspect:

- current semantic/report query services and account-classification helpers;
- current Banking/Bank Transactions query classification and configured bank-account authority;
- Journal drill-through coordination;
- active period/report explicit-date semantics;
- donor `EventAccountingService` and `EventAccountingPanel` as reference only.

### Completion gate

S2 is complete only when its totals reconcile to canonical Activity-linked Journal splits, bank/deposit identification uses the current `ASSET + BANK function` model, drill-through reaches the existing Journal workflow, no durable accounting state is mutated by the workspace, full tests/CI pass, and owner desktop acceptance is recorded.

## Candidate later work deliberately not activated

The donor repository also contains donor/receipt and monthly-close concepts. They remain candidates for a future deliberately selected phase or slice. P21 does not silently import them, and this document does not establish them as committed scope.
