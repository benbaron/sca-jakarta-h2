# Company lifecycle and active-company authority

## Authority

The `company` table is authoritative for company existence and active/inactive lifecycle. `MultiCompanyState` is only a recent-selection convenience. A code found only in sidecar state is not a company and must never become the active workspace company.

`Company.id` is stable record identity. The code is a unique business label and may be edited without creating a second company row. Code-keyed company UI preferences/state and period-close history move to the new code in the same transaction; company-owned tables that use `company_id` remain attached automatically.

Chart ownership and chart selection are separate durable facts:

- `chart_of_accounts.company_id` is the ownership boundary for each chart;
- `company.active_chart_of_accounts_id` selects the company's current Chart of Accounts;
- selecting a chart never moves accounts, transactions, identities, or historical references between charts or companies.

## Persisted profile

P12-S3 persists the scalar company profile fields already supported by the H2 model:

- code;
- display name;
- legal name;
- branch type;
- parent organization or kingdom;
- active state;
- fiscal-year start month and day; and
- ISO-4217 default currency.

P19-S1 adds deliberate active-chart assignment using the already-persisted company/chart relationship. Tax filing and expanded reporting-default editors remain deferred until their separate vertically complete persistence workflows exist. Bank accounts remain in the Banking workspace.

## Create and edit

`CompanyAdminService.save(CompanyCommand, currentCompanyCode)` performs create or stable-ID update in one JPA transaction. It validates required values, lengths, case-insensitive code uniqueness, the fiscal date, and ISO currency before writing. A failure rolls the transaction back.

The existing Administration destination continues to use `AppPanelId.SETTINGS`. Its Company Admin tab provides New, Save, Select Active, Refresh, and Chart of Accounts assignment operations. It does not add a second shell destination or administration framework.

The adjacent **Company Ownership Diagnostics** tab is a corrective legacy-data workflow, not another company editor. It lists the unresolved rows that block governed interchange, preserves the entity type, stable record ID, human-readable record description, diagnostic code, candidate count, cause, and resolution guidance, and permits a single direct ownerless row to be assigned to the active company receiving the import after an actor and audit note are supplied. The operator's selected import target is authoritative; the workflow does not require reconstructing a separate historical company. The confirmation and result name the exact row and company. Cross-company reference conflicts remain non-assignable and explain that the underlying accounting links must be corrected in their owning workflow.

The center workspace stacks the company table above the profile editor. A horizontal draggable divider separates them, and the editor keeps its own vertical scrolling so the full-width form remains usable at laptop dimensions. The divider position remains company-owned UI state.

## Chart of Accounts assignment

`CompanyAdminService.listCompanyCharts(companyId)` returns only charts whose durable `chart_of_accounts.company_id` equals the selected stable company ID. Ownerless legacy charts remain a Company Ownership Diagnostics concern and do not become selectable merely because the database has one company.

`CompanyAdminService.assignActiveChart(companyId, chartId)` is the sole Company Admin write boundary for P19-S1. It locks and revalidates both records in one transaction and applies these rules:

1. the company must still exist and be active;
2. the chart must still exist and already belong to that exact company;
3. a `RETIRED` chart cannot be selected;
4. selecting a `DRAFT` chart promotes it to `ACTIVE`;
5. selecting an already `ACTIVE` chart changes only the company active-chart pointer;
6. the previously selected chart is not automatically retired or rewritten;
7. no account, transaction, bank configuration, report history, interchange identity, or other durable record is moved to the newly selected chart.

This intentionally permits more than one company-owned chart to remain `ACTIVE`: chart lifecycle status and the company's current-chart pointer are different facts. The pointer removes runtime ambiguity. If a legacy company lacks a pointer, existing compatibility fallback may still resolve one unambiguous ACTIVE chart; multiple ACTIVE charts without a pointer remain an error until Company Admin deliberately selects one.

The UI requires confirmation before changing the pointer and explains the effects. Unsaved scalar company-profile edits must be saved or discarded first. When the operator later selects a chart-sensitive workspace, normal `PanelHost.onPanelShown()` refresh behavior re-queries current H2 authority, so open tabs do not need a second chart-selection cache.

Chart of Accounts JSON `CREATE_NEW_CHART` continues to create a company-owned `DRAFT` chart without silently activating it. Company Admin is now the deliberate activation/selection workflow. `MERGE_BY_CODE` and account administration continue to use the company's active-chart pointer.

