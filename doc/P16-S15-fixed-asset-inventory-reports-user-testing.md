# P16-S15 Fixed-asset and inventory reports — owner testing

## Preconditions

- Use a disposable migrated database with two active companies.
- In the test company, prepare at least one fixed asset with a completed depreciation run, one impairment and reversal, and one disposed asset if available.
- Prepare one valued inventory item with a receipt and issue plus one zero-value/nonfinancial movement.
- Keep the other company's asset and inventory names recognizable so isolation is easy to verify.

## Report catalog and controls

1. Open **Report Library** and confirm these selectable reports exist: **Fixed Asset Register**, **Fixed Asset Depreciation History & Schedule**, **Inventory On Hand & Valuation**, and **Inventory Movement History**.
2. Select each report. Confirm as-of reports show only an as-of date and range reports show start/end dates.
3. Confirm Fund, Maximum rows, Control account, Asset/Item, and Status controls appear for these reports and remain usable at laptop width without clipping the preview.
4. Switch between the asset and inventory reports. Confirm choices belong to the active company and the subject label changes between Asset and Item.

## Fixed-asset results

1. Run **Fixed Asset Register** before and after a disposal/reversal date. Confirm status, recognized cost, accumulated depreciation, impairment, and book value follow the lifecycle dates.
2. Apply fund, control-account, status, and single-asset filters individually and together. Confirm the preview context names the exact stable selections and no other-company asset appears.
3. Confirm Domain total, Ledger control total, and Difference rows are visible. If a shared account or fund-scoped opening balance causes a difference, confirm it remains explicit and is explained.
4. Run **Fixed Asset Depreciation History & Schedule** across a completed run and impairment reversal. Confirm original and reversal transaction IDs are visible and the reversal amount is negative.
5. Confirm schedule-summary rows say they are projections and do not claim that future journal transactions exist.

## Inventory results

1. Run **Inventory On Hand & Valuation** before the first movement, between movements, and after the latest movement. Confirm quantity and value follow persisted movement history rather than today's quantity.
2. Apply fund, control-account, status, and single-item filters. Confirm no other-company item appears.
3. Confirm valuation, canonical inventory control total, exact difference, unlinked movement net, and any fund-unallocated opening balance are visibly labeled.
4. Run **Inventory Movement History** across linked and unlinked movements. Confirm signed quantities/values, resulting quantity, real transaction ID where present, and **Nonfinancial / no canonical transaction** where absent.
5. Narrow the date range to an empty period and confirm the report remains stable with zero/reconciliation summary rows and no inferred movement.

## Preview, export, and drill-through parity

1. For one filtered asset report and one filtered inventory report, record the request controls and visible totals.
2. Export TEXT, CSV, PDF, and XLSX. Confirm each export describes the same report, rows, transaction identities, totals, and differences as the preview; CSV numeric cells remain raw decimal values.
3. Use **Drill to Journal** and confirm the context reflects the same report name, dates, fund, row limit, and asset/item/account/status filters.
4. Change one filter after preview and export again. Confirm export regenerates from the new request and does not reuse the stale preview.

## Acceptance

- [ ] All four reports are present and usable at desktop and laptop widths.
- [ ] Company/date/fund/account/status/asset-or-item scope is exact.
- [ ] Lifecycle reversals and linked/unlinked inventory identities are truthful.
- [ ] Domain and canonical totals agree or show an exact retained difference.
- [ ] Preview, TEXT, CSV, PDF, XLSX, and Journal drill-through use one request.
- [ ] No missing transaction, future depreciation, or cross-company fact is inferred.

Owner result: PENDING

Notes:

-
