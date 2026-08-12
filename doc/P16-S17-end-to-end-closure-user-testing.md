# P16-S17 End-to-end closure — owner testing

## Preconditions

- Check out the exact final P16-S17 pull-request head recorded in `doc/PLAN.md` after CI completes.
- Use a disposable migrated H2 database containing two active companies with distinct accounts, funds, budgets, bank accounts, fixed assets, inventory, users, roles, and assignments.
- Keep a second disposable H2 database available for database-switch testing.
- Launch the production desktop at 1280 × 800 and keep the application log visible.

## Automated closure evidence

| Contract | Authoritative automated evidence |
|---|---|
| One migrated file, two companies, restart, late rollback, close/finalization protections | `P16EndToEndClosureTest` |
| Atomic depreciation and protected lifecycle writes | `FixedAssetAtomicDepreciationTest`, `FixedAssetLifecycleAccountingTest` |
| COA CSV rollback and preview drift | `CoaCsvImportServiceTest` |
| Reconciliation matching/finalization | `BankReconciliationMutationIntegrityTest` |
| Atomic database switching and failed-target retention | `DatabaseSessionControllerTest` |
| Stable draft/activation and company-owned budgets | `BudgetPlanServiceTest` |
| Company-scoped factual audit history | `AuditHistoryServiceTest` |
| Explicit reviewed-statement acceptance | `ReviewedStatementAcceptanceServiceTest` |
| Atomic inventory movements and corrections | `InventoryMovementAccountingTest` |
| Journal cleared-state projection after restart | `TransactionClearedStateProjectionTest` |
| Production preference consumers | `PreferenceConsumerMatrixTest`, `PreferenceProductionConsumerSourceTest` |
| Truthful global command capabilities | `AppPanelCommandCapabilityTest`, `GlobalCommandCapabilitySourceTest`, `ProductionPanelRouteComplianceTest` |
| Semantic report parity and asset/inventory reporting | `TruthfulSemanticReportIntegrationTest`, `AssetInventoryReportIntegrationTest` |
| Every canonical destination at laptop width | `ProductionPanelRouteComplianceTest`, `ProductionDesignRulesTestFxTest` |

## Cross-domain accounting workflow

1. In company A, import a valid COA CSV after preview and confirm the accepted accounts, interchange identities, and one factual audit event reload after restart. Repeat an identical import and confirm it is idempotent.
2. Run monthly depreciation for one fixed asset. Confirm one balanced canonical transaction and one completed run appear. Attempt the same period again and confirm no duplicate facts are written.
3. Record a financially relevant inventory receipt and correction. Confirm quantity, immutable movement history, canonical transaction/reversal links, and factual audit history agree after restart.
4. Create a budget draft, enter category/fund amounts, activate it, and run Budget vs Actual through the shell-selected fiscal range. Confirm reload never creates a hidden draft.
5. Import a bank statement into durable review, explicitly accept one reviewed row into a balanced canonical transaction, then match and finalize it in reconciliation.
6. Open Journal and confirm the bank line displays Cleared with the factual cleared date and reconciliation session. Attempt edit, delete, and reversal; confirm each is blocked without a partial write.
7. Close a separate accounting date and attempt transaction entry, depreciation, and inventory movement on that date. Confirm each is blocked and factual status text identifies the closed range.
8. Open Audit History and confirm the successful material actions appear with company, actor, entity, before/after facts, and reason. Confirm rejected actions do not create success audits.
9. Run Fixed Asset Register, Fixed Asset Depreciation, Inventory Valuation, Inventory Movement History, Trial Balance, and General Ledger. Confirm preview and CSV use the same filtered result and reconciliation differences are stated rather than hidden.

## Company, restart, and database boundaries

1. Switch to company B. Confirm Journal, budgets, bank review, reconciliation, assets, inventory, reports, audit history, and company assignments contain only company B facts.
2. Create one distinct company B fact in each writable domain, switch back to company A, and confirm company A remains unchanged.
3. Restart the desktop. Confirm the selected authoritative company, durable domain facts, table state, divider state, and preference consumers restore according to the documented policies.
4. With all editors clean, switch to the second database. Confirm every open panel reloads from the new datasource and no stale company A facts remain visible.
5. Make an editor dirty and attempt another database or company switch. Cancel the guard and confirm the old datasource/company and unsaved editor remain active.
6. Select an invalid database target. Confirm the prior datasource, company, active path, panels, and records remain unchanged and the failure text is factual.

## Canonical destination and command pass at 1280 × 800

1. Open every canonical destination once: Dashboard, Journal, Banking, Budgets, Budget vs Actual, Assets, Depreciation, Inventory, Reconciliation, Period Close, Import Preview, Audit History, Bank Transactions, Report Library, Chart of Accounts, Funds, Administration, Diagnostics, and Help.
2. For each destination, confirm the primary work area remains usable without clipping; tables/editors scroll independently where needed; split panes can be resized; and important actions do not require horizontal window growth.
3. Resize, reorder, and sort every production table. Switch company and restart, then confirm only company-owned table state restores.
4. Hover truncated labels and disabled command controls. Confirm tooltips explain the complete value or the factual unavailable reason.
5. On every destination and each Administration inner tab, compare menu, toolbar, and shortcut New/Save state. Confirm enabled commands perform the visible operation and disabled commands state that they are unavailable.
6. Create dirty state in Journal, Chart of Accounts, Funds, Budget Editor, Assets, Inventory, and Administration. Confirm close-tab, close-all, company-switch, and database-switch guards name the unsaved work and preserve it when cancelled.
7. Confirm status text never claims that a preview posted, a save occurred, an import succeeded, a reconciliation finalized, or authentication/authorization is enforced unless that exact operation occurred.

## Acceptance

- [ ] The cross-domain workflow survives restart with no partial facts from rejected or injected-failure operations.
- [ ] Company A and company B remain isolated across every writable and reporting surface.
- [ ] Database switching is atomic and preserves the prior session on cancel or failure.
- [ ] Closed-period and completed/finalized-reconciliation protections block every tested mutation.
- [ ] Every canonical destination is usable at 1280 × 800 with truthful command enablement, scrolling, split state, tooltips, guards, and status text.
- [ ] Preview, screen, CSV, canonical ledger, domain records, and audit history agree for every sampled operation.
- [ ] Authentication and runtime authorization remain explicitly deferred; administration never claims enforcement.
- [ ] Full `mvn clean verify`, repeated normal Maven suite, and focused JavaFX route-compliance suite pass on the exact final head.

Owner result: PENDING

Exact tested head: PENDING

Notes:

-
