# P25-S4A — D03 NMR accounting-contract proposal

Status: **OWNER-ACCEPTED FRAMEWORK — external operating policies excluded from software scope**. The owner accepted the custodial-liability framework on 2026-10-14 and clarified on 2026-10-17 (UTC): “These don't need to be codified in the software.” This amendment supersedes the former requirement to supply and encode D03 operating-policy inputs before NMR accounting development. [PLAN.md](PLAN.md) owns execution and remaining validation status.

## Accepted framework and software boundary

Non-Member Remittance (NMR) received for another organization is a **custodial liability**. Users enter the applicable amounts, accounting dates, recipient, accounts, funds, event and settlement allocations. The application records those explicit accounting facts through canonical Journal transactions and the existing supplemental liability lifecycle; it does not determine who owes a charge or calculate that charge from admissions.

Rates, eligibility, exemptions, operational effective dates, remittance deadlines and the organization's merchant/refund arrangements remain outside the application. There is no automatic assessment, exemption evaluator, deadline enforcement or policy-driven recognition/settlement engine. Users apply external instructions when entering accounting facts. No written policy source or completed policy record is a prerequisite to an NMR accounting operation.

Existing [S4B configuration](P25-S4B-nmr-policy-configuration.md) and [S4C portability](P25-S4C-nmr-policy-sclx.md) remain available as optional reference records. Their stored terms are not evaluated and are not mandatory dependencies of accounting. Preserve their history and data; this amendment introduces no schema or deletion change.

Balanced entries, company ownership, existing permissions, period/reconciliation protection, explicit allocation and factual audit remain required. Form-specific classification belongs to its owning reporting slice and must not be inferred from recipient names or geography. The owner subsequently recorded P25-S3 desktop acceptance as “Passed — no issues” on 2026-10-17; its merged final-head CI is confirmed in PLAN. This clears that separate prerequisite, without extending acceptance to other panes.

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

## D03 disposition

The original D03-01–08 checklist mixed external operating rules with accounting integrity requirements. It is superseded as follows; actual rates or policy approvals are no longer development inputs.

| ID | Current software responsibility |
|---|---|
| D03-01 Recognition | Record explicitly entered custodial amounts and accounting dates; exclude those amounts from branch income. No admission/collection trigger engine |
| D03-02 Counts and exemptions | No eligibility or exemption calculation. External gate/count evidence may be referenced in notes; no software assessment is calculated |
| D03-03 Rates and dates | No rate selection or charge calculation. Optional saved reference versions do not supply defaults or gate accounting; saved ledger amounts never change when reference data changes |
| D03-04 Recipient and reporting | Users select company-owned accounting IDs and identify the recipient. No inferred legal relationship or automatic transfer/donation classification; exact form mappings remain in the reporting contract |
| D03-05 Merchant settlement | Record separately entered payout, fees, outstanding receivable and confirmed settlement amounts. Withholding alone does not automatically discharge an obligation |
| D03-06 Remittance and exceptions | Users enter payments and explicit item allocations. No deadline/frequency engine or automatic refund/recovery treatment; reject excess settlement allocations |
| D03-07 Opening history | Link supported existing GL liabilities to detail without reposting them; expose unmapped balances. Opening entries use existing accounting controls |
| D03-08 Governance | Reuse existing authorization, company ownership, period/correction and audit controls. No new approval queue or mandatory policy-completion workflow |

D03's operating-policy prerequisite is resolved by this scope decision. D04 stale-check treatment and D07 functional-expense meanings are separate decisions and are unchanged.

## Accounting contract

1. Users enter actual NMR amounts, dates and stable company-owned event/fund/account/recipient references. Receipt accounting is Dr cash/bank or merchant receivable, Cr branch admission income and Cr NMR liability for the entered components. Do not derive amounts or mappings from a memo, NMR checkbox or optional policy record.
2. Reuse `OTHER_LIABILITY` supplemental items and exact control-line allocations. Preserve item identity across periods; no second balance engine. A multi-event payment explicitly allocates its entered amount to the relevant outstanding items.
3. A remittance is Dr NMR liability / Cr the selected disbursement account. Merchant settlements separately record entered payout, fees, liability discharge and remaining receivable. Retain transaction/reference/evidence facts; bank-cleared status remains owned by reconciliation. Never count a merchant settlement again as an ordinary branch remittance.
4. Reject allocations exceeding outstanding obligations, including concurrent and backdated writes. Refunds, chargebacks and reversals identify original records and use existing explicit correction controls; no automatic policy-based recovery or negative-liability plug.
5. Carry supported opening liabilities through existing item links without duplicate accounting. Report any legacy/unmapped balance as an explicit tie-out difference rather than inventing a collection.
6. Ledger lines, supplemental allocations, NMR metadata, request identity and audit save/correct atomically. Direct edits/deletion must not orphan metadata; reversals/replacements preserve cutoff history and restore exact allocations. Existing authorization, ownership, closed-period and finalized-reconciliation protections apply.

