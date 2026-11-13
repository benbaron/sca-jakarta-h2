# P27-C3 Journal and SCLX chart consistency

## Reproduced defects and decision

The owner selected account 1000 in a new Journal entry and then SCLX export rejected it as outside the company's active chart. No chart import or switch occurred between those operations. Production inspection and real H2 regressions reproduce two independent causes; the owner's database has not been inspected, so neither is assumed to be its sole cause.

Journal reference data previously offered active posting accounts from every company-owned chart. Multiple charts can remain ACTIVE and account codes are unique only within a chart. SCLX exports one selected chart, so a different chart's account 1000 could be saved from that dropdown and rejected by export immediately. Separately, export used Java reference comparison for chart identity. A Hibernate proxy and its implementation can represent the same persisted chart and still compare unequal.

New interactive Journal drafts now offer accounts from the company's explicitly selected chart only. `TransactionEntryService.enterJournalDraft` refreshes the company after acquiring its lock, then rechecks header and line account ownership and selected chart identity inside the existing atomic create boundary. Lock acquisition alone does not replace JPA's previously loaded state; a competing chart assignment committed after the first company read must be observed. A missing selection requires Company Admin; no arbitrary chart fallback is introduced. A stale selection is rejected without writes. Existing authorization, period protection, validation, auditing and request receipts remain authoritative. Receipt replay precedes stale-chart validation so a previously committed request remains safely replayable after a chart change.

Historical loads/corrections and general import/generated entry APIs retain stable company-owned account references. This change does not reclassify history, restrict report lookups to the current chart, or change chart ownership. No new schema, migration, persistence path or donor model is needed; this corrects the established production services.

The full gate exposed a direct prerequisite: sample-company initialization created accounts without selecting its first chart. Explicit sample creation/refresh now selects its sample chart only when it is the company's sole owned chart and no chart is selected, under the company lock. Existing selections and ambiguous multiple-chart states remain untouched. This keeps the supported fresh sample-company workflow usable without adding a Journal fallback.

SCLX core, banking, fixed asset and inventory chart guards use persisted chart and company IDs, while supporting identical transient objects in assembler tests. Equal account codes never imply equal accounts or charts. Foreign-company and genuinely different-chart references remain rejected. Same-company mismatches identify the transaction/date where available, the account, its chart and the selected chart. Export validates before replacing the destination; failures leave both the database and any previous export untouched.

## Existing entries and limits

The single-chart SCLX format is unchanged. A genuinely different-chart entry already saved by the prior UI requires review. For a mistakenly selected account on an editable entry, explicitly choose the intended current-chart account in Journal and save through ordinary correction rules. Do not move accounts, change charts or remap history by matching code 1000 merely to force export. Legitimate historical use of multiple charts requires separate format/product work; this slice does not silently omit those records. A proxy-only mismatch needs no accounting correction.

## Verification and user testing

The initial two H2 regressions failed against the original implementation: the other-chart account was offered and the same persisted chart was rejected across a real proxy boundary. Expanded coverage exercises valid create/export, stale line/header rollback, missing chart, receipt replay after chart change, actionable errors for existing records, failed-export destination preservation and explicit canonical correction. Existing SCLX section and authorization/idempotency tests remain in the validation gate.

On the updated build, create an entry using account 1000 offered by Journal, save it and export the active company without changing charts. The newly saved entry should export. If older data still blocks export, the message should identify the affected record and charts for review. Verify ordinary Save completion/error feedback. This focused check does not reopen the separately deferred native acceptance checklist.

Actual test totals and publication/CI evidence are recorded in [PLAN.md](PLAN.md).
