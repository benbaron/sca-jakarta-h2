# P15-S5 complete SCLX import desktop checklist

Status: required owner acceptance before P15-S5-C10 merge.

## Setup

1. Start the packaged JavaFX application with a disposable database.
2. Create and select a new active target company with no business records.
3. Keep a representative SCLX 1.3 export available that contains accounts, funds, budgets, transactions, transaction details, assets, inventory, banking/reconciliation, period-close history, factual audit history, and a reversal/replacement pair.

## Preview and protection

1. Open **Import Preview** and confirm **Import Previewed SCLX…** is disabled before preview.
2. Clear **Import actor** and confirm the import button remains disabled.
3. Preview the representative file. Confirm the target, source, version, exact counts, identities, mappings, transaction diagnostics, warnings, and `READY TO IMPORT` status are readable at laptop width.
4. Confirm the preview states that no data changed. Restore a nonblank actor and confirm the import button becomes enabled.
5. Cancel the confirmation once. Confirm the dialog names the exact source, target, SHA-256, entity count, empty-target rule, and atomic rollback behavior, and that cancellation changes no data.
6. Preview a malformed or unsupported populated-extension file and confirm the import action remains disabled with a path-coded blocking message.

## Atomic commit and result

1. Preview the representative file again, enter the intended audit actor, choose **Import Previewed SCLX…**, review the confirmation, and approve it.
2. Confirm the UI stays responsive while the import runs and prevents a duplicate click.
3. Confirm success reports the fixed target, created/identical counts, and the previewed SHA-256, then disables the import button.
4. Reopen the relevant workspaces and verify representative accounts/funds, budget, transaction details, asset/run, inventory/movement, bank/reconciliation, close history, imported audit fact, and reversal/replacement links.
5. Export the target company to SCLX and verify the representative graph and correction relationships remain semantically equivalent.

## Reimport and scope safety

1. Preview the same file again against the imported company. Confirm every governed identity is identical and commit is a no-op with no duplicate operation audit.
2. Select a different empty company, return to Import Preview, and preview again. Confirm the displayed and confirmed target is the newly selected company.
3. Populate a disposable target before preview and confirm the empty-target protection blocks commit.
4. Confirm warnings, tables, actor field, buttons, confirmation, and result text remain visible and operable at the supported laptop-width window size.

Record pass/fail notes, operating system, display scale, database path label, and tested SCLX SHA-256 in the PR before merge.
