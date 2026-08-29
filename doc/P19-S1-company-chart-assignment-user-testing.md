# P19-S1 — Company Chart of Accounts assignment owner verification

## User-visible changes

Company Admin now contains a **Chart of Accounts assignment** section for each persisted company.

- It lists only charts owned by the selected company.
- The current chart is identified in the selector.
- **Make Active Chart** performs a real service-owned change after confirmation.
- Selecting a DRAFT chart promotes it to ACTIVE.
- Existing charts/accounts/history are not moved, deleted, or automatically retired.
- RETIRED charts cannot be selected.

## Manual checks

Use a disposable database/company with an existing active chart. Create or import a second company-owned Chart of Accounts as DRAFT before beginning.

1. Open **Administration → Company Admin**, select the active company, and confirm the **Chart of Accounts assignment** section is visible inside the scrollable editor.
2. Confirm the selector shows the existing current chart as `current` and also shows the second DRAFT chart.
3. Select the DRAFT chart. Confirm **Make Active Chart** becomes enabled and the confirmation explains that new chart-targeted operations will change while existing accounting history will not be moved or deleted.
4. Cancel once and confirm no chart/status changes occur.
5. Repeat and accept. Confirm the selected chart becomes `ACTIVE` and is marked current after the UI reloads.
6. Reopen Company Admin and confirm the selection persists from H2.
7. Confirm the formerly current chart is still present and remains ACTIVE rather than being silently retired.
8. Open Chart of Accounts and confirm account maintenance now shows/uses the newly selected chart. Re-select an already-open Chart of Accounts tab if necessary and confirm its normal refresh picks up the new pointer.
9. Inspect a pre-existing account from the old chart and confirm it still belongs to that old chart. Confirm historical Journal/report facts were not rewritten by the assignment.
10. Preview a Chart of Accounts JSON `MERGE_BY_CODE` import and confirm it targets the newly selected current chart.
11. Create/import another Chart of Accounts JSON with `CREATE_NEW_CHART`; confirm it remains DRAFT until explicitly selected in Company Admin.
12. If a RETIRED chart exists, select it in the combo and confirm **Make Active Chart** remains disabled (and service validation rejects it if invoked programmatically).
13. Switch to another company and confirm the chart choices change to only that company's owned charts; no chart from the first company appears.
14. Make an unsaved scalar Company Admin edit and confirm chart assignment is disabled until the company edit is saved or discarded.
15. At normal laptop width and a narrower window, confirm the company table remains independently scrollable, the vertical divider remains draggable, and the chart assignment controls are reachable through the editor scroll pane with full text available through normal production tooltip behavior.

## Acceptance boundary

A green workflow proves compile/regression/route compliance, not desktop observation. If any check reveals that assignment moves historical data, exposes a foreign chart, or silently changes a retired/history lifecycle fact, do not accept the slice; record the defect as a corrective slice.