## Active lifecycle

- A company may be selected only when an active H2 row exists.
- A missing or inactive persisted recent selection falls back to an existing active H2 company; it never creates a row.
- The current company cannot be deactivated. Another active company must be selected first.
- A save that would leave no active companies is rejected.
- Companies are deactivated rather than hard-deleted. No Company Admin Delete command is exposed.
- Editing the current company's code preserves its stable ID and updates the active session selection to the new code.

## Workspace switching

`CompanySessionController` coordinates the H2 service with `UiSessionState` and `AppStateStore`. It filters recent codes against active H2 companies before saving selection convenience state. It also exposes the service-owned chart list/assignment operations to Company Admin without creating a second persistence path.

The production toolbar lists only active H2 companies. A company change:

1. validates the requested H2 company;
2. prompts before discarding dirty open workspaces;
3. updates session and workspace context;
4. persists the recent-selection convenience; and
5. recreates open panels so cached services, company formatting, and company-owned layout state use the new active company.

Database changes are owned by `DatabaseSessionController`, not Preferences. The Preferences tab displays the connected database path as read-only factual state and directs database selection/creation to the File and recovery commands.

A production database change:

1. captures the current connected path and dirty open-workspace titles, and requires confirmation before discarding edits;
2. prepares the target without changing the current session: create/locate the path, run Flyway, build JPA/services, and resolve an active company from target H2;
3. builds target-only database/company recent-selection convenience state and persists those two shell facts together;
4. activates the prepared service bundle, then publishes the same database path and resolved company to `UiSessionState`/`WorkspaceContext`; and
5. refreshes the company selector and recreates open panels once so no panel keeps stale target/source service state.

If target preparation, migration, validation, company resolution, or dirty-state confirmation fails, the prior database service bundle, active company, displayed path, Diagnostics path, and open records remain authoritative. A failed target switch is reported as a target failure; it does not incorrectly replace a healthy source session with the recovery dashboard.

## Donor-reference decision

The donor `CompanyManagementService`, `CompanyManagementPanelFX`, and `CompanySetupWizardFX` were reviewed for stable-ID editing, fiscal/currency validation, explicit selection, and archive/deactivate interaction ideas. Their serialized company repository, static `CurrentCompany`, delete workflow, and alternate persistence model were not imported.

For P19-S1 the donor repository did not provide a compatible company-owned active-chart pointer workflow to import. The current H2 `chart_of_accounts.company_id` plus `company.active_chart_of_accounts_id` model therefore remains authoritative rather than introducing donor/static company state.

## Manual validation

1. Open Administration at laptop width and select Company Admin.
2. Create a company with a non-January fiscal start and a valid non-USD currency.
3. Edit its code and confirm the same company ID remains.
4. Select it from Company Admin and from the production toolbar; confirm open workspaces refresh and display the selected company.
5. Attempt to select a nonexistent or inactive company and confirm selection is rejected.
6. Attempt to deactivate the current company and confirm it is rejected.
7. Select another company, deactivate the former company, and confirm it remains listed as inactive.
8. Attempt to deactivate the final active company and confirm it is rejected.
9. Resize, reorder, and sort the company table, move the divider, reopen the company, and confirm company-owned layout restoration.
10. Import/create a second company-owned chart as `DRAFT`. In Company Admin select it and confirm the warning states that existing records are not moved or deleted.
11. Confirm the assignment. Reopen Company Admin and verify that chart is marked current and its status is `ACTIVE`.
12. Confirm the previously active chart and all of its accounts still exist unchanged. Open Chart of Accounts and confirm new account maintenance now uses the newly selected chart.
13. Confirm a Chart of Accounts JSON `MERGE_BY_CODE` preview targets the newly selected chart, while creating another new chart still leaves that chart `DRAFT` until explicitly selected.
14. Confirm a `RETIRED` chart cannot be selected and a chart owned by another company never appears in the selected company's choices.
15. Open **Company Ownership Diagnostics**. For a direct ownerless test row, confirm the preselected active import company, enter an actor and audit note, confirm the assignment, and verify the row disappears and an audit event exists. Confirm a cross-company reference row cannot be assigned or silently dismissed.
