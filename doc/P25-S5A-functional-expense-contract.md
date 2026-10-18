# P25-S5A — Functional-expense contract

Status: VERIFYING — [draft MR #377](https://github.com/benbaron/sca-jakarta-h2/pull/377); D07 adopted, full local/exact-head CI passed, owner review/merge remain. Documentation only. [PLAN](PLAN.md) owns execution. [Adopted program S5](P23-P28-runbook-correction-program.md#p25-s5--add-independent-functional-expense-classification-g10) and [baseline D07](P23-S1-baseline-and-acceptance.md#decision-and-evidence-backlog) require the meanings, report basis and defaults to be confirmed before application implementation. Parent S4 financial accounting is accepted through approved/merged MR #376.

## Adopted D07 decision

| Decision | Adopted behavior | Reference and limit |
|---|---|---|
| Codes and labels | AR — Events; OA — Administration; FR — Fundraising. These describe the purpose of an expense, independently of natural account, Fund, Event and Budget Category. | Supplied runbook explicitly says AR (Events), OA (Admin.), FR (Fundr.). These labels are adopted software meanings, not a certification of an official submission form or an expansion of AR into an unverified phrase. |
| Eligible facts and basis | Classify actual canonical EXPENSE-account lines. Report net expense (debits less credits) on the canonical transaction date, with the same selected company/date/Fund/Event scope as GL controls. Refunds/expense credits reduce their entered classification; reversals affect their own date. | A derived GL-purpose view, not a new cash/accrual recognition policy. A later official-form cash/payment-date or category mapping needs the P27 contract; it cannot silently replace this basis. |
| Defaults and missing values | Blank initially; no automatic company/account/Event/Budget/Merchant inference. Users explicitly select a code, or retain Unclassified with a visible diagnostic. Existing records stay unclassified until deliberately corrected. | Adopted permitted default is no classification. This narrows the program's defaults-with-override wording to explicit entry; account/company default suggestions require a separately adopted follow-up. |

Owner explicitly answered “Adopt this proposal (Recommended)” for this complete three-row contract on 2026-10-18. D07 is resolved; no repeated classification confirmation is required. This concerns functional expense reporting. NMR rates, exemptions, settlement policies and the already accepted P25-S3 workflow are not being reopened.

## Accounting and persistence contract after adoption

Use one optional classification on each canonical TxnSplit expense line. AR/OA/FR is separate from `AccountFunction` (bank/control behavior), `Activity` (Event), `Fund`, and `BudgetCategory`. Do not rename or reuse those fields, create another ledger or a parallel expense-allocation table. No automatic posting, approval queue or second Save is introduced.

A mixed-purpose payment uses distinct ordinary expense lines with exact entered amounts and one existing balancing bank/payable line. For a payment of 1,000, explicit AR 700 + OA 200 + FR 100 must equal GL expense 1,000; identical account/Fund/Event/Budget selections on those lines remain valid. The function does not create another monetary amount. Unsupported codes and a nonblank function on nonexpense lines reject atomically; bank/asset/liability/income and custodial NMR lines are not expense functions. Negative presentation effects come from entered expense credits/reversals, not from invented adjustments.

The future migration is nullable/nondestructive, with no fabricated historical backfill. Stable code storage and company-owned transaction references retain existing permission/ownership/period/reconciliation/audit authority. The native create-receipt hash must cover any newly accepted function without invalidating exact retries of previously accepted version-1 requests: absent facts retain existing semantics; new facts need a versioned compatible receipt representation. Merely changing the hash globally and rejecting old valid retries is not acceptable.

## Journal and generated entries

The existing Entry Lines editor exposes an explicit Expense function choice with an Unclassified/blank option, ID/code-safe entry, explanatory labels, dirty tracking and the existing Save Entry/File Save. Saved values reload and survive restart. Existing busy guards, failed-draft retention, permissions, Delete/Reverse and protected-date behavior apply. Company money/date formatting, remembered sortable/resizable/reorderable columns, actual dividers and independent two-axis scrolling remain mandatory. No pane, independent Save or additional configuration authority is proposed.

Reversal copies the exact source classification onto its opposite expense line; earlier as-of results remain unchanged. Replacement initially preserves source classification, with any later explicit reclassification governed by the ordinary permitted edit/correction route and audit. Do not look at current Event/account labels to recalculate historical purpose.

Generated COGS, depreciation, disposal and other expense lines must carry an explicit accepted code when their command supplies one; otherwise remain visibly Unclassified. Existing accounting generators must not invent AR because an Event exists, OA because an asset is old, or FR because a receipt was fundraising. Existing source copies/retries/corrections preserve accepted facts. The implementation inspection must enumerate all canonical writers and either carry explicit selection through their current UI/commands or document the visible Unclassified result. No generator may silently lose a supported classification.

## Reporting, diagnostics and interchange

A read-only functional-expense report derives AR/OA/FR/Unclassified detail and totals from canonical lines, with company/Fund/Event/date scope, originating-transaction drill-through and existing Generate/Export. For each scope, AR + OA + FR + Unclassified equals eligible net GL expense. Full control totals survive display row limits. The report exposes unclassified gross activity as well as net totals, so opposing missing values cannot cancel into a false complete result. It does not assert official form completion or infer a function from account names, categories or Event association.

Read-only donor inspection found optional free-text `TransactionLine.usedFor` in its interchange model/schema; its JournalEntry and this application's current TxnSplit/core export line do not supply an adopted functional-classification authority. Before implementation choose one compatible mapping after inspecting the current governing schema: reuse a suitable supported core field or the established versioned extension boundary, never two independently writable meanings. Do not import donor persistence or assert an external consumer validates AR/OA/FR.

SCLX must preserve the accepted function on the intrinsic transaction-line identity with strict optional/versioned shape and code validation, complete-transaction conflict identity, explicit Keep target, atomic rollback, exact import retry and restart. Older absence is Unclassified, not an implicit default. Existing external-consumer limitations remain visible; no unsupported external round-trip is claimed. An implementation slice that accepts classified facts must include their supported native export/import rather than postpone portability and lose them. Backup/restore retains the canonical record.

## Acceptance specifications — not executed feature tests

| Case | Required result after adopted implementation |
|---|---|
| F01 Independent dimensions | Same expense account/Fund/Event/Budget can have distinct functions; Event or category changes do not rewrite classification. Nonexpense codes and foreign references reject. |
| F02 Mixed payment | AR 700 + OA 200 + FR 100 balances one 1,000 bank/payment line, with no duplicate expense or function amount. Four-decimal precision is retained. |
| F03 GL basis and scope | Selected company/date/Fund/Event functional totals equal independently summed canonical expense debits less credits. Prior periods and future corrections retain proper cutoff; no second recognition. |
| F04 Unknown history | Old/new blank lines remain readable and visibly unclassified. Unknown +100 and -100 produce net zero but nonzero gross unknown diagnostics. No account/Event/rate policy infers a code. |
| F05 Correction and protection | Update/delete/reversal/replacement retain permitted history and classification, VIEWER/foreign/closed/completed-reconciliation changes reject, and late failure rolls back ledger/metadata/audit. |
| F06 Native retry | Exact native request reuses accepted classified lines, changed code with accepted key rejects, old version-1 retry remains valid, and concurrent commands cannot duplicate the expense. |
| F07 Generated expenses | Enumerate inventory COGS, depreciation/disposal and other writers; explicit inputs survive generation/correction, missing inputs are visibly Unclassified, and no historical code is overwritten. |
| F08 SCLX and restart | Exact function/line/correction facts survive export/import/restart/retry; changed/missing supported facts conflict, explicit Keep target preserves target, malformed code/shape rejects and injected failure leaves no residue. |
| F09 Presentation and Save | Real Journal choice/cell commit protects the draft and shared Save, saved/reloaded values and busy capability behavior work, VIEWER cannot save, company precision/table/divider/overflow work at laptop size/larger text, and report truncation retains full controls. |

Only S5A documentation/link/scope checks are performed here. These are future executable requirements; existing Maven success is not presented as an implemented functional-expense feature.

## Implementation sequence and owner review

D07 is adopted. After this selected contract slice's review/merge, select one vertically coherent S5 implementation slice covering canonical nullable facts/validation, current Journal Save, correction/native-retry compatibility, generated-entry disposition, derived report and lossless SCLX. Establish a fresh compile/relevant-test baseline before production edits, read every current writer/reader named above, reproduce missing classification with synthetic fixtures and add meaningful service/migration/report/interchange/actual JavaFX regressions. Full clean verify and exact-head clean/repeat/JavaFX CI are required. Do not mark parent S5 DONE merely from a field, a report, tests or this proposal.

Owner review now: verify the document records the already adopted D07 rows and the mixed payment/Unclassified behavior, without conflating Event/Fund/Budget or introducing operating rules. The owner decision is complete; publication/merge is the remaining selected-slice gate. No application desktop check is required for this documentation-only proposal. Actual functional entry/report desktop acceptance belongs to the later implemented slice. P26/P27 work is not selected by S5A.


## S5A validation and handoff

Documentation/link/scope review passed. Local offline clean verify passed 965 tests, zero failures/errors, 58 display-dependent skips, at 2026-10-18 18:01:31 UTC (3:12). Application/test/schema/workflow/dependency bytes are unchanged from merged S4I main; this result does not implement or pass F01–F09. Draft publication and exact-head CI are pending. At that initial validation checkpoint the owner question was pending. Owner subsequently adopted all D07 choices; this supersedes that pending state. No new application desktop test is required for this documentation-only proposal.


## Recorded adoption

On 2026-10-18 (America/Denver), the owner explicitly adopted AR = Events, OA = Administration, FR = Fundraising; explicit classification/no automatic defaults; net GL expense by transaction date; Unclassified/correction/SCLX preservation. D07 is resolved. This is authorization for that implementation scope after this selected contract slice lands, not a claim that the future F01–F09 feature tests have run or that any particular official submission form is certified.


## Published contract checkpoint

[Draft MR #377](https://github.com/benbaron/sca-jakarta-h2/pull/377) records the adopted contract and S4 closeout. Head `540ea03aecc291d1e6a140c9aba41a7e0016f8ff`, tree `540aed02eee6ac3691b1684ba67e14d20ec56ca5`, matches local `3d1f5a7a`. All three local messages/order and every blob/tree matched; expected-parent lease and native fetch/content comparison passed. Final-head CI run 39152631112 is pending completion. D07 is resolved; only selected contract review/merge precedes implementation. This later verification evidence stays local to preserve remote CI.


## Final verification and remaining action

[Run 39152631112](https://github.com/benbaron/sca-jakarta-h2/actions/runs/39152631112), job 117749218528, completed success at **2026-10-18 18:18:04 UTC** on exact head **540ea03aecc291d1e6a140c9aba41a7e0016f8ff**. Independent run/step checks confirm clean verification, repeat suite and expanded production JavaFX passed. Full local clean verify passed 965 tests, zero failures/errors, 58 display-related skips. The application/test/schema/workflow/dependency bytes remain unchanged; this is verification of the documentation slice's unchanged application, not executed F01–F09 functionality.

D07 is adopted; no additional meaning/default/basis decision is needed. Owner review/merge of draft #377 remains before the next vertical implementation slice. Final verification evidence stays local for the next authorized publication, preserving the green remote head. After confirmed merge, carry it to fresh main, establish the new implementation baseline and implement this contract through canonical entry, Journal, correction/native retry, generated-entry disposition, derived reporting and SCLX together. No NMR policy gate is reopened.
