# P23-S3 — Historical open-item balances

Reports, Dashboard and Apply Existing Item now respect the effective dates of corrections. Reversing a transaction in March no longer removes its supplemental effect from a February report. No migration or data rewrite is required.

Use a test company and the existing Journal correction workflow. Create balanced transactions and attach the matching supplemental Item ID, Effect, amount and Ledger Line.

1. Create a receivable of 100 on January 10 (INCREASE). Apply a payment of 25 on February 10 (DECREASE, same Item ID). February 28 open balance must be 75.
2. Reverse the payment on March 5. Rerun February 28: still 75. March 5 and March 31: 100. Compare the receivable control account in GL over the same dates.
3. Repeat for Payable, Prepaid Expense, Deferred Revenue, Other Asset and Other Liability using the appropriate increasing/reducing accounting direction. Each report must use the same dated result.
4. Select February and March in Dashboard and confirm matching open amounts. In a Journal entry dated February 28, Apply Existing Item must offer the February balance, even after the March correction.
5. Reverse an opening of 100 in March. February remains 100; March closes to zero if no other applications exist. Reverse-and-replace instead: March remains 100 with the same logical Item ID. A later reversal of the replacement must apply only on its own date.
6. Reverse a reversal in a later period. The prior period remains unchanged; the original allocation's effect returns on the new reversal date.
7. Where open-period policy permits, backdate an inverse before its source. Confirm the earlier date reflects the inverse and its report explanation identifies the backdated reversal. This can legitimately change earlier balances. Do not bypass closed-period protection.
8. Check another company, future-dated entries, and migrated legacy supplemental rows. Other-company/future amounts must not enter the selected balance. Legacy rows remain visibly UNMATCHED_LEGACY, without an inferred open balance.

Totals for increases/reductions are net of their own corrections. Over-applied/unmatched diagnostics remain visible. Direct editing and deletion retain their existing policy; S3 does not preserve deleted data as a second ledger. SCLX lifecycle round-trip remains a separate S5 task.

Owner desktop acceptance is outstanding until these checks are confirmed. Automated tests exercise real entry/correction commands; headless results do not claim visual validation.
