# P15-S5 SCLX import preview UI — owner testing

## Scope

The production **Import Preview** workspace now contains **Preview SCLX…**. This action reads and
diagnoses one selected-company SCLX file against the currently selected target company. It does not
write to H2 and does not expose an SCLX commit action.

## Desktop checklist

1. Back up the current database, then open the application and select a disposable empty company.
2. Open **Import Preview** and confirm that **Preview SCLX…**, **Preview COA CSV…**,
   **Preview Bank OFX/QFX…**, and the existing COA commit action are visibly distinct.
3. Select **Preview SCLX…** and confirm that the chooser is labeled **SCLX Active Company Files** and
   offers `.sclx` and `.json` candidates.
4. Cancel the chooser and confirm that no status, company data, or existing preview rows change.
5. Preview a valid SCLX 1.3 export for the empty target. Confirm that the status names the source,
   version, target company, entity/new/identical/error totals, recommended account mode, and states
   **No data was changed**.
6. Review **SCLX Counts**. Confirm that every included entity type is listed and that total entities,
   references, relationships, and unsupported sections agree with the file.
7. Review **SCLX Entities**. Confirm that every row shows type, external ID, disposition, optional
   local ID, and source path.
8. Review **SCLX Mappings**. Confirm that account and fund rows show source, target, used state,
   resolution, blocking state, and an explanation. An empty compatible target should recommend
   `AS_IS`.
9. Review **SCLX Transactions**. Confirm that each row shows source/posting/zero line counts,
   balanced state, and any required balancing-account, closed-period, or finalized-reconciliation
   action.
10. Review **Preview Warnings**. Confirm that each item includes severity, stable code, source path,
    and readable message.
11. Confirm that **Commit Accepted COA Rows** is disabled after the SCLX preview and that no SCLX
    commit/import button is present.
12. Close and reopen the company or restart the application. Confirm that no accounts, funds,
    transactions, period-close facts, reconciliation facts, or external identities were created or
    changed by preview.

## Blocking cases

Repeat the preview against a populated company and with a source containing an unbalanced
transaction, a closed-period date, or a transaction identity bound to a finalized reconciliation.
Confirm that the status says `BLOCKED`, the stable errors appear in **Preview Warnings**, and the
corresponding mapping or transaction rows explain the conflict. Confirm again that H2 is unchanged.

## Layout checks

At the normal laptop window size and at 100%, 125%, and 150% display scaling:

- move the Preview Warnings/results divider and the existing COA accepted/rejected divider;
- resize and reorder each SCLX table column;
- confirm each table retains independent horizontal and vertical scrolling; and
- confirm long identifiers, paths, and explanations remain discoverable through scrolling and the
  production full-text hover tooltip behavior.
