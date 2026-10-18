# P25-S4F — Explicit NMR item detail and liability tie-out

Status: DONE through MR #373, merged at b2ff4aa6 after owner confirmation; corrected full local and final-head CI passed. Separate human visual evidence is not inferred. Governing scope: [accepted S4A](P25-S4A-nmr-contract-proposal.md).

Add an optional stable company-owned NMR recipient to existing OTHER_LIABILITY supplemental allocations. Choosing a recipient explicitly designates that item as NMR; empty means classification unknown, not an inferred non-NMR obligation. Existing narrative counterparty text remains narrative. Account, fund and event come from the exact linked canonical line; an NMR allocation requires a credit-normal liability account and an event and consistent recipient/account/fund/event across its stable item. Do not require or evaluate policy records. Preserve inactive historical references.

The existing Journal Save Entry saves all fields atomically with accounting, supplemental allocation, identity and audit. Existing Delete/Reverse and period/reconciliation protections apply; canonical reversals retain designation through original supplemental history. Direct edits/deletes cannot leave inconsistent item history. No new ledger or independently editable running balance.

A read-only Report Library result derives item balances at a requested cutoff from the existing supplemental lifecycle. Show recipient, event, fund, account, item and owning transaction. Reconcile explicit NMR totals against canonical OTHER_LIABILITY balances by account/fund; expose unclassified/unallocated differences and unsupported opening balances. A legacy NMR flag alone does not create item detail or certify readiness. Exports preserve these distinctions and include warnings when rows are limited.

SCLX adds optional versioned NMR recipient metadata to supplemental details, using existing counterparty portable IDs. Preserve exact item/event/line links and correction history through existing strict preview, complete-transaction conflict resolution and atomic commit. Missing extension remains readable with no inferred classification; retained history cannot be silently overwritten.

Donor review: current NonprofitAccounting code offers split-level flags but no reusable canonical NMR item authority. Extend production supplemental facts rather than its static stores or a second model.

## Owner checks

Use a disposable company and open dates. Create an event and recipient in existing maintenance. In Journal enter a balanced mixed receipt, allocate its OTHER_LIABILITY control line to an INCREASE item, choose its NMR recipient and tag that line with the event/fund. Save Entry; reopen and confirm the recipient. Apply Existing Item to a partial DECREASE; choose the same account/fund/event and save. Report Library NMR Liabilities must show the remaining amount, recipient/event and account/fund tie-out, without a policy version. Reverse the payment and compare cutoffs.

Try an inconsistent recipient/event and an excess payment: the whole save must reject. Confirm VIEWER cannot save. Add an ordinary/unclassified liability or unsupported opening balance and confirm the report exposes it without counting it as NMR. Export/import into a separate disposable company/database; compare facts, balances, reversal cutoffs and retry. At laptop size and scaling, move Journal dividers, scroll the supplemental table both ways, change column order/width and reopen. No new human desktop acceptance is claimed by automated tests.

## Remaining parent scope

Merchant-specific settlement/discharge evidence, withheld-unconfirmed versus confirmed merchant/branch payment presentation and period movement columns remain subsequent P25-S4 work. Existing explicitly entered canonical payments already use the same item allocations; no automatic settlement is added.


## Implemented boundaries and evidence

V82 adds a nullable recipient FK and lifecycle/kind check to existing supplemental rows, with no backfill or seed. Entry/correction uses the existing company lock and balance/period/reconciliation/authorization services; canonical audit snapshots include designation and dimensions. Identity consistency also runs after direct edit/delete and final atomic history import. Reversal lineage retains original allocation facts and dated balances; replacement copies retain recipient facts. No separate item master or running balance is introduced.

The NMR Liabilities report is a company-scoped as-of projection: existing supplemental amounts, canonical GL by account/fund, unassigned account openings, explicit recipient/event detail, owning transactions, legacy flag and allocation-gap diagnostics. Coverage is deliberately conservative across Other Liabilities: ordinary/unclassified balances are shown as differences and never called NMR. Zero net GL gaps cannot hide unmatched gross allocations. Detail row limits are disclosed and never truncate control totals. Increases/reductions are cumulative net effects to cutoff, not period collections or merchant/branch payment categories. Those later columns remain parent S4 work.

Baseline compile and 25 existing relevant tests passed. New focused service/interchange/migration gates passed, including real mixed receipt, partial payment/reversal cutoffs, restart/retry, no policy, invalid references/dimensions, unclassified/opening diagnostics, permissions, exact metadata conflict handling and injected rollback. Real Journal JavaFX Save/reload and recipient-only dirty protection passed under Xvfb with zero skips. PLAN records final broader/full/CI results and exact published head; the preceding statements do not substitute for those pending gates.


Other Liabilities controls/help and table have a real remembered vertical divider. Controls expose independent two-axis scrolling without a forced panel minimum; the table keeps its own two scrollbars and zero minimum sizes. Automated JavaFX checks include actual overflow with larger text, divider movement and durable Save; manual native checks remain owner work.


Final-review prerequisite: Journal cannot rewrite a canonical reversal whose supplemental items are inherited from its source. Rewriting can otherwise add a duplicate item effect or detach the liability line. The entry service rejects before mutating anything. Use the existing correction workflow to reverse that reversal, then enter corrected accounting; cutoff history remains intact. The regression reproduces the former acceptance and covers both rejected rewrite shapes and a supported reverse-of-reversal. PLAN distinguishes prior-head verification from the required successor checks.


Final local gate after the reversal-edit prerequisite passed 936 tests, zero failures/errors, 57 display-related skips. Dedicated real JavaFX Save/overflow validation passed separately under Xvfb. PLAN records exact local/published commit/tree mapping and subsequent CI evidence; no native human acceptance is claimed.


Final published head `1a353fdc54cb815008b5df59527a1d89b9e8d77b` passed [Maven PR Tests run 39048718804](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39048718804), including clean verification, repeat tests and expanded JavaFX/Xvfb. MR #373 remains draft/open/unmerged. This local-only verification update follows the passed published head without restarting CI; PLAN records the complete handoff and next slice.
