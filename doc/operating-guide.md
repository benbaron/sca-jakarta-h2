# SCA Bookkeeping operating guide

This guide is a quick reference to the current production workspace. For task-based procedures, use the [Word user manual](user-manual.docx), the authoritative manual for direct editing and review. Verify the active company in the toolbar; select accounting defaults in Period Close. Commands are permission-gated; a disabled command explains the missing role, selection, preview or other prerequisite.

## Company setup and master data

Open **Administration**, then use its **Company Admin** tab for company settings. Use the navigation destinations **Chart of Accounts**, **Funds**, **Budget Categories**, **Payees / Counterparties**, **Merchants**, and **Events / Activities** for maintained master records. Create and search events by name or code in **Events / Activities**. Referenced historical records normally deactivate rather than delete.

Do not edit H2 directly or maintain event/category/payee/merchant identities in an external spreadsheet. The application saves stable IDs and preserves inactive historical dimensions.

## Journal and corrections

Use **Journal** under **ACCOUNTING** for canonical balanced entries. Choose Fund and Event independently on each line. Enter structured payment/check references in their fields rather than the memo. Use the supplemental detail editors for receivables, payables, advances, deposits, prepaid/deferred balances and other liabilities. Explicit NMR recipient/movement/evidence, Merchant facts and AR/OA/FR functional classifications remain entered facts; blank values remain visible as unclassified or unknown.

Use the configured correction method. Direct edit/delete is available only when policy and protections permit it; otherwise create a reversal or replacement. Closed periods and finalized reconciliation protect affected entries.

## Events, budgets and transfers

Use **Event Accounting** under **ACCOUNTING** to select an event by name/code and review income, expense, net result, line detail and related configured-bank context. Open a selected transaction in Journal for correction. No memo inference or clearing-account workaround is required for event attribution.

Use **Budget Editor** and **Budget vs Actual** under **PLANNING** for versioned Fund/Event plans and actual comparison. Use **Fund Transfers** under **ADMINISTRATION** for same-company internal allocations; its Save creates the balanced canonical entry and retained transfer fact atomically. Bank-account movement and inter-entity payment remain Journal/banking operations rather than internal fund transfers.

## Payments, checks and reconciliation

Open **Banking** under **ACCOUNTING**. One shared bank/account selector controls **Accounts**, **Bank journal**, and **Reconcile**. Accounts shows the bank, masked number, full ledger/chart identity and configured account's Active status; use **Bank / account settings…** for bank maintenance and new links. Deactivation retains history.

Enter payment references in Journal or edit an eligible instrument in **Bank journal**. Its cleared flag/date describe this bank account only. A transfer's other bank account clears independently. **Open complete journal entry** opens the canonical entry for permitted corrections.

In **Reconcile → Statement setup**, import OFX/QFX or CSV, or enter a statement header and lines manually. Enter statement manually reveals inline fields with Save statement entry / Cancel entry. Use **Match entries** for the paired tables. Imported dates and ending balance are offered for explicit review. For the first statement, confirm a known opening amount/date and review the starting journal position and outstanding items; the application does not assume zero or create an opening entry. Later statements inherit the preceding completed closing statement balance for that account. Beginning book balance is shown separately.

Select equal-amount statement and bank-journal items, then **Match and clear**. One instrument's Fund allocations match together; normal journal and bank dates can differ. **Unmatch** restores the prior clearing facts. Outstanding checks carry forward. **Save progress** retains unfinished work; **Complete reconciliation** requires a known opening, zero difference, all statement items accounted for and no unresolved source errors. Completed history is read-only. Erroneous unlinked manual statement rows can be removed with their source/audit history retained; ledger corrections use Journal.

Use **Checks / exceptions…** within Reconcile for delivery, review, Void and Reissue. Save Review changes review facts; Void and Reissue retain correction/reference history without duplicating the original expense. **Statements / export…** retains account-scoped review, explicit journal acceptance and statement export. Import alone does not post ledger entries.

## Operational assistants

- **Recognition Assistant** under **PLANNING**: Preview prepaid/deferred proposals, then **Save Selected Recognition**. Preview does not write.
- **Merchant Settlement Assistant** under **ACCOUNTING**: enter the bank payout, explicit fee and optional evidenced NMR discharge; Preview, then **Save Settlement**. Chargebacks/refunds use Journal correction.
- **Opening Conversion** under **IMPORT & OVERSIGHT**: inspect opening ledger/register/schedule ties; **Save Conversion Acceptance** records one immutable acceptance fact and creates no balancing entry.
- **Inventory** under **ASSETS & INVENTORY**: maintain items and preview quantity/value movements. Moving weighted average is authoritative for capitalized stock; expensed supplies remain zero-value inventory facts.
- **Asset Register** under **ASSETS & INVENTORY**: save acquisitions and governed depreciation/disposal history. Use **Property Custody** to append assignment, confirmation, loan, return, transfer or removal facts; custody-only events do not post accounting.

## Reports, packet and close

Use **Report Library** under **REPORTS** for reports including Event Accounting detail, functional expenses, NMR liabilities/movements, merchant receivable movements, inventory valuation and asset/inventory schedules. Report results are read-only; use **Export** rather than Save.

Use **Export Accounting Packet (all funds/events)** for the generic quarter packet. Choose a new destination. The packet contains one-snapshot statements, ledger and operational sections plus a manifest and readiness exceptions. It does not claim an official external template or complete-ready status when a source/control difference remains.

Use **Period Close** under **IMPORT & OVERSIGHT**:

1. Set Start/End and choose **Preview Readiness**.
2. Open correction destinations for blockers.
3. In **Review Evidence**, record external references, factual attestations and reasons for every warning, then **Save Review**.
4. **Save Closed Range** revalidates the same scope under the database write guard. Reopen requires a reason; reclosing requires a fresh review.

External receipts, attachments, signatures and approvals stay in the organization's custody. The application stores references and attestations; it does not enforce independent approval.

## SCLX selected-company interchange

Choose **File -> Export Active Company to SCLX…** to write deterministic SCLX 1.3 for the active company. Import through **Import** under **IMPORT & OVERSIGHT**. Review mappings, conflicts, unsupported records and required confirmations, re-preview after every choice, then **Save Previewed SCLX Import**.

Readers accept SCLX 1.0, 1.2 and 1.3; writers emit 1.3. SCLX is selected-company business-data interchange. It excludes credentials, UI preferences, schema internals, raw attachments and other companies. Keep a whole-database backup for complete recovery.

## Database backup and recovery

Open **Administration**, then its **Database Transfer** tab, or use the File menu.

1. Choose **Backup Database…**. The confirmation dialog is titled **Back Up Database**. Select a new ZIP path; existing destinations are rejected. Record its SHA-256 and displayed company/transaction/split counts.
2. Choose **Restore Database Copy…**, select the backup ZIP and a new target database path. Restore never overwrites or silently switches the active database.
3. Review migration and validation results, then choose **Switch to Validated Copy** when ready. The confirmation dialog is titled **Switch Database**.
4. Confirm the active path/company and reopen Journal, master data and reports. Retain the source backup until the restored copy is accepted.

Only users with database-administration authority can back up, restore or switch after login. Never replace an active database file manually, edit Flyway history or use SCLX as a complete-database backup.
