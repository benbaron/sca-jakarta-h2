---
plan_version: 71
active_phase: P15
active_slice: P15-S0
active_status: IN_PROGRESS
active_branch: codex/P15-S0-data-exchange-plan
active_pull_request: 186
active_head: bb71c84aca21adeef90564b3e4da5a237f7a0181
next_action: "Review and merge the P15-S0 planning contract, then begin P15-S1 from the resulting current main on a fresh branch."
---

# SCA Bookkeeping Program — Codex Execution Plan

## 1. Purpose

This document is the phase controller for Codex work in `benbaron/sca-jakarta-h2`. Codex must select one phase and one slice using `AGENTS.md`, execute only that scope, and update this file with actual state.

This revision records P14 as complete and activates documentation-only P15-S0 to govern versioned SCLX, Chart of Accounts JSON, database transfer, and OFX 2.x/QFX/CSV bank-record interchange.

## 2. Status values

- `BLOCKED`
- `READY`
- `IN_PROGRESS`
- `VERIFYING`
- `DONE`
- `ELIMINATED`

Only merged and verified behavior is `DONE`. `ELIMINATED` means the former phase or function is no longer part of the product plan and must not be reintroduced without a new requirements decision.

## 3. Phase index after requirements clarification

| Phase | Name | Depends on | Status |
|---|---|---|---|
| P00 | Documentation and implementation inventory | none | DONE; update matrices as touched |
| P01 | Production shell and workspace composition | P00 | DONE; corrective P01-C1 DONE through PR #141 |
| P02 | Canonical ledger and transaction operations | P00 | DONE; retain |
| P03 | Journal workspace and canonical transaction operations | P01, P02 | DONE through corrective P03-C9 / PR #154 |
| P04 | Persistent budgeting | P02 | DONE; retrofit as touched |
| P05 | Banking configuration and statement import | P02, P03-C1 | DONE through PR #137; corrective P05-C5 DONE through PR #148 |
| P06 | Bank reconciliation and cleared-state comparison | P05 | DONE through PR #138; corrective P06-C1 DONE through PR #146; corrective P06-C2 DONE through PR #147 |
| P07 | Eliminated former Schedules phase | n/a | DONE through PR #139 |
| P08 | Asset Register and depreciation | P02 | DONE through PR #140; corrective P08-C1 DONE through PR #144 |
| P09 | Inventory and supplies | P02 | DONE through PR #142; corrective P09-C1 DONE through PR #143 |
| P10 | Period close, reopening, and factual audit history | P02, P06 | DONE through P10-S1 / PR #156 and P10-C1 / PR #157 |
| P11 | Report Library | P02, P04, P06, P08, P09, P10 | DONE through P11-S1 / PR #158 |
| P12 | Administration, company lifecycle, preferences, and Funds edit | P01, P02 | DONE through P12-S1, P12-S2, P12-S3, P12-C1, P12-C2, and P12-C3 |
| P13 | Data exchange and diagnostics without Import/Export Jobs | P02, P05, P12 | DONE through P13-S1 / PR #177 and P13-S2 / PR #179 |
| P14 | End-to-end hardening | P03-P13 except eliminated P07 | DONE through P14-S1, P14-S2, P14-S3, P14-S4, and P14-C1 |
| P15 | Versioned data interchange and database transfer | P02, P05, P06, P12, P13, P14 | IN_PROGRESS at P15-S0 |

## 4. Governing documents

Always read:

- `AGENTS.md`
- `doc/PLAN.md`

