# Posting Rules

## 1) Balancing rule
A transaction is postable if, after mapping signed amounts to DR/CR using account normal balance:
- totalDebits == totalCredits

## 2) Signed -> DR/CR mapping
Given split.amountSigned and account.normalBalance:
- normalBalance=DEBIT:
  - debit = max(amount, 0)
  - credit = max(-amount, 0)
- normalBalance=CREDIT:
  - credit = max(amount, 0)
  - debit = max(-amount, 0)

## 3) Journal view
Journal is derived; DR/CR is never stored.

## 4) Fund requirement
Every split must have a fund. Consider a special “GENERAL/UNASSIGNED” fund if needed.

## 5) Fund transfer template (planned)
To move X from Fund A to Fund B:
- Create a posted transaction using a clearing account (e.g., 3000 Fund Transfer Clearing):
  - Split 1: account=3000, fund=A, amountSigned = -X (or +X depending on normal-balance convention)
  - Split 2: account=3000, fund=B, amountSigned = +X
- Create fund_transfer record pointing to posted_txn_id.
