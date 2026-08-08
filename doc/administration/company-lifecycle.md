# Company lifecycle and active-company authority

## Authority

The `company` table is authoritative for company existence and active/inactive lifecycle. `MultiCompanyState` is only a recent-selection convenience. A code found only in sidecar state is not a company and must never become the active workspace company.

`Company.id` is stable record identity. The code is a unique business label and may be edited without creating a second company row. Code-keyed company UI preferences/state and period-close history move to the new code in the same transaction; company-owned tables that use `company_id` remain attached automatically.

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

Tax filing, chart assignment, and expanded reporting-default editors are not exposed as enabled placeholder controls. Bank accounts remain in the Banking workspace. Those areas require separate vertically complete persistence workflows before they return to Company Admin.

## Create and edit

`CompanyAdminService.save(CompanyCommand, currentCompanyCode)` performs create or stable-ID update in one JPA transaction. It validates required values, lengths, case-insensitive code uniqueness, the fiscal date, and ISO currency before writing. A failure rolls the transaction back.

The existing Administration destination continues to use `AppPanelId.SETTINGS`. Its Company Admin tab provides New, Save, Select Active, and Refresh operations. It does not add a second shell destination or administration framework.

The center workspace stacks the company table above the profile editor. A horizontal draggable divider separates them, and the editor keeps its own vertical scrolling so the full-width form remains usable at laptop dimensions. The divider position remains company-owned UI state.

## Active lifecycle

- A company may be selected only when an active H2 row exists.
- A missing or inactive persisted recent selection falls back to an existing active H2 company; it never creates a row.
- The current company cannot be deactivated. Another active company must be selected first.
- A save that would leave no active companies is rejected.
- Companies are deactivated rather than hard-deleted. No Company Admin Delete command is exposed.
- Editing the current company's code preserves its stable ID and updates the active session selection to the new code.

## Workspace switching

`CompanySessionController` coordinates the H2 service with `UiSessionState` and `AppStateStore`. It filters recent codes against active H2 companies before saving selection convenience state.

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
