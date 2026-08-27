from pathlib import Path
import re

p = Path('doc/PLAN.md')
text = p.read_text()
old_front = '''---
plan_version: 221
active_phase: P17
active_slice: P17-C5
active_status: VERIFYING
active_branch: codex/P17-C5-inventory-item-lifecycle
active_pull_request: 297
active_head: 50986693a28964e25e581d9d61be39efd387a3a6
next_action: "Run Maven PR Tests on PR #297 final documentation-record head, correct any findings, then complete the owner Inventory lifecycle checklist and stop before merge until owner acceptance."
---'''
new_front = '''---
plan_version: 222
active_phase: P17
active_slice: P17-C6
active_status: IN_PROGRESS
active_branch: codex/P17-C6-fund-hierarchy-lifecycle
active_pull_request: pending
active_head: 2e9114a769b15c0f5e7b0a1147d84c0fe308cc53
next_action: "Implement Fund hierarchy lifecycle integrity across interactive administration and SCLX boundaries, add focused regressions and governing documentation, publish a draft PR, run Maven PR Tests, then stop before merge for owner acceptance."
---'''
if old_front not in text:
    raise SystemExit('PLAN front matter target not found')
text = text.replace(old_front, new_front, 1)
text = text.replace(
    'P17-C4 merged through PR #296 at `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4`; the owner explicitly accepted C4 and Maven PR Tests push run `33020990140` passed on that exact merged `main` head before C5 began.',
    'P17-C4 merged through PR #296 at `e7bf80a10fcbafe2edc46261f8cfa886e70ce5d4`. P17-C5 merged through PR #297 at `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`; the owner explicitly accepted C5 and Maven PR Tests push run `33028403587` passed on that exact merged `main` head before C6 began.',
    1)
text = text.replace(
    '| P17 | Cross-cutting UI and durable-record lifecycle corrections | C1 DONE; C2 DONE; C3 DONE; C4 DONE; C5 VERIFYING |',
    '| P17 | Cross-cutting UI and durable-record lifecycle corrections | C1 DONE; C2 DONE; C3 DONE; C4 DONE; C5 DONE; C6 IN_PROGRESS |',
    1)
new_tail = '''### P17-C5 — Inventory item lifecycle completion

Status: DONE.

Completion evidence:

- PR #297 merged to `main` at `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53` after owner acceptance.
- Inventory lifecycle status is service-owned; ordinary edits cannot silently alter it, zero quantity is required before deactivation/disposal, and disposed items remain terminal retained history.
- Metadata edits, lifecycle transitions, and governed quantity movements serialize on the same durable `InventoryItem` lock; lifecycle transitions write factual audit history.
- Exact merged-main Maven PR Tests run `33028403587` passed on `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`.

### P17-C6 — Fund hierarchy lifecycle integrity

Status: IN_PROGRESS.

Branch: `codex/P17-C6-fund-hierarchy-lifecycle`  
Starting base: `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`  
Pull request: pending

Required reading:

- `doc/ui_design_rules.md`
- `doc/interface-operation-matrix.md`
- `doc/ui/editor-guidelines.md`
- `doc/data-exchange/sclx.md`
- donor Fund model only as design reference; donor persistence is not authoritative

Required implementation/test inspection:

- `src/main/java/org/nonprofitbookkeeping/model/Fund.java`
- `src/main/java/org/nonprofitbookkeeping/service/FundCommand.java`
- `src/main/java/org/nonprofitbookkeeping/service/FundAdminService.java`
- `src/main/java/org/nonprofitbookkeeping/service/FundLookupService.java`
- `src/main/java/org/nonprofitbookkeeping/ui/FundsPanel.java`
- `src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxStructureValidator.java`
- `src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportDocumentValidator.java`
- `src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxImportCommitService.java`
- focused Fund and SCLX tests plus current production-route/core-editor compliance tests

Purpose:

- Close the remaining Fund hierarchy lifecycle integrity gap without changing fund accounting authority, transaction posting, or introducing a second fund model.
- Preserve stable Fund identity/history while ensuring an active child can never be maintained beneath an inactive parent hierarchy.
- Apply the same invariant to SCLX validation/import/export so interchange cannot bypass interactive lifecycle rules.

Audit finding and selected direction:

- `FundsPanel` already uses stable IDs, real protected `Delete Unused`, Active/inactive state, retained-history guidance, H2-backed layout state, and company date formatting.
- `FundAdminService.apply(...)`, however, currently copies `command.active()` and a validated parent independently. It allows an active child beneath an inactive parent and allows a parent to be deactivated while active children remain.
- `FundLookupService.listActiveFunds()` filters only the child row's Active flag, so such an invalid child remains selectable by production posting/reference-data consumers.
- SCLX currently writes Fund parent/status directly and validates only that exported parent IDs resolve; therefore interchange can also manufacture or serialize the same invalid hierarchy.
- The donor Fund model contains the same basic parent/Active fields but no stronger lifecycle authority worth porting.

Planned deliverables:

- Serialize interactive Fund hierarchy mutations and protected deletion on company authority.
- Require every active Fund's parent ancestry to be active; reject active creation/reactivation/reparenting beneath an inactive parent.
- Reject deactivation of a Fund while active child Funds remain, preserving an explicit child-first retirement / parent-first reactivation order.
- Keep inactive children under inactive parents valid retained history; retain the existing real `Delete Unused` operation only for completely unreferenced Funds.
- Add visible Funds-panel guidance for hierarchy retirement/reactivation ordering.
- Make SCLX structure validation reject missing/circular Fund parents and active-child/inactive-parent hierarchies before commit; make export validation reject the same invalid snapshot; retain a defensive import-time check and serialize SCLX Fund writes on the same company authority.
- Add focused H2 Fund lifecycle regressions, SCLX validator regressions, source/UI guardrails, a Fund lifecycle contract, and an owner desktop checklist.

Validation status:

- Exact starting `main` is `2e9114a769b15c0f5e7b0a1147d84c0fe308cc53`.
- C5 merged-main Maven PR Tests run `33028403587` succeeded and is the C6 baseline.
- No local Maven result is claimed; GitHub Maven PR Tests will be authoritative after publication.

Known failures:

- None at task start.

Owner acceptance:

- A P17-C6 owner checklist will be added before handoff.
- Do not merge until final-head GitHub validation passes and the owner accepts the checklist.

Next exact action:

- Implement the Fund hierarchy service/interchange invariants and focused regressions, update governing documentation/UI guidance, publish a draft PR, run Maven PR Tests, correct any failure, then stop before merge for owner desktop acceptance.
'''
updated, count = re.subn(r'### P17-C5 — Inventory item lifecycle completion\n.*\Z', new_tail, text, count=1, flags=re.S)
if count != 1:
    raise SystemExit('P17-C5 tail target not found')
p.write_text(updated)
print('P17-C6 PLAN controller staged')
