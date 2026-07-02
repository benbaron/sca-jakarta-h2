# Union application direction

This document consolidates still-current direction from the legacy union application migration notes into the production `doc/` tree.

## Direction

- `benbaron/sca-jakarta-h2` is the primary implementation repository.
- The production application is one JavaFX/H2 application, not a second shell or donor sidecar.
- The existing JPA/Hibernate model plus reviewed Flyway migrations are the schema foundation.
- Donor or experiment code may be used only as reference and must be adapted into the production architecture.
- Workbook-style reports belong in `REPORT_LIBRARY` and should use report services/templates rather than standalone workbook UI.
- Supplies belong within Inventory rather than a separate application area.
- `BudgetCategory` is distinct from `Activity` and from account master data.

## Practical rules for later phases

1. Do not copy donor persistence or static stores as production authority.
2. Extend the established package namespace `org.nonprofitbookkeeping`.
3. Keep JavaFX views dependent on application/query services rather than SQL or direct sidecar files.
4. Keep H2 authoritative for accepted operational/accounting data.
5. Record any deliberate architecture change in `doc/` before implementation.
