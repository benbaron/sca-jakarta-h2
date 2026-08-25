# P05-C8 owner desktop verification — canonical bank ledger activity

## Purpose

Verify that **Bank Transactions** now distinguishes the canonical accounting ledger from imported bank-statement review evidence. The first/default **Ledger Activity** tab must be derived only from canonical `Txn` / `TxnSplit` rows for configured bank accounts. The **Statement Review** tab must retain imported review, explicit acceptance, linked-transaction drill-through, and statement export without becoming a second ledger.

## Preconditions

Use a company with at least:

- one active configured checking/savings account linked to an `ASSET / BANK / DEBIT` Chart of Accounts account;
- one entered transaction affecting that configured bank account;
- preferably one cleared and one uncleared bank split;
- one imported statement row, whether matched, accepted, or still imported;
- if practical, one inactive configured bank account with historical activity; and
- if practical, one `ASSET / BANK / DEBIT` account that is not linked through `CompanyBankAccount`.

## Verification

1. Open **Accounting → Bank Transactions**.
   - **Ledger Activity** is the first/default tab.
   - The explanatory text states that Ledger Activity is the accounting record and Statement Review is imported evidence.

2. Inspect **Ledger Activity**.
   - Each row is a canonical bank-account journal split and shows Date, Transaction, Configured Account, Ledger Account, Payee, Memo, Fund, Debit, Credit, Cleared, and Cleared On.
   - Debit/credit presentation follows the bank account's normal balance and the signed canonical split amount.
   - Cleared and Cleared On match the Journal/reconciliation state for that exact bank split.
   - **Drill to Journal** opens the selected canonical transaction.

3. Exercise the configured-account selector.
   - **All configured bank accounts** shows canonical activity for every configured bank account in the active company.
   - Selecting one account limits the table to that configured account.
   - Historical activity for an inactive configured account remains viewable when that account is selected.
   - A BANK-function chart account that is not linked through `CompanyBankAccount` does not appear as configured-bank activity.
   - A configured `ASSET / BANK / DEBIT` account with a non-CASH subtype remains eligible and visible.

4. Open **Statement Review**.
   - Imported durable statement rows remain visible here, not in Ledger Activity.
   - **Create Transaction from Reviewed Row…** remains available only for one eligible unmatched imported row.
   - Matched/accepted statement rows can still drill to their linked canonical Journal transaction.
   - Statement export remains scoped to an active configured bank account and the selected date range.

5. Confirm authority separation.
   - Importing a statement row does not make a new Ledger Activity row by itself.
   - Explicitly accepting a reviewed row creates a canonical transaction; after refresh/reopen, its bank split appears in Ledger Activity.
   - Matching/clearing changes are reflected in Ledger Activity after refresh because cleared state is read from `TxnSplit`.

6. Confirm layout behavior.
   - Both tables allow horizontal/vertical scrolling as needed and columns remain sortable, resizable, reorderable, and company-state persisted.
   - Ledger Activity and Statement Review each retain their table/control divider position for the active company.

## Expected result

Bank Transactions presents one authoritative accounting view over configured-bank `TxnSplit` lines and one clearly separate statement-review view over imported evidence. No second bank-transaction persistence model is introduced.
