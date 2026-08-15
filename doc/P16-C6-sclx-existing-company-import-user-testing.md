# P16-C6 existing-company SCLX import — owner desktop checklist

## User-visible changes

- Import Preview can import SCLX into the active company when its existing data is limited to company
  settings, chart/accounts, funds, and ordinary factual audit history.
- The SCLX Mappings tab distinguishes accounts/funds that will be created from existing records that
  will be reused.
- An incompatible same-code collision offers only compatible target accounts or funds. **Apply SCLX
  Mappings** runs a fresh preview; **Approve shown SCLX account/fund mappings** is required before import.
- Existing company name, currency, fiscal-year settings, active-chart name/version, mapped accounts,
  and mapped funds are preserved.
- A target with transactions, budgets/categories, parties/merchants, banking/reconciliation,
  assets, inventory, or period-close history remains blocked.
- Activities assigned to the active import company are target master data, not a competing historical
  company. A compatible same-code Activity is reused and linked to the SCLX identity; different content
  remains a blocking conflict.
- Open legacy ownership diagnostics now appear as blocking preview errors, not only as a commit-time
  exception. Selecting a message keeps its complete resolution visible below the list.
- **Administration -> Company Ownership Diagnostics** supports confirmed, audited assignment of one
  direct ownerless record to the active company receiving the import. It does not bulk-guess owners or rewrite
  cross-company accounting references.
- **Re-preview Same SCLX** reruns the exact retained file after ownership repair without reopening the chooser.

## Manual verification

1. Back up the test database and open an active company that has its intended chart and General Fund,
   but no operational history listed above.
2. Record the company name, currency/fiscal-year settings, active chart name/version, account count,
   and fund count.
3. Open **Import Preview**, select **Preview SCLX…**, and choose the supplied donor SCLX file. If the
   database has legacy ownership diagnostics, confirm the preview is `BLOCKED`, every diagnostic is an
   error in **Preview Messages**, and the selected error directs you to **Administration -> Company
   Ownership Diagnostics**.
4. In **Administration -> Company Ownership Diagnostics**, select each direct ownerless row, review the
   record description and any related-company constraint, confirm the preselected active import company,
   enter the actor and audit note, and confirm **Assign to Import Company…**. Confirm the remaining count decreases and a failed/stale assignment changes
   nothing. Confirm cross-company reference rows are non-assignable and explain the appropriate next step.
5. Return to Import Preview and click **Re-preview Same SCLX** after the open diagnostic count reaches zero.
   Confirm no file chooser is required and the same normalized path is used.
6. Confirm the status reports created, mapped, and identical counts and still says no data changed.
7. Open **SCLX Mappings**. Confirm missing noncolliding account/fund codes say `CREATE`; compatible
   existing records say `MAPPED` and identify the exact target code.
8. For an incompatible same-code row such as source account `1000`, select a compatible target from
   **Target / Select**, click **Apply SCLX Mappings**, and confirm a fresh preview removes that mapping
   conflict. Confirm an incompatible target is never offered.
9. Confirm import stays disabled after changing a selector until mappings are applied, and remains
   disabled until both **Import into existing company (preserve settings)** and **Approve shown SCLX
   account/fund mappings** are selected and the audit actor is nonblank.
10. Import, accept the final confirmation, and confirm the result reports created, mapped, and identical
   counts with the exact target and SHA-256.
11. Reopen Administration, Chart of Accounts, Funds, Journal, Budgets, Assets, Inventory, Banking,
   Reconciliation, Period Close, and Audit History. Confirm the original company/chart settings and
   mapped master records are unchanged, missing masters and the SCLX business graph were added once,
   and one local `SCLX_IMPORTED` fact exists.
12. Preview the same file again. Confirm every governed identity is identical and importing is a no-op.
13. Confirm a compatible pre-assigned Activity is reported as reused and is not duplicated. In a separate
    company that already has a transaction or another operational-history family, preview
    the file and confirm `SCLX_OPERATIONAL_DATA_MERGE_UNSUPPORTED` blocks import with guidance to use a
    target without operational data or an identical reimport.
14. Cancel one confirmation and confirm no data changes. If practical, exercise an injected/test late
    failure and confirm the entire import rolls back without partial masters, history, or identities.