Focused documents for current UI/accounting work:

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/requirements/requirements-clarification-overlay.md`
- `doc/requirements/phase-remap-after-clarification.md`
- `doc/accounting/ledger-authority.md`
- `doc/accounting/transaction-lifecycle.md`
- `doc/accounting/period-and-correction-policy.md`
- `doc/reporting/report-library.md`
- `doc/administration/fund-lifecycle.md`
- `doc/administration/company-lifecycle.md`
- `doc/workflow/development-workflow.md`

`doc/architecture/production-workspace.md` was removed. Do not re-add it or list it as required reading.

## 5. Established product decisions after clarification

- One production JavaFX application.
- H2 is authoritative for accepted operational/accounting data.
- Existing JPA/Hibernate model and Flyway migrations are the schema foundation.
- No parallel ledgers, budget stores, import stores, or panel frameworks.
- Write services own validation and transactions.
- Query services return projections for panels and reports.
- Constructor injection is preferred.
- Left Navigation under Accounting exposes one Journal destination rather than separate Ledger Register, Transaction Editor, and Inspect Journal destinations.
- Left Navigation must not include Schedules.
- Left Navigation must not include Import/Export Jobs as a separate function.
- No approval queue or formal approval/rejection workflow.
- Reconciliation approve/reject semantics are replaced by saved comparison/reconciliation state.
- Notes and factual audit history are in scope.
- Reports belong in `REPORT_LIBRARY`.
- The former Schedules function is eliminated.
- Import/Export Jobs is eliminated as both a panel and generic durable job-tracking function.
- Fixed assets are distinct from inventory and require H2-backed asset/depreciation records.
- Inventory/supplies are distinct from fixed assets and require H2-backed item/movement records.
- Production widgets with visible non-blank display text should expose their full text on hover, except text boxes and custom-help tooltip cases.
- Disabled placeholder Delete buttons are not part of the UI contract; a Delete button must perform a real supported operation.
- Bank Reconciliation must be a full statement-to-ledger matching workspace, not only a saved comparison table.
- Transaction supplemental schedule/detail panels are per-transaction detail editors and viewers, not the eliminated generic Schedules function.
- Period close uses calculated or custom date ranges rather than an accounting-period table as the business authority.
- Reopening is supported and creates factual audit history; P10 must not expose approval/rejection semantics.
- The desktop JPA bootstrap explicitly selects the Hibernate provider configured by `persistence.xml`; it does not rely on launcher-sensitive Jakarta Persistence service discovery.
- Report preview, export, and drill-through use one immutable validated report request.
- Visible report dates and money follow active-company preferences; machine CSV remains unadorned and stable.
- Funds are edited by stable database ID. Referenced funds are retained and deactivated; physical deletion is limited to unreferenced funds after explicit confirmation.
- The stable `SETTINGS` workspace destination hosts the Administration workspace; Preferences, Company Admin, and User Admin are tabs rather than separate shell identifiers.
- Company records in H2 are authoritative for company existence and active/inactive lifecycle; shell recent-company state may remember selections but must not create fictional companies.
- Companies and funds are deactivated rather than hard-deleted when referenced by accounting or operational records.

## 6. Completed phases and slices

### P00 — Documentation and implementation inventory

Status: DONE, with clarification updates as touched.

### P01 — Production shell and workspace composition

Status: DONE, retrofit as touched.

Corrective slices:

- P01-C1 Full-text hover tooltips for production widgets: DONE through PR #141.

### P02 — Canonical ledger and transaction operations

Status: DONE, retain.

### P03 — Journal workspace and canonical transaction operations

Status: DONE through corrective P03-C9 / PR #154.

Completed slices:

- P03-C1 Transaction Editor modes and Ledger Register buttons: DONE.
- P03-C2 Journal Pane and Inspect Journal navigation: DONE.
- P03-C3 Transaction Editor Delete correction action: DONE.
- P03-C4 Transaction Editor and Journal Pane redesign: DONE through PR #149.
- P03-C5 Persisted Transaction Editor supplemental details: DONE through PR #150.
- P03-C6 Unified Journal workspace port: DONE through PR #151.
- P03-C7 Journal UI design-rule compliance: DONE through PR #152.
- P03-C8 Journal compliance cleanup and verification: DONE through PR #153.
- P03-C9 Remove visible Journal table commentary: DONE through PR #154.

Known remaining P03 limitation:

- `TransactionView.Line` does not yet expose authoritative line-level cleared state. The Journal must not claim authoritative mixed cleared/uncleared transaction detail until that projection is added.

### P10 — Period close, reopening, and factual audit history

Status: DONE through P10-S1 / PR #156 and corrective P10-C1 / PR #157.

#### P10-S1 — Calculated period close and reopen service

Branch: `codex/P10-S1-period-close-implementation`  
Pull request: #156, merged into `main` at `fc9e8ddb5bb2583ec744ff7fe6e9ce7ba07a5e8a`  
Tested implementation head: `2462a591de7965d69c8909991443011665daba8a`

Completed deliverables:

- Added H2-backed `period_close_range` and `period_close_event` records.
- Added company-scoped close, overlap validation, list, lookup, reopen-policy, and factual event/audit operations.
- Enforced authoritative close ranges inside canonical transaction entry and correction services.
- Replaced run/approval controls with calculated/custom Close Range, Reopen Selected, Refresh, range-state, and factual-history controls.
- Preserved completed-reconciliation protection and open-destination reversal behavior.
- Added focused persistence, service, correction, migration, UI, and documentation coverage.

#### P10-C1 — Explicit desktop JPA provider bootstrap

Branch: `codex/P10-C1-explicit-jpa-provider`  
Pull request: #157, merged into `main` at `78cd26748181af9665229318231c5bf8ae4a7d0c`  
Tested head: `0d82d960d2a47529eb5883fe8ccf388eb8bc2551`

Completed deliverables:

- Replaced launcher-sensitive provider discovery with explicit `HibernatePersistenceProvider` bootstrap.
- Retained `META-INF/persistence.xml`, `scaLedgerPU`, RESOURCE_LOCAL behavior, migrations, and JDBC overrides.
- Added focused missing-provider diagnostics and a provider-resolver regression test.
- Maven PR Tests runs `29177308667` and `29177357943` passed.
- The user directed that the slice be marked DONE.

Known P10 follow-up:

- `AccountingPeriod`/`AccountingPeriodService` and legacy period-close run artifacts remain compatibility structures, not close authority.
- `REQUIRE_FORMAL_ADJUSTMENT` blocks direct reopen; a specialized formal-adjustment workflow remains a later slice.

### P11 — Report Library

Status: DONE through P11-S1 / merged PR #158 and owner verification.

#### P11-S1 — Typed report catalog and parameters

Branch: `codex/P11-S1-report-catalog-parameters`  
Pull request: #158, merged into `main` at `eb0ff9a2769d9935ba2ba89a74152dc6a8ad57f7`  
Tested head: `4f779e64b7ce8ec8d64a748c9b513a3b983463bf`

Completed deliverables:

- Added typed report definitions, immutable validated requests/results, fund selection, report-specific dates, and conditional row limits.
- Reused one request/result for preview, export, and Journal drill-through.
- Added active-company visible formatting while preserving stable machine CSV.
- Preserved TEXT, CSV, PDF, and XLSX export.
- Added company-owned divider state and focused tests/documentation.
- Maven PR Tests run `29179022737` passed on the final head.
- PR #158 merged on 2026-07-13.
- The owner verified the desktop/laptop-width Report Library behavior.

## 7. P12 — Administration, company lifecycle, preferences, and Funds edit

Status: DONE through P12-S1, P12-S2, P12-S3, P12-C1, P12-C2, and P12-C3 with owner desktop acceptance.

### P12-S1 — Stable-ID Funds editing and lifecycle rules

Status: DONE through merged PR #159, corrective PR #171, and owner desktop acceptance.

Branch: `codex/P12-S1-fund-lifecycle-rules`  
Pull request: #159, merged into `main` at `affbae227b9751d9f9caad9cd301656c0ac640e7`  
Tested head: `93618fb368b0c50175a912e502e316a8b17ddb94`

Completed deliverables:

- Added stable-ID create/update for code, name, type, active state, parent, effective dates, and restriction text.
- Added uniqueness, field-length, date-order, parent-existence, self-parent, and cycle validation.
- Added authoritative usage assessment across transaction splits, budgets, fixed assets, inventory, aliases, transfers, and child funds.
- Added protected `deleteUnused(...)`; referenced funds are retained and deactivated.
- Added split table/editor UI, company-aware dates, dirty-state handling, real New/Save/Delete Unused/Refresh actions, and company-owned table/divider state.
- Added integration tests, UI source guardrails, `doc/administration/fund-lifecycle.md`, and focused matrix/inventory updates.
- Maven PR Tests runs `29224690525`, `29224900193`, and `29224988928` passed.
- The owner created a Fund, changed its code, and verified that one stable row remained, the old code was unavailable, no duplicate was created, and all persisted values reloaded after reopening Funds.
- The owner verified parent selection, effective dates and company formatting, restriction text, active/inactive display, table sorting, resizing, reordering and scrolling, and company-owned divider and table-state restoration.
- The owner verified that referenced Fund deletion was blocked with reference details and deactivation guidance, that deactivation retained the referenced row, and that unused Fund deletion required explicit confirmation and honored cancellation.

Next exact action:

- None; P12-S1 is DONE.

### P12-C3 — Funds horizontal divider correction

Status: DONE through merged PR #171 and owner desktop acceptance.

Branch: `codex/P12-C3-funds-horizontal-split`
Pull request: #171, merged into `main` at `b199f3e66d736d8f4e743fe61199b0f1683eacf9`
Base head: `34a28f513d2116878d03a43d3b0fffbf8e42fda7`
Implementation head: `e307633189c94a4f15428044965b76af83341ed8`

Purpose: correct the Funds center workspace discovered during laptop-width desktop validation so the fund table and editor are stacked top/bottom and separated by a horizontal draggable divider.

Planned deliverables:

- Set the existing Funds `SplitPane` to vertical item orientation, producing a horizontal draggable divider.
- Preserve the stable-ID table/editor workflow, independent editor scrolling, and company-owned divider persistence.
- Add a focused source/layout guardrail and update the Fund lifecycle documentation.
- Run the full Maven PR Tests workflow and leave the correction VERIFYING until desktop confirmation and merge.

Completed deliverables:

- Set `fundsWorkspaceSplit` to vertical item orientation so the divider runs horizontally and the fund table is above the editor.
- Allowed both split items to shrink while preserving table scrolling, the independent editor `ScrollPane`, and existing company-owned divider persistence.
- Added a focused Funds panel source guardrail and documented the top/bottom workspace behavior.
- Local Maven validation was unavailable because the container has neither Maven nor a Maven wrapper; GitHub Maven PR Tests is authoritative for this correction.
- Maven PR Tests run `29469588821` passed on handoff head `e14f778594c76f277230f37ac49f36638099b3b1`.
- Final Maven PR Tests run `29469691118` passed on PR head `ec2b949793a66f3322a598dfbdb4fbbdedd58496`.
- The owner verified on current `main` that the table is above the editor, the horizontal divider is draggable and restored, and both regions remain usable at laptop width.

Next exact action:

- None; P12-C3 is DONE.

### P12-S2 — Administration workspace hub

Status: DONE through merged PR #160 and owner desktop acceptance.

Branch: `codex/P12-S1-administration-navigation`  
Pull request: #160, merged into `main` at `04966951a68f2e594ad1bfa289c8026840e9dbd0`  
Tested implementation head: `bc9b65c06e31f6d71032d8ac0f05d6c7a85a262b`

Completed deliverables:

- Added `AdministrationPanel` with Preferences, Company Admin, and User Admin tabs.
- Routed stable `AppPanelId.SETTINGS` to the Administration hub.
- Renamed the visible navigation destination while preserving saved destinations and command-palette compatibility.
- Delegated global Save, New, and dirty-state behavior to the selected tab.
- Preserved existing H2-backed company, user, role-assignment, and preference services.
- Added focused source guardrails and updated the operation matrix.
- Maven PR Tests runs `29259566867` and `29259761711` passed.
- The owner verified at laptop width that Preferences, Company Admin, and User Admin loaded through the single Administration destination, that global Save/New delegation reached the selected tab where supported, and that no separate Company Admin or User Admin shell destination exists.

Next exact action:

- None; P12-S2 is DONE.

### P12-C1 — Reconcile overlapping P12 slice records

Status: DONE through merged PR #162.

Branch: `codex/P12-C1-reconcile-plan-ledger`  
Pull request: #162, merged into `main` at `fa6b94a67c9f6aa2d6d79e1de77b257240c49a42`

Purpose: repair the execution ledger after PRs #159, #160, and #161 reused `P12-S1` and overwrote one another’s plan records.

Deliverables:

- Preserve PR #159 as P12-S1 and PR #160 as P12-S2.
- Record PR #161 as a merged plan-only activation; it did not implement company lifecycle.
- Restore the phase-contract tail removed by PR #161.
- Assign unimplemented company lifecycle work to P12-S3.
- Make no product-code changes.

### P12-S3 — Company lifecycle and active-company authority

Status: DONE through merged PR #163, corrective PR #164, and owner desktop acceptance.

Branch: `codex/P12-S3-company-lifecycle`
Pull request: #163, merged into `main` at `b08a0a29755d0d6a5e19fb152798c4d5e6eb4784`
Tested implementation head: `f1cf7333b981aa766498fd725853ecc5ccf187d1`

Purpose: make H2 company rows authoritative for company creation, editing, activation/deactivation, and active-company selection through the existing Administration hub.

Required reading:

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/requirements/requirements-clarification-overlay.md`
- `doc/requirements/phase-remap-after-clarification.md`

