# Company lifecycle and active-company authority

## Authority

The `company` table is authoritative for company existence, profile metadata, and active/inactive lifecycle. `MultiCompanyState` is only a recent-selection convenience. A code found only in sidecar state is not a company and must never become the active workspace company.

`Company.id` is stable record identity. The code is a unique business label and may be edited without creating a second company row. Code-keyed company UI preferences/state and period-close history move to the new code in the same transaction; company-owned tables that use `company_id` remain attached automatically. EIN is informational metadata on this same stable `company` row.

Chart ownership and chart selection are separate durable facts:

- `chart_of_accounts.company_id` is the ownership boundary for each chart;
- `company.active_chart_of_accounts_id` selects the company's current Chart of Accounts;
- selecting a chart never moves accounts, transactions, identities, or historical references between charts or companies.

Company-owned display/workflow preferences use the established H2 `company_ui_preference` and `company_ui_state` authorities. They are not company master-data columns and do not create an alternate Company persistence model.

## Persisted profile

The scalar company profile fields supported by the H2 model are:

- code;
- display name;
- legal name;
- branch type;
- parent organization or kingdom;
- optional informational EIN;
- active state;
- fiscal-year start month and day; and
- ISO-4217 default currency.

P19-S1 adds deliberate active-chart assignment using the already-persisted company/chart relationship. P19-S2 adds the two Report Library opening defaults that have real production consumers today. P19-S3 makes EIN ordinary company metadata and explicitly rejects a tax-filing workflow. Bank accounts remain in the Banking workspace.

## EIN informational metadata

`company.ein` is the sole live production authority for EIN. It is optional, trimmed on save, blank-to-null, and limited to 40 characters. The application deliberately does not impose an IRS-specific syntax validator or treat the field as evidence that a filing identity is valid.

V46 originally created `company_tax_profile`, including `ein`, `tax_jurisdiction`, `filing_name`, `filing_address`, and `notes`. Those filing-oriented fields never acquired a production maintenance/reporting workflow. V75 adds `company.ein` and backfills any nonblank legacy EIN into the corresponding company row without dropping the legacy table or its data.

After V75, `CompanyTaxProfile` is no longer a mapped production entity and `CompanyAdminService` no longer exposes a tax-profile query. The legacy table is retained only as nondestructive historical migration residue; it is not a second writable EIN authority. No tax jurisdiction, filing period, return, status, or submission workflow is provided.

## Create and edit

`CompanyAdminService.save(CompanyCommand, currentCompanyCode)` performs create or stable-ID update in one JPA transaction. It validates required values, lengths, case-insensitive code uniqueness, the fiscal date, and ISO currency before writing. EIN is optional informational text and receives only the shared trim/blank-to-null/length validation. A failure rolls the transaction back.

The existing Administration destination continues to use `AppPanelId.SETTINGS`. Its Company Admin tab provides New, Save, Select Active, Refresh, Company profile including EIN, Chart of Accounts assignment, and company reporting-default administration. It does not add a second shell destination, tax-filing panel, or administration framework.

The adjacent **Company Ownership Diagnostics** tab is a corrective legacy-data workflow, not another company editor. It lists the unresolved rows that block governed interchange, preserves the entity type, stable record ID, human-readable record description, diagnostic code, candidate count, cause, and resolution guidance, and permits a single direct ownerless row to be assigned to the active company receiving the import after an audit note and explicit confirmation. The mutation requires P20 `DATABASE_ADMIN`; its audit actor is the authenticated ADMIN username and the UI actor display is read-only. The operator's selected import target is authoritative; the workflow does not require reconstructing a separate historical company. The confirmation and result name the exact row and company. Cross-company reference conflicts remain non-assignable and explain that the underlying accounting links must be corrected in their owning workflow.

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

## Reporting defaults

P19-S2 deliberately separates reporting workflow convenience from accounting/report parameters.

