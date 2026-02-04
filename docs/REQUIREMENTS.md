# SCA Ledger (H2 + Jakarta) — Requirements

## Goal
Replace the Excel-based SCA treasurer workbook workflow with a Java desktop app backed by an H2 database, while preserving the *Excel-like grid* experience and the ability to show the underlying **General Journal** view at any time for training and clarity.

## Primary principles
1. **Signed storage**: store signed amounts in the DB; derive debit/credit columns for display using `Account.normal_balance`.
2. **Always show journal**: from any screen (ledger entry, schedule item, budget line, etc.), users can view the underlying postings in classic DR/CR format.
3. **Funds are explicit**: fund assignment is required on every posting split, and fund balances/reporting is core.
4. **Chart of Accounts is explicit**: accounts are first-class records, importable/exportable, versionable over time.

## Functional requirements
### Ledger / General Journal
- Enter transactions in an Excel-like grid: date, payee, memo, bank register, and one or more splits.
- Each split must reference:
  - an Account (CoA)
  - a Fund
  - signed amount
  - optional activity, merchant, NMR flag, notes
- System must validate the transaction balances before posting.
- Provide a General Journal view derived from signed splits.

### Chart of Accounts (CoA)
- CoA stored in DB tables:
  - account code/name/type/normal balance/hierarchy/posting flag/active flag/effective dates
- Import/export CoA:
  - CSV import/export for spreadsheet-friendly editing
  - JSON export for versioning (“git the chart”)
- Support aliases for legacy names (later iteration)

### Funds
- Funds stored in DB tables with metadata:
  - code/name/type/hierarchy/restriction text/active/effective dates
- Import/export funds as CSV (and later JSON)
- Fund transfers tracked as explicit objects linked to the posted transaction (later posting template)

### Subsidiary functions (phased)
- Budgeting: budgets reference accounts (budget categories are accounts)
- Inventory: operational records + optional postings (expense-on-purchase vs inventory-as-asset)
- Fixed assets and depreciation: asset register + depreciation runs posting to GL

## Non-functional requirements
- Desktop-first (JavaFX later), offline-friendly, local H2 file DB.
- Auditability: no silent changes; prefer append-only or deactivation over deletes.
- Migration-controlled schema (Flyway); Hibernate `validate` to prevent drift.
- Maintainability: layered architecture (model + persistence + service + UI).

## Constraints / assumptions
- Java 17
- H2 file DB (upgrade to PostgreSQL possible later)
- Jakarta Persistence (JPA) + Hibernate ORM
- CDI (Weld SE) for dependency injection
- Flyway migrations for schema evolution
