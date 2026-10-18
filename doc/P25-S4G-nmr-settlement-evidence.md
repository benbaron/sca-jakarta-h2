# P25-S4G — NMR settlement attribution and period movements

Status: VERIFYING in draft [MR #374](https://github.com/benbaron/sca-jakarta-h2/pull/374); local full/focused/JavaFX and exact published-head CI gates passed. Owner review/merge remain. Extends [S4F](P25-S4F-nmr-item-detail.md) under the [accepted accounting contract](P25-S4A-nmr-contract-proposal.md).

## Bounded scope

Add optional explicit COLLECTION, BRANCH_REMITTANCE, MERCHANT_DISCHARGE, REFUND or ADJUSTMENT attribution and an external evidence pointer to existing NMR OTHER_LIABILITY allocations. Collection requires INCREASE; remittance/discharge/refund require DECREASE; adjustment can have either effect. Merchant discharge requires the existing company-owned Merchant dimension on its linked liability line and a nonblank evidence pointer. This records an operator's stated accounting facts, not verification of external evidence. It neither calculates amounts nor selects a policy or category automatically. Legacy/unlabeled allocations remain unknown.

No new payment, balance engine, item master, approval, attachment or independent posting operation. Existing Journal Save Entry/File Save persists the complete transaction, allocation and facts atomically through existing ownership, permission, period, reconciliation, correction, availability and audit controls. Replace copies retain facts; canonical inverses inherit them at their own dates, without a second allocation. Apply Existing Item carries identity/recipient, but never copies the opening movement label or evidence into a new payment.

**NMR Period Movements** uses explicit requested dates and existing supplemental dated effects. Per item: opening + collections − branch payments − merchant discharges − refunds + signed adjustments + signed unclassified movements = closing. Reversals appear on their own dates, net their source category and remain identifiable as corrections; do not count correction columns again. Show current-period detail, evidence/merchant and owning transaction, opening/closing account/fund GL coverage, unclassified gross movements and actionable gaps. Missing classification never fabricates a payment and cannot claim full movement classification, even when unknown increases/reductions net to zero. Row limits disclose truncation and never truncate summary/control totals.

SCLX supplemental nmr version 2 carries exactly version, recipientId, movement and evidenceReference; accept version 1 unchanged. Export version 1 if no new facts, preserving old conflict hashes. Resolve Merchant via existing canonical line merchant relationships; reject malformed shapes/version, wrong effects or missing merchant/evidence before Save. New facts affect complete transaction and detail identity. Existing target history requires explicit Keep target; no overwrite or silent data loss. All writes remain inside the existing canonical atomic history import.

## Deliberate following scope

Dedicated unconfirmed-withholding observations linked to merchant receivables, and gross/payout/fee/receivable presentation, require a separate coherent slice. Users can already enter those exact receivable/payout/fee facts through Journal. This slice must test that an entered payout/fee with remaining receivable creates no NMR discharge or default label. Withholding alone never discharges a liability. No policy inputs are required for either slice. Full parent S4 and form-specific presentation are not declared complete.

## Owner checks

Use disposable companies/open dates. Enter an explicitly labeled NMR collection. Allocate a partial branch remittance and a separate merchant discharge, choosing Merchant on its liability line and entering external evidence. Save Entry/reopen and confirm facts. Missing evidence/Merchant or wrong INCREASE/DECREASE must reject without partial writes; VIEWER cannot save. Evidence-only changes protect the draft. Apply Existing Item must not classify the payment for you.

Run NMR Period Movements across a prior-period opening, current collection/payments and a later reversal. Verify the equation and net category/correction dates; compare NMR Liabilities and Journal GL. An unlabeled payment is visible as unclassified, not assumed branch or merchant. A payout/fee transaction leaving merchant receivable open must leave NMR open. Verify export/import/restart/retry and changed-fact Keep target. Move Journal dividers, resize/reorder columns and scroll both axes at laptop size/larger text. No new human desktop acceptance is claimed by automation.

## Implementation and verification checkpoint

V83 adds only nullable movement/evidence columns and constraints, preserving legacy rows and amounts. Existing canonical service, correction copies, audit, shared Journal cells, range report and SCLX are integrated. Period projection consumes the same dated-effects loader as as-of item balances; opening/closing account/fund coverage comes from the existing NMR liability query. Merchant relationships on imported inverses are validated before and during canonical history commit. No independent settlement balance or posting command exists.

Baseline compile and 28 existing tests passed before production edits. Final focused gate: 52 tests, zero failures/errors/skips (`p25-s4g-final-focused.log`, BUILD SUCCESS 2026-10-18 00:37:11 UTC). Real Xvfb gate: 11 tests, zero failures/errors/skips (`p25-s4g-xvfb.log`, BUILD SUCCESS 2026-10-18 00:36:00 UTC). Coverage includes the balanced accepted fixture, prior opening/current movements/later reversal, actual report pipeline and row-limit totals, withholding without discharge, unknown gross movements, refund/adjustment, permission/atomic validation, V82 upgrade/history recovery, strict metadata/conflict/Keep target/retry/rollback/restart, actual Journal movement/evidence Save/reload and evidence-only dirty protection with existing laptop/large-text scrolling geometry.

Full local clean verification and exact published-head clean/repeat/expanded-Xvfb CI remain pending at this checkpoint. Human desktop review is not inferred. After publication record actual results in the MR and local-only ledger; do not replace a successful remote head solely for evidence.

## Publication and full local result

Draft MR #374 head `10220a3be6654d464884a50ca9a017d7e0e53eb0` has the exact reviewed implementation tree. Full offline local clean verify passed 945 tests, zero failures/errors, 57 display-dependent skips, BUILD SUCCESS 2026-10-18 00:44:28 UTC. The independent real Xvfb gate passed 11 tests. Exact-head CI run `39083944191` is pending; no human desktop pass is claimed. PLAN contains the complete matching-tree publication sequence and remaining action. This evidence remains local until carried onto fresh main after merge.

## Final-head CI and next action

Exact published head `10220a3be6654d464884a50ca9a017d7e0e53eb0` passed [Maven run 39083944191](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39083944191), job `117546465691`: clean headless verification, repeat tests and expanded production JavaFX routes all succeeded. MR #374 records the actual results. No source/test/schema change followed the gates and no known executed-check failure remains. Status VERIFYING pending owner review/acceptance/merge, with no new human desktop result claimed.

The PLAN/S4G publication and final verification ledger is committed locally only after implementation `e94861fa`; it does not replace the successful remote head solely for evidence. After confirmed merge, carry it onto fresh main and select the dedicated withholding/receivable and gross/payout/fee slice described above. Full parent S4 is not declared complete.
