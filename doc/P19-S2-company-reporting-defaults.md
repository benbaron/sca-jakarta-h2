# P19-S2 — Company reporting defaults

## Purpose

P19-S2 completes the narrow Company Admin reporting-default workflow that has real production consumers today. It does not create accounting/report policy, a second preference store, or remembered report requests.

## Persisted authority

The existing H2 `company_ui_state` table and `CompanyUiPreferencesService` remain the single authority for company-owned workflow/display state. No Flyway migration is required.

P19-S2 persists exactly two values under the `reportingDefaults.` prefix:

- `reportingDefaults.defaultReportId` — the stable `ReportDefinition.id()` to select when a new Report Library is created;
- `reportingDefaults.defaultExportFormat` — the `FinancialReportExportFormat` enum name selected when a new Report Library is created.

`CompanyReportingDefaults` is the typed service projection. Missing, blank, removed, or invalid saved values safely fall back to **Trial Balance** and **Text** so stale UI state cannot prevent Report Library startup.

## Deliberately transient values

The following are not company reporting defaults and are not persisted by P19-S2:

- report start/end/as-of dates;
- fund selection;
- row limit;
- account filter;
- fixed-asset or inventory selection/status filters.

Those values are governed by the active accounting period/fiscal-range contract or are deliberate parameters of the current `ReportRequest`. Persisting them as company policy would compete with the existing reporting authority and could silently widen/narrow a later report.

## Company Admin behavior

The existing **Administration → Company Admin** editor adds a **Reporting defaults** section with:

- **Default opening report**;
- **Default export format**.

The controls are enabled only for a persisted company whose scalar profile is not dirty. A change is saved immediately through `CompanyUiPreferencesService.saveReportingDefaults(...)`; it is not bundled into the `CompanyAdminService.save(...)` JPA transaction because these values are UI/workflow preferences rather than company master-data fields.

If the company code is being edited, reporting-default controls are disabled until the scalar company profile is saved or discarded. This prevents a preference write from being attached to a stale business code.

## Report Library behavior

A newly constructed `ReportLibraryPanel` loads `CompanyReportingDefaults` for the active company exactly once and uses them for its initial report selection and export-format combo.

After the panel opens:

- changing reports is transient to that open panel and does not rewrite the company default;
- changing export format is transient to that open panel and does not rewrite the company default;
- changing the Company Admin default does not replace the operator's selection in an already-open Report Library;
- reopening/recreating Report Library after a company switch uses the newly active company's defaults.

All existing date/fiscal behavior, report execution, export adapters, filters, table state, and report query services remain unchanged.

## Donor decision

The donor `benbaron/NonprofitAccounting` repository was searched for a persisted default-report/export-format workflow. No compatible production consumer/persistence implementation was found. P19-S2 therefore follows the current H2 company-state authority rather than importing donor static or serialized preference state.

## Validation

Automated coverage must prove:

- defaults are company-isolated and round-trip through existing H2 company UI state;
- missing/stale report IDs and export-format values fall back safely;
- Company Admin exposes both real controls and no longer labels reporting-default administration as deferred;
- Report Library loads company reporting defaults instead of hard-coding Trial Balance/Text;
- no migration or report execution/query change is introduced;
- full repository Maven PR Tests pass: clean verification, repeat tests, and production JavaFX route compliance.

Owner verification is recorded in `doc/P19-S2-company-reporting-defaults-user-testing.md`.
