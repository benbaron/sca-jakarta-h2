# P27-C1 — Reconciliation WARN_ONLY cleared-state correction

[PLAN](PLAN.md) owns status. This corrective slice follows merged P27-S4D3 / PR #400 and keeps the existing reconciliation workspace, canonical `TxnSplit` cleared facts, V54 guard and role boundaries.

## Reproduced defect

`matchSelected(sessionId, statementLineId, splitId, false)` wrote the match row and both relationship sides before discovering that an uncleared split cannot carry `matched_bank_statement_line_id` under V54. A WARN_ONLY match therefore surfaced an H2 constraint exception instead of a governed policy result. The same path was reachable from Auto Match.

## Correct behavior

- `OVERWRITE_LEDGER_CLEARED_STATE` may atomically mark an uncleared split cleared and create the match.
- The per-line path may do the same only when its explicit caller choice requests clearing.
- `WARN_ONLY` and `NEVER_OVERWRITE_REQUIRE_MANUAL` preserve the ledger cleared fact. Manual matching of an uncleared split rejects before any match, statement, split or session mutation and directs the operator to **Mark Cleared**.
- Auto Match skips uncleared candidates when the session policy does not authorize automatic clearing. The candidate remains available for explicit review.
- After the operator marks the ledger line cleared, the same exact match succeeds without an override.
- Existing finalized-session, company/account ownership, authorization, symmetric unmatch, correction and period protections remain unchanged.

No schema or migration changes are needed. The V54 constraint remains authoritative. The Reconciliation workspace displays the explicit WARN_ONLY instruction beside Match, Unmatch and Mark Cleared.

## Executable coverage and owner check

`BankReconciliationMutationIntegrityTest` covers the red constraint failure, zero-write rejection, Auto Match skip, explicit Mark Cleared and successful retry. Existing mutation, role-authorization and JavaFX route tests remain required; `ReconciliationRunsPanelTest` guards the visible instruction.

On disposable data, start a WARN_ONLY reconciliation with an exact statement/ledger pair whose ledger line is uncleared. Auto Match must leave it unmatched. Match Selected must explain that cleared state is preserved and write nothing. Choose Mark Cleared, then Match Selected; the pair must match and remain cleared. Confirm a Viewer cannot perform either write and a finalized session stays read-only.