Required inspection:

- `Company`, `CompanyTaxProfile`, `ChartOfAccounts`, and company-related migrations.
- `CompanyAdminService`, `CompanyAdminPanel`, `AdministrationPanel`, `CompanyWizardDialog`, `MainWindow`, `UiSessionState`, `WorkspaceContext`, `PanelFactory`, and `UiServiceRegistry`.
- Current company/admin tests and donor company-administration UI only as a design reference.

Planned deliverables:

- Use the existing Administration hub and Company Admin tab; do not add a redundant shell destination.
- Replace sidecar-only Add Company and Company Wizard creation with service-backed H2 creation and validation.
- Persist active state, fiscal-year start, default currency, and other supported company profile fields in one service transaction.
- Permit active-company selection only for an existing active H2 company and propagate it through session/workspace context and service composition.
- Deactivate rather than hard-delete companies; prevent deactivating the current company or leaving no active company without an explicit switch.
- Remove or defer enabled non-persistent placeholder tabs and controls.
- Apply UI design rules, company-owned layout state, dirty-state/discard protection, focused service/UI tests, and governing-document updates.

Completed deliverables:

- Added stable-ID H2 company create/edit/deactivate transactions with complete supported profile persistence and active-company invariants.
- Reconciled persisted recent-company convenience state against active H2 rows, so fictional or inactive codes cannot become the workspace company.
- Added the production toolbar selector and refreshed open database-bound panels after guarded active-company changes.
- Preserved the `SETTINGS` Administration hub while reducing Preferences to preferences and replacing Company Admin placeholders with one real split table/editor workflow.
- Added service integration coverage, restart/sidecar authority coverage, JavaFX source guardrails, and `doc/administration/company-lifecycle.md`.
- Maven PR Tests runs `29305022115`, `29305144314`, and `29305406957` passed; the last run is on the tested implementation head.
- The owner created a company with every supported profile field and verified that the same authoritative H2 profile reloaded after application restart.
- The owner renamed the current company code and verified that the toolbar, Administration workspace, Inspector and open-workspace context, and restart selection followed the same stable company record; the old code was no longer selectable and no duplicate company row was created.
- The owner verified that cancelling an active-company switch preserved the current company and unsaved workspace edits without refreshing panels, while approving the switch changed the active company and refreshed open workspaces without retaining stale company data.
- The owner deactivated a non-current company and verified that its authoritative row remained inactive while disappearing from active-company selectors and recent-company selection; attempts to deactivate the current or last active company were blocked.
- The owner verified Company Admin table sorting, resizing, reordering, horizontal and vertical scrolling, company-owned divider restoration, full-text tooltip behavior, and dirty-state prompts for every tested discard path.

Next exact action:

- None; P12-S3 is DONE.

### P12-C2 — Company Admin horizontal divider correction

Status: DONE through merged PR #164 and owner desktop verification.

Branch: `codex/P12-C2-company-admin-horizontal-split`
Pull request: #164, merged into `main` at `c59c11c25bc7c77841da0a8b77822404a358646a`
Tested implementation head: `1dafac8b6cfa0755eed1c8a2590da49cb60163ad`

Purpose: correct the Company Admin center workspace discovered during laptop-width desktop validation so the company table and profile editor are stacked top/bottom and separated by a horizontal divider.

Planned deliverables:

- Set the existing Company Admin `SplitPane` to vertical item orientation, producing a horizontal draggable divider.
- Preserve the existing table/editor workflow, scrolling, company-owned divider state, and Administration hub.
- Add a focused source/layout guardrail and update the lifecycle documentation.
- Run the full Maven PR Tests workflow and leave the correction VERIFYING until desktop confirmation and merge.

Completed deliverables:

- Set the existing `companyAdminWorkspaceSplit` to vertical item orientation so its divider runs horizontally and the table is above the editor.
- Preserved independent editor scrolling, table growth, company-owned divider persistence, and the existing Administration destination.
- Added a focused orientation guardrail and documented the top/bottom laptop-width layout.
- Maven PR Tests runs `29372548918` and `29372653080` passed; the last run is on final handoff head `ef6fb259a69fd03ce4ae39a2b612f07dddb07e08`.
- The owner verified the corrected laptop-width top/bottom layout and horizontal draggable divider after merge.

Next exact action:

- None; P12-C2 is DONE.

## 8. Active and recent phase contracts

# P13 — Data exchange and diagnostics without Import/Export Jobs

**Selector:** `PHASE=P13`
**Status:** DONE through P13-S1 / PR #177 and P13-S2 / PR #179
**Depends on:** P02, P05, P12

Purpose: preserve useful data exchange, import review, and diagnostics while eliminating the prohibited generic Import/Export Jobs function and generic job-tracking concept. Domain-specific banking, reconciliation, import-review, diagnostic, and audit facts remain governed by their authoritative services and tables.

Required reading:

