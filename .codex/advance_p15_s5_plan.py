from __future__ import annotations

import os
import re
from pathlib import Path

PLAN = Path("doc/PLAN.md")
text = PLAN.read_text(encoding="utf-8")
pr_number = os.environ["PR_NUMBER"]
branch = "codex/P15-S5-sclx-import-preview"

replacements = [
    (r"(?m)^plan_version: 97$", "plan_version: 98"),
    (r"(?m)^active_slice: P15-S4$", "active_slice: P15-S5"),
    (r"(?m)^active_status: VERIFYING$", "active_status: IN_PROGRESS"),
    (r"(?m)^active_branch: codex/P15-S4-C6-audit-history-sclx-export$", f"active_branch: {branch}"),
    (r"(?m)^active_pull_request: 226$", f"active_pull_request: {pr_number}"),
    (r'(?m)^active_head: "230f247245073c39747f3573ccb1682e41eaf42f"$', 'active_head: "PENDING_FIRST_IMPLEMENTATION_COMMIT"'),
    (r'(?m)^next_action: "Complete the P15-S4 owner desktop acceptance checklist and merge PR #226 only after explicit owner authorization\."$',
     'next_action: "Inspect current SCLX parser, validation, preview, identity, and transaction-service boundaries; then implement the first coherent P15-S5 import-preview slice on the recorded branch."'),
    (r"\| P15 \| Versioned data interchange and database transfer \| P02, P05, P06, P12, P13, P14 \| IN_PROGRESS at P15-S4 \|",
     "| P15 | Versioned data interchange and database transfer | P02, P05, P06, P12, P13, P14 | IN_PROGRESS at P15-S5 |"),
    (r"Status: VERIFYING on branch `codex/P15-S4-C6-audit-history-sclx-export` in draft PR #226\.",
     "Status: DONE through merged PR #226 and owner desktop acceptance."),
    (r"Final plan-inclusive tested head: `230f247245073c39747f3573ccb1682e41eaf42f`\.",
     "Final exact tested head: `6ed522251193ae3aa16942f84dd8ff4a91556ebb`; merged to `main` at `d7304eca38e21715bc7f1d039ed3ac3c4c9e5bed`."),
    (r"Status: VERIFYING on `codex/P15-S4-C6-audit-history-sclx-export` in draft PR #226\.",
     "Status: DONE through merged PR #226 and owner desktop acceptance."),
    (r"Status: BLOCKED until P15-S4 merges\.",
     f"Status: IN_PROGRESS on `{branch}` in draft PR #{pr_number}."),
]

for pattern, replacement in replacements:
    text, count = re.subn(pattern, replacement, text, count=1)
    if count != 1:
        raise SystemExit(f"Expected exactly one PLAN replacement for: {pattern!r}; found {count}")

old_s4_next = """Next exact action:\n\n- Confirm final plan-inclusive Maven PR Tests on PR #226, complete `doc/P15-S4-sclx-export-ui-user-testing.md`, and merge only after explicit owner authorization. After merge, mark P15-S4 DONE and start P15-S5 from fresh current `main`.\n"""
new_s4_next = """Owner verification:\n\n- The owner completed and passed `doc/P15-S4-sclx-export-ui-user-testing.md`.\n- PR #226 merged to `main` at `d7304eca38e21715bc7f1d039ed3ac3c4c9e5bed`.\n\nNext exact action:\n\n- None; P15-S4 is DONE.\n"""
if old_s4_next not in text:
    raise SystemExit("P15-S4 next-action block was not found")
text = text.replace(old_s4_next, new_s4_next, 1)

old_c6_next = """Next exact action:\n\n- Perform the owner desktop checklist and merge PR #226 only after explicit owner authorization.\n"""
new_c6_next = """Owner verification:\n\n- The owner completed and passed the P15-S4 desktop checklist.\n- PR #226 merged to `main` at `d7304eca38e21715bc7f1d039ed3ac3c4c9e5bed`.\n\nNext exact action:\n\n- None; P15-S4-C6 is DONE.\n"""
if old_c6_next not in text:
    raise SystemExit("P15-S4-C6 next-action block was not found")
text = text.replace(old_c6_next, new_c6_next, 1)

p15_s5_marker = f"""## P15-S5 — SCLX preview, mapping, and transactional import\n\nStatus: IN_PROGRESS on `{branch}` in draft PR #{pr_number}.\n"""
if p15_s5_marker not in text:
    raise SystemExit("P15-S5 status marker was not produced")

p15_s5_start = f"""## P15-S5 — SCLX preview, mapping, and transactional import\n\nStatus: IN_PROGRESS on `{branch}` in draft PR #{pr_number}.\n\nStartup scope:\n\n- Begin with a coherent non-mutating import-preview slice: parsed-document projection, target-company scope, exact entity/reference counts, unsupported-section reporting, identity classification, account/fund mapping requirements, transaction-balance diagnostics, closed-period/reconciliation conflict diagnostics, and no H2 writes.\n- Reuse the existing bounded SCLX parser, validators, shared interchange contracts, durable interchange identity, canonical transaction services, and Import Preview workspace; do not introduce a donor parallel repository or generic job framework.\n- Defer transactional commit until the preview contract and mappings are governed and tested.\n\nCurrent validation status:\n\n- P15-S4 export final exact-head Maven PR Tests run `30597102760` passed all gates before merge.\n- P15-S5 baseline and focused tests are pending first implementation.\n\nNext exact action:\n\n- Inspect the current parser, validator, identity repository, Import Preview route, account/fund services, closed-period enforcement, and reconciliation protections; then implement and test the first non-mutating preview slice.\n"""
text = text.replace(p15_s5_marker, p15_s5_start, 1)

PLAN.write_text(text, encoding="utf-8")
