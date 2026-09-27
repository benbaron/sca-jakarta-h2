# SCA Accounting — Deficiency Correction Action Plan

Prepared for Ben Baron • 26 September 2026

## Objective and baseline

Correct all 14 findings in **SCA_2026_Runbook_Application_Guide.md**, including the owner-reported event-tracking gap, and address the additional workflow limitations described in that guide. Completion means an operator can carry out the runbook with reliable accounting, usable screens, preserved history, and a verifiable submission packet.

Current GitHub `main` was rechecked for this plan: **7a18adce529e23cadd09cdaba74d3821af1a6275**. `doc/PLAN.md` version 299 records P22 complete, with no active successor. Root `AGENTS.md`, the plan, P21 event requirements, the interface matrix, and UI/editor rules were inspected. Budget and open-item query predicates and Journal Fund/Activity columns were also rechecked. This is a planning deliverable; no application changes, new regression execution, desktop testing, or CI verification were performed during preparation.

The phase numbers below are **proposed P23–P28**, not existing repository commitments. Adopt them through a deliberate `doc/PLAN.md` amendment before implementation. Preserve the completion history of P21 and P22; add corrective and extension phases rather than rewriting completed history.

## Implementation rules

1. Extend the existing Java 17+, JavaFX, H2, JPA/Hibernate, Maven, JUnit 5, and Flyway application. `Txn`/`TxnSplit` remain the single ledger.
2. Keep Fund, Event/Activity, Budget Category, and functional expense classification distinct. Existing `Activity` identity remains the authority for events; do not introduce a competing Event ledger or duplicate Event master table.
3. Make every slice vertically complete: authoritative service, persistence where needed, production UI, reporting/interchange effects, tests, documentation, and user testing instructions. Split a large slice before implementation if it cannot be reviewed coherently.
4. Use stable IDs; scope every query and mutation to company; enforce current authorization and closed-period/correction protections at service boundaries.
5. Use nondestructive migrations. Do not infer missing historical event, item, check, or custody identities from similar names or free text without a reviewed mapping.
6. Treat external approvals and factual evidence separately from accounting. Current `AGENTS.md` excludes attachments, approval queues, formal oversight roles, and a separate posting workflow unless deliberately amended. The default plan handles G13 through structured evidence references, factual attestations, and external procedures; a full internal document/approval system requires the explicit scope decision described below.
7. Execute one selected slice at a time. Publication follows the repository's owner-authorization rule; completion requires merge, applicable checks, documentation, and required desktop acceptance.

## Priorities and dependencies

| Phase | Outcome | Principal prerequisites |
|---|---|---|
| P23 | Accurate reversals, historical reports, complete open-item controls | Adopt plan and reproduce findings |
| P24 | Usable named events, Journal tags, complete event costs and setup | P23; current Activity/Fund authority retained |
| P25 | Controlled transfers, checks, NMR, and functional classification | P23–P24; relevant accounting policy decisions |
| P26 | Linked property records, improved planning, recognition, and conversion | P23–P25; valuation and conversion contracts |
| P27 | Complete interchange, workbook output, and close-readiness evidence | Required P23–P26 data contracts; approved workbook template |
| P28 | End-to-end runbook acceptance and release | All preceding required slices merged and verified |

Start collecting the authoritative workbook and resolving policy questions during P23-S1. Do not defer discovery of submission requirements until P27. Dependencies describe technical readiness; they do not authorize simultaneous implementation branches.

## P23 — Accounting correctness and open-item integrity

### P23-S1 — Adopt scope and establish reproducible acceptance cases

**Work:** Add the agreed phase/slice ledger and a governing correction specification under `doc/`. Inventory each finding against current source, tests, and the owner's installed build. Record executable version/commit, launch entry point, role, company, display scaling, saved table layout, and visible routes for the event complaint. Obtain the exact required 2026 workbook/template, runbook revision, and submission instructions. Maintain a runbook-action-to-test matrix.

