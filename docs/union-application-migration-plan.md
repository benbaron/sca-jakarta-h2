# Union Application Migration Plan

## Decision

`benbaron/sca-jakarta-h2` is now the primary application repository.

`benbaron/npbk-javafx-h2` is a donor/prototype repository. It may provide mature panels, subpanels, report templates, UX behavior, or design notes, but its architecture should not replace the primary `sca-jakarta-h2` architecture.

## Settled constraints

- Primary repository: `benbaron/sca-jakarta-h2`.
- Donor repository: `benbaron/npbk-javafx-h2`.
- Package namespace remains `org.nonprofitbookkeeping`.
- Reports imported from the donor app should appear in `REPORT_LIBRARY`, not as a separate top-level reporting shell.
- Supplies should be folded into Inventory, not kept as a separate top-level domain.
- Budget categories should be modeled as a distinct `BudgetCategory` concept, not overloaded onto `Activity`.
- The merged application should become a superset/union application, but only by importing mature pieces deliberately.

## Schema authority

Use the `sca-jakarta-h2` JPA/Hibernate model as the primary schema authority.

Do not copy `npbk-javafx-h2`'s direct JDBC `Database.java` bootstrap or its schema wholesale. That schema was useful for prototype thinking, but `sca-jakarta-h2` already has a more mature persistence design around JPA entities, services, and panel contracts.

When a concept exists in both applications, prefer the `sca-jakarta-h2` model unless the donor app has a clearly better workbook-specific design.

When the donor app has a useful concept missing from `sca-jakarta-h2`, add it as a JPA entity, embeddable, relationship, service, report definition, or UI panel inside the primary app.

## Current primary model strengths

The primary application already has mature foundations:

- `Txn` as the transaction header.
- `TxnSplit` as the accounting split/line.
- `Account` and `ChartOfAccounts` for account identity and account hierarchy.
- `Fund` for fund accounting.
- `ReportSection` and `AccountReportSection` for mapping accounts to report sections.
- `Counterparty`, `Merchant`, and `Activity` as supporting dimensions.
- `MainWindow`, `PanelHost`, `NavigationPane`, `InspectorPane`, and `UiServiceRegistry` as the primary JavaFX application shell.
- Existing panels for ledger, transaction editing, schedules, budget, assets, depreciation, inventory, reconciliation, period close, imports, bank transactions, reporting, chart of accounts, funds, settings, diagnostics, and help.

## Concepts to import or re-express from the donor app

The donor app should be mined for mature pieces, but those pieces should be adapted to the primary architecture.

Potential imports:

- Semantic JSON report templates for workbook-modeled forms.
- Workbook-modeled `BalanceStmt`, `IncomeStmt`, `WorkbookSummary`, `TransactionsList`, `AllChecksTfrs`, and `FundTransfers` report definitions.
- Minimum readable column width behavior for refreshed tables.
- Scrollable center workspace behavior if needed.
- Workbook-style report layout cues.
- Supplemental schedule concepts for receivables, prepaid expenses, other assets, deferred revenue, payables, and other liabilities.
- Bank statement line and reconciliation support concepts.
- Period close workflow concepts.
- Inventory/supplies data-entry behavior, folded into the primary Inventory domain.

Avoid importing:

- The donor app's direct `Database.java` schema bootstrap.
- The donor app's flat transaction-row repository assumptions.
- Any donor component that stores workbook row/cell identity as durable accounting truth.
- Any raw worksheet-cell JSON dump as the canonical report template language.

## Target schema direction

### Keep and evolve

Keep these primary entities as the foundation:

- `ChartOfAccounts`
- `Account`
- `AccountAlias`
- `ReportSection`
- `AccountReportSection`
- `Fund`
- `FundAlias`
- `Counterparty`
- `Merchant`
- `Activity`
- `Txn`
- `TxnSplit`
- `FundTransfer`

### Add or refine

Add or refine these concepts in the primary app:

- `BudgetCategory`
- `BudgetCategoryAlias` if import/workbook names need normalization.
- A `TxnSplit` relationship to `BudgetCategory`.
- Reference/admin service and panel support for budget categories.
- Inventory subtype/category support so supplies can be represented within Inventory.
- Supplemental schedule rows linked to `TxnSplit` for schedule-required accounts.
- Bank statement imported line records, if not already complete.
- Reconciliation matching records, if not already complete.
- Period close records/runs sufficient for close workflow and report cutoff control.
- Report template metadata for workbook-modeled forms in `REPORT_LIBRARY`.

### Transaction model target

Prefer this conceptual shape:

