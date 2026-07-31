from pathlib import Path

path = Path("doc/PLAN.md")
text = path.read_text(encoding="utf-8")


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"expected one PLAN anchor, found {count}: {old[:140]!r}")
    text = text.replace(old, new, 1)


replace_once(
    'active_head: "58c17129c7e77fa9eab690c4a3a79db280f2f060"\nnext_action: "Confirm final plan-inclusive Maven PR Tests, complete the P15-S4 owner desktop acceptance checklist, and merge PR #226 only after explicit owner authorization."',
    'active_head: "230f247245073c39747f3573ccb1682e41eaf42f"\nnext_action: "Complete the P15-S4 owner desktop acceptance checklist and merge PR #226 only after explicit owner authorization."')

replace_once(
    'Current tested implementation head: `9a0c22cbbfa19b438ea100f2228d09c8b23b22b7`; owner-checklist-inclusive head: `58c17129c7e77fa9eab690c4a3a79db280f2f060`.',
    'Final plan-inclusive tested head: `230f247245073c39747f3573ccb1682e41eaf42f`.')

replace_once(
    '- The owner checklist was reconciled on head `58c17129c7e77fa9eab690c4a3a79db280f2f060` to verify inventory, period-close, and audit-history counts/content and the completed no-deferred-section state; final plan-inclusive CI remains required.',
    '- The owner checklist was reconciled to verify inventory, period-close, and audit-history counts/content and the completed no-deferred-section state. Final Maven PR Tests run `30596836488` passed on clean plan-inclusive head `230f247245073c39747f3573ccb1682e41eaf42f`: `mvn clean verify`, the repeated Maven test suite, and JavaFX production-route compliance all succeeded.')

replace_once(
    '- The completed owner checklist is present on head `58c17129c7e77fa9eab690c4a3a79db280f2f060`; final plan-inclusive CI and owner desktop acceptance remain open.',
    '- Final plan-inclusive Maven PR Tests run `30596836488` passed on clean head `230f247245073c39747f3573ccb1682e41eaf42f`; owner desktop acceptance remains open.')

replace_once(
    '- Confirm the final plan-inclusive Maven PR Tests, perform the owner desktop checklist, and merge PR #226 only after explicit owner authorization.',
    '- Perform the owner desktop checklist and merge PR #226 only after explicit owner authorization.')

path.write_text(text, encoding="utf-8", newline="\n")
marker = Path(".codex/P15-S4-C6-final-ci-trigger.txt")
marker.parent.mkdir(parents=True, exist_ok=True)
marker.write_text("Delete after the final validation record is committed.\n", encoding="utf-8")
print("Recorded successful P15-S4-C6 final validation")