**Acceptance:** Each G1–G14 finding has an owner, acceptance case, required inspection list, and disposition. Confirmed existing behavior is credited; an unverified complaint remains open for reproduction rather than being dismissed. P23-S2 is the first implementation slice. No database or accounting mutation is required by this planning slice.

### P23-S2 — Correct Budget vs Actual reversal treatment (G1)

**Work:** Reproduce the original/reversal mismatch in `BudgetPlanService`. Establish the same effective-date and signed-amount rules used by the canonical ledger reports. Correct actuals without merely removing a status predicate indiscriminately. Cover reverse-and-replace, refunds, income/expense natural signs, fund/event/category filters, and fiscal boundaries.

**Acceptance:** A $100 expense reversed in the same period produces zero actual; a March reversal does not erase February's expense. A $100 original reversed and replaced by $80 yields $80. Budget vs Actual, GL, and Income Statement agree for equivalent scope. Untagged amounts are identified rather than silently assigned to a category.

### P23-S3 — Preserve historical open-item balances (G2)

**Work:** Replace current-status-only lifecycle interpretation with a documented effective-date projection. Inspect reversal relationships and replacement allocation behavior before choosing the implementation. Existing reversal rows do not duplicate supplemental effects, so adding a status condition alone is insufficient. Prefer derivation from canonical reversal facts; add persistent facts only if required evidence cannot be reconstructed. All six reports, Dashboard, and Apply Existing Item must use the same projection.

**Acceptance:** January receivable $100, February settlement $25, March reversal: February balance remains $75 and March balance is $100. Cover opening reversal, partial settlements, reverse-and-replace, backdated corrections where permitted, future transactions, multiple companies, and migrated legacy rows. Historical immutability is required for later-dated reversals; permitted backdated corrections must be reported as such.

### P23-S4 — Enforce and explain schedule-to-ledger completeness (G3)

**Work:** Add control-account tie-outs and line-level completeness diagnostics for all six supplemental kinds. For new governed entries, require the full qualifying control-account amount to be allocated, or a specifically documented exception policy; do not silently invent an item. Reject excessive applications atomically unless a separately specified overpayment/credit workflow applies. Revalidate at save under concurrent edits. Preserve legacy unmatched rows with a reviewed remediation path.

**Acceptance:** Missing/partial allocations are visible with a repair action; $120 of applications cannot silently settle a $100 obligation. Two simultaneous applications cannot bypass the limit. Reports show ledger balance, explained amount, unmatched amount, and difference by control account. Corrections, imports, and generated entries receive equivalent validation. Existing incomplete data stays readable and blocks readiness where material.

### P23-S5 — Preserve open-item lifecycle in SCLX (G4)

**Work:** Define a versioned lifecycle extension using stable portable transaction/split/item references. Update export, preview, resolution, ownership validation, atomic commit, and correction relationships. Coordinate the SCLX specification and workbook exporter compatibility; older producers must remain usable under an explicit reduced-information contract. Never fabricate lifecycle linkage for legacy files.

**Acceptance:** A partially settled item round-trips with identical links and balances at dates before and after settlement/reversal. Re-import is idempotent; cross-company, dangling, conflicting, and unsupported references fail or require an explicit supported resolution. Legacy import clearly identifies unmatched history. Retain full-database backup as the recovery mechanism.

## P24 — Named events and usable entry workflows

### P24-S1 — Make event creation and discovery explicit (G14)

**Work:** Reproduce the owner's navigation problem. Present **Events / Activities** consistently in navigation and help, with **New Event**, **Edit**, and lifecycle actions backed by `ActivityAdminService`. Provide name search and readable name/code display; repeated annual event names must be distinguishable by stable identity and code. Add an obvious route from Event Accounting to maintenance. This amends P21's read-only workspace navigation contract without creating a second write authority.

**Acceptance:** From the normal application launch, the owner can create a named event, find it by name, rename it without losing history, and deactivate/reactivate it. Used events cannot be physically deleted; the screen explains why. VIEWER reads but cannot mutate. A clean and a previously customized layout both pass desktop testing.

