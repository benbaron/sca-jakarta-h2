# P25-S4A — D03 NMR accounting-contract proposal

Status: **OWNER-ACCEPTED FRAMEWORK — policy inputs pending**. Prepared on 2026-10-13 (America/Denver) at main `cc96e3a94703fd58bc70721513f72dd1fff0bd4b`; owner accepted the framework and authorized its MR on 2026-10-14 with “accept. Put out the MR.” Actual rates, exemptions, recognition-date choices, entity mappings and settlement arrangements remain to be supplied; acceptance does not fill those blanks. [PLAN.md](PLAN.md) owns execution status; P25-S4 implementation remains BLOCKED on D03 and P25-S3 acceptance.

## Accepted framework and remaining decisions

The owner-accepted framework treats Non-Member Remittance (NMR) collected for another organization as a **custodial liability**, with event-linked assessment and settlement evidence. Record the full receipt or merchant receivable, split the branch's earned admission income from NMR owed, and reduce the liability only through a documented settlement or approved correction. Derive cash and balances from canonical Journal transactions. Keep workbook presentation mappings distinct from ledger recognition.

This acceptance establishes the design framework. Completing D03 still requires the outstanding applicable policy inputs and their approval by the responsible accounting authority identified by the owner. If that authority requires another recognition model, revise the journal examples, tie-outs and acceptance cases together before adoption. No current charge rate, exemption, due date, legal classification or merchant arrangement is established by this document.

## Source evidence and conflict