- `doc/requirements/requirements-clarification-overlay.md`
- `doc/requirements/phase-remap-after-clarification.md`
- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/banking/import-and-reconciliation.md`
- `doc/ui_design_rules.md`

Required inspection:

- `AppPanelId`, `NavigationPane`, `MainWindow`, `PanelFactory`, and command-palette tests.
- `ImportExportJobsPanel`, `UiWorkspaceDataStore`, `ImportPreviewPanel`, `BankTransactionsPanel`, and `DiagnosticsPanel`.
- Current import/export, banking-import, diagnostics, navigation, and UI-store tests.

### P13-S1 — Remove generic Import/Export Jobs function and session job tracking

Status: DONE through merged PR #177 and owner desktop verification.

Branch: `codex/P13-S1-remove-import-export-jobs`
Pull request: #177, merged
Base head: `58eab6563cbe245d39beb7ef6cb814946df6cf9d`
Implementation head: `495bf4ab296cb66ee2b4b7c8aebee803ca808ae2`
Merged head: `69ef903ab00e209f0855d9b374c0fc4fc3d39377`

Planned deliverables:

- Remove `IMPORT_EXPORT_JOBS` as an `AppPanelId`, navigation item, menu/command-palette destination, panel-factory route, and production panel.
- Remove the generic `ImportExportJob` session list and append/clear APIs rather than replacing them with durable generic job tracking.
- Preserve actual import/export commands and domain-specific H2 banking, reconciliation, import-issue, diagnostic, and audit facts.
- Preserve the temporary bank-transaction staging surface only to the extent still required by its owning banking work; do not broaden P13-S1 into unrelated P05 remediation.
- Update governing inventories, focused navigation/source tests, and obsolete-name consistency checks.
- Run the full Maven PR Tests workflow and leave the slice VERIFYING until desktop navigation confirmation and merge.

Out of scope:

- Diagnostics command redesign, accepted-import canonical transaction writes, and unrelated P14 hardening.

Completed deliverables:

- Removed `IMPORT_EXPORT_JOBS` from `AppPanelId`, navigation, the Tools menu, command-palette labels/capabilities, and `PanelFactory`.
- Deleted `ImportExportJobsPanel` and removed the generic `ImportExportJob` session list plus append, query, and clear APIs.
- Removed generic job-log writes from CoA and bank import/export actions while preserving the actual operations, user-facing outcome messages, and temporary bank-transaction staging.
- Updated the interface matrix and persistence inventory, simplified the staging-store tests, and added a focused elimination/source guardrail.
- Local Maven validation remains unavailable because the container has neither Maven nor a Maven wrapper; Maven PR Tests runs `29670629196` and `29670678408` passed, with the latter on the final PR head.
- PR #177 merged at `69ef903ab00e209f0855d9b374c0fc4fc3d39377`.
- The owner verified that Import / Export Jobs is absent from navigation, menus, and the command palette while CoA and OFX/QFX import/export operations and their user-facing outcome messages remain available.

Next exact action:

- None; P13-S1 is DONE.

### P13-S2 — Typed diagnostics and recovery command ownership

Status: DONE through merged PR #179 and owner desktop verification.

Branch: `codex/P13-S2-typed-diagnostics-recovery`
Pull request: #179, merged
Base head: `6809601310fda171b6259af042133ef105b75c81`
Implementation head: `cb6881d9810e249d7503e5944fab58254ca2f63e`
Merged head: `7ef06129551eb967f6caa1f9d30141835a7f6ddc`

Required reading:

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/requirements/requirements-clarification-overlay.md`
- `doc/requirements/phase-remap-after-clarification.md`

Required inspection:

- `DiagnosticsPanel`, `DatabaseRecoveryPanel`, `ProductionWorkspaceWindow`, `MainWindow`, and `PanelFactory`.
- `DatabaseLocationService`, `DatabaseMigrationService`, `FlywaySchemaRecoveryService`, `DatabaseSessionController`, `WorkspaceContext`, `UiSessionState`, and `UiServiceRegistry`.
- Current diagnostics, migration-recovery, database-session, navigation, tooltip, and JavaFX source/behavior tests.

Planned deliverables:

- Introduce explicit typed diagnostics queries/results for runtime, active-company, active-database, datasource, account, fund, and duplicate-code health rather than assembling diagnostic facts directly in the JavaFX panel.
- Give database retry/repair, select-existing, and create-new recovery actions explicit typed command ownership while preserving the existing Dashboard recovery surface and database-session composition.
- Keep diagnostics factual and non-destructive: do not add automatic data repair, generic job tracking, or enabled placeholder actions.
- Preserve drill-through to the existing Chart of Accounts and Funds destinations with explicit context when duplicate codes exist.
- Apply current scrolling, tooltip, formatting, error-message, and company-owned UI-state rules; add focused service tests and JavaFX source/behavior guardrails.
- Update the interface matrix and persistence inventory, run the full Maven PR Tests workflow, and leave the slice VERIFYING until desktop validation and merge.

Out of scope:

- Canonical acceptance from Import Preview, banking/reconciliation remediation, schema redesign, and unrelated P14 end-to-end hardening.

Completed deliverables:

- Added `DiagnosticsQueryService` with an immutable typed report for runtime, Java, active-company, active-database, datasource, active/total account and fund counts, duplicate codes, quality state, and safe failure details.
- Removed datasource, session, account, and fund fact gathering from `DiagnosticsPanel`; lifecycle-owned `WorkspaceServices` and `PanelFactory` now supply the diagnostics query service.
- Added typed `DatabaseRecoveryCommand` values and one command handler; File-menu and Dashboard recovery controls route retry-current, select-existing, and create-new through the production workspace shell.
- Preserved the existing Diagnostics destination, duplicate-code drill-through, Dashboard recovery surface, successful-connection-only database selection, and non-destructive failure behavior.
- Updated the interface matrix and persistence inventory and added focused service, JavaFX behavior, and source-boundary tests.
- Local Maven validation remains unavailable because the container has neither Maven nor a Maven wrapper; Maven PR Tests run `29672098613` passed on implementation head `cb6881d9810e249d7503e5944fab58254ca2f63e`.
- Final Maven PR Tests run `29672152187` passed on final PR head `094d9e4a33997431564c71c15c38669394a42e4c`.
- PR #179 merged at `7ef06129551eb967f6caa1f9d30141835a7f6ddc`.
- The owner verified factual Diagnostics refresh and warnings, duplicate account/fund drill-through behavior, all three Dashboard database-recovery actions, and protection of the selected database after a failed connection.

Next exact action:

- None; P13-S2 and P13 are DONE.

# P14 — End-to-end hardening

**Selector:** `PHASE=P14`
**Status:** DONE through P14-S1, P14-S2, P14-S3, P14-S4, and documentation-only P14-C1
**Depends on:** P03 through P13 except eliminated P07

Purpose: harden the one production JavaFX/H2 application through cross-workspace lifecycle and regression coverage. P14 repairs defects exposed by end-to-end use; it does not absorb unfinished feature expansion owned by earlier domain phases.

Required reading:

- `doc/interface-operation-matrix.md`
- `doc/persistence-authority-inventory.md`
- `doc/ui_design_rules.md`
- `doc/ui/editor-guidelines.md`
- `doc/requirements/requirements-clarification-overlay.md`
- `doc/requirements/phase-remap-after-clarification.md`
- `doc/workflow/development-workflow.md`

Original P14 audit findings and closure ownership:

- P14-S1 closed universal table interaction/state authority and production-route smoke gaps.
- P14-S2 closed core editor layout, scrolling, divider-state, and dirty-state gaps.
- P14-S3 closed scoped company money/date formatting and financial-view split/table-layout gaps.
- P14-S4 closes stale Help content/links, canonical production destination-menu names, and exact all-destination/alias smoke assertions.

### P14-S1 — Production UI compliance foundation and route smoke

Status: DONE through merged PR #181 and owner desktop verification.

