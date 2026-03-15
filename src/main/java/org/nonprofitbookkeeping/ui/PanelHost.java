package org.nonprofitbookkeeping.ui;

import javafx.scene.layout.BorderPane;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Represents the PanelHost component in the nonprofit bookkeeping application.
 */
public class PanelHost extends BorderPane
{
    private static final Map<AppPanelId, Supplier<AppPanel>> FACTORIES = new EnumMap<>(AppPanelId.class);

    static
    {
        FACTORIES.put(AppPanelId.DASHBOARD, DashboardPanel::new);

        FACTORIES.put(AppPanelId.LEDGER_REGISTER, LedgerRegisterPanel::new);
        FACTORIES.put(AppPanelId.TXN_EDITOR, TransactionEditorPanel::new);

        FACTORIES.put(AppPanelId.SCHEDULES, SchedulesPanel::new);

        FACTORIES.put(AppPanelId.BUDGET_EDITOR, BudgetEditorPanel::new);
        FACTORIES.put(AppPanelId.BUDGET_VS_ACTUAL, BudgetVsActualPanel::new);

        FACTORIES.put(AppPanelId.ASSETS_REGISTER, AssetsRegisterPanel::new);
        FACTORIES.put(AppPanelId.DEPRECIATION_RUNS, DepreciationRunsPanel::new);
        FACTORIES.put(AppPanelId.INVENTORY, InventoryPanel::new);

        FACTORIES.put(AppPanelId.RECONCILIATION_RUNS, ReconciliationRunsPanel::new);
        FACTORIES.put(AppPanelId.PERIOD_CLOSE_RUNS, PeriodCloseRunsPanel::new);
        FACTORIES.put(AppPanelId.IMPORT_PREVIEW, ImportPreviewPanel::new);
        FACTORIES.put(AppPanelId.APPROVAL_AUDIT, ApprovalAuditPanel::new);
        FACTORIES.put(AppPanelId.IMPORT_EXPORT_JOBS, ImportExportJobsPanel::new);
        FACTORIES.put(AppPanelId.BANK_TRANSACTIONS, BankTransactionsPanel::new);

        FACTORIES.put(AppPanelId.REPORT_LIBRARY, ReportLibraryPanel::new);

        FACTORIES.put(AppPanelId.CHART_OF_ACCOUNTS, ChartOfAccountsPanel::new);
        FACTORIES.put(AppPanelId.FUNDS, FundsPanel::new);
        FACTORIES.put(AppPanelId.SETTINGS, SettingsPanel::new);
        FACTORIES.put(AppPanelId.DIAGNOSTICS, DiagnosticsPanel::new);
        FACTORIES.put(AppPanelId.HELP, HelpPanel::new);
    }

    private final Map<AppPanelId, AppPanel> panels = new EnumMap<>(AppPanelId.class);
    private AppPanelId activeId;

    public static EnumSet<AppPanelId> supportedPanelIds()
    {
        return EnumSet.copyOf(FACTORIES.keySet());
    }

    public void show(AppPanelId id)
    {
        AppPanel panel = panels.computeIfAbsent(id, this::create);
        activeId = id;
        setCenter(panel.root());
    }

    public String getActiveTitle()
    {
        AppPanel p = getActive();
        return p == null ? "(none)" : p.title();
    }

    public void saveActive() { AppPanel p = getActive(); if (p != null) p.onSave(); }
    public void newItemActive() { AppPanel p = getActive(); if (p != null) p.onNew(); }
    public void copySelectionActive() { AppPanel p = getActive(); if (p != null) p.onCopy(); }
    public void pasteActive() { AppPanel p = getActive(); if (p != null) p.onPaste(); }

    public AppPanel.RunCommandResult runCommandActive(AppPanel.RunCommand command)
    {
        AppPanel p = getActive();
        if (p == null)
        {
            return new AppPanel.RunCommandResult(false, "No active panel selected.");
        }
        return p.onRunCommand(command);
    }

    public java.util.Optional<AppPanel.JournalSelection> activeJournalSelection()
    {
        AppPanel p = getActive();
        return p == null ? java.util.Optional.empty() : p.activeJournalSelection();
    }

    AppPanelId activePanelId()
    {
        return activeId;
    }

    private AppPanel getActive() { return activeId == null ? null : panels.get(activeId); }

    private AppPanel create(AppPanelId id)
    {
        Supplier<AppPanel> factory = FACTORIES.get(id);
        if (factory == null)
        {
            throw new IllegalArgumentException("Unsupported panel id: " + id);
        }
        return factory.get();
    }
}