### P24-S2 — Make Fund/Event tagging obvious and reliable (G14)

**Work:** Make Fund and Event/Activity selectors visible and searchable in Journal. Add clearly labelled entry defaults and an explicit apply-to-selected-lines action if needed; persist only canonical line assignments. Preserve intentional multi-fund/multi-event allocations. Show event names in saved-entry review and filters. Refresh choices after creating a fund/event without discarding unsaved Journal data. Repair clipping, persisted column widths/order, focus-loss commits, or inactive-reference rendering found during reproduction.

**Acceptance:** Create event → enter a receipt and expense → select Fund and Event → save → close/reopen → filter by event name. Assignments and totals survive restart, renaming, and corrections. Different funds/events can coexist within one balanced transaction. Non-event transactions remain valid. An untagged line is not assumed to belong to an event because another line shares its transaction.

### P24-S3 — Attribute generated inventory costs (G6)

**Work:** Carry Event and Budget through financial inventory movement commands, preview, canonical splits, history, reversal, and interchange. Preserve the existing Fund authority. Define which generated lines receive each dimension. Require an explicit event choice or a clear non-event designation for the event-cost workflow; do not infer from an unrelated sale.

**Acceptance:** Event sales of $500 and inventory cost-out of $200 show income $500, expense $200, and net $300 in Event Accounting, with correct Budget actuals, inventory valuation, and company totals. Reversal restores both quantity and accounting/tag effects. Preview and commit are atomic; no clearing-account workaround remains necessary for ordinary use.

### P24-S4 — Complete lookup maintenance (G8)

**Work:** Add production maintenance for Budget Categories, Payees/Counterparties, and Merchants using existing authorities. Deliver one master-data family per sub-slice if necessary. Provide New/Edit, active/inactive, safe unused delete or a visible restriction, company ownership, duplicate checks, and return-to-entry choice refresh.

**Acceptance:** A new company can create the required choices without SQL, import tricks, or losing a draft Journal entry. Used records retain history; cross-company and unauthorized changes fail. Category, Event, and Fund remain independently selectable.

## P25 — Transfers, payment references, and reporting classification

### P25-S1 — Implement one internal fund-transfer operation (G7)

**Work:** Connect a clear Funds transfer action to canonical balanced accounting and the existing FundTransfer reporting authority in one transaction. Define restriction checks, source/destination funds, date, amount, explanation, and correction linkage. Preserve the distinction between internal reallocation, actual bank transfer, and transfer to another SCA entity.

**Acceptance:** A $100 internal transfer moves $100 between funds, preserves total company net assets, and causes no bank movement unless separately requested. The fund-transfer report and GL reconcile. Invalid restrictions, closed periods, duplicate requests, and failures cannot leave half a transfer. Reversal undoes both accounting and operational reporting facts.

### P25-S2 — Add structured payment/check references (G9)

**Work:** Specify reference ownership for a Journal transaction containing one or multiple bank payments. Add structured payment method, reference/check number, bank relationship, and issue/delivery facts where applicable. Do not parse memo text into authoritative references automatically. Display, search, import/export, and report the fields; retain memo for narrative.

**Acceptance:** Operators can find a payment by check/reference and bank account. Leading zeros and alphanumeric EFT references survive save/export/import. Multiple payments are not forced into one ambiguous header reference. Duplicate-number policy is explicit and scoped to the issuing account.

### P25-S3 — Complete check exception and reconciliation workflows (G9)

**Work:** Link issue, delivery, void, replacement, and stale review to the existing correction and reconciliation services. Provide exception reports for undelivered/stale checks, unresolved bank errors, and missing supporting evidence. Explain whether an action affects the ledger, an outstanding obligation, or only review facts.

**Acceptance:** Reissue preserves old/new references and their relationship without double expense. A valid obligation is not extinguished solely because a check is old. Cancelled pairs are presented clearly in reconciliation. Cleared-state ownership remains with reconciliation; finalized sessions and closed periods retain protections.

