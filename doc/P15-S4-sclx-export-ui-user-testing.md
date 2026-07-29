# P15-S4 selected-company SCLX export UI — owner testing

## User-visible change

The production **File** menu now contains **Export Active Company to SCLX…**. It exports the currently selected active company through the deterministic atomic SCLX 1.3 service. It is distinct from database backup, Chart of Accounts JSON, and bank-statement exchange.

## Desktop acceptance steps

1. Open a normal production database and select the company to export.
2. Open **File → Export Active Company to SCLX…**.
3. Confirm that the chooser is labeled **SCLX Active Company Files**, proposes a `.sclx` filename containing the company code, and does not describe the file as a database backup or Chart of Accounts JSON.
4. Save to a new destination.
5. Confirm that the application remains responsive while the export runs and that the menu action is unavailable during the operation.
6. Confirm that the completion dialog names the selected company and exact destination and shows SCLX version 1.3, the export timestamp, byte count, SHA-256, included record counts including **Activities**, **Counterparties**, **Merchants**, and **Supplemental details**, warnings, deferred governed sections, and explicit exclusions.
7. Open the resulting file in a text editor and confirm that it is readable UTF-8 JSON whose root contains `"format" : "SCLX"` and `"version" : "1.3"`. Confirm that `extensions.scaJakartaH2.activities` contains the selected company’s activities and that transaction-line `activityId` values, when present, resolve to those entries. Confirm that `extensions.scaJakartaH2.counterparties` contains the selected company’s counterparties and merchants, that transaction-line `counterpartyId` values resolve to counterparty entries, and that `transactionLineMerchants` entries resolve to both a transaction line and a merchant. Confirm that `extensions.scaJakartaH2.supplementalDetails` contains the selected company’s persisted supplemental rows and that every `transactionId` resolves to a transaction in the file.
8. Run the export again to the same path. Cancel the replacement confirmation and confirm that the original file remains unchanged.
9. Run it a third time, confirm replacement, and confirm that the export completes successfully.
10. Switch to another company and export again. Confirm that the result dialog and file identify only the newly selected company.

## Expected current limitation

The result must visibly list the governed sections that remain deferred in P15-S4. **Activities**, **Counterparties**, and **Supplemental details** must not appear in that deferred list because activities, parties, their supported transaction references, and canonical supplemental transaction rows are now exported. Other deferred records are not silently omitted or claimed as exported. This acceptance covers the production route and result presentation, not completion of those later mappings or SCLX import.

## Banking portable-identity prerequisite

This migration-only unit introduces no new menu item or visible banking export section. After applying the migration,
open an existing company and confirm the application and current SCLX export still operate normally. Banking
configuration, statement-review, and reconciliation sections must continue to appear as deferred until the following
mapping unit is merged; this prerequisite must not make an empty banking section appear implemented.
