# P19-S2 — Company reporting defaults owner verification

## User-visible changes

Company Admin now exposes two safe Report Library defaults for each persisted company:

- **Default opening report**;
- **Default export format**.

They affect only a newly opened Report Library. They do not save report dates, fund/account filters, row limits, asset/item filters, or overwrite an already-open report selection.

## Manual checks

Use a disposable database with at least two active companies.

1. Open **Administration → Company Admin**, select company A, and confirm the **Reporting defaults** section appears below the Chart of Accounts assignment section.
2. Choose **Balance Sheet** as the default opening report and **PDF** as the default export format. Confirm the status says the reporting defaults were saved and will apply the next time Report Library opens.
3. Close the Report Library tab if it is already open, then open **Report Library**. Confirm Balance Sheet is selected and the export-format combo shows PDF.
4. In that open Report Library, select another report and another export format. Return to Company Admin and confirm the stored defaults still show Balance Sheet/PDF; normal use of Report Library must not rewrite the company defaults.
5. While Report Library remains open on the operator-selected report, change the company default in Company Admin. Confirm the already-open Report Library selection is not replaced.
6. Close and reopen Report Library. Confirm the newly configured default is now used.
7. Change the shell accounting period and confirm the existing Report Library date/default-following behavior remains unchanged. Edit report dates explicitly and confirm they still detach from later shell-period changes.
8. Confirm fund, row-limit, account, fixed-asset, inventory, and status choices are not added to Company Admin and remain report-specific controls.
9. Switch to company B. Confirm its reporting defaults initially use Trial Balance/Text unless separately configured, proving company isolation.
10. Configure company B differently, switch back to company A, close/reopen Report Library, and confirm company A's defaults are restored.
11. Begin editing company A's code or another scalar profile field. Confirm the reporting-default controls become disabled until the company profile is saved or discarded, preventing a preference write under a stale code.
12. At normal laptop width and a narrower window, confirm the new controls remain reachable in the Company Admin editor scroll and full visible text remains available through normal production tooltip behavior.

## Acceptance boundary

A green workflow proves compile/regression/route compliance, not desktop behavior. Do not accept P19-S2 if changing defaults alters accounting data, report dates/filters, an already-open report selection, or another company's preferences.
