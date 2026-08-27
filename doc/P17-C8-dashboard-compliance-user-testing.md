# P17-C8 Dashboard production-compliance user testing

## User-visible changes

P17-C8 corrects Dashboard navigation wording and removes one enabled self-targeting control. It does not change accounting, Dashboard calculations, Journal behavior, SCLX parsing/commit behavior, reconciliation logic, or persistence.

Expected Dashboard changes:

- Recent Transactions footer shows **View Journal →** rather than **View Ledger Register →**.
- Quick Links shows **Import SCLX File** with file/preview wording rather than **Import SCLX Workbook**.
- The former **All Quick Links →** footer is absent; the Quick Links card itself remains present.
- **New Transaction**, **Enter Journal Entry**, **Import SCLX File**, and **Reconcile Bank Account** remain usable and route to their existing governed workflows.

## Manual verification

Use a disposable or ordinary test company with the production workspace.

1. Open Dashboard and confirm there is no visible **Ledger Register**, **Transaction Editor**, **Import SCLX Workbook**, or **All Quick Links** wording.
2. In Recent Transactions, select **View Journal →** and confirm the existing canonical **Journal** tab opens or is selected; no second Ledger Register tab appears.
3. Return to Dashboard and choose **New Transaction**. Confirm the unified Journal opens in the existing new-entry workflow.
4. Return to Dashboard and choose **Enter Journal Entry**. Confirm the same canonical Journal workspace is reused rather than opening a separate Transaction Editor tab.
5. Return to Dashboard and choose **Import SCLX File**. Confirm Import Preview opens. Do not commit an import merely for this navigation test.
6. Return to Dashboard and choose **Reconcile Bank Account**. Confirm Bank Reconciliation opens.
7. Choose the Budget vs Actual Dashboard footer and confirm Budget vs Actual opens.
8. Confirm the Quick Links card has no footer action that merely returns to Dashboard.
9. Resize the workspace through normal laptop-width and narrower/wider layouts and confirm the corrected labels remain readable and no Dashboard card is clipped by the change.

## Acceptance

Accept P17-C8 when all steps above pass and GitHub Maven PR Tests are green on the final branch head. Stop before merge for owner acceptance unless the owner separately authorizes merge.
