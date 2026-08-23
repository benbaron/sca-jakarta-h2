# P05-C6 ASSET / BANK / CASH classification — owner desktop checklist

Use a disposable copy of a company database that contains at least one existing configured bank account and representative ledger activity. Keep the original database unchanged for comparison.

## Existing-data migration

1. Open the copied database with the P05-C6 build and let Flyway upgrade it.
2. Open **Chart of Accounts** and select an account that previously had Type `BANK`, such as Operating Checking.
3. Confirm it now displays **Type = ASSET**, **Function = BANK**, retains its prior **Subtype** (normally `CASH`), and retains **Normal = DEBIT**.
4. Open **Banking** and confirm the existing configured bank account still points to that same Chart of Accounts row; no duplicate account or bank configuration is created.
5. Open Journal and representative historical bank/reconciliation data and confirm existing transactions, cleared state, matches, and statement rows remain intact.

## Classification behavior

1. In **Chart of Accounts**, create or edit an ordinary deposit account as `ASSET / BANK / CASH / DEBIT` and confirm it appears in Banking's **Existing account** choices after Refresh.
2. Create a test account as `ASSET / BANK / OTHER_ASSET / DEBIT`. Confirm it also appears as a Banking choice and can be configured for statement/reconciliation operations.
3. Post a test transaction to that non-CASH bank-function account. Confirm Journal treats the split as bank-related and **Bank Account Activity** includes it.
4. Confirm that same `ASSET / BANK / OTHER_ASSET` balance is **not** included in Dashboard **Book cash** and is not placed in the Balance Sheet cash breakout; it remains an asset.
5. Create a petty-cash-style account as `ASSET / no Function / CASH / DEBIT`. Post representative activity and confirm it contributes to Dashboard **Book cash** and the Balance Sheet cash breakout, but does **not** appear as an eligible Banking account and is not treated as a reconciliable bank line.
6. Attempt an invalid combination such as `LIABILITY / BANK` or `ASSET / BANK / CREDIT`; confirm the save is rejected with an actionable classification message.

## Interchange compatibility

1. Export the active Chart of Accounts as SCA-COA JSON. Confirm an internal `ASSET / BANK` account is emitted with the existing portable `type: "BANK"` token and its independent subtype.
2. Re-preview/re-import that file into a disposable target and confirm portable `BANK` maps back to internal **Type = ASSET, Function = BANK** without losing `CASH` or another subtype.
3. Export representative SCLX data and confirm bank-function accounts retain the portable SCLX `BANK` type; re-preview it and confirm no account-type compatibility regression.

Do not approve the correction for merge if the migration duplicates accounts, breaks an existing configured bank/reconciliation link, includes a non-CASH bank-function balance in cash presentation, or excludes a BANK-function account from banking operations.