The original reference files were retrieved read-only through connected Google Drive. Only runbook guidance and workbook Instructions were inspected for policy; private ledger rows are not copied into this proposal or test fixtures. The workbook bytes match the [S1 baseline](P23-S1-baseline-and-acceptance.md#workbook-identity-and-structure). These references do not establish the applicable kingdom policy or the official current submission revision (D02).

| Reference | Observed guidance / decision needed |
|---|---|
| `2026 Forms Runbook.docx`, §8 opening and §26.3 | NMR is flow-through money owed to others, not branch operating revenue; a receipt and later remittance must explain the liability |
| Same runbook, §8.1–8.3 | Gross gate/merchant intake is described as event income, followed by remittance reporting; this conflicts with the liability recognition guidance unless an explicit reclassification or presentation convention is specified |
| Same runbook, §8.4 and §8.8 | Merchant-remitted NMR uses a special category and must not also be counted as an ordinary branch remittance; withholding and actual settlement need separate evidence |
| Same runbook, §8.6 and §26.3 | Prior-period NMR carries forward as an outstanding liability; paying it must not fabricate current-period collections |
| Workbook Instructions C135, D136–D137, B139 | Transfer versus donation presentation depends on whether branch and recipient belong to the same legal organization; geography or a shared name is insufficient |
| Workbook Instructions B143–B144 | Describes a merchant paying the kingdom, which deducts fees/NMR and forwards the remainder to the branch; this is one arrangement to confirm, not a universal payment path |

Reference fingerprints:

- Runbook: 154,420 bytes; SHA-256 `8f965762edbd23c1c92eb353fccea0b1bfc08242a2bfda16da5f4defd1d9129c`.
- `SCA Exchequer Report - 2026-03 - With Data.xlsx`: 11,461,550 bytes; SHA-256 `f7596a2c699e79c95c919f7c6231856011e5f7378ebd0ff804b4ec7f017862c7`.

## Decisions and inputs required for adoption

The owner supplies the authority's written resolution and source/effective date for each row. Record actual approved values; a blank field, illustrative rate or this proposal's recommendation cannot stand in for approval.

| ID | Proposed approach | Authority must supply or confirm |
|---|---|---|
| D03-01 Recognition | Custodial liability for amounts received on behalf of the recipient; branch admission income excludes that amount | Accounting treatment; when the obligation arises (admission, charge, collection or another trigger); treatment of undercollection, complimentary admissions and preregistration that crosses a period |
| D03-02 Counts and exemptions | Record aggregate eligible/exempt/refunded counts by event and declared policy category, with external gate evidence | Who is liable, exemption categories, count basis and refund/no-show rules; how total admissions reconcile to those categories; whether liability can arise without a collected charge |
| D03-03 Rates and dates | Dated, sourced configuration with an explicit recognition-date basis and frozen historical application | Currency, each applicable rate, validity intervals, jurisdiction, rounding rule and date used to select the rate (event/charge/collection date); treatment of events spanning a rate change |
| D03-04 Recipient and reporting | Dated recipient/entity mapping; workbook labels do not create another GL posting | Branch and recipient legal identities, supporting authority and effective dates; applicable transfer/donation presentation and exact form/category mapping; liability account and permitted fund allocation |
| D03-05 Merchant settlement | Separate a retained amount from proven settlement; clear NMR only when the branch's obligation is discharged | Actual processor/kingdom/branch payment path, which party owes the branch its payout, evidence and date that discharge NMR, and handling of delayed withholding, fees or disputes |
| D03-06 Remittance and exceptions | Allocate each ordinary payment to identified obligations, retaining current/prior-period distinctions | Payee, deadlines/frequency, acceptable evidence, shortfall/overpayment handling, refunds after remittance and any authority-approved recovery process |
| D03-07 Opening history | Link supported existing GL liabilities to detail; enter an opening transaction only for genuinely unbooked opening balances | Cutover date, supported opening amounts by event/recipient/fund, unidentified legacy balances and permissible remediation; never duplicate an already booked liability |
| D03-08 Governance and completion | Existing authentication, company ownership, period/correction controls and factual audit remain mandatory | Approver's identity/capacity, decision/source version and effective date; existing permission for policy configuration; whether any incomplete-policy operation is permitted and how it is visibly reported |

A complete adoption record names the approving authority, records approved/revised/rejected decisions and references, and identifies any unresolved row. D03 stays unresolved until all applicable rows have a coherent answer. P25-S3 acceptance is a separate prerequisite; this proposal cannot certify it. D04 stale-check policy and D07 functional-expense meanings remain separate decisions.

## Proposed accounting and operational contract

### Assessment, collections and dates

1. Calculate an expected NMR assessment from the approved eligible counts, exemption rules and dated rate. Preserve the applied policy/rate version, basis date, count breakdown, evidence reference, Activity ID, company and recipient. Do not apply the newest rate retrospectively to saved history.
2. Record actual amounts received separately from the assessment. A difference is an explicit exception. The authority must determine whether an undercollection creates a branch-funded obligation, an amount receivable or a revised assessment; the application must not guess or plug the difference into income/expense.
3. Under the recommended receipt-based model, a gross cash receipt is Dr bank/cash, Cr branch admission income, Cr NMR liability. A merchant collection substitutes the evidenced merchant/kingdom receivable for bank/cash. The full debit includes NMR; only the branch-owned component is income. Preregistration's branch-owned portion uses the existing deferred-revenue treatment when applicable.
4. Keep event, Fund, Budget Category and Merchant as independent existing dimensions. Assessments require an explicit Activity; a payment covering multiple events must carry allocations to each obligation. No event, fund, recipient or liability is inferred from memo text, the NMR checkbox or a similar amount.
5. A policy/count deficiency may be preserved as a visible incomplete factual record if expressly permitted by the adopted rule. It cannot produce a complete/ready NMR report or a fabricated assessment. Ordinary existing Journal access does not become an automatic NMR policy engine.

### Settlement and merchant withholding

6. A branch remittance is Dr NMR liability / Cr bank or the actual disbursement account, with explicit allocation to the outstanding items. Preserve reference, issued date and evidence; bank-cleared status stays with reconciliation. The authority defines the obligation-discharge date, which may differ from bank clearing.
7. Processor fees are distinct from NMR. Record the full receivable settlement, actual net payout, fees and NMR settled as separate canonical lines. Each qualifying control-account line retains its required supplemental lifecycle allocation.
8. Withholding alone does not prove that NMR has been remitted or the branch's obligation discharged. If funds remain owed to the branch or recipient, use the authority-approved asset/receivable classification and retain the NMR liability. Move balances only when supported by the actual payment path and evidence. Never mark the withheld amount as both a merchant remittance and a later branch remittance.
9. Reject settlement allocations exceeding available outstanding obligations under the recommended contract, including concurrent and backdated writes. Genuine recoverable overpayments require an explicitly approved asset/credit process; do not silently create a negative liability or erase another event's debt.

### Carryover, corrections and history

10. Carry the outstanding obligation by its original stable item identity across periods. A current-period payment can settle a prior-period obligation without increasing current-period collections. Link supported existing balances before considering a new opening entry. Any unallocated opening balance remains an explicit tie-out exception.
11. Linked NMR entries and operational facts must save/correct atomically through the existing canonical service boundary. Specify their correction hooks and retained-history rules before implementation: direct edit/delete must not orphan metadata; reversal/replacement must restore or replace the exact allocations without duplicate supplemental effects. Historical cutoffs use canonical effective dates and reversal links, consistent with existing open-item reports.
12. Refunds, chargebacks, cancelled checks and reversed remittances identify the original assessment/settlement and preserve historical rate/evidence. A refund after payment to the recipient does not automatically make cash available or an obligation negative; apply the adopted recovery rule. Existing closed-period and finalized-reconciliation protection remains enforced.

## Illustrative balanced fixture

**Synthetic test inputs only:** ordinary earned event admission 900.00; NMR collected 100.00; processor fee 20.00; confirmed merchant NMR settlement 30.00; prior-period liability 40.00 already present in the GL. A hypothetical approved test policy may derive 100.00 from 10 liable admissions at 10.00 each. **10.00 is not an asserted SCA rate.** Currency, timing and policy values must be replaced with approved test inputs before implementation acceptance.

Use one disposable company, explicit stable event/fund/account IDs, open dates and the appropriate existing supplemental items. Link the opening 40.00 to its existing accounting; the fixture does not post it a second time. If a test starts with a genuinely empty GL, seed supported opening accounting once and test that retry does not duplicate it.

| Step | Canonical entry | Outstanding NMR after step |
|---|---|---|
| Opening | Existing prior-period liability 40.00, linked to item O | 40.00 |
| Collect through processor | Dr merchant receivable 1,000.00; Cr admission income 900.00; Cr NMR liability 100.00 linked to current item C | 140.00 |
| Receive payout, with evidence discharging 30.00 NMR | Dr bank 950.00; Dr merchant-fee expense 20.00; Dr NMR liability 30.00 against C; Cr merchant receivable 1,000.00 | 110.00 |
| Remit current balance | Dr NMR liability 70.00 against C; Cr bank 70.00 | 40.00 |
| Pay prior-period balance | Dr NMR liability 40.00 against O; Cr bank 40.00 | 0.00 |

Every new transaction balances independently. Receivable ends at zero; branch event income is 900.00 and fee expense is 20.00. Current item C settles for 100.00, comprising merchant 30.00 plus branch 70.00. Item O settles for 40.00 without a current-period collection. Current-period bank movement is +840.00; total NMR discharged is 140.00, fully explained by current 100.00 plus opening 40.00.

**Withholding variant:** without discharge evidence, settle only the known payout and fee: Dr bank 950.00, Dr fee expense 20.00, Cr merchant receivable 970.00. Receivable 30.00 and NMR liability 140.00 remain open. A later evidenced discharge is Dr NMR liability 30.00 / Cr receivable 30.00 with both item allocations. A different actual payment arrangement requires its approved journal treatment.

## Proposed implementation boundaries after adoption

Inspection of current main establishes these existing authorities:

| Current authority | Required extension / limitation |
|---|---|
| `TxnSplit.nmr`, `TransactionLineCommand.nmr`, Journal NMR checkbox, `TransactionEntryService` | Boolean is saved on a line; it carries no count, rate version, recipient, remittance allocation or proof that an obligation was discharged. Preserve legacy readability; do not reinterpret every checked line as complete NMR detail |
| `Activity` and canonical line dimensions | Reuse stable company-owned IDs; Event Accounting stays a read model over canonical transactions |
| `OTHER_LIABILITY` supplemental kind, item/split/effect links and `SupplementalOpenItemQueryService` | Reuse this lifecycle for NMR obligations and settlements, including full control-line allocation and as-of history; no second open-item balance engine |
| Canonical entry/correction, ownership, authorization and period services | Same caller-owned atomic write includes ledger, supplemental allocations, NMR source metadata, request identity and factual audit; no UI SQL or sidecar ledger |
| Current SCLX core snapshot/import | Inspected import creates transaction lines with NMR `false`; no NMR policy/count/settlement extension exists. Do not claim that SCLX currently round-trips the checkbox or the proposed contract |

Proposed additions are dated policy/entity configuration and event assessment/evidence metadata linked to the canonical transaction/split and existing item identity. They must not own a separately editable running financial balance. Design any nondestructive migrations only after adoption and complete remaining service/query/migration/test inspection. Donor code is not used to settle policy; this preparation introduces no model or implementation import.

The later implementation must provide visible Save controls for actual draft operations, name their scope, protect unsaved edits and show validation before invoking the canonical command. Count/evidence saves that do not change accounting must say so. Service permissions and company isolation apply even when UI controls are bypassed. There is no new posting/approval queue or internal evidence-file store.

P25-S4 must specify and implement a versioned NMR SCLX extension or obtain an explicit, documented reduced-portability scope decision before declaring its acceptance complete. Cover source policy snapshots, recipient/event/item addressing, stable allocations and exact retry behavior; older files remain readable with NMR completeness unknown. P27-S4 integrates later features, but does not justify silently losing newly accepted facts. Full-database backup remains the complete recovery route.

## Reports and required tie-outs

Derive amounts at a requested cutoff from canonical accounting and existing lifecycle projections:

- Opening outstanding + recognized increases + dated correction effects − obligation discharges = closing outstanding, by company, liability account, fund, recipient and event/item where known.
- NMR explained outstanding agrees with the mapped GL liability balance. Any unmapped or legacy difference remains visible; a nonzero difference cannot claim a complete tie-out.
- Actual collections, calculated assessment, refunds, fees, withheld-unconfirmed amounts, confirmed merchant discharges and branch remittances are separate columns. Report discrepancies rather than manufacturing a balancing collection.
- Merchant gross receivable = net payout + fees + evidenced NMR discharge + remaining receivable, adjusted for other explicitly identified components. Never treat all differences as fees or NMR.
- Current-period collections are distinct from prior-period balances and payments. Transfer/donation workbook presentation uses the adopted legal-entity mapping without duplicating GL income/expense or reducing NMR twice.

Missing policy, ambiguous mapping, absent count/evidence, duplicated settlement, unexplained opening balance or GL/detail difference produces an actionable incomplete result with navigation to the owning record. No automatic historical-data conversion or “ready” assertion follows from the legacy checkbox.

## Acceptance specifications — not executed feature tests

| Case | Required result after implementation |
|---|---|
| A10-N01 Mixed gross receipt | Bank/receivable includes both components; branch income excludes custodial NMR; every qualifying control line has complete supplemental allocation |
| A10-N02 Rates and exemptions | Two dated rates, exempt categories, an event spanning a change, preregistration and refund use the adopted basis; later configuration edits do not change saved historical amounts |
| A10-N03 Merchant discharge / withholding | Illustrative fixture and withholding variant tie exactly; unconfirmed withholding leaves an obligation open; a merchant allocation cannot also be paid as a duplicate branch allocation |
| A10-N04 Prior-period settlement | Current-period payments can exceed current-period collections only to the extent of identified opening/other obligations; no fabricated collection, repeated opening posting or anonymous plug |
| A10-N05 Partial and multi-event remittance | One payment settles explicit portions of several event items; remaining balances and funds are correct; over-allocation rejects atomically |
| A10-N06 Retry and concurrency | Exact retry reuses the accepted operation; changed/foreign-company request reuse rejects; competing payments cannot both consume the same available obligation |
| A10-N07 Corrections and cutoff | Later reversal retains earlier as-of results, restores exact allocations and avoids duplicate effects; replacement/refund/chargeback follows the approved recovery rule |
| A10-N08 Protection and rollback | VIEWER, cross-company IDs, closed dates and protected reconciliations reject; failure after a partial attempted write leaves no ledger, metadata or audit residue |
| A10-N09 Legacy and portability | Unmapped old NMR flags remain readable and visibly incomplete; supported new export/import preserves approved facts, item/correction identities and balances; reimport is idempotent |
| A10-N10 Reports and presentation | Detail, GL, bank/merchant totals and adopted form fields reconcile across fiscal boundaries; legal-entity mapping changes apply by approved date and create no second ledger entry |

Extend A10 with the authority's actual examples before writing feature expectations. These cases specify future acceptance; today's documentation validation or existing Maven suite cannot pass an unimplemented NMR feature.

## Owner review and exact next action

1. Review the conflict table and D03-01 recognition recommendation with the responsible accounting authority; record approved or revised treatment and its source.
2. Complete all applicable D03-01–08 inputs, including actual rates/exemptions, entity identities, merchant path, dates and exception treatment. Replace synthetic fixture policy values; verify the entries and report tie-outs against an authority-approved worked example.
3. Review the proposed lifecycle, legacy-data and portability boundaries. Record any approved departure explicitly rather than relying on this proposal's defaults.
4. Record the authority's name/capacity, decision date, approved policy version and evidence reference. Update the D03 baseline and proposal status together only after adoption.
5. Confirm P25-S3 desktop acceptance separately. When that prerequisite and D03 are satisfied, select the first approved implementation slice on fresh current main, perform its remaining source/migration/test inspection and baseline, and implement one coherent vertical slice.

Until then, P25-S4 is BLOCKED. P25-S4A delivers this reviewable proposal and its validation/handoff; the framework is owner-accepted, while missing policy inputs and native desktop acceptance cannot be inferred from preparation or merge. Publication was explicitly authorized with that acceptance; implementation remains gated.
