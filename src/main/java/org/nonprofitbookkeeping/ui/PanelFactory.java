package org.nonprofitbookkeeping.ui;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Creates workspace panels from one shell-owned composition root. */
public final class PanelFactory
{
    private final Map<AppPanelId, Supplier<AppPanel>> factories = new EnumMap<>(AppPanelId.class);

    PanelFactory(WorkspaceServices services)
    {
        Objects.requireNonNull(services, "services");
        registerFactories(() -> new DashboardHomePanel(
                services.dashboardQueryService(),
                services.context()));
    }

    PanelFactory()
    {
        registerFactories(DashboardHomePanel::new);
    }

    private void registerFactories(Supplier<AppPanel> dashboardFactory)
    {
        factories.put(AppPanelId.DASHBOARD, dashboardFactory);
        factories.put(AppPanelId.JOURNAL_PANE, JournalWorkspaceCompliancePanel::new);
        factories.put(AppPanelId.BANKING, BankingPanel::new);
        factories.put(AppPanelId.BUDGET_EDITOR, BudgetEditorPanel::new);
        factories.put(AppPanelId.BUDGET_VS_ACTUAL, BudgetVsActualPanel::new);
        factories.put(AppPanelId.ASSETS_REGISTER, AssetsRegisterPanel::new);
        factories.put(AppPanelId.DEPRECIATION_RUNS, DepreciationRunsPanel::new);
        factories.put(AppPanelId.INVENTORY, InventoryPanel::new);
        factories.put(AppPanelId.RECONCILIATION_RUNS, ReconciliationRunsPanel::new);
        factories.put(AppPanelId.PERIOD_CLOSE_RUNS, PeriodCloseRunsPanel::new);
        factories.put(AppPanelId.IMPORT_PREVIEW, ImportPreviewPanel::new);
        factories.put(AppPanelId.APPROVAL_AUDIT, ApprovalAuditPanel::new);
        factories.put(AppPanelId.IMPORT_EXPORT_JOBS, ImportExportJobsPanel::new);
        factories.put(AppPanelId.BANK_TRANSACTIONS, BankTransactionsPanel::new);
        factories.put(AppPanelId.REPORT_LIBRARY, ReportLibraryPanel::new);
        factories.put(AppPanelId.CHART_OF_ACCOUNTS, ChartOfAccountsPanel::new);
        factories.put(AppPanelId.FUNDS, FundsPanel::new);
        factories.put(AppPanelId.SETTINGS, SettingsPanel::new);
        factories.put(AppPanelId.DIAGNOSTICS, DiagnosticsPanel::new);
        factories.put(AppPanelId.HELP, HelpPanel::new);
    }

    public EnumSet<AppPanelId> supportedPanelIds()
    {
        EnumSet<AppPanelId> result = EnumSet.copyOf(factories.keySet());
        result.add(AppPanelId.LEDGER_REGISTER);
        result.add(AppPanelId.TXN_EDITOR);
        return result;
    }

    public AppPanel create(AppPanelId id)
    {
        AppPanelId canonicalId = AppPanelId.canonical(Objects.requireNonNull(id, "id"));
        Supplier<AppPanel> factory = factories.get(canonicalId);
        if (factory == null)
        {
            throw new IllegalArgumentException("Unsupported panel id: " + id);
        }
        return factory.get();
    }
}
