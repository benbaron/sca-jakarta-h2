from pathlib import Path
import re

path = Path('doc/PLAN.md')
text = path.read_text()
text = re.sub(r'plan_version: \d+', 'plan_version: 224', text, count=1)
text = re.sub(r'active_slice: P17-C6', 'active_slice: P17-C7', text, count=1)
text = re.sub(r'active_status: VERIFYING', 'active_status: IN_PROGRESS', text, count=1)
text = re.sub(r'active_branch: codex/P17-C6-fund-hierarchy-lifecycle', 'active_branch: codex/P17-C7-fixed-asset-lifecycle', text, count=1)
text = re.sub(r'active_pull_request: 298', 'active_pull_request: pending', text, count=1)
text = re.sub(r'active_head: [0-9a-f]{40}', 'active_head: d067877d699f4aa05c635b52abcc0aa65d55fbc3', text, count=1)
text = re.sub(r'next_action: ".*?"', 'next_action: "Complete fixed-asset status lifecycle authority: make ACTIVE/INACTIVE explicit audited actions, preserve DISPOSED as Sale/Retirement-owned, serialize metadata/depreciation/lifecycle writes on the asset lock, update UI/docs/tests, publish a draft PR, run Maven PR Tests, and stop before merge for owner acceptance."', text, count=1)
text = text.replace('C1 DONE; C2 DONE; C3 DONE; C4 DONE; C5 DONE; C6 VERIFYING', 'C1 DONE; C2 DONE; C3 DONE; C4 DONE; C5 DONE; C6 DONE; C7 IN_PROGRESS')
marker = '### P17-C6 — Fund hierarchy lifecycle integrity\n'
pos = text.find(marker)
if pos < 0:
    raise SystemExit('C6 section not found')
prefix = text[:pos]
replacement = '''### P17-C6 — Fund hierarchy lifecycle integrity

Status: DONE.

Completion evidence:

- PR #298 merged to `main` at `d067877d699f4aa05c635b52abcc0aa65d55fbc3` after owner acceptance.
- Exact final C6 PR head `2c9ebaf804d07b744554b35f03f5a70bc82e764d` passed Maven PR Tests run `33029795711`.
- Exact merged-main push run `33033595424` passed clean verify, repeat tests, and production JavaFX route compliance.
- Active Fund ancestry is enforced across interactive administration, active lookup, and SCLX import/export; lifecycle writes serialize on company authority while inactive hierarchy history remains retained.

### P17-C7 — Fixed-asset status lifecycle completion

Status: IN_PROGRESS.

Branch: `codex/P17-C7-fixed-asset-lifecycle`
Starting base: `d067877d699f4aa05c635b52abcc0aa65d55fbc3`
Pull request: pending

Required reading:

- `doc/ui_design_rules.md`
- `doc/interface-operation-matrix.md`
- `doc/ui/editor-guidelines.md`
- `doc/inventory/inventory-and-assets.md`
- `doc/data-exchange/sclx.md` for the historical restore boundary

Required implementation/test inspection:

- `src/main/java/org/nonprofitbookkeeping/model/FixedAsset.java`
- `src/main/java/org/nonprofitbookkeeping/service/FixedAssetCommand.java`
- `src/main/java/org/nonprofitbookkeeping/service/FixedAssetService.java`
- `src/main/java/org/nonprofitbookkeeping/ui/AssetsRegisterPanel.java`
- fixed-asset service/lifecycle/SCLX tests and current production-route/core-editor compliance tests

Purpose:

- Complete one fixed-asset lifecycle authority without changing Sale, Retirement, Impairment, depreciation calculations, or canonical ledger behavior.
- Preserve stable `FixedAsset.id` history while removing ACTIVE/INACTIVE status mutation from ordinary metadata editing.
- Keep `DISPOSED` exclusively owned by confirmed Sale/Retirement and restored only by domain lifecycle reversal.

Audit finding and selected direction:

- The financial lifecycle is already strong: Sale/Retirement/Impairment use frozen previews, canonical transactions, lifecycle events, audit facts, and pessimistic revalidation; `DISPOSED` is not directly editable.
- `AssetsRegisterPanel`, however, still exposes ACTIVE/INACTIVE in the ordinary asset form, and `FixedAssetService.update(...)` copies that requested status while loading the asset without a pessimistic lock.
- `runMonthlyDepreciation(...)` likewise reads the asset without the lock used by lifecycle commit, so metadata/status/depreciation operations can race the financial lifecycle boundary.
- Company Admin and User Admin already expose governed retained-history lifecycle behavior, while fixed assets remain the next concrete P17 durable-record gap.

Planned deliverables:

- Interactive asset creation starts ACTIVE; ordinary metadata updates preserve the persisted status and cannot change lifecycle state.
- Add explicit audited ACTIVE <-> INACTIVE service actions with factual actor/reason. `DISPOSED` remains unavailable to that action and requires Sale/Retirement; disposed assets require lifecycle reversal before any reactivation/edit.
- Serialize ordinary asset updates, explicit status changes, monthly depreciation, lifecycle commit, and lifecycle reversal through the same pessimistic `FixedAsset` lock before validating/mutating the asset.
- Keep SCLX `createForImport(...)` as the caller-owned historical source-status restore seam without fabricating local lifecycle audit facts.
- Replace the editable status combo with read-only status plus explicit Deactivate/Reactivate controls, retained-history/no-delete guidance, and the existing financial lifecycle controls.
- Add focused H2 lifecycle/audit regressions, concurrency/source/UI guardrails, governing documentation/matrix updates, and an owner desktop checklist.

Validation status:

- Exact starting `main` is `d067877d699f4aa05c635b52abcc0aa65d55fbc3`.
- Merged-main Maven PR Tests push run `33033595424` passed all three repository gates and is the C7 baseline.
- No local Maven result is claimed; GitHub Maven PR Tests will be authoritative after publication.

Known failures:

- None at task start.

Owner acceptance:

- A P17-C7 owner checklist will be added before handoff.
- Do not merge until final-head GitHub validation passes and the owner accepts the checklist.

Next exact action:

- Implement the fixed-asset status/lifecycle lock corrections and UI lifecycle actions, add focused regressions and governing documentation, publish a draft PR, run Maven PR Tests, correct any failure, then stop before merge for owner desktop acceptance.
'''
path.write_text(prefix + replacement)
print('P17-C7 PLAN controller staged')