### P25-S4 — Implement the adopted NMR accounting contract (G10)

**Work:** Resolve the runbook's conflicting recognition/remittance instructions with the responsible accounting authority. Specify applicable counts, rates/effective dates, exemptions, collection, merchant withholding, remittance, corrections, and carryover. Add linked event detail and a liability tie-out using canonical transactions. Rate configuration must be dated and sourced, not hard-coded from this plan.

**Acceptance:** A fixture with collected NMR, processor withholding, later remittance, and prior-period balance reconciles detail to liability and cash. No amount is counted both as retained event revenue and as a custodial obligation contrary to the adopted policy. Missing inputs prevent a misleading complete report.

### P25-S5 — Add independent functional-expense classification (G10)

**Work:** Confirm the meanings and permitted use of AR/OA/FR in the required forms. Add the adopted independent line classification, defaults with explicit override, missing-classification diagnostics, and report/export mappings. Preserve it through generated entries, corrections, and interchange.

**Acceptance:** Expenses can be reported by function independently of account, fund, event, and budget. A mixed-purpose payment can split allocation without duplication. Functional totals reconcile to eligible GL expense totals; unclassified amounts are explicit.

## P26 — Property, planning, recognition, and conversion

### P26-S1 — Link fixed-asset acquisition and opening records (G11)

**Work:** Provide two deliberate routes: create acquisition accounting with the asset atomically, or link the register to existing validated accounting. Distinguish a new purchase from a historical opening record. Handle multi-asset invoices and prevent repeated claims on the same allocated cost.

**Acceptance:** Register cost and GL acquisition agree; repeated clicks/imports cannot duplicate acquisition. A failed asset/transaction write rolls back both. Existing assets can be linked through reviewed mappings without reposting their costs. Depreciation and disposal retain their canonical links.

### P26-S2 — Complete custody, supplies, and property-transfer records (G11)

**Work:** Extend existing asset/inventory authority with guardian/contact, location, quantity where meaningful, condition, confirmation date/status/history, loans/returns, removal reason, and external authorization reference. Support expensed equipment/supplies without capitalizing them again. Add a branch property-transfer form/report tied to the existing lifecycle/accounting treatment.

**Acceptance:** The required property and supplies forms can be produced with custody history and overdue confirmations. Loan/return and removal facts survive corrections. Expensed supplies do not acquire a duplicate financial value. A transfer shows both physical custody change and its adopted accounting treatment.

### P26-S3 — Complete event/fund budget planning and reporting filters (G12)

**Work:** Extend budget planning with explicit Event/Fund scope where required, reusing budget versions and category authority. Define whether general and event-specific plans are additive or mutually exclusive to prevent double counting. Add Fund/Event filters to open-item reports with a documented allocation basis when opening and settlement lines differ.

**Acceptance:** A two-event, two-fund plan reports each scope and company total correctly. Shared costs and unallocated amounts are visible. Historical/inactive dimensions remain reportable. Open-item filtered totals reconcile to the unfiltered total under the stated allocation rules.

### P26-S4 — Add governed prepaid/deferred recognition assistance (G12)

**Work:** Add recognition proposals from linked items and agreed date/amount rules, with preview and explicit commit through canonical transaction services. Define rounding, partial periods, remaining balances, cancellation, and corrections. Do not resurrect the retired Schedules destination or a second ledger.

**Acceptance:** A $1,200 twelve-month prepaid recognizes $100 per month and totals exactly $1,200. Repeating a completed run produces no duplicates. Closed periods, missing classification, over-recognition, and concurrent settlement changes are detected. Each generated transaction retains Fund/Event/Budget/functional tags and item links.

### P26-S5 — Resolve inventory valuation limitations (G12)

