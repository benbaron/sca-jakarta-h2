# P23-S4 — Proposed D08 allocation policy

Status: proposed for owner decision; not yet adopted. No production behavior changes have been made.

The adopted program already requires rejecting silent excess applications. The S1 decision backlog additionally gates S4 on D08: legacy allocation exceptions and genuine overpayment handling. Current SCLX imports provide legacy supplemental detail without item/split/effect linkage, so unconditional strict allocation enforcement would prevent previously supported historical imports. The following proposal resolves that conflict without inventing historical links.

## Proposed rules

1. New Journal entries and generated entries must allocate the full absolute amount of every qualifying control-account split to explicit lifecycle details of the matching kind and direction. Missing or partial details reject the entire operation with transaction/line, required amount, allocated amount and repair instructions. No automatic item creation from amounts or memo text.
2. Reject applications above the effective available item balance. Recheck within the write transaction under a common company/item locking protocol, including updates, backdated changes, corrections and concurrent saves. No negative obligation is silently reclassified as a credit. A genuine overpayment/credit workflow remains separately specified before implementation; there is no implicit exception to this rule.
3. Existing incomplete data remains readable. Historical imports that lack lifecycle linkage may enter only through an explicitly acknowledged legacy exception identifying the source/batch and factual reason in audit history. This exception permits preservation, not a claim that the ledger is explained. Do not invent item identities or matches for legacy detail. Ordinary entry and generated-entry paths cannot invoke the import exception.
4. Reports and readiness show ledger amount, explained amount, unmatched amount and difference by control account, plus transaction/line diagnostics. Any unresolved nonzero deficiency blocks a complete/ready result; no materiality threshold or silent waiver is invented.
5. Remediation opens the existing Journal transaction by stable ID for reviewed allocation repair under current correction/closed-period protections. An edit to legacy data must satisfy current completeness rules before saving; preserved originals and canonical inverses remain auditable. Reversal effects use S3's existing links rather than duplicate supplemental rows.
6. SCLX lifecycle portability remains S5. S4 implements only the explicit reduced-information exception and truthful diagnostics needed to preserve supported historical imports.

## Inspection evidence and implementation work after decision

- `TransactionEntryService.persistSupplementalLines` currently validates supplied rows and rejects per-split excess, but does not require every qualifying split to be fully allocated.
- `requireItemIdentityConsistency` uses S3's effective-date increase check, but does not reject cumulative applications above remaining balance.
- Journal, SCLX and generated transactions share entry-service boundaries. `SclxImportCommitService` currently maps legacy supplemental values without lifecycle triples into that boundary. A global full-allocation check would therefore change import acceptance.
- Neither inspected entry nor correction service currently establishes a shared pessimistic locking protocol for supplemental availability. Tests must demonstrate exactly one of two competing applications commits when their sum exceeds the obligation.
- S3 report results expose item projections and legacy diagnostics, not a full control-account completeness reconciliation. S4 must add that reconciliation and genuine repair navigation; source inspection alone is not acceptance evidence.

Required acceptance fixtures: all six kinds; missing and partial details; applications of 60 plus 60 against 100; concurrent competing applications; edit/correction rollback; historical cutoff preservation; company isolation; acknowledged legacy import and rejected unacknowledged exception; generated entries; report totals and Journal repair navigation.

Owner decision requested: adopt these rules, or identify permitted legacy/credit exceptions that require a different contract before S4 implementation.
