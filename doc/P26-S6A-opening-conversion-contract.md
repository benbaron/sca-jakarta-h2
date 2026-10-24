# P26-S6A — Opening conversion review and acceptance

Status: ADOPTED FOR IMPLEMENTATION. [PLAN](PLAN.md) owns execution and merge status.

## Purpose and boundary

P26-S6A provides the first bounded half of P26-S6. It reviews a company's already-entered opening/carryover facts before the operator accepts the conversion. It does not import a second ledger, invent balancing entries, infer open-item identities, replace bank reconciliation, or mutate source accounting merely to make the review pass.

Opening facts continue to enter through the existing authorities:

- Chart of Accounts opening balances for reviewed account-level baselines;
- configured bank-account opening balances plus canonical uncleared transaction splits for individually reconcilable outstanding items;
- canonical Journal transactions and supplemental allocations for receivables, payables, prepaids, deferred revenue, other assets and other liabilities;
- the existing fixed-asset and inventory registers, their canonical transaction links, and their report reconciliation rules.

The later P26-S6B merchant-settlement assistant is separate. Provider API downloads and official-form mappings remain outside S6A.

## Review contract

The operator chooses an as-of date and explicitly previews:

1. nonzero account opening balances and opening debit/credit equality;
2. every configured bank baseline, its linked ledger-account opening, duplicate/missing links, and canonical uncleared splits through the cutoff;
3. fixed-asset register net value against the canonical ledger through the cutoff;
4. inventory carrying value against the canonical ledger through the cutoff;
5. all six supplemental schedule reconciliations, including raw control-account openings and incomplete allocation gaps; and
6. orphan fixed-asset, inventory, or bank control-account balances that have no owning register/configuration.

Account and configured-bank openings that describe the same reviewed bank baseline are compared, not added together. Uncleared transaction splits remain separate outstanding items. Raw opening balances on supplemental control accounts are unresolved because they lack stable item identity; the operator repairs them through the canonical Journal and supplemental allocation workflow.

The preview is ready only when it contains source facts, opening debits equal credits, bank baselines agree, property/inventory controls reconcile, no governed control balance is orphaned, and every supplemental reconciliation is complete. Informational outstanding bank items do not block acceptance when each is already a stable canonical split.

## Save and audit contract

**Save Conversion Acceptance** requires `BOOKKEEPING_WRITE`, an unchanged ready preview fingerprint, an explicit request UUID, and the current authenticated actor. It writes one immutable `OPENING_CONVERSION_ACCEPTED` `AuditEvent` for the company. The existing audit-event SCLX authority preserves that fact.

An exact retry returns the existing acceptance. A changed preview after review is stale and must be previewed again. A company with an existing acceptance cannot accept a different conversion snapshot. Late failure rolls back the audit fact. Acceptance records the as-of date, deterministic fingerprint, review counts, actor, timestamp, and optional factual note; it does not freeze later ordinary accounting or claim external approval.

## Required evidence

Automated coverage must prove balanced ready acceptance, an unbalanced opening, bank mismatch and duplicate link, supplemental opening/gap blocking, asset/inventory mismatch, orphan controls, stable outstanding bank items, stale preview rejection, exact retry, conflicting second acceptance, authorization/company isolation, rollback, restart durability, and the actual JavaFX Preview/Save route. The P25/P26 unit coverage ledger must identify these tests.

Manual review should enter a balanced opening with one configured bank baseline and an uncleared check, preview the individually listed check, save acceptance, restart, and confirm the saved acceptance remains visible. A deliberately mismatched bank opening and a receivable account opening without item allocations must remain blocked with repair guidance.