**Work:** Adopt a documented valuation method based on the runbook's required results before implementing cost layers. Recommended starting decision: support varying-cost receipts through a defined cost method rather than silently using an unchanged item unit value. Specify whether FIFO, weighted average, or explicit separately identified stock is required; do not claim all are implemented. Extend movement valuation, reversal, reporting, and opening migration consistently.

**Acceptance:** Receipts of 10 units at $2 and 10 at $3, a partial issue, return, and reversal produce the adopted cost result and exact register-to-GL agreement. Historical stock migration identifies its opening cost basis without inventing unavailable lot history. Negative stock and revaluation policies are explicit.

### P26-S6 — Provide controlled conversion and merchant-settlement assistance

**Work:** Address guide limitations beyond the numbered gaps in two bounded sub-slices. First, an opening/carryover preview ties opening GL balances, outstanding bank items, assets, inventory, and open-item allocations together without double counting. Second, a merchant settlement assistant links gross receipts, fees, withholding, chargebacks, and net deposits using existing Journal/open-item/reconciliation services. Provider API downloads are not required for this correction; supported file/manual input is sufficient.

**Acceptance:** A first-time conversion carries one opening balance for each economic amount and individually reconcilable outstanding items. A $1,000 gross batch with $30 fees and $970 payout clears its receivable with no duplicate income. Repeated conversion/settlement acceptance is blocked or idempotent; preview identifies unresolved source data.

## P27 — Submission, evidence, and complete portability

### P27-S1 — Finalize the form contract and resolve runbook conflicts (G5/G10/G13)

**Work:** Turn the template inventory begun in P23-S1 into a versioned field-to-source mapping for every required workbook tab/form. Resolve NMR, stale checks, carryover/classification conflicts, duplicated sections, and the missing substantive section 22 with the document owner. Record the adopted rule, source, effective date, and approving authority. Identify external evidence and signatures explicitly.

**Acceptance:** Every required field has a canonical source, operator input, external evidence reference, or a visible unresolved blocker. No invented zero or unsupported certification fills a missing requirement. This contract must be ready before affected coding slices proceed; P27-S1 finalizes, rather than postpones, earlier decisions.

### P27-S2 — Export the complete required workbook and supporting packet (G5)

**Work:** Populate a copy of the approved template, preserving required formulas, validation, names, layout, and macros where applicable. Map organization/period metadata, ledger, outstanding items, asset/liability details, property/supplies, and financial statements according to the actual template. Produce supporting reports and an export manifest with scope, application/template version, and creation time. Keep exports reproducible from a defined data snapshot.

**Acceptance:** A representative quarter and year-end workbook open in the owner's Excel environment without repair, with expected formulas/results and all required tabs intact. Every reported total ties to the same canonical scope. PDF and on-screen financial reports use consistent formatting. Re-export does not overwrite a previously submitted packet without explicit destination handling.

### P27-S3 — Add factual close readiness and evidence references (G3/G5/G13)

**Work:** Add a close-readiness projection covering bank differences, unsettled items, control-account tie-outs, missing classifications/tags, inventory/assets, NMR/transfers, custody confirmation, and required submission fields. Each failure links to its existing correction surface. Retain structured external receipt/approval/count-packet references and factual who/when attestations through H2 audit authority. Define blockers versus warnings and reasons for permitted exceptions. Integrate with `PeriodCloseRangeService`; recheck at commit to prevent stale readiness results.

**Acceptance:** An unbalanced schedule or incomplete required packet cannot appear fully ready. A recorded external review is described as an attestation, not independently verified approval. Reopening/reclosing retains history. Reports and readiness reference the same company, cutoff, and data revision. Missing receipts and policy questions appear in an actionable exceptions list without introducing an approval queue.

### P27-S4 — Complete interchange coverage for all new fields (G4 and cross-cutting)

**Work:** Audit and finish SCLX coverage for every new durable feature from P24–P27: dimensions, checks, transfer links, NMR, asset acquisitions, custody, valuation, recognition, and evidence references. Each earlier slice must already document its portability behavior; this is the final integrated compatibility gate. Keep computed projections derived, not new import authority.

