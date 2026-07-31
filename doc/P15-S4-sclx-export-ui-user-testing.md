# P15-S4 selected-company SCLX export UI — owner testing

## User-visible change

The production **File** menu contains **Export Active Company to SCLX…**. It exports the currently selected active company through the deterministic atomic SCLX 1.3 service. It is distinct from database backup, Chart of Accounts JSON, and bank-statement exchange.

## Desktop acceptance steps

1. Open a normal production database and select the company to export.
2. Open **File → Export Active Company to SCLX…**.
3. Confirm that the chooser is labeled **SCLX Active Company Files**, proposes a `.sclx` filename containing the company code, and does not describe the file as a database backup or Chart of Accounts JSON.
4. Save to a new destination.
5. Confirm that the application remains responsive while the export runs and that the menu action is unavailable during the operation.
6. Confirm that the completion dialog names the selected company and exact destination and shows SCLX version 1.3, the export timestamp, byte count, SHA-256, and exact included record counts. The detailed counts must include **Activities**, **Counterparties**, **Merchants**, **Supplemental details**, **Banks**, **Bank accounts**, **Import batches**, **Statement lines**, **Import issues**, **Reconciliation sessions**, **Reconciliation matches**, **Fixed assets**, **Depreciation runs**, **Inventory items**, **Inventory movements**, **Period-close ranges**, **Period-close events**, and **Audit events**. The dialog must also disclose explicit exclusions.
7. Confirm that the completion dialog reports no deferred governed export sections and no deferred-section warnings. Authentication material, UI state, database internals, filesystem paths, raw attachments, compatibility authorities, generic job history, and other-company records remain explicit exclusions rather than deferred export work.
8. Open the resulting file in a text editor and confirm that it is readable UTF-8 JSON whose root contains `"format" : "SCLX"` and `"version" : "1.3"`.
9. Confirm that `extensions.scaJakartaH2.activities` contains the selected company’s activities and that transaction-line `activityId` values, when present, resolve to those entries.
10. Confirm that `extensions.scaJakartaH2.counterparties` contains the selected company’s counterparties and merchants, that transaction-line `counterpartyId` values resolve to counterparty entries, and that `transactionLineMerchants` entries resolve to both a transaction line and a merchant.
11. Confirm that `extensions.scaJakartaH2.supplementalDetails` contains the selected company’s persisted supplemental rows and that every `transactionId` resolves to a transaction in the file.
12. Confirm that `extensions.scaJakartaH2.bankConfiguration`, `bankStatementFacts`, and `reconciliation` contain only the selected company’s configured accounts, reviewed import/statement facts, clearance relationships, sessions, and matches, with every bank/account/batch/statement/transaction-line reference resolving inside the file.
13. Confirm that `extensions.scaJakartaH2.fixedAssets` contains only the selected company’s assets and completed depreciation runs, and that every account, fund, fixed-asset, and canonical transaction reference resolves within the file.
14. Confirm that `extensions.scaJakartaH2.inventory` contains only the selected company’s inventory items and factual movements, and that every account, fund, item, and canonical transaction reference resolves within the file.
15. Confirm that `extensions.scaJakartaH2.periodClose` contains the selected company’s authoritative calculated/custom close ranges and factual close/reopen events, with every event referencing a range in the same extension.
16. Confirm that `extensions.scaJakartaH2.auditHistory` contains only factual `AuditEvent` rows owned by the selected company. Verify that actors, action/entity types, optional subject identifiers, summaries, before/after values, reasons, and timestamps are preserved, and that legacy approval-workflow records are not substituted.
17. Confirm that portable identities do not expose local numeric database IDs. Audit-event identities must use the `audit-event:<company-code>:<uuid>` namespace. Confirm that no source filesystem path, credential, password hash, login state, or importing-user identity is present.
18. Run the export again to the same path. Cancel the replacement confirmation and confirm that the original file remains unchanged.
19. Run it a third time, confirm replacement, and confirm that the export completes successfully.
20. Switch to another company and export again. Confirm that the result dialog and file identify and contain only the newly selected company.

## Banking and reconciliation acceptance

Open a company that has at least one configured bank account, a reviewed statement batch, a statement line or import issue, and a reconciliation session. Export SCLX and confirm that the completion counts agree with the visible banking data. A company with no banking history must still contain the three governed extension objects with empty arrays rather than reporting those sections as deferred. Switch companies and repeat to verify strict company isolation.

## Fixed-assets and depreciation acceptance

Open or create a company with at least one fixed asset and one completed depreciation run. Export SCLX and verify that each asset preserves the register fields and account/fund references and that each completed depreciation run references both the exported asset and the canonical transaction created by the run. Verify that an absent optional asset or run note is omitted rather than serialized as empty text. Repeat with a company that has no fixed assets; the extension must still be present with empty arrays and must not produce a warning.

## Inventory acceptance

Open or create a company with an inventory item and at least one factual movement. Export SCLX and verify the item’s type, quantity, unit, unit value, acquisition date, custodian, location, condition, status, notes, inventory account, fund, and timestamps. Verify each movement’s item reference, date, type, quantity change, resulting quantity, value, optional canonical transaction, notes, and timestamp. Repeat with an empty inventory company; both arrays must be present and empty.

## Period-close and factual audit-history acceptance

Use a company with a closed range, a reopened range or reopen event, and several factual audit events. Export SCLX and verify the period-close counts and range/event relationships. Verify the audit-event count against the company-owned factual audit history and inspect at least one event with before/after values and one event with omitted optional values. Switch companies and confirm that neither extension contains records from the prior company. A company with no owned audit events must still contain `auditHistory` version 1 with an empty `events` array and no deferred warning.
