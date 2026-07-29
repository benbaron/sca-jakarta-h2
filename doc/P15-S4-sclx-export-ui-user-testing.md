# P15-S4 selected-company SCLX export UI — owner testing

## User-visible change

The production **File** menu now contains **Export Active Company to SCLX…**. It exports the currently selected active company through the deterministic atomic SCLX 1.3 service. It is distinct from database backup, Chart of Accounts JSON, and bank-statement exchange.

## Desktop acceptance steps

1. Open a normal production database and select the company to export.
2. Open **File → Export Active Company to SCLX…**.
3. Confirm that the chooser is labeled **SCLX Active Company Files**, proposes a `.sclx` filename containing the company code, and does not describe the file as a database backup or Chart of Accounts JSON.
4. Save to a new destination.
5. Confirm that the application remains responsive while the export runs and that the menu action is unavailable during the operation.
6. Confirm that the completion dialog names the selected company and exact destination and shows SCLX version 1.3, the export timestamp, byte count, SHA-256, included record counts including **Activities**, **Counterparties**, **Merchants**, **Supplemental details**, **Banks**, **Bank accounts**, **Import batches**, **Statement lines**, **Import issues**, **Reconciliation sessions**, and **Reconciliation matches**, warnings, deferred governed sections, and explicit exclusions.
7. Open the resulting file in a text editor and confirm that it is readable UTF-8 JSON whose root contains `"format" : "SCLX"` and `"version" : "1.3"`. Confirm that `extensions.scaJakartaH2.activities` contains the selected company’s activities and that transaction-line `activityId` values, when present, resolve to those entries. Confirm that `extensions.scaJakartaH2.counterparties` contains the selected company’s counterparties and merchants, that transaction-line `counterpartyId` values resolve to counterparty entries, and that `transactionLineMerchants` entries resolve to both a transaction line and a merchant. Confirm that `extensions.scaJakartaH2.supplementalDetails` contains the selected company’s persisted supplemental rows and that every `transactionId` resolves to a transaction in the file. Confirm that `extensions.scaJakartaH2.bankConfiguration`, `bankStatementFacts`, and `reconciliation` contain only the selected company’s configured accounts, reviewed import/statement facts, clearance relationships, sessions, and matches, with every bank/account/batch/statement/transaction-line reference resolving inside the file. Confirm that no source filesystem path or importing-user identity is present.
8. Run the export again to the same path. Cancel the replacement confirmation and confirm that the original file remains unchanged.
9. Run it a third time, confirm replacement, and confirm that the export completes successfully.
10. Switch to another company and export again. Confirm that the result dialog and file identify only the newly selected company.

## Expected current limitation

The result must visibly list the governed sections that remain deferred in P15-S4. **Activities**, **Counterparties**, **Supplemental details**, **Bank configuration**, **Bank statement facts**, and **Reconciliation** must not appear in that deferred list because those selected-company records and relationships are now exported. Other deferred records are not silently omitted or claimed as exported. This acceptance covers the production route and result presentation, not completion of those later mappings or SCLX import.

## Banking and reconciliation acceptance

Open a company that has at least one configured bank account, a reviewed statement batch, a statement line or import issue, and a reconciliation session. Export SCLX and confirm that the completion counts agree with the visible banking data. A company with no banking history must still contain the three governed extension objects with empty arrays rather than reporting those sections as deferred. Switch companies and repeat to verify strict company isolation.

## Fixed-asset portable-identity prerequisite

This migration-only prerequisite introduces no new menu action and does not add `extensions.scaJakartaH2.fixedAssets` to exported files. Fixed assets and completed depreciation runs must remain in the governed deferred-section warning until the separate mapping unit is implemented. After migration, open an existing company and run the current SCLX export route to confirm that the application and existing export continue to operate normally, with no empty fixed-assets section presented as implemented.
