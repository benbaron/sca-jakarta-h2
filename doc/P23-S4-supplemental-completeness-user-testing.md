# P23-S4 user testing

The [adopted policy](P23-S4-completeness-policy-proposal.md) adds allocation completeness, excess-application rejection and visible legacy gaps. No database migration is required.

## Desktop acceptance

Use a disposable company and an open period. Repeat the applicable allocation checks for advances, receivables, payables, prepaid expenses, deferred revenue and inventory.

1. Enter a balanced transaction with a 100 control split but no lifecycle details, then with only 50 allocated. Saving must explain the missing allocation and leave no transaction. Allocate the full 100 to the correct item/effect/ledger line and save successfully.
2. Apply 60 to that item, then attempt another 60. The second save must fail without changing the 40 remaining. Try moving the opening after the application, deleting/reversing the opening, or increasing the application beyond the opening. Invalid operations must leave the original state intact.
3. Import a legacy SCLX file containing a control-account line. Preview must identify the transaction/line/amount. The commit button must require the legacy acknowledgment as well as existing approvals. Run preview again and verify the acknowledgment resets. Acknowledge and import; verify the audit records the source/hash and allocation exception.
4. Open the corresponding supplemental report. Check NOT READY, ledger/explained/gross unmatched/difference and transaction gaps. Offsetting uncovered positive and negative lines must still show gross unmatched even when signed difference is zero. Check text and CSV exports, including CONTROL/GAP rows.
5. Select a gap and open it in Journal; verify the exact transaction. Review and complete its Item ID, Effect and Ledger Line details under the configured correction method. Re-run the report and confirm readiness changes only when all deficiencies are repaired. Closed-period and reconciled-transaction protections must remain enforced.
6. At laptop size and increased UI scaling, move the header/table divider, resize/collapse sidebars and scroll the report horizontally and vertically. Repair is disabled for account summaries and enabled for transaction gaps. Check that no controls or values disappear under adjacent panes.
7. Switch company and report cutoff; confirm totals, gaps and repair targets belong to that company and date. Existing legacy detail remains readable. A nonzero unlinked account opening balance must remain NOT READY rather than acquire a guessed item.

## Validation evidence

Automated coverage includes missing/partial allocations, caller-owned generated writes, competing applications, backdating/edit/delete/reversal rollback, offsetting legacy gaps, reviewed repair, acknowledged/unacknowledged SCLX import, six-kind projections and report export integration. Final local build evidence is recorded in PLAN.md. Desktop visual acceptance and published-head CI remain separate gates; local headless skips are not visual acceptance.