Branch: `codex/P14-S1-production-ui-compliance-foundation`
Pull request: #181, merged into `main` at `9bbdda0ab7113df75bc0fd16705df7e8bd5c9c14`.
Base head: `62d0a8408dd482296d5a80736c7a31cba4cae7b1`
Implementation head: `6c5e2f5bbe02098679deba90769824c40fc264bc`
Validation: Maven PR Tests run `29716315784` passed, including the complete established headless suite and focused Xvfb production-route/table-state compliance tests.
Final documentation-head Maven PR Tests run `29716454453` passed.
The owner verified representative table sorting, resizing, reordering, restart restoration, and company-specific state, then confirmed the slice for merge.

Required inspection:

- `MainApp`, `ProductionWorkspaceWindow`, `NavigationPane`, `PanelFactory`, `PanelHost`, `WorkspaceServices`, `WorkspaceServicesFactory`, and `UiServiceRegistry`.
- `DatabaseBootstrap`, `DatabaseSessionController`, `CompanySessionController`, `WorkspaceContext`, `UiSessionState`, and test H2/bootstrap helpers.
- `AppPanelId`, `AppPanelConsistencyTest`, `WorkspaceCompositionTest`, `ProductionWorkspaceJavaFxBehaviorTest`, `ProductionDesignRulesTestFxTest`, and current panel source/behavior tests.

Planned deliverables:

- Build a repeatable JavaFX smoke fixture backed by a disposable migrated H2 database with an authoritative active company.
- Enumerate and open every canonical production destination through the production workspace composition, while confirming compatibility aliases reuse their canonical destination and eliminated destinations remain absent.
- Add one reusable H2-backed company-owned table-state helper for column order, widths, sort direction, and sort priority; do not add another preferences sidecar.
- Add production-root compliance guardrails that can enumerate every table and detect constrained policies, non-sortable/non-resizable/non-reorderable columns, and missing registered company-state ownership.
- Apply the shared helper at `PanelFactory` so every production table receives the common contract, while preserving the richer H2 owners already used by Journal, Funds, and Company Admin.
- Remove Banking and Inventory Java Preferences table stores so production table layout has only the H2 company-state authority.
- Update governing inventories, run the full Maven PR Tests workflow, and leave the slice VERIFYING until desktop validation and merge.

Out of scope:

- New Import Preview acceptance, bank-statement review, reconciliation resolution, inventory-to-ledger automation, report expansion, schema redesign, or other unfinished domain features merely listed in earlier-phase backlogs.

Next exact action:

- None; P14-S1 is DONE.

### P14-S2 — Core editor form layout and dirty-state compliance

Status: DONE through merged PR #182 and owner desktop verification.

Branch: `codex/P14-S2-core-editor-form-compliance`
Pull request: #182, merged into `main` at `96993c9a5c143dbd617ba6ac7ec5c1c0026ad980`.
Base head: `9bbdda0ab7113df75bc0fd16705df7e8bd5c9c14`
Activation head: `707078169c39f74e4f1169afa55e31dd8437aaaf`
Implementation head: `927c0d2024d7aed126c8dd5704e9ce2fd3a244f7`
Validation: Maven PR Tests run `29756498806` passed, including the complete established suite plus focused Xvfb production-route and seven-editor dirty-state coverage.
Final documentation-head Maven PR Tests run `29756777489` passed on `0bd9afa56bd70f380f2910e01ca8f94c8fbdc1cc`.
The owner verified the horizontal dividers, independent scrolling, company-owned divider restoration, dirty-state prompts, and hidden Administration-tab dirty aggregation, then confirmed the slice for merge.

Required inspection:

- `AssetsRegisterPanel`, `BankingPanel`, `BudgetEditorPanel`, `ChartOfAccountsPanel`, `SettingsPanel`, `UserAdminPanel`, and `AdministrationPanel`.
- `PanelFactory`, `PanelHost`, `ProductionWorkspaceWindow`, `CompanyTableStateBinder`, `CompanyUiPreferencesService`, and active-company workspace refresh behavior.
- Current panel source/behavior tests, dirty-state tests, production route compliance tests, and relevant service integration tests.
- `doc/ui_design_rules.md`, `doc/ui/editor-guidelines.md`, `doc/interface-operation-matrix.md`, and `doc/persistence-authority-inventory.md`.

Planned scope:

- Repair Assets, Banking, Budget Editor, Chart of Accounts, Preferences, User Admin, and the Administration dirty-state aggregator.
- Add required table/form split regions, editor scrolling, company-owned state, and loss-prevention prompts without changing domain services.
- Add focused JavaFX/source guardrails and run the complete Maven PR Tests workflow; leave the slice VERIFYING until desktop acceptance and merge.

Completed deliverables:

- Added reusable immutable form-snapshot dirty tracking and H2 company-owned split-divider persistence without introducing a new data authority.
- Rebuilt Asset Register and Chart of Accounts as table-over-scrollable-editor workspaces.
- Rebuilt both Banking sections and the User Admin user/assignment tabs with dedicated table/editor splits and independently scrollable forms.
- Moved the Budget amount editor out of the header into its own scrollable split region and protected unsaved target amounts from row, refresh, and activation loss.
- Added dirty-state reporting and discard protection to Assets, Banking, Budget Editor, Chart of Accounts, Preferences, and User Admin.
- Changed Administration dirty aggregation to inspect Preferences, Company Admin, and User Admin regardless of which tab is selected.
- Added focused source guardrails and Xvfb behavior coverage for all seven targets; no domain services, H2 models, or ledger behavior changed.

Next exact action:

- None; P14-S2 is DONE.

### P14-S3 — Financial view formatting and table-layout compliance

Status: DONE through merged PR #183 and owner desktop verification.

Branch: `codex/P14-S3-financial-view-formatting-compliance`
Pull request: #183, merged into `main` at `9e5edf02e7d85ae14f317435c0527788d2ec82f1`.
Base head: `96993c9a5c143dbd617ba6ac7ec5c1c0026ad980`
Activation head: `df737cc9d498e1fa0222b4989ae1d0fcadca4715`
Implementation head: `6e4ddc45f64a50d5f48beeb16aa257c49b8cbfab`
Validation: Maven PR Tests run `29789423476` passed, including the complete established suite, production-route Xvfb coverage, focused financial-view source guardrails, and company formatter behavior tests.
Final Maven PR Tests run `29789568217` passed on documentation head `56060b06e86c3e782a91a6fdde4e7326bfb24c87`.
The owner verified company-specific formatting, horizontal divider behavior/restoration, independent scrolling, and the visible Audit History title, then confirmed the slice for merge.

Planned scope:

- Repair Dashboard, Budget vs Actual, Depreciation Runs, Inventory, Reconciliation, Period Close, Import Preview, Audit History, and Bank Transactions.
- Apply company money/date formatting, unconstrained tables, company-owned state, and required split regions without expanding their domain workflows.

Completed implementation:

- Routed Dashboard, Budget vs Actual, Depreciation Runs, Inventory, Reconciliation, Period Close, Audit History, and Bank Transactions money/date presentation and editing through active-company `CompanyUiFormat`.
- Extended the shared formatter to company-aware date/time display without changing persisted timestamp values.
- Removed scoped constrained table policies and retained the universal H2 company-owned table-state boundary.
- Added company-owned split-divider state to Dashboard table cards, Budget vs Actual, Depreciation Runs, Inventory, Reconciliation, Period Close, and Import Preview.
- Stacked Depreciation and Import Preview table regions top/bottom with horizontal draggable dividers; preserved independent table scrolling and current domain operations.
- Renamed the visible legacy Approval Audit surface to factual Audit History while retaining its stable compatibility identifier and repository.
- Added focused source and formatter behavior guardrails. No services, entities, migrations, canonical ledger behavior, or domain workflows changed.

Next exact action:

- None; P14-S3 is DONE.