## Illustrative balanced fixture

**Synthetic test inputs only:** ordinary earned event admission 900.00; NMR collected 100.00; processor fee 20.00; confirmed merchant NMR settlement 30.00; prior-period liability 40.00 already present in the GL. These are directly entered synthetic accounting amounts, not charges calculated from counts or a production rate. Tests require no real-world policy values.

Use one disposable company, explicit stable event/fund/account IDs, open dates and the appropriate existing supplemental items. Link the opening 40.00 to its existing accounting; the fixture does not post it a second time. If a test starts with a genuinely empty GL, seed supported opening accounting once and test that retry does not duplicate it.

| Step | Canonical entry | Outstanding NMR after step |
|---|---|---|
| Opening | Existing prior-period liability 40.00, linked to item O | 40.00 |
| Collect through processor | Dr merchant receivable 1,000.00; Cr admission income 900.00; Cr NMR liability 100.00 linked to current item C | 140.00 |
| Receive payout, with evidence discharging 30.00 NMR | Dr bank 950.00; Dr merchant-fee expense 20.00; Dr NMR liability 30.00 against C; Cr merchant receivable 1,000.00 | 110.00 |
| Remit current balance | Dr NMR liability 70.00 against C; Cr bank 70.00 | 40.00 |
| Pay prior-period balance | Dr NMR liability 40.00 against O; Cr bank 40.00 | 0.00 |

Every new transaction balances independently. Receivable ends at zero; branch event income is 900.00 and fee expense is 20.00. Current item C settles for 100.00, comprising merchant 30.00 plus branch 70.00. Item O settles for 40.00 without a current-period collection. Current-period bank movement is +840.00; total NMR discharged is 140.00, fully explained by current 100.00 plus opening 40.00.

**Withholding variant:** without discharge evidence, settle only the known payout and fee: Dr bank 950.00, Dr fee expense 20.00, Cr merchant receivable 970.00. Receivable 30.00 and NMR liability 140.00 remain open. A later evidenced discharge is Dr NMR liability 30.00 / Cr receivable 30.00 with both item allocations. Users explicitly enter the accounting for a different actual payment arrangement; the application does not infer it.

## Implementation boundaries

The original S4A source inspection identified these authorities; S4C's subsequent portability change is noted below. Recheck their current implementation before financial work:

| Current authority | Required extension / limitation |
|---|---|
| `TxnSplit.nmr`, `TransactionLineCommand.nmr`, Journal NMR checkbox, `TransactionEntryService` | Boolean is saved on a line; it carries no count, rate version, recipient, remittance allocation or proof that an obligation was discharged. Preserve legacy readability; do not reinterpret every checked line as complete NMR detail |
| `Activity` and canonical line dimensions | Reuse stable company-owned IDs; Event Accounting stays a read model over canonical transactions |
| `OTHER_LIABILITY` supplemental kind, item/split/effect links and `SupplementalOpenItemQueryService` | Reuse this lifecycle for NMR obligations and settlements, including full control-line allocation and as-of history; no second open-item balance engine |
| Canonical entry/correction, ownership, authorization and period services | Same caller-owned atomic write includes ledger, supplemental allocations, NMR source metadata, request identity and factual audit; no UI SQL or sidecar ledger |
| Current SCLX core snapshot/import | S4C preserves optional policy versions. [S4E](P25-S4E-nmr-line-portability.md) preserves entered transaction-line NMR flags through existing conflict-aware import. [S4F](P25-S4F-nmr-item-detail.md) adds explicit recipient/item designation with canonical event/account/fund addressing and liability coverage. Merchant settlement evidence/presentation remain subsequent work; line flags alone do not complete financial portability |

Future additions are user-entered NMR accounting/evidence metadata linked to canonical transaction/split and existing item identity. They must not own a separately editable running financial balance or require a policy-version foreign key. Complete current service/query/migration/test inspection before designing any nondestructive migration. No new policy/entity configuration engine is required.