```text
Txn
  id
  txnDate
  payee
  memo
  bankAccount
  reference/check number
  affectsBank / bank timing if needed
  affectsBudget / budget timing if needed
  createdAt
  updatedAt

TxnSplit
  id
  txn
  account
  fund
  budgetCategory
  activity
  merchant
  nmrFlag
  amountSigned
  notes
```

Keep `amountSigned` unless there is a strong reason to move to stored debit/credit columns. Presentation code can derive debit and credit from signed amount plus account normal balance.

## BudgetCategory decision

`BudgetCategory` must be distinct from `Activity`.

Suggested semantics:

- `Activity`: event, project, occasion, or operational activity.
- `BudgetCategory`: workbook/budget/reporting category used to classify planned and actual amounts.
- `Fund`: restriction or fund bucket.
- `Account`: accounting/report-line classification.

A transaction split may have both an activity and a budget category.

## Report-library migration direction

The donor app's workbook-modeled reports should be migrated into the primary app's `REPORT_LIBRARY` area.

Target reports:

- `BalanceStmt`
- `IncomeStmt`
- `WorkbookSummary`
- `TransactionsList`
- `AllChecksTfrs`
- `FundTransfers`

Report templates should be semantic and compact. They should describe sections, rows, labels, value keys, formats, table columns, and source workbook traceability. They should not store every blank cell, formula, border, or style ID from the workbook.

Excel formulas are traceability metadata only. The application must calculate report values through Java/H2/JPA services.

## Inventory and supplies direction

Supplies should be represented inside the Inventory domain.

Suggested direction:

```text
InventoryItem
  category/subtype: durable asset, supply, regalia, equipment, etc.
  quantity/count
  value fields
  guardian/custodian
  acquisition/removal information
  confirmed/review dates
  linked TxnSplit where applicable
```

The former Supplies workbook page can become a filtered inventory view or a supplies subpanel within Inventory.

## Migration phases

### Phase 0: Documentation and alignment

- Record this decision document.
- Keep `sca-jakarta-h2` as primary.
- Treat `npbk-javafx-h2` as donor/prototype.
- Do not make further architectural investments in the donor repo except for reference extraction.

### Phase 1: Schema alignment

- Add `BudgetCategory` entity.
- Add `BudgetCategory` service/repository support.
- Add `TxnSplit.budgetCategory` relationship.
- Add schema migration for budget categories.
- Add/adjust tests for split classification.

### Phase 2: Report Library integration

- Add compact semantic report templates to the primary app.
- Integrate workbook-modeled reports into `ReportLibraryPanel`.
- Use primary services/entities for value providers.
- Avoid raw worksheet-cell template resources as runtime source.

### Phase 3: Inventory union

- Extend Inventory to cover Supplies behavior.
- Add supply-specific fields/subtype only where needed.
- Migrate any useful donor Supplies panel behavior into Inventory.

### Phase 4: Supplemental schedules

- Add schedule rows linked to `TxnSplit` for receivables, prepaid expenses, other assets, deferred revenue, payables, and other liabilities.
- Integrate with schedule eligibility rules already present in the primary app.
- Add input panels/subpanels where appropriate.

### Phase 5: Banking, reconciliation, and period close

- Compare donor banking and period close concepts with primary reconciliation/period close panels.
- Add missing imported bank-line and matching fields as needed.
- Ensure close workflow drives report cutoff and audit state.

### Phase 6: Cleanup and deprecation

- Mark `npbk-javafx-h2` as deprecated/archive/reference in README or repository settings.
- Move any remaining useful documentation into `sca-jakarta-h2`.
- Stop opening implementation PRs against `npbk-javafx-h2`.

## Immediate next code PR after this document

The recommended first code PR is:

```text
Add BudgetCategory to the primary JPA model
```

Scope:

- `BudgetCategory` entity.
- Optional `BudgetCategoryAlias` entity if needed.
- Add relationship from `TxnSplit` to `BudgetCategory`.
- Add service/repository lookup and admin operations.
- Add migration SQL or schema update consistent with the primary app's schema management.
- Add tests.

## Open implementation questions

Before coding Phase 1, confirm:

1. Whether budget categories need a hierarchy, or a flat list is sufficient for the first pass.
2. Whether budget categories should be scoped by fiscal year, chart, company, or globally active list.
3. Whether a split can have zero, one, or multiple budget categories. The recommended first pass is zero-or-one.
4. Whether imported workbook category names should be stored as aliases on `BudgetCategory`.
5. Whether `Txn` needs explicit `affectsBank` and `affectsBudget` timing fields, or whether those should live on splits or imported workbook staging rows.
