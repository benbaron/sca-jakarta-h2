# P18-S1 — Accounting-period depreciation owner verification

## User-visible changes

Depreciation Runs is now an accounting-period workflow rather than a one-asset/arbitrary-date form.

- The selected shell accounting period and company-configured period start day determine the preview period.
- The posting date is the period end.
- The upper table previews every fixed asset with its proposed amount, disposition, and reason.
- **Run Eligible Depreciation** confirms the eligible count and total before committing.
- Each asset remains an independently atomic canonical depreciation transaction/run.
- **Open Depreciation Report** opens Report Library with the selected period range for the existing Fixed Asset Depreciation History & Schedule report.

## Manual checks

Use a disposable database/company with at least two active depreciable assets and, if practical, one inactive/disposed asset.

1. Set an accounting period whose configured start day is not the first of the month. Open Depreciation Runs and confirm the displayed start/end match the shell period and Settings start day.
2. Confirm the posting date shown is the accounting-period end and there is no arbitrary run-date field.
3. Confirm the preview table shows eligible assets, proposed company-formatted amounts, and explanatory dispositions for excluded assets.
4. Run the eligible batch and inspect the confirmation text before accepting. Confirm it states that each asset is independently atomic and that prior successes remain committed if a later asset fails.
5. Accept the run. Confirm each successful asset creates its own completed depreciation row and canonical transaction ID; refresh/reopen and confirm the facts persist.
6. Preview the same period again. Confirm successfully completed assets are `ALREADY_RUN` and are not proposed for another period run.
7. Change the shell to the next accounting period. Confirm the Depreciation Runs preview refreshes to that period and proposes the next amount only for currently eligible assets.
8. If a later depreciation run exists, select an earlier period and confirm that asset is excluded with `LATER_RUN_EXISTS` rather than being backfilled out of chronology.
9. Use **Open Depreciation Report**. Confirm Report Library opens with the period date range, then select **Fixed Asset Depreciation History & Schedule** if necessary and verify the completed run rows/transaction IDs and schedule summary agree with the depreciation workspace.
10. At the normal 1440x900 target and a narrower laptop window, confirm both tables remain independently scrollable, the divider is draggable, columns can be sorted/resized/reordered, and full values remain reachable.
11. Switch companies and confirm amounts/dates/table state use the newly active company and no prior-company asset preview remains authoritative.

## Acceptance boundary

A green workflow proves automated regression/build coverage, not desktop observation. Do not mark P18-S1 complete until the owner accepts the manual checks above. Any accounting mismatch should become a corrective slice rather than being hidden by UI wording.
