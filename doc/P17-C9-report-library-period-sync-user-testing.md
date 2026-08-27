# P17-C9 Report Library active-period synchronization user testing

## User-visible changes

P17-C9 makes Report Library dates follow the shell-selected accounting period while those dates are still the panel's untouched fiscal defaults. It does not change report calculations, report definitions, export formats, Journal drill-through, accounting persistence, or the active-period authority itself.

When Report Library opens without an explicit global date range:

- Start date is the active fiscal-year start.
- End/As-of date is the end of the shell-selected accounting period.
- Changing the shell accounting period updates those default dates and regenerates the selected report.
- Editing either report date explicitly detaches the Report Library from later shell-period changes for that open panel, so the operator's report-specific range remains authoritative.
- If an explicit global date range was already supplied when Report Library opened, the panel starts detached and shell-period changes do not overwrite it.

## Manual verification

Use a disposable or ordinary test company with a fiscal year and transactions spanning at least two accounting periods.

1. Set the global date range to All Dates/default state and choose an accounting period in the production top chrome.
2. Open Report Library. Confirm the report Start date equals the active fiscal-year start and End/As-of equals the end of the selected accounting period.
3. Select Balance Sheet and note the beginning/end/difference values and heading dates.
4. Change the shell accounting period without editing the Report Library dates. Confirm the Report Library dates change to the new fiscal default and the report regenerates for that period.
5. Select Income Statement and confirm its period heading and values use the same updated range.
6. Edit either Report Library date to a deliberate custom date, then change the shell accounting period again. Confirm the custom Report Library dates do not change.
7. Press Run and confirm preview uses the custom dates. Export TEXT or CSV and confirm the export represents the same request rather than the newer shell period.
8. Close/reopen Report Library after restoring the global range to its default/All Dates state. Confirm the panel again starts from the currently selected shell accounting period.
9. If the shell can supply an explicit global date range, open Report Library with one and confirm a later accounting-period change does not overwrite that explicit range.

## Acceptance

Accept P17-C9 when these checks pass and GitHub Maven PR Tests are green on the final branch head. Stop before merge for owner acceptance unless the owner separately authorizes merge.
