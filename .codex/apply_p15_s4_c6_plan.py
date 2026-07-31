from pathlib import Path

path = Path("doc/PLAN.md")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected one PLAN anchor, found {count}: {old[:120]!r}")
    text = text.replace(old, new, 1)


replace_once(
    '''---
plan_version: 96
active_phase: P15
active_slice: P15-S4
active_status: IN_PROGRESS
active_branch: codex/P15-S4-C6-audit-history-sclx-export
active_pull_request: null
active_head: "PENDING_IMPLEMENTATION_COMMIT"
next_action: "Complete factual AuditEvent portable identity and selected-company SCLX export on P15-S4-C6, open a draft PR, and run full Maven PR Tests."
---''',
    '''---
plan_version: 97
active_phase: P15
active_slice: P15-S4
active_status: VERIFYING
active_branch: codex/P15-S4-C6-audit-history-sclx-export
active_pull_request: 226
active_head: "58c17129c7e77fa9eab690c4a3a79db280f2f060"
next_action: "Confirm final plan-inclusive Maven PR Tests, complete the P15-S4 owner desktop acceptance checklist, and merge PR #226 only after explicit owner authorization."
---''')

replace_once(
    'This revision records fixed-asset export corrections merged through PR #220, durable inventory identities merged through PR #221, the inventory SCLX contract merged through PR #222, and its production implementation in corrective PR #223.',
    'This revision records selected-company SCLX inventory export through PR #223, period-close foundation/correction through PRs #224 and #225, and factual audit-history export under verification in draft PR #226.')

replace_once(
    '''Status: IN_PROGRESS on branch `codex/P15-S4-C6-audit-history-sclx-export`; draft PR pending.

Current implementation head: pending first P15-S4-C6 implementation commit.''',
    '''Status: VERIFYING on branch `codex/P15-S4-C6-audit-history-sclx-export` in draft PR #226.

Current tested implementation head: `9a0c22cbbfa19b438ea100f2228d09c8b23b22b7`; owner-checklist-inclusive head: `58c17129c7e77fa9eab690c4a3a79db280f2f060`.''')

replace_once(
    '- Maven PR Tests run `30512885063` passed on exact head `8a7eec251d4bba14c3d7cc0e167e2e2bebfdfe47`: `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance all succeeded.\n',
    '''- Maven PR Tests run `30512885063` passed on exact head `8a7eec251d4bba14c3d7cc0e167e2e2bebfdfe47`: `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance all succeeded.
- PR #224 merged the governed period-close extension foundation before production integration was complete; corrective PR #225 completed selected-company query/assembly, strict validation, exact counts, and completion-summary integration and merged at `6959f57daf840b9f93edb0bd9ed9a8d188685170` after successful Maven PR Tests run `30582139713`.
- Initial authoritative PR #226 run `30595756059` compiled production and ran the new migration/extension tests successfully but exposed one stale export-result fixture that still expected a deferred-section warning after all governed P15-S4 sections became included.
- Corrected PR #226 run `30596426183` passed on exact implementation head `9a0c22cbbfa19b438ea100f2228d09c8b23b22b7`: `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance all succeeded.
- The owner checklist was reconciled on head `58c17129c7e77fa9eab690c4a3a79db280f2f060` to verify inventory, period-close, and audit-history counts/content and the completed no-deferred-section state; final plan-inclusive CI remains required.
''')

replace_once(
    '''Next exact action:

- Review and merge PR #223 only after owner authorization. Then start a fresh P15-S4 branch for period-close facts and factual audit-history export; complete owner desktop acceptance before P15-S4 is marked done.''',
    '''Next exact action:

- Confirm final plan-inclusive Maven PR Tests on PR #226, complete `doc/P15-S4-sclx-export-ui-user-testing.md`, and merge only after explicit owner authorization. After merge, mark P15-S4 DONE and start P15-S5 from fresh current `main`.''')

replace_once(
    'Status: IN_PROGRESS on `codex/P15-S4-C6-audit-history-sclx-export`; draft PR pending.',
    'Status: VERIFYING on `codex/P15-S4-C6-audit-history-sclx-export` in draft PR #226.')

replace_once(
    '''Current validation status:

- Implementation publication and Maven PR Tests pending.

Next exact action:

- Publish the implementation commit, open a draft PR, run full Maven PR Tests, correct any failures, and update this handoff with the exact head and run.''',
    '''Current validation status:

- V67 recovery/backfill/default/uniqueness coverage and focused selected-company extension/ownership/count tests pass.
- Initial PR #226 run `30595756059` exposed only the stale warning-message expectation after audit history became included.
- Corrected implementation run `30596426183` passed all Maven PR Tests gates on head `9a0c22cbbfa19b438ea100f2228d09c8b23b22b7`.
- The completed owner checklist is present on head `58c17129c7e77fa9eab690c4a3a79db280f2f060`; final plan-inclusive CI and owner desktop acceptance remain open.

Next exact action:

- Confirm the final plan-inclusive Maven PR Tests, perform the owner desktop checklist, and merge PR #226 only after explicit owner authorization.''')

path.write_text(text, encoding="utf-8", newline="\n")
marker = Path(".codex/P15-S4-C6-plan-ci-trigger.txt")
marker.parent.mkdir(parents=True, exist_ok=True)
marker.write_text("Delete this marker with a normal branch commit to trigger final authoritative CI.\n", encoding="utf-8")
print("P15-S4-C6 PLAN reconciled")