`CompanyUiPreferencesService` persists two typed values through existing H2 `company_ui_state` rows under `reportingDefaults.`:

- the stable ID of the report selected when a new Report Library is opened;
- the export format selected when a new Report Library is opened.

The service exposes these as `CompanyReportingDefaults`. Missing or stale values fall back to Trial Balance/Text. No schema migration or second preference repository is introduced.

Company Admin saves reporting-default changes immediately through `CompanyUiPreferencesService`; they are not folded into the company master-data JPA transaction. The controls are disabled while scalar company-profile edits are dirty so an edited company code cannot cause a preference write under stale identity text.

A newly constructed Report Library reads these two defaults once. An already-open Report Library keeps the operator's current report/export choices. Report dates, fund selection, row limits, account filters, and fixed-asset/inventory filters remain governed by active-period/fiscal authority or the current `ReportRequest` and are intentionally not persisted as company reporting policy.

EIN is not automatically added to report headings or exports by P19-S3. Any future report/interchange consumer for EIN requires an explicit separate requirement rather than being inferred from storage.

## Active lifecycle

- A company may be selected only when an active H2 row exists.
- A missing or inactive persisted recent selection falls back to an existing active H2 company; it never creates a row.
- The current company cannot be deactivated. Another active company must be selected first.
- A save that would leave no active companies is rejected.
- Companies are deactivated rather than hard-deleted. No Company Admin Delete command is exposed.
- Editing the current company's code preserves its stable ID, EIN, and other company-owned relationships and updates the active session selection to the new code.

## Workspace switching

`CompanySessionController` coordinates the H2 service with `UiSessionState` and `AppStateStore`. It filters recent codes against active H2 companies before saving selection convenience state. It also exposes the service-owned chart list/assignment operations to Company Admin without creating a second persistence path. Company reporting defaults remain in the existing company UI preference service rather than the session controller.

The production toolbar lists only active H2 companies. A company change:

1. validates the requested H2 company;
2. prompts before discarding dirty open workspaces;
3. updates session and workspace context;
4. persists the recent-selection convenience; and
5. recreates open panels so cached services, company formatting, company-owned layout state, and new Report Library opening defaults use the new active company.

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

For P19-S1 the donor repository did not provide a compatible company-owned active-chart pointer workflow to import. For P19-S2 it likewise did not provide a compatible persisted opening-report/export-default consumer. Searches for EIN/tax-filing behavior did not identify a compatible donor implementation for P19-S3. Current H2 Company authority therefore owns informational EIN, and no donor tax-filing architecture is introduced.

## Manual validation

1. Open Administration at laptop width and select Company Admin.
2. Create a company with a non-January fiscal start, a valid non-USD currency, and an optional EIN.
3. Edit its code and confirm the same company ID and EIN remain.
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
15. Set a non-default opening report/export format in Company Admin, close/reopen Report Library, and confirm the new window uses those choices.
16. Change the report/export format inside the open Report Library and confirm Company Admin defaults do not change; modify the Company Admin default while the report stays open and confirm the open selection is not overwritten.
17. Switch companies and confirm each company restores its own opening report/export defaults while report dates/funds/filters remain transient/current-context parameters.
18. Begin a scalar company edit and confirm reporting-default controls remain disabled until that edit is saved or discarded.
19. Enter/change/clear EIN and confirm it follows ordinary Company save/reload semantics, is isolated per company, and is not presented as tax-filing configuration.
20. If a disposable pre-P19-S3 database has `company_tax_profile.ein`, migrate it and confirm the legacy EIN appears in Company Admin without losing the retained legacy table data.
21. Log in as ADMIN and open **Company Ownership Diagnostics**. For a direct ownerless test row, confirm the preselected active import company and read-only authenticated actor, enter an audit note, confirm the assignment, and verify the row disappears and an audit event names the authenticated ADMIN. Confirm a non-ADMIN account cannot perform the repair and a cross-company reference row cannot be assigned or silently dismissed.
