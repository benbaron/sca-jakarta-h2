from pathlib import Path
import re


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 occurrence, found {count}")
    return text.replace(old, new, 1)


def regex_once(text, pattern, replacement, label, flags=0):
    new_text, count = re.subn(pattern, replacement, text, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 regex match, found {count}")
    return new_text


path = Path("doc/PLAN.md")
text = path.read_text()
for old, new, label in [
    ("plan_version: 131", "plan_version: 132", "plan version"),
    ("active_slice: P16-S1", "active_slice: P16-S2", "active slice"),
    ("active_branch: codex/P16-S1-atomic-monthly-depreciation", "active_branch: codex/P16-S2-atomic-coa-csv-commit", "active branch"),
    ("active_pull_request: 252", "active_pull_request: 253", "active PR"),
    ("active_head: 96175d54fd5c739ca29cf8bb35e49ebf89938d70", "active_head: e495865b91a4979c1571f6ab61922ea43fedd839", "active head"),
    ('next_action: "Complete the P16-S1 owner desktop checklist on draft PR #252; if accepted, record owner approval and await explicit merge authorization. Do not start P16-S2."',
     'next_action: "Complete the P16-S2 owner desktop checklist on draft PR #253; if accepted, record owner approval and await explicit merge authorization. Do not start P16-S3."',
     "frontmatter next action"),
    ("| P16 | Interface-to-authority completion and integrity corrections | P03-P15 except eliminated P07 | VERIFYING through P16-S1 / PR #252 |",
     "| P16 | Interface-to-authority completion and integrity corrections | P03-P15 except eliminated P07 | VERIFYING through P16-S2 / PR #253 |",
     "phase index P16"),
    ("Status: VERIFYING in draft PR #252.", "Status: DONE through merged PR #252 and owner desktop acceptance.", "P16-S1 status"),
]:
    text = replace_once(text, old, new, label)

text = replace_once(
    text,
    "- Automated acceptance is complete. The owner desktop checklist in `doc/P16-S1-atomic-monthly-depreciation-user-testing.md` remains required before merge.",
    "- Automated acceptance completed on implementation head `96175d54fd5c739ca29cf8bb35e49ebf89938d70` in Maven PR Tests run `31147294296`.\n"
    "- Final plan-inclusive head `501374df0318e4008291c53c2a00e2d7e8c857a3` passed the complete Maven PR Tests gate in run `31147729410`.\n"
    "- The owner completed and accepted `doc/P16-S1-atomic-monthly-depreciation-user-testing.md` and explicitly authorized merge.\n"
    "- PR #252 merged to `main` at `a88becddf7ede7fcf3d986e7d8861351ce5438d5` on 2026-08-07.",
    "P16-S1 completed validation",
)
text = replace_once(
    text,
    "- Complete the owner desktop checklist on draft PR #252. If accepted, record owner approval and await explicit merge authorization. Do not merge or start P16-S2 without authorization.",
    "- None; P16-S1 is DONE and P16-S2 is active.",
    "P16-S1 next action",
)
text = replace_once(
    text,
    "## P16-S2 — Atomic COA CSV accepted-row commit\n\nStatus: BLOCKED by P16-S1.\n\nPurpose:",
    "## P16-S2 — Atomic COA CSV accepted-row commit\n\n"
    "Status: VERIFYING in draft PR #253.\n\n"
    "Branch: `codex/P16-S2-atomic-coa-csv-commit`\n\n"
    "Pull request: #253  \n"
    "Starting base: `a88becddf7ede7fcf3d986e7d8861351ce5438d5`  \n"
    "Validated implementation head: `e495865b91a4979c1571f6ab61922ea43fedd839`\n\n"
    "Purpose:",
    "P16-S2 status block",
)
text = replace_once(
    text,
    "- Restart and company-isolation tests prove no partial chart is visible.\n\n## P16-S3",
    "- Restart and company-isolation tests prove no partial chart is visible.\n\n"
    "Validation status:\n\n"
    "- Draft PR #253 is based on exact P16-S1 merge `a88becddf7ede7fcf3d986e7d8861351ce5438d5`.\n"
    "- `CoaCsvImportService` owns the frozen CSV preview and one caller-owned JPA commit boundary; the production JavaFX route no longer loops accepted rows through independently committing `AccountAdminService.upsert()` calls.\n"
    "- The batch freezes source SHA-256, company, target chart, target fingerprint, accepted/rejected rows, validation messages, and confirmation state; commit rejects source, company, chart, or target-state drift and requires a new preview.\n"
    "- All account writes, `COA_CSV` external identities, and one factual operation `AuditEvent` commit together or roll back together. Late failure reports zero committed created/updated/skipped counts.\n"
    "- Identical re-preview/recommit is idempotent and does not duplicate identities or operation audit history. P15 Chart-of-Accounts JSON import/export remains unchanged.\n"
    "- Public single-account `AccountAdminService.upsert(...)` now validates code/name/type/normal-balance before any persistence access; caller-owned batch validation remains independently enforced.\n"
    "- Exact implementation head `e495865b91a4979c1571f6ab61922ea43fedd839` passed `mvn clean verify`, the deliberately repeated Maven suite, and JavaFX production-route compliance in Maven PR Tests run `31192123755`.\n"
    "- Automated acceptance is complete. The owner desktop checklist in `doc/P16-S2-atomic-coa-csv-user-testing.md` remains required before merge.\n\n"
    "Next exact action:\n\n"
    "- Complete the owner desktop checklist on draft PR #253. If accepted, record owner approval and await explicit merge authorization. Do not merge or start P16-S3 without authorization.\n\n"
    "## P16-S3",
    "P16-S2 validation block",
)
path.write_text(text)

path = Path("doc/interface-operation-matrix.md")
text = path.read_text()
text = replace_once(
    text,
    "Status: P00 inventory of current main, updated through P15-S8-C4 interchange progress, pre-commit cancellation, and laptop-width closure.",
    "Status: P00 inventory of current main, updated through P16-S2 atomic COA CSV commit, P15-S8-C4 interchange progress, pre-commit cancellation, and laptop-width closure.",
    "interface status",
)
new_import_row = (
    "| `IMPORT_PREVIEW` | `ImportPreviewPanel` | SCLX/COA controls plus configured-account/profile selectors, explicit CSV-profile save, "
    "OFX/QFX, mapped-CSV, and normalized-bank-CSV preview, original/normalized rows, account confirmation, actor, atomic commit, bounded progress, "
    "and Cancel Preview | fixed-scope SCLX services; `CoaCsvImportService`; `BankStatementReviewService`, `BankCsvReviewService`, "
    "`NormalizedBankCsvReviewService`, `BankCsvMappingProfileService`, `BankConfigurationService` | exact successful SCLX or bank preview commits "
    "through its canonical one-transaction service after confirmation; COA CSV **Commit Accepted COA Rows** commits all accepted account writes, "
    "`COA_CSV` identities, and one factual operation audit through `CoaCsvImportService` plus the caller-owned `AccountAdminService` seam in one "
    "JPA transaction; P15 COA JSON remains owned by `ChartOfAccountsJsonImportService` | yes for committed facts/profiles/accounts | yes | bounded "
    "secure parsers, frozen source SHA-256, active company, target chart/fingerprint, exact accepted/rejected rows, configured account, "
    "caller-owned services, transient `InterchangeTaskController` | no direct bank staging or ledger creation and no row-by-row COA durability; "
    "cancelled previews publish no result or audit; cancellation locks when a durable commit starts; source/company/chart/target drift requires "
    "preview again; any COA row failure rolls back the complete batch and reports zero committed counts | P16-S2 owner desktop verification plus "
    "final P15 laptop-width/progress verification | P15-S5/P15-S6/P15-S7/P15-S8/P16-S2 |"
)
text = regex_once(text, r"^\| `IMPORT_PREVIEW` \|.*$", new_import_row, "interface import preview row", flags=re.MULTILINE)
path.write_text(text)

path = Path("doc/persistence-authority-inventory.md")
text = path.read_text()
text = replace_once(
    text,
    "Status: P00 inventory of current main, updated through P15-S8-C4 interchange progress, pre-commit cancellation, and laptop-width closure.",
    "Status: P00 inventory of current main, updated through P16-S2 atomic COA CSV commit, P15-S8-C4 interchange progress, pre-commit cancellation, and laptop-width closure.",
    "persistence status",
)
new_preview_row = (
    "| Import preview | `ImportPreviewService` remains transient staging for legacy preview families; P16-S2 `CoaCsvImportService` owns the frozen "
    "COA CSV preview/commit scope | no by design until acceptance; yes for the resulting account/identity/audit facts after commit | preview data is "
    "intentionally in-memory, but accepted COA CSV writes now use one caller-owned transaction instead of independently committing rows | preserve "
    "transient preview; require atomic accepted-row commit, idempotent identical recommit, and new preview on source/company/chart/target drift |"
)
text = regex_once(text, r"^\| Import preview \|.*$", new_preview_row, "persistence import preview row", flags=re.MULTILINE)
text = replace_once(
    text,
    "- Import preview can remain in-memory while users review rows.\n"
    "- Accepted COA imports may write through admin services today; accepted bank statements write durable review facts through the exact-scope OFX/QFX, mapped-CSV, or normalized-CSV service and never through static UI state.",
    "- Import preview can remain in-memory while users review rows; preview state is not accepted business data.\n"
    "- P16-S2 COA CSV preview freezes the source SHA-256, active company, target chart, target fingerprint, accepted/rejected rows, validation messages, and confirmation state. **Commit Accepted COA Rows** revalidates the frozen scope and writes every accepted account, `COA_CSV` external identity, and one factual operation audit through `CoaCsvImportService` plus the caller-owned `AccountAdminService` seam in one JPA transaction. Any row, identity, audit, or constraint failure rolls back the whole batch and reports zero committed counts; identical re-preview/recommit is idempotent, while source/company/chart/target drift requires a new preview. P15 Chart-of-Accounts JSON import/export remains a separate unchanged authority.\n"
    "- Accepted bank statements write durable review facts through the exact-scope OFX/QFX, mapped-CSV, or normalized-CSV service and never through static UI state.",
    "persistence staging bullets",
)
path.write_text(text)

path = Path("doc/data-exchange/shared-operation-contract.md")
text = path.read_text()
text = replace_once(
    text,
    "Status: governing P15-S1 contract for operation lifecycle, company ownership, diagnostics, and external identity.",
    "Status: governing P15-S1 contract for operation lifecycle, company ownership, diagnostics, and external identity, clarified by P16-S2 atomic COA CSV commit semantics.",
    "shared contract status",
)
anchor = "This controller has no collection of jobs, persistence, retry history, scheduler, or cross-session state.\n"
addition = (
    "This controller has no collection of jobs, persistence, retry history, scheduler, or cross-session state.\n\n"
    "P16-S2 applies this same boundary to the production COA CSV **Commit Accepted COA Rows** path without changing the P15 Chart-of-Accounts JSON interchange. "
    "The COA CSV preview freezes the exact source SHA-256, active company, target chart, target fingerprint, accepted/rejected rows, validation messages, "
    "and confirmation state. Commit revalidates that frozen scope before mutation, orders parent-before-child writes, and persists all accepted accounts, "
    "`COA_CSV` external identities, and one factual operation audit in one caller-owned JPA transaction. Any row, identity, audit, or constraint failure "
    "rolls back the entire batch and reports zero committed created/updated/skipped counts. An identical re-preview/recommit is idempotent; source, company, "
    "chart, or target-state drift requires a new preview.\n"
)
text = replace_once(text, anchor, addition, "shared P16-S2 paragraph")
path.write_text(text)
