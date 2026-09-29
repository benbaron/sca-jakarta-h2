# P23-S5 SCLX lifecycle user testing

SCLX export/import now preserves item identities and linked allocations for receivables, payables, prepaid expenses, deferred revenue, other assets and other liabilities. No database migration is required.

Use disposable source and destination databases, with an open period and a current database backup.

1. Enter an item opening of 100 dated January 10 and an application of 25 dated February 10. Export through the production SCLX command and import into an empty company. Preview should not request legacy allocation acknowledgment for fully linked control lines. Existing mapping/company approvals still apply.
2. Confirm the imported item has the same Item ID and shows 100 at January 31 and 75 at February 28. Journal detail must reference the correct ledger split. Repeat the import: transaction/allocation counts must not increase.
3. Reverse the application on March 5; repeat export/import into another empty test company. February must remain 75 and March become 100. Repeat with an opening reversal and replacement and verify the same dated balances. Check report reconciliation/readiness as well as item totals.
4. Repeat for all six kinds and a transaction with multiple control lines, including repeated account/fund pairs. Export the destination again and verify lifecycle IDs/references remain intact.
5. Preview a disposable copy with an invalid lifecycle version, wrong transaction-line reference or changed item identity. Invalid links must block; a changed existing detail must require conflict resolution and must not silently replace target history. Importing excess applications must roll back the whole batch.
6. Import a file from the existing workbook/VBA producer. Missing lifecycle information must still require explicit legacy acknowledgment for affected control lines, retain factual details, and show NOT READY until reviewed repair. No automatic item matches should appear.
7. Verify a VIEWER cannot commit, a target-company switch requires a fresh preview, and closed periods remain protected. Check the preview messages, existing acknowledgment control and Journal allocations at normal laptop size/scaling.

The existing workbook bridge remains a reduced-information producer. Passing a lifecycle-aware export through Excel/VBA is not certified lossless. Keep the original export and full database backup. User acceptance and published-head CI remain separate gates; automated headless passes do not certify desktop appearance.