**Acceptance:** A full fixture exports/imports into a new company and preserves supported identities, allocations, histories, and report results. Existing-company conflicts and unsupported versions are visible before commit. A whole-database backup/restore reproduces the complete operational state; SCLX's deliberate exclusions are documented field by field.

### G13 scope decision — internal attachments and approvals

The recommended baseline is external document custody with durable references, exception tracking, factual attestations, and a submission checklist. This satisfies the currently adopted product boundary but does **not** provide internal attachment storage, approval routing, or independent two-person enforcement.

If complete internal handling is required, amend `AGENTS.md` and the governing plan explicitly, then add **P27-S5** before final acceptance. Split it into (a) attachment storage/retention/access/backup and (b) approval and two-person controls with role separation, immutable decision history, and recovery. Acceptance must include denied access, missing evidence, altered documents, self-approval prevention where required, and backup/restore. Until that decision is adopted and implemented, classify those portions of G13 as **external by approved design**, never as implemented software features.

## P28 — End-to-end acceptance and release

### P28-S1 — Run the complete runbook acceptance matrix

Use a disposable, explicitly created test company with deterministic fixtures. Cover ordinary income/expense, multi-line allocations, event preregistration/recognition/refunds, merchant fees/payout, NMR, advances, deposits, partial settlements, internal/external transfers, check void/reissue, inventory sale/cost-out, assets/depreciation/disposal, custody, conversion, close/reopen, and interchange.

Test at least two companies, multiple funds/events, inactive historical dimensions, a non-calendar fiscal year, and dates before/after cutoff and reversal. Verify role changes at the service and UI boundaries. The owner must complete event creation/tagging and quarter-end export at normal laptop size and Windows display scaling using the built application.

**Acceptance:** Every runbook action maps to a passing executable/manual test or an explicitly accepted external procedure. No critical/high defect remains open; significant remaining exceptions are itemized and accepted rather than hidden by phase completion.

### P28-S2 — Release and close the correction program

Update the operating guide to actual screen labels and steps. Replace workarounds that are no longer necessary. Publish user testing/release notes, migration and backup guidance, supported SCLX/template versions, and the exact tested commit. Verify final merged CI and record owner desktop acceptance. Reconcile `doc/PLAN.md` and G1–G14 closure evidence; retain factual limitations.

## Coverage ledger

| Finding | Primary correction slices | Required closure evidence |
|---|---|---|
| G1 Reversal actuals | P23-S2 | Budget/GL/Income Statement agreement across cutoffs |
| G2 Historical open items | P23-S3 | Before/after reversal as-of fixtures |
| G3 Incomplete schedules | P23-S4, P27-S3 | Commit controls, remediation, ledger tie-outs |
| G4 Lifecycle portability | P23-S5, P27-S4 | Graph and historical-report round-trip |
| G5 Submission/readiness | P27-S1–S3 | Accepted workbook, packet, and readiness checks |
| G6 Inventory event costs | P24-S3 | Complete event gross/cost/net and reversal |
| G7 Fund transfers | P25-S1 | Atomic ledger/transfer report reconciliation |
| G8 Lookup maintenance | P24-S4 | New-company setup without external editing |
| G9 Check/reference lifecycle | P25-S2–S3 | Searchable references and valid correction/reconciliation history |
| G10 NMR/functions | P25-S4–S5, P27-S1 | Adopted policy fixtures and form tie-outs |
| G11 Acquisition/custody | P26-S1–S2 | Linked acquisition and complete property records |
| G12 Planning/automation | P26-S3–S5 | Scoped plans/filters, recognition, valuation fixtures |
| G13 Evidence/approvals | P27-S3; P27-S5 if adopted | External procedure acceptance or verified internal controls |
| G14 Event usability | P23-S1, P24-S1–S2 | Owner creates/finds/tags/reopens/reports by event name |
| Additional guide limitations | P25-S3, P26-S6, P27-S1–S3 | Bank exceptions, conversion, merchant settlement, policy corrections |