### P14-S4 — Help, production desktop sweep, and final compliance closure

Status: DONE through merged PR #184 and owner desktop verification.

Branch: `codex/P14-S4-help-desktop-compliance-closure`
Pull request: #184, merged into `main` at `cf1c99788505b1cbb90bd92da1dcbc902af9a765`.
Base head: `9e5edf02e7d85ae14f317435c0527788d2ec82f1`
Activation head: `bffbe75ecb6e883dfcc6e909ec0104fe46c4d72b`
Implementation head: `3a6f5e4bcd2ecc1e8e9076d8ce661137e3fd7a1a`
Validation: Maven PR Tests run `29791007379` passed, including the complete established suite, exact all-destination production H2/Xvfb route smoke, alias reuse checks, and focused Help/menu/path guardrails.
Final Maven PR Tests run `29791138579` passed on documentation head `c70b5c981c4ace9db9913e672f19375a8227d86e`.
The owner completed and confirmed the final laptop-width production desktop sweep after merge, including Help, canonical destination names, all navigation routes, sidebars, scrolling, dividers, tooltips, formatting, dirty prompts, and company switching.

Planned scope:

- Correct Help navigation/document links, run the all-destination production smoke path, perform laptop-width desktop validation, and close only verified residual design-rule findings.

Completed implementation:

- Rebuilt Help as a laptop-safe scrollable surface with current database, active-company, active-period, Journal, Banking, import, and Administration guidance.
- Replaced nonexistent/wrong-owner Help links with the authoritative repository, plan, UI rules, and development-workflow paths; browser-unavailable failures now show the copyable URL instead of failing silently.
- Replaced retired Ledger Register and Transaction Editor entries in the production Destinations menu with canonical Journal, Administration, and Help entries.
- Strengthened the production H2/Xvfb route smoke to require the exact canonical destination set, reject the eliminated Schedules route, and prove both retired Journal aliases reuse one existing Journal root/tab.
- Added focused Help/menu source and documentation-path guardrails. No domain service, persistence, schema, ledger, or accounting behavior changed.

Next exact action:

- None; P14-S4 is DONE.

### P14-C1 — Final plan-ledger reconciliation

Status: DONE through documentation-only PR #185.

Branch: `codex/P14-C1-finalize-plan-ledger`
Pull request: documentation-only PR #185.
Base head: `cf1c99788505b1cbb90bd92da1dcbc902af9a765`
Activation head: `b41544371165bbb2fdb45c1a7a764c320334c37e`

Purpose:

- Record the owner-confirmed P14-S4 desktop sweep after product PR #184 merged with the slice still marked VERIFYING.
- Mark P14-S4 and P14 DONE only through a fresh documentation-only PR from current main.

Completed deliverables:

- Verified PR #184 merged at `cf1c99788505b1cbb90bd92da1dcbc902af9a765` after both Maven PR Tests runs passed.
- Recorded the owner's final desktop acceptance of Help, navigation, sidebars, scrolling, dividers, tooltips, company formatting, dirty prompts, and company switching.
- Reconciled the phase index, active front matter, P14 status, P14-S4 record, and next action without changing product code.

Next exact action:

- None after PR #185 merges; P14 is DONE and no later phase is authorized without an explicit plan amendment.


# P15 — Versioned data interchange and database transfer

