package org.nonprofitbookkeeping.ui;

import javafx.scene.layout.BorderPane;

import java.util.EnumMap;
import java.util.Map;

/**
 * Represents the PanelHost component in the nonprofit bookkeeping application.
 */
public class PanelHost extends BorderPane
{
    private final Map<AppPanelId, AppPanel> panels = new EnumMap<>(AppPanelId.class);
    private AppPanelId activeId;

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

    private AppPanel getActive() { return activeId == null ? null : panels.get(activeId); }

    private AppPanel create(AppPanelId id)
    {
        return switch (id)
        {
            case DASHBOARD -> new DashboardPanel();

            case LEDGER_REGISTER -> new LedgerRegisterPanel();
            case TXN_EDITOR -> new TransactionEditorPanel();

            case SCHEDULES -> new SchedulesPanel();

            case BUDGET_EDITOR -> new BudgetEditorPanel();
            case BUDGET_VS_ACTUAL -> new BudgetVsActualPanel();

            case ASSETS_REGISTER -> new AssetsRegisterPanel();
            case DEPRECIATION_RUNS -> new DepreciationRunsPanel();

            case REPORT_LIBRARY -> new ReportLibraryPanel();

            case CHART_OF_ACCOUNTS -> new ChartOfAccountsPanel();
            case FUNDS -> new FundsPanel();
            case SETTINGS -> new SettingsPanel();
        };
    }
}
