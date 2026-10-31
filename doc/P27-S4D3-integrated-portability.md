# P27-S4D3 — Integrated portability and recovery acceptance

[PLAN](PLAN.md) owns status. **BLOCKED: unmerged work preserved on `codex/P27-S4D3-integrated-portability`; the [recovery prerequisite](P27-S4D3R-recovery-integrity.md) must merge first.** This gate follows merged #398 and the [durable-field inventory](P27-S4-portability-audit.md). Generic accounting rules remain the contract. No new UI, accounting default, policy approval, migration or persistence authority is introduced. S4/P27 remain incomplete while this slice is unmerged; P28 stays blocked.

## Combined fixture and comparison

`IntegratedPortabilityFixture` builds one synthetic company with one canonical ledger:

- Fund hierarchy and inactive/unused Fund/category masters; Event, recipient and merchant.
- Six supplemental item kinds, partial reductions and corrections, accepted prepaid/deferred recognition and explicit AR/OA/FR plus visible Unclassified expenses.
- Operational transfer and reversal with source-native request provenance.
- Year-end delivered/reviewed check and January reissue with correction relationships.
- Optional saved NMR configuration; merchant receipt, assisted payout/fee, unconfirmed withholding observation, evidenced discharge and branch remittance.
- New-purchase acquisition and completed depreciation; weighted-average receipts, issue/reversal, expensed supplies and corrected custody with later register maintenance.
- Non-calendar scoped archived/active/draft budgets with notes and times; accepted opening conversion.
- Real prior-period factual reviews, a retained closed range and a closed/reopened range; bank import/statement, explicitly cleared match, retained draft reconciliation and earlier finalized sessions.

A second company's real accounting, a global/unowned audit, compatibility alias, company preferences, user/role assignment and password credential populate deliberate full-database-only state. Historical selected-company audit prose can retain actor names; it never imports authentication or security authority. Raw external files and in-memory sessions remain outside recovery.

`IntegratedPortabilityTest` shares the immutable source fixture across four independent target scenarios:

| Scenario | Required assertions |
|---|---|
| New company with occupied native IDs | Real deterministic SCLX snapshot/serializer, file Preview/Commit, different company/native IDs, every supported source fact and original audit payload retained, other-company isolation, historical review cannot authorize fresh close, closed-period protection, restart/exact retry adds no rows/identities/history |
| Populated target | Visible changed-category conflict, explicit Keep target retains the complete graph and reports; unsupported category/budget/transfer/policy/custody/period-close versions, invalid quantity and recognized nested review payload block before writes |
| Denial and rollback | Actual Viewer denial writes only its security audit; injected penultimate business-write failure rolls back the entire graph, identities and history; subsequent retry succeeds |
| Full database recovery | Back up/restore the same populated source, compare every public-table row/column plus triggers, retain credentials/preferences/aliases/other-company facts, native transfer/close retries write nothing, closed ranges protect writes, new restored identity allocation succeeds, source stays unchanged and active database is not switched |

Re-export comparisons normalize only the reserved synthetic target company namespace, collection order and additional target-local audit events. Every original audit UUID/payload remains required. ID-backed arrays retain explicit order fields and canonical relationships. Opaque source audit text is not rewritten into guessed foreign keys. Source-native command hashes remain provenance after SCLX remapping; full recovery preserves native retries.

Independent canonical report comparisons cover ten cutoffs: January 31, February 10, March 5, May 3/4, June 2/3/30, December 31 and January 15 of the following year. Compare Trial Balance, Balance Sheet, Income Statement, GL, functions, six schedules/control differences, NMR/merchant movements and observations, assets/depreciation, inventory/movements, custody, checks and budget actuals. Remove native query references while retaining portable item UUIDs, codes, dates, amounts and diagnostics. Validate the derived cancelled-check label against its actual correction IDs before normalizing those native ID labels; historical evidence text remains exact. Explicit expected merchant/NMR balances independently check the partial-settlement cutoffs.

## Narrow prerequisite repairs

The combined gate exposed four directly blocking defects:

1. **Balance Sheet contra signs:** credit-normal accumulated depreciation increased Assets. Section signs now follow AccountType (debit-positive Assets, credit-positive Liabilities/Equity); opposite-normal contra balances reduce the section. `BalanceSheetContraAccountTest` covers contra assets/liabilities/equity, balancing, dates/Fund scope and unchanged Trial Balance sides. No posting changes.
2. **Journal-line note loss:** Commit trimmed line memo text and collapsed empty strings to null. Preserve nullable raw line text when constructing canonical commands. Null, empty and Unicode/whitespace text survive the full graph comparison. Identifier normalization and existing identity-hash compatibility remain unchanged; no silent rewrite of earlier target records.
3. **Custody replay overwrote inventory:** historical event restoration replaced current custodian/location/condition and touched update time, including exact retries. Retain the authoritative inventory snapshot and restore custody as immutable history. A later legitimate register edit proves historical events cannot overwrite current facts/timestamps.
4. **Custody decimal/version validation:** JsonNode.decimalValue() silently returned zero for exported decimal strings. Parse strings/numbers explicitly and require nonnegative DECIMAL(19,4); reject missing/null/coerced/malformed/overflow/excess precision. Strict integral version checking and the same custody/period-close parsers now run during Preview with bounded, correctly located blocking messages. `SclxPropertyCustodyTest` covers quantity and version boundaries; the combined real-file tests verify saved quantities, unsupported versions and zero writes.

`DatabaseSnapshotTestSupport` extracts the existing complete-row/trigger oracle from `ClosePortabilityAuditTest`; all three earlier tests retain their assertions. Existing P25/P26 feature, migration and legacy compatibility tests remain required.

Retain canonical JPA snapshots and caller-owned transactions. The donor's retained raw-payload export and separate typed repositories do not establish production fidelity; import no donor persistence. UI matrix/design/editor rules were consulted; existing Preview/Save, destination and role boundaries remain authoritative.

## Validation and remaining work

Run focused tests, full `mvn clean verify`, and the exact production JavaFX selector under Xvfb. PLAN/PR record actual gates and final-head GitHub evidence. Do not infer native human acceptance from automation or merge.

A separate existing reconciliation-policy issue was reproduced: `matchSelected(..., false)` under WARN_ONLY assigns a statement link to an uncleared split, violating V54. The fixture uses genuine explicit per-line clearing (`true`). Track a focused corrective slice after S4D3; do not drop V54 or redesign matching policy inside this portability gate.

## Owner checks

The user-visible repair makes contra balances reduce their Balance Sheet section. On disposable data, run depreciation and compare Balance Sheet/Trial Balance: the statement must balance, with the contra account still on its natural Trial Balance side.

Export a populated company, preview/import into a fresh company, inspect accounting/history, restart and retry. Inspect custody quantities, current register guardian/location, source timestamps and retained corrections; confirm closed-period protection and that imported reviews cannot certify changed target data. Back up/restore to another path and inspect preferences, security, accounting and history. No new Save command or native visual acceptance is claimed.