**Selector:** \`PHASE=P15\`  
**Status:** IN_PROGRESS at documentation-only P15-S0  
**Depends on:** P02, P05, P06, P12, P13, P14

Purpose: provide safe, previewable, versioned transfer of active-company business data, reusable Charts of Accounts, complete database copies, and bank-statement records without creating a second ledger, a parallel persistence model, or the eliminated generic Import/Export Jobs framework.

Established boundaries:

- SCLX is active-company business-data interchange reconstructed from current canonical H2 authority; it is not a raw H2 backup.
- Chart of Accounts JSON is an independently versioned chart-transfer format; it is not SCLX and does not contain transaction history.
- Database backup/import is a separate whole-database administration workflow.
- OFX 2.x, QFX, and bank CSV are single-account bank-statement interchange formats. They do not represent double-entry accounting and must not be advertised as complete ledger export.
- Imported bank records become durable review facts in \`bank_import_batch\`, \`bank_statement_line\`, and \`import_issue\` before any explicit canonical transaction acceptance or reconciliation action.
- SCLX bank sections preserve supported source statement metadata and match/reconciliation facts; OFX/QFX/CSV remain dedicated bank-statement entry and exit formats.
- Every import has a non-mutating preview and validation result before commit. Blocking errors prevent all writes.
- Every commit is atomic at its documented boundary and reports created, updated, skipped, warning, and error counts.
- No workflow in P15 writes compatibility journal/open-item tables, serializes JPA entities directly, retains passwords, or revives generic durable job tracking.

Required reading:

- \`doc/interface-operation-matrix.md\`
- \`doc/persistence-authority-inventory.md\`
- \`doc/ui_design_rules.md\`
- \`doc/ui/editor-guidelines.md\`
- \`doc/requirements/requirements-clarification-overlay.md\`
- \`doc/requirements/phase-remap-after-clarification.md\`
- \`doc/accounting/ledger-authority.md\`
- \`doc/accounting/transaction-lifecycle.md\`
- \`doc/accounting/period-and-correction-policy.md\`
- \`doc/banking/banking-and-reconciliation.md\`
- \`doc/banking/import-and-reconciliation.md\`
- \`doc/administration/company-lifecycle.md\`
- \`doc/workflow/development-workflow.md\`
- donor \`benbaron/NonprofitAccounting\` SCLX, COA, bank import/export, database administration, tests, and actual emitted fixtures as reference only

Required inspection:

- \`Company\`, \`ChartOfAccounts\`, \`Account\`, \`Txn\`, \`TxnSplit\`, \`Fund\`, \`BudgetCategory\`, \`Activity\`, \`Counterparty\`, \`Merchant\`, bank, asset, inventory, reconciliation, and audit entities and their company-ownership paths.
- \`ImportExportOrchestrationService\`, \`ImportPreviewService\`, \`CoaCsvMapper\`, \`OfxQfxTransactionExtractor\`, \`BankDataEnvelopeRecognizer\`, \`BankImportNormalizationService\`, \`BankImportReviewService\`, \`ReconciliationComparisonService\`, canonical transaction services, and current export adapters.
- \`ImportPreviewPanel\`, \`BankTransactionsPanel\`, \`BankingPanel\`, \`ChartOfAccountsPanel\`, production File menu, workspace lifecycle, dirty-state, progress, and company-owned UI-state composition.
- Flyway migrations and tests for company ownership, accounts, canonical transactions, banking imports, reconciliation, fixed assets, inventory, period close, audit history, and database bootstrap/recovery.
- Donor SCLX versions 1.0/1.2/1.3, COA JSON output, import modes/options/results, round-trip tests, and database-transfer behavior. Port behavior deliberately; do not copy donor sidecar repositories or alternate shell architecture.

## P15-S0 — Interchange contract and donor fixtures

Status: IN_PROGRESS

Branch: \`codex/P15-S0-data-exchange-plan\`  
Pull request: #186  
Activation head: `bb71c84aca21adeef90564b3e4da5a237f7a0181`

Purpose: freeze the governing contracts, compatibility fixtures, authority boundaries, and slice dependencies before implementation.

Planned deliverables:

- Add \`doc/data-exchange/sclx.md\`, \`doc/data-exchange/chart-of-accounts-json.md\`, \`doc/data-exchange/database-transfer.md\`, and \`doc/data-exchange/bank-statement-interchange.md\`.
- Govern SCLX read compatibility for 1.0, 1.2, and 1.3 and deterministic SCLX 1.3 output.
- Generate donor-produced COA JSON golden files and document the actual compatibility shape before implementing the new codec; do not rely on donor Javadoc where it differs from emitted JSON.
- Freeze representative valid and invalid fixtures for SCLX, COA JSON, OFX 2.x XML, practical QFX variants, and normalized/mapped bank CSV.
- Specify file-size, nesting-depth, entity-count, encoding, date, money, path, atomic-write, source-hash, and external-identity limits.
- Specify preview-only, validate-only, commit-to-active-company, and create-new-database/company modes where applicable.
- Specify AS_IS and explicit MAPPED account-reference behavior, generated balancing-line confirmation for incomplete SCLX transactions, conflict policies, unsupported fields, and result counts.
- Audit direct and indirect company ownership. Record required nondestructive migrations before active-company SCLX export is allowed.
- Decide the maintained OFX/QFX parser/writer boundary through fixtures and security tests. Support OFX 2.x XML and real-world QFX envelopes deliberately; do not depend on filename alone.
- Define the normalized bank CSV profile and export schema, including transaction/posted dates, amount or debit/credit inputs, FITID/source ID, type, name/payee, memo, check/reference values, account identity, currency, and optional statement balances.
- Update the interface matrix and persistence inventory.
- Make no product-code, entity, service, migration, or JavaFX changes in this slice.

Acceptance:

- Each format has one documented authority, version policy, import/export scope, and error policy.
- Golden fixtures prove the donor compatibility claims and the intended OFX/QFX/CSV boundary.
- The plan identifies every prerequisite that would otherwise make active-company export ambiguous.
- The generic Import/Export Jobs destination and generic durable job log remain eliminated.

Next exact action:

- Review and merge P15-S0, then begin P15-S1 from the resulting current \`main\`.

## P15-S1 — Shared operation contract, company ownership, and external identity

Status: BLOCKED until P15-S0 merges.

Planned deliverables:

- Add immutable shared preview, validation-message, confirmation, progress, operation-count, and result types used by SCLX, COA JSON, database transfer, and bank-statement exchange.
- Preserve the four donor-established modes where meaningful: preview only, validate only, commit to active company, and create/import into a new database and company.
- Add nondestructive company-ownership migrations only where the P15-S0 audit proves current active-company selection is ambiguous.
- Backfill ownership deterministically, change global uniqueness to company-scoped uniqueness where required, and reject cross-company references at service boundaries.
- Add durable interchange identity keyed by company, format, entity type, source system, and external ID. This is deduplication/traceability evidence, not a job queue.
- Add migration-upgrade and multi-company-isolation tests.

Acceptance:

- Every record eligible for active-company SCLX export has an unambiguous authoritative owner.
- Reimport can distinguish identical, new, and conflicting records without relying on local numeric primary keys.
- Shared contracts remain independent of JavaFX and JPA entities.

## P15-S2 — Whole-database backup, import-copy, validation, and recovery

Status: BLOCKED until P15-S1 merges.

Planned deliverables:

- Implement consistent H2 backup through supported H2 backup/script facilities rather than copying an open \`.mv.db\` file.
- Restore/import only into a new explicit target path by default, migrate and validate it, then offer a guarded database switch.
- Require exclusive-access and backup confirmation for any repair/recovery action that could modify a database.
- Display exact source, destination, backup, and validation-result paths.
- Reuse current database-session, Dashboard recovery, diagnostics, and migration composition; do not create a second database controller.
- Add failure-injection, source-equals-target, active-database overwrite, corrupt input, version migration, and round-trip tests.

Acceptance:

- A backup restored to a new database preserves every H2-backed company and record, including compatibility structures intentionally retained by current schema.
- Failed validation never replaces or changes the active database.
- Database transfer remains visibly separate from SCLX and COA JSON.

## P15-S3 — Chart of Accounts JSON import and export

Status: BLOCKED until P15-S1 merges.

Planned deliverables:

- Add DTO-based, deterministic, pretty-printed UTF-8 JSON; never serialize Hibernate entities.
- Import the actual donor compatibility shape and export a documented independently versioned shape.
- Preserve every donor field with a valid current-model equivalent, including account number/code, name, type, normal/increase side, parent, currency, opening balance, funds, and supplemental-detail kinds; warn on unsupported fields.
- Support \`CREATE_NEW_CHART\`, \`MERGE_BY_CODE\`, and explicit \`MAP_CODES\`; missing input accounts never delete or deactivate local accounts.
- Preview and validate duplicate codes, parent references, cycles, type/subtype/normal-balance compatibility, posting state, dates, and history restrictions before any write.
- Treat opening-balance or history-sensitive structural changes as financial changes requiring explicit supported policy and confirmation.
- Persist parent-before-child in one transaction and make repeated identical import idempotent.
- Add Import JSON and Export JSON actions to the existing Chart of Accounts workspace with dirty-state and desktop design-rule compliance.

Acceptance:

- Export/import into a clean company produces a semantically equivalent chart.
- Blocking errors or injected late failure produce no partial chart.
- Merge does not delete absent accounts and identical reimport makes no changes.

## P15-S4 — SCLX model, parser, and deterministic active-company export

Status: BLOCKED until P15-S1 and P15-S3 merge.

Planned deliverables:

- Adapt donor SCLX document/parser/options/result concepts while keeping DTOs independent of JPA entities.
- Read SCLX 1.0, 1.2, and 1.3; write deterministic SCLX 1.3.
- Export the active company, chart/accounts, funds, budgets, supported counterparties/activities, canonical transactions/splits, supplemental details, bank configuration and reviewed statement facts, reconciliation facts, fixed assets/depreciation, inventory/movements, period-close facts, and factual audit history where the governed format supports them.
- Put supported application-specific facts not expressible in standard SCLX under a documented \`extensions.scaJakartaH2\` namespace or list them explicitly as excluded.
- Preserve supported OFX provenance such as FITID, transaction type, transaction/posted dates, check/reference numbers, payee/name, memo, payee ID, correction references/actions, and statement/account metadata.
- Exclude authentication material, UI state, Flyway/H2 internals, filesystem paths, raw attachments, compatibility journal/open-item authority, eliminated Schedules, and generic job history.
- Validate references and balanced canonical transactions before writing through a temporary file and atomic move; report counts, warnings, exclusions, and SHA-256 content hash.

Acceptance:

- Export is reconstructed from current canonical H2 data, so later application edits appear.
- Deterministic exports from unchanged data compare equal except explicitly documented envelope metadata.
- No local numeric primary key is used as a portable identity.

## P15-S5 — SCLX preview, mapping, and transactional import

Status: BLOCKED until P15-S4 merges.

Planned deliverables:

- Preview format/version, entity counts, references, external IDs, account/fund mappings, duplicates, unsupported sections, transaction balance, closed-period conflicts, reconciliation protections, and target-company conflicts without changing H2.
- Support donor-established \`AS_IS\` and explicit \`MAPPED\` account reference modes.
- For single-sided or unbalanced source transactions, require an explicitly selected active posting cash account and display every generated balancing line before commit.
- Skip zero-value lines and transactions with no posting lines with explicit warnings and counts.
- Import masters before dependent history, then route financial records through transaction-aware canonical services inside one caller-owned transaction.
- Default to a new or empty target company; reject accidental populated-company merge until explicit conflict rules are implemented and tested.
- Preserve external identity for idempotent reimport; skip identical records and require explicit resolution for conflicts.
- Write one factual audit event with source name/hash, version, mappings, target, counts, warnings, and user after successful commit.
- Roll back the entire documented commit boundary on any failure.

Acceptance:

- Export/import/export is semantically equivalent for every supported section.
- Identical second import creates no duplicates.
- Closed-period and completed-reconciliation protections remain authoritative.
- No compatibility journal table or donor parallel SCLX repository is written.

## P15-S6 — OFX 2.x/QFX and bank CSV import to durable review

Status: BLOCKED until P15-S0 and P15-S1 merge.

Purpose: replace temporary/session bank-transaction staging with a complete, company-scoped, configured-bank-account import and review path.

Planned deliverables:

- Parse OFX 2.x XML and governed real-world QFX envelopes, including XML and explicitly supported SGML/header variants established by P15-S0 fixtures.
- Reject malformed, encrypted, unsupported-message-set, multi-account-ambiguous, oversized, or entity-expansion input safely and explain the blocking reason.
- Add mapped CSV import with previewable column profiles for common amount or debit/credit layouts, delimiter/quote handling, header mapping, date format, sign convention, currency, and account selection.
- Persist reusable CSV mapping profiles in H2 per company and configured bank account only after explicit save; do not use Java Preferences or a sidecar file.
- Normalize source identifiers, transaction and posted dates, amount, type, payee/name, memo, check/reference data, currency, bank/account IDs, and correction metadata before durable review.
- Require an explicit active configured bank account; validate OFX/QFX bank/account identity against it and require a visible override decision for a non-blocking mismatch.
- Reuse and extend \`BankImportNormalizationService\` and \`BankImportReviewService\` so one import creates one atomic \`bank_import_batch\`, its \`bank_statement_line\` rows, and \`import_issue\` facts.
- Detect exact duplicates by stable source ID/FITID and probable duplicates by deterministic normalized fingerprint; show both in preview and never silently discard a conflict.
- Wire Import Preview, Banking, and Bank Transactions to the durable review authority and remove \`UiWorkspaceDataStore.bankTransactions\` when no production consumer remains.
- Do not create canonical \`Txn\`/\`TxnSplit\` rows merely because a bank statement was imported; acceptance/matching remains an explicit banking workflow.
- Add parser golden files, malformed/security cases, multi-company isolation, duplicate/correction, CSV profile, rollback, JavaFX behavior, and desktop tests.

Acceptance:

- OFX 2.x, QFX, and CSV imports produce equivalent normalized statement facts for equivalent inputs.
- Reimport is idempotent by source identity/fingerprint and reports skipped/conflicting rows.
- A late failure leaves no partial batch, statement line, issue, or saved CSV profile.
- Restart and company switching preserve only authoritative company-owned review state.

## P15-S7 — OFX 2.x/QFX and normalized bank CSV export

Status: BLOCKED until P15-S6 merges.

Purpose: export portable single-account bank-statement records without presenting OFX/QFX as a double-entry ledger export.

Planned deliverables:

- Export one explicitly selected configured bank account and date range from authoritative durable reviewed statement lines.
- Write standards-conformant OFX 2.x XML and a governed QFX profile with deterministic ordering, account/currency/statement metadata, transaction identifiers, dates, amounts, types, payee/name, memo, check/reference fields, supported correction metadata, and opening/closing balances when authoritative.
- Preserve imported FITID/source identity where valid. When a required export identifier is absent, derive a deterministic namespaced export ID and report that derivation without changing accounting authority.
- Export normalized UTF-8 CSV with a frozen round-trip schema and RFC 4180 quoting. Include source ID/FITID, transaction date, posted date, amount, debit/credit indicator, type, payee/name, memo, check/reference, currency, configured bank-account identity, batch/source provenance, duplicate/review state, and supported match identifiers.
- Make normalized CSV export directly re-importable through P15-S6 without losing governed statement facts.
- Keep canonical double-entry ledger export in SCLX/report exports; do not flatten the whole ledger into OFX/QFX.
- Write through a temporary file and atomic move, with counts, warnings, source scope, and SHA-256 hash.
- Add OFX/QFX schema/profile validation, CSV round-trip, deterministic export, correction, balance-availability, empty-range, and cross-company-isolation tests.
- Add explicit Export OFX 2.x, Export QFX, and Export Bank CSV actions to the existing Banking/Bank Transactions workflow; do not add an Import/Export Jobs destination.

Acceptance:

- Export/import round trips preserve every governed normalized bank-statement field.
- Export never includes another company or bank account.
- OFX/QFX output represents bank-statement activity only and is labeled accordingly.
- Missing optional source metadata produces disclosed warnings rather than fabricated values.

## P15-S8 — Integrated JavaFX workflow and end-to-end verification

Status: BLOCKED until P15-S2 through P15-S7 merge.

Planned deliverables:

- Keep SCLX preview/validation/mapping/commit in Import Preview and expose Export Active Company to SCLX from the File menu.
- Keep COA JSON actions in Chart of Accounts.
- Keep OFX/QFX/CSV import, review, and export in Banking, Bank Transactions, and Import Preview as defined by the operation matrix.
- Keep whole-database backup/import/validate/recovery under database administration and existing recovery composition.
- Run long operations asynchronously with bounded progress and cancellation before commit; write factual audit history only for completed durable operations.
- Apply current scrolling, split-pane, tooltip, formatting, dirty-state, company-owned state, and guarded company/database switching rules.
- Add all-format golden-file, unsupported-version, malformed/oversized, conflict, rollback, semantic round-trip, idempotency, multi-company, closed-period, reconciliation-protection, production-route, and laptop-width desktop coverage.
- Run full \`mvn clean verify\` through the PR workflow and complete owner desktop acceptance.

Acceptance:

- Each operation is labeled by exact scope and target; “database,” “active company,” “Chart of Accounts,” and “bank statement” are never used interchangeably.
- No generic Import/Export Jobs destination or generic durable job log exists.
- Every enabled action has a real service-backed operation and a preview/confirmation path appropriate to its risk.


# P06 — Bank reconciliation and cleared-state comparison

**Selector:** `PHASE=P06`  
**Status:** DONE through PR #138; corrective P06-C1 DONE through PR #146; corrective P06-C2 DONE through PR #147  
**Depends on:** P05

Completed deliverables: configured-account reconciliation, durable sessions and match/resolution rows, statement/manual/import sources, matching and cleared-state workflow, saved unresolved/finalized state, and the Setup/Statement/Match/Review workflow layout.

# P07 — Eliminated former Schedules phase

**Selector:** `PHASE=P07`  
**Status:** DONE through PR #139

Completed deliverables: removed the Schedules panel, navigation, production route, runbook sidecars, formatting tests, and related inventory/matrix references. Any retained compatibility enum value has no product route.

# P08 — Asset Register and depreciation

**Selector:** `PHASE=P08`  
**Status:** DONE through PR #140; corrective P08-C1 DONE through PR #144  
**Depends on:** P02

Completed deliverables: H2 fixed-asset and depreciation-run records, canonical depreciation transactions, service-backed Asset Register and Depreciation Runs panels, removed runbook sidecars, migration guardrails, and readable account/fund selector labels.

# P01-C1 — Full-text hover tooltips

**Selector:** `PHASE=P01`  
**Status:** DONE through PR #141

Completed deliverables: `FullTextTooltipInstaller`, production installation, UI-design documentation, and focused JavaFX tests.

# P09 — Inventory and supplies

**Selector:** `PHASE=P09`  
**Status:** DONE through PR #142; corrective P09-C1 DONE through PR #143  
**Depends on:** P02

Required behavior: genuine Inventory item add/edit and movement history, no runbook subpane, and canonical transactions when movements are financially relevant.