## Decisions to settle before the affected slice

| Decision | Recommended approach | Needed before |
|---|---|---|
| Event terminology | Events / Activities in UI; preserve Activity identity and separate Fund | P24-S1 |
| Event completeness | Explicit event tagging; visible untagged exceptions, no inference from memo | P24-S2/S3 |
| Legacy supplemental gaps | Preserve; diagnose; reviewed remediation; no automatic identity guessing | P23-S4/S5 |
| Excess settlements | Reject by default; model genuine overpayments separately | P23-S4 |
| NMR and stale-check policy | Written resolution from responsible accounting authority | P25-S3/S4 |
| Functional classification | Confirm actual AR/OA/FR semantics and required forms | P25-S5 |
| Inventory costing | Choose and document required method with example results | P26-S5 |
| Workbook authority | Exact template/revision and submission requirements | P27-S2; acquire during P23-S1 |
| Evidence/approvals | External evidence plus factual references by default; explicit scope amendment for internal controls | P27-S3/S5 |

These are bounded decisions within a complete plan, not reasons to delay unrelated corrective work. Do not substitute an undocumented implementation assumption for an accounting policy decision.

## Validation and execution handoff

For each slice, re-read current `main`, root and applicable nested `AGENTS.md`, `doc/PLAN.md`, the owning domain specification, relevant migrations/tests, and touched production routing. Consult donor code only after current-repository inspection and record whether any design is adopted.

Run the least expensive meaningful baseline, normally `mvn -DskipTests compile` plus targeted existing tests. Add focused behavioral regressions, H2 migration/service coverage where applicable, authorization/company isolation, atomic rollback, and report/export checks. Use JavaFX route/layout tests and required owner desktop validation. Finish with `mvn clean verify` locally and required GitHub checks; report unavailable gates honestly. Do not call a slice DONE on local tests alone.

Each PR and PLAN entry must record phase/slice, branch, exact commit, deliverables, migrations, validation results, owner test steps, remaining issues, and next action. Preserve one coherent slice per branch/PR, with no force updates or reuse of merged branches.

**First next action:** Adopt P23–P28 and the G1–G14 coverage ledger in the repository plan through a focused planning change, with P23-S1 selected. Then reproduce and correct G1 in P23-S2. Event usability follows immediately after the foundational accounting/open-item corrections, rather than being left until submission work.

## Sources

- [Reviewed main commit](https://github.com/benbaron/sca-jakarta-h2/commit/7a18adce529e23cadd09cdaba74d3821af1a6275)
- [Root operating contract](https://github.com/benbaron/sca-jakarta-h2/blob/7a18adce529e23cadd09cdaba74d3821af1a6275/AGENTS.md)
- [Execution ledger](https://github.com/benbaron/sca-jakarta-h2/blob/7a18adce529e23cadd09cdaba74d3821af1a6275/doc/PLAN.md)
- [Existing Activity/Event authority](https://github.com/benbaron/sca-jakarta-h2/blob/7a18adce529e23cadd09cdaba74d3821af1a6275/doc/P21-activity-event-accounting.md)
- [Production interface matrix](https://github.com/benbaron/sca-jakarta-h2/blob/7a18adce529e23cadd09cdaba74d3821af1a6275/doc/interface-operation-matrix.md)
- [UI design rules](https://github.com/benbaron/sca-jakarta-h2/blob/7a18adce529e23cadd09cdaba74d3821af1a6275/doc/ui_design_rules.md)
- [Journal editor rules](https://github.com/benbaron/sca-jakarta-h2/blob/7a18adce529e23cadd09cdaba74d3821af1a6275/doc/ui/editor-guidelines.md)
- SCA_2026_Runbook_Application_Guide.md, updated with G14, is the deficiency inventory and contains pinned supporting code references. Its source-derived defect findings remain subject to executable reproduction; its owner feedback remains subject to desktop acceptance.