The later implementation must provide visible Save controls for actual draft operations, name their scope, protect unsaved edits and show validation before invoking the canonical command. Reference/evidence saves that do not change accounting must say so. Service permissions and company isolation apply even when UI controls are bypassed. There is no new posting/approval queue or internal evidence-file store.

P25-S4 must specify and implement a versioned NMR SCLX extension or obtain an explicit, documented reduced-portability scope decision before declaring its acceptance complete. Cover entered accounting facts, recipient/event/item addressing, stable allocations and exact retry behavior; older files remain readable with NMR completeness unknown. P27-S4 integrates later features, but does not justify silently losing newly accepted facts. Full-database backup remains the complete recovery route.

## Reports and required tie-outs

Derive amounts at a requested cutoff from canonical accounting and existing lifecycle projections:

- Opening outstanding + recognized increases + dated correction effects − obligation discharges = closing outstanding, by company, liability account, fund, recipient and event/item where known.
- NMR explained outstanding agrees with the mapped GL liability balance. Any unmapped or legacy difference remains visible; a nonzero difference cannot claim a complete tie-out.
- User-entered collections, refunds, fees, withheld-unconfirmed amounts, confirmed merchant discharges and branch remittances are separate columns. Report discrepancies rather than manufacturing a balancing collection.
- Merchant gross receivable = net payout + fees + evidenced NMR discharge + remaining receivable, adjusted for other explicitly identified components. Never treat all differences as fees or NMR.
- Current-period collections are distinct from prior-period balances and payments. Form-specific transfer/donation presentation is addressed by its owning reporting contract; it must not duplicate GL income/expense or reduce NMR twice.

Missing accounting references, duplicated settlement, unexplained opening balance or GL/detail difference produces an actionable incomplete result with navigation to the owning record. Missing optional policy records or external gate counts does not block accounting or a valid accounting tie-out. No automatic historical-data conversion or “ready” assertion follows from the legacy checkbox.

## Acceptance specifications — not executed feature tests

| Case | Required result after implementation |
|---|---|
| A10-N01 Mixed gross receipt | Bank/receivable includes both components; branch income excludes custodial NMR; every qualifying control line has complete supplemental allocation |
| A10-N02 External policy independence | Enter NMR amounts without any saved policy; optional reference changes do not alter historical amounts, calculate charges or block accounting |
| A10-N03 Merchant discharge / withholding | Illustrative fixture and withholding variant tie exactly; unconfirmed withholding leaves an obligation open; a merchant allocation cannot also be paid as a duplicate branch allocation |
| A10-N04 Prior-period settlement | Current-period payments can exceed current-period collections only to the extent of identified opening/other obligations; no fabricated collection, repeated opening posting or anonymous plug |
| A10-N05 Partial and multi-event remittance | One payment settles explicit portions of several event items; remaining balances and funds are correct; over-allocation rejects atomically |
| A10-N06 Retry and concurrency | Exact retry reuses the accepted operation; changed/foreign-company request reuse rejects; competing payments cannot both consume the same available obligation |
| A10-N07 Corrections and cutoff | Later reversal retains earlier as-of results, restores exact allocations and avoids duplicate effects; replacement/refund/chargeback uses explicit entered corrections without automatic recovery |
| A10-N08 Protection and rollback | VIEWER, cross-company IDs, closed dates and protected reconciliations reject; failure after a partial attempted write leaves no ledger, metadata or audit residue |
| A10-N09 Legacy and portability | Unmapped old NMR flags remain readable and visibly incomplete; supported new export/import preserves approved facts, item/correction identities and balances; reimport is idempotent |
| A10-N10 Reports and presentation | Detail, GL, bank/merchant totals and adopted form fields reconcile across fiscal boundaries; no legal classification is inferred and no presentation creates a second ledger entry |

Use the balanced synthetic accounting fixture above for implementation expectations. These cases specify future acceptance; documentation checks or the existing Maven suite cannot pass an unimplemented NMR feature.

## Exact next action

MR #372 is merged and P25-S3 desktop acceptance remains passed. Execute selected [S4F](P25-S4F-nmr-item-detail.md) on fresh current main: explicit item designation, recipient/event detail and liability tie-out using existing canonical and supplemental authorities, including lossless SCLX support. Finish local and published-head validation and owner review/merge before selecting the next bounded S4 settlement-evidence slice. Do not request operating policies as a coding prerequisite.

The configuration-first and policy-portability slices were explicitly selected and merged in MRs #369 and #370. Their records remain optional reference data. No application, migration, or saved history is removed by this documentation amendment.
