package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Hosts one reusable workspace tab per panel type. */
public class PanelHost extends TabPane
{
    private static final Map<AppPanelId, Supplier<AppPanel>> FACTORIES = new EnumMap<>(AppPanelId.class);

    static
    {
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

    private final Supplier<AppPanel> dashboardFactory;
    private final Map<AppPanelId, AppPanel> panels = new EnumMap<>(AppPanelId.class);
    private final Map<AppPanelId, Tab> tabs = new EnumMap<>(AppPanelId.class);
    private AppPanelId activeId;

    public PanelHost()
    {
        this(DashboardHomePanel::new);
    }

    PanelHost(Supplier<AppPanel> dashboardFactory)
    {
        this.dashboardFactory = Objects.requireNonNull(dashboardFactory, "dashboardFactory");
        setTabClosingPolicy(TabClosingPolicy.SELECTED_TAB);
        getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) ->
        {
            activeId = newTab == null ? null : panelIdFor(newTab);
        });
    }

    public static EnumSet<AppPanelId> supportedPanelIds()
    {
        EnumSet<AppPanelId> ids = EnumSet.copyOf(FACTORIES.keySet());
        ids.add(AppPanelId.DASHBOARD);
        return ids;
    }

    public void show(AppPanelId id)
    {
        Tab tab = tabs.computeIfAbsent(id, this::createTab);
        if (!getTabs().contains(tab))
        {
            getTabs().add(tab);
        }
        getSelectionModel().select(tab);
        activeId = id;
    }

    public void showReplacement(AppPanelId id, AppPanel replacement)
    {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(replacement, "replacement");
        Tab previous = tabs.remove(id);
        if (previous != null)
        {
            getTabs().remove(previous);
        }
        panels.remove(id);
        panels.put(id, replacement);
        Tab tab = createTab(id, replacement);
        tabs.put(id, tab);
        getTabs().add(tab);
        getSelectionModel().select(tab);
        activeId = id;
    }

    public void reset()
    {
        getTabs().clear();
        tabs.clear();
        panels.clear();
        activeId = null;
    }

    /**
     * Closes every non-permanent workspace tab and selects the Dashboard.
     *
     * @return number of tabs closed
     */
    public int closeAllClosableTabs()
    {
        List<AppPanelId> closableIds = tabs.entrySet().stream()
                .filter(entry -> entry.getValue().isClosable())
                .map(Map.Entry::getKey)
                .toList();

        for (AppPanelId id : closableIds)
        {
            Tab tab = tabs.remove(id);
            panels.remove(id);
            if (tab != null)
            {
                getTabs().remove(tab);
            }
        }

        Tab dashboardTab = tabs.get(AppPanelId.DASHBOARD);
        if (dashboardTab == null || !getTabs().contains(dashboardTab))
        {
            show(AppPanelId.DASHBOARD);
        }
        else
        {
            getSelectionModel().select(dashboardTab);
            activeId = AppPanelId.DASHBOARD;
        }
        return closableIds.size();
    }

    public boolean isOpen(AppPanelId id)
    {
        Tab tab = tabs.get(id);
        return tab != null && getTabs().contains(tab);
    }

    public int openPanelCount()
    {
        return getTabs().size();
    }

    public Node activeRoot()
    {
        AppPanel panel = getActive();
        return panel == null ? null : panel.root();
    }

    public boolean isClosable(AppPanelId id)
    {
        Tab tab = tabs.get(id);
        return tab != null && tab.isClosable();
    }

    public String getActiveTitle()
    {
        AppPanel panel = getActive();
        return panel == null ? "(none)" : panel.title();
    }

    public void saveActive()
    {
        AppPanel panel = getActive();
        if (panel != null)
        {
            panel.onSave();
        }
    }

    public void newItemActive()
    {
        AppPanel panel = getActive();
        if (panel != null)
        {
            panel.onNew();
        }
    }

    public void copySelectionActive()
    {
        AppPanel panel = getActive();
        if (panel != null)
        {
            panel.onCopy();
        }
    }

    public void pasteActive()
    {
        AppPanel panel = getActive();
        if (panel != null)
        {
            panel.onPaste();
        }
    }

    public AppPanel.RunCommandResult runCommandActive(AppPanel.RunCommand command)
    {
        AppPanel panel = getActive();
        return panel == null
                ? new AppPanel.RunCommandResult(false, "No active panel selected.")
                : panel.onRunCommand(command);
    }

    public java.util.Optional<AppPanel.JournalSelection> activeJournalSelection()
    {
        AppPanel panel = getActive();
        return panel == null ? java.util.Optional.empty() : panel.activeJournalSelection();
    }

    AppPanelId activePanelId()
    {
        return activeId;
    }

    private AppPanel getActive()
    {
        return activeId == null ? null : panels.get(activeId);
    }

    private Tab createTab(AppPanelId id)
    {
        AppPanel panel = panels.computeIfAbsent(id, this::create);
        return createTab(id, panel);
    }

    private Tab createTab(AppPanelId id, AppPanel panel)
    {
        Tab tab = new Tab(panel.title(), panel.root());
        tab.setUserData(id);
        tab.setClosable(!WorkspaceLayoutPolicy.isPermanentTab(id));
        tab.setOnClosed(event ->
        {
            tabs.remove(id);
            panels.remove(id);
        });
        return tab;
    }

    private AppPanelId panelIdFor(Tab tab)
    {
        Object value = tab.getUserData();
        return value instanceof AppPanelId id ? id : null;
    }

    private AppPanel create(AppPanelId id)
    {
        if (id == AppPanelId.DASHBOARD)
        {
            return dashboardFactory.get();
        }
        Supplier<AppPanel> factory = FACTORIES.get(id);
        if (factory == null)
        {
            throw new IllegalArgumentException("Unsupported panel id: " + id);
        }
        return factory.get();
    }
}
