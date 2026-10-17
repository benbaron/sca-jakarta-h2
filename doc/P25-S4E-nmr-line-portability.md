# P25-S4E — Preserve entered NMR line attribution

Selected after MR #371 merged. This bounded prerequisite of the amended [NMR accounting contract](P25-S4A-nmr-contract-proposal.md) fixes the existing loss of `TxnSplit.nmr` through SCLX. Journal already exposes the flag and saves it through canonical transaction entry. It is a user-entered fact, not an assessment or proof of a settled liability.

## Contract

Use optional `extensions.scaJakartaH2.transactionNmr`, with exactly integer `version: 1` and `lineIds`, an array of unique nonblank transaction-line IDs. Each listed line is NMR `true`; unlisted lines are `false` when creating a new transaction. Export only marked lines in deterministic ID order and omit the extension when none are marked. Zero-value, missing, duplicate or ambiguous line references, unsupported versions/fields and malformed extension shapes block preview/Save. An explicit null extension is invalid; absence remains compatible with old files.

The normalized flag participates in existing transaction and line conflict identity, preserving historical hashes for unmarked lines. A flag change on retained transaction history offers the existing explicit Keep target choice; it cannot silently overwrite the target. Old files do not clear flags on retained transactions. Source-file changes after preview remain rejected. Restore flags on canonical line commands inside the established atomic import, with existing company ownership, permissions, correction relationships, period/reconciliation protection, rollback and audit behavior.

The existing Export and Import Preview/Save routes are the user workflow. Preview states how many source lines are marked. No new pane, Save operation, migration, policy requirement, rate calculation or generated NMR entry is introduced. Full NMR item designation, liability/event detail and remittance workflow remain subsequent slices of parent S4; this slice does not certify legacy flags as complete financial detail.

Required inspection: core snapshot assembler/query, strict serializer, preview extraction/conflicts, native identity ownership, canonical import history/correction writer, transaction entry flags, existing budget/payment extension patterns and interchange tests. Required reading: PLAN, root AGENTS, S4A, adopted correction program, SCLX spec and UI/editor rules. Donor NonprofitAccounting at `c697630ec1f784ebe8338d7300da6c9ac801b180` retains a split flag but supplies no production NMR interchange/lifecycle authority; extend the current governed extension mechanism without importing donor data or persistence.

## Validation and owner checks

Test strict parsing and deterministic export; true/false flags through real canonical save, export, preview, import, restart and repeat import; unchanged amounts/dimensions and reversal relationships; changed-flag conflicts/Keep target and legacy files; foreign company, VIEWER denial, source-change protection and injected atomic rollback. Use synthetic amounts only and no saved policy. Run relevant existing tests before coding and final Maven clean verification; exercise the real preview Save route under Xvfb where applicable.

In disposable databases, enter a balanced Journal transaction with one NMR line and one unmarked line, Save, export SCLX, import through Preview/Save into another disposable company, and reopen Journal. Confirm the same flags, amounts and dimensions; restart and repeat import without duplicates. Change only the flag in source history, export again and confirm preview requires explicit Keep target for retained history. A file without the extension remains supported. Existing UI geometry/Save controls are unchanged; this note is not a claimed human desktop pass.
