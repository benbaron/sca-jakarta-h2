# Actionable error buttons

Chart JSON review now shows the reason import is blocked beside the import
controls. A chart-wide error belongs in that summary. A row error belongs on
the affected proposal, and its action navigates by source identity so sorting
does not change which account opens. Technical codes and original locations
remain in Validation Messages.

Use a remedy when the application knows the affected record or field and can
take the user to it safely. Use typed error codes and stable record IDs. Open
the relevant editor or review without writing data. Preserve pending work and
re-evaluate the result after correction. When there are two legitimate
resolutions, label both choices and explain their consequences. Errors with
ambiguous causes or no available target should show the facts and next step
without guessing a correction.

| Workflow inspected | Current behavior | Suggested remedy |
|---|---|---|
| [SCLX export](../src/main/java/org/nonprofitbookkeeping/ui/SclxExportCoordinator.java) | Failure window shows detailed record and ownership information. | Link a named bank link to **Banking → Accounts**, an offending journal entry to its editor, or an ownership issue to **Company Ownership Diagnostics**. Route by IDs carried by the validation result. |
| [Exchequer CSV review](../src/main/java/org/nonprofitbookkeeping/ui/ExchequerImportReviewPane.java) | Lookup fields already provide Add links and keep the review when the user returns. | Add **Fix account**, **Fix fund**, or **Review duplicates** to the batch blockers. Select the proposal and allocation before focusing its lookup. Reuse the existing Add flow from there. |
| [Journal](../src/main/java/org/nonprofitbookkeeping/ui/JournalWorkspacePanel.java) | Save validation reports errors and totals inline. Setup links are available separately. | Use **Go to line** for an invalid account, amount or dimension. Use **Open Period Close** for a closed date when the date and period are known. An imbalance can focus the amounts and show the difference without choosing which amount to change. |
| [Period Close](../src/main/java/org/nonprofitbookkeeping/ui/PeriodCloseRunsPanel.java) | The close action is gated by blockers, a current recorded review and warning reasons. Review Evidence is a separate tab. | Add **Open Review Evidence**, **Review warning**, or a source-record link next to the corresponding close blocker. Keep the period and company context. |
| [Banking and reconciliation](../src/main/java/org/nonprofitbookkeeping/ui/BankingWorkspacePanel.java) | The workspace already links to statement import, Journal correction and creation of a missing Journal entry. | Attach those actions to a specific unmatched statement or ledger row. Use **Review selected bank account** for a target mismatch and carry the bank account ID and statement context. |
| [SCLX and bank file import](../src/main/java/org/nonprofitbookkeeping/ui/ImportPreviewPanel.java) | Mapping and approval controls are available, while the disabled-import reason can summarize several prerequisites. | Add **Resolve mapping** for the exact source account or fund, **Review approvals** for incomplete confirmation, and **Preview again** when the source changed. Preserve completed choices that remain valid. |
| [Opening Conversion](../src/main/java/org/nonprofitbookkeeping/ui/OpeningConversionPanel.java) | Preview lists blocking checks before acceptance. | Link each check to its account or conversion input when an ID is available, then provide **Preview again**. |
| [Chart deletion](../src/main/java/org/nonprofitbookkeeping/ui/ChartOfAccountsPanel.java) | The deletion guard names protected reference categories. | Offer **Show child accounts** or **Open referencing records** when the service can supply precise IDs. Keep protection decisions in the service. |
| [Database recovery](../src/main/java/org/nonprofitbookkeeping/ui/DatabaseRecoveryPanel.java) | Retry, repair, select and create are already visible remedies. | Keep this pattern. Highlight Retry for a temporary open problem and the available recovery route for a verified migration failure. |
