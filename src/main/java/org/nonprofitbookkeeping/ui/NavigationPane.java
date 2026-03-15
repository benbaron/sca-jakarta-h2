package org.nonprofitbookkeeping.ui;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Represents the NavigationPane component in the nonprofit bookkeeping application.
 */
public class NavigationPane extends VBox
{
    private final TreeView<NavItem> tree;
    private final Map<AppPanelId, TreeItem<NavItem>> index = new EnumMap<>(AppPanelId.class);
    private final Consumer<AppPanelId> openPanel;
    private final BiConsumer<String, String> openInspector;

    public NavigationPane(Consumer<AppPanelId> openPanel, BiConsumer<String, String> openInspector)
    {
        this.openPanel = openPanel;
        this.openInspector = openInspector;

        getStyleClass().add("nav");

        TreeItem<NavItem> root = new TreeItem<>(new NavItem(null, "Root"));
        root.setExpanded(true);

        TreeItem<NavItem> ops = group(root, "Operations");
        add(ops, AppPanelId.DASHBOARD, "Dashboard");

        TreeItem<NavItem> ledger = group(ops, "Ledger");
        add(ledger, AppPanelId.LEDGER_REGISTER, "Ledger Register");
        add(ledger, AppPanelId.TXN_EDITOR, "Transaction Editor");

        add(ops, AppPanelId.SCHEDULES, "Outstanding / Schedules");

        TreeItem<NavItem> budget = group(ops, "Budget");
        add(budget, AppPanelId.BUDGET_EDITOR, "Budget Editor");
        add(budget, AppPanelId.BUDGET_VS_ACTUAL, "Budget vs Actual");

        TreeItem<NavItem> assets = group(ops, "Assets");
        add(assets, AppPanelId.ASSETS_REGISTER, "Asset Register");
        add(assets, AppPanelId.DEPRECIATION_RUNS, "Depreciation Runs");
        add(assets, AppPanelId.INVENTORY, "Inventory");

        TreeItem<NavItem> workflows = group(root, "Workflows");
        add(workflows, AppPanelId.RECONCILIATION_RUNS, "Reconciliation Runs");
        add(workflows, AppPanelId.PERIOD_CLOSE_RUNS, "Period Close Runs");
        add(workflows, AppPanelId.IMPORT_PREVIEW, "Import Preview");

        TreeItem<NavItem> outputs = group(root, "Outputs");
        add(outputs, AppPanelId.REPORT_LIBRARY, "Reports Library");

        TreeItem<NavItem> ref = group(root, "Reference");
        add(ref, AppPanelId.CHART_OF_ACCOUNTS, "Chart of Accounts");
        add(ref, AppPanelId.FUNDS, "Funds");

        TreeItem<NavItem> sys = group(root, "System");
        add(sys, AppPanelId.SETTINGS, "Settings");
        add(sys, AppPanelId.DIAGNOSTICS, "Diagnostics");
        add(sys, AppPanelId.HELP, "Help");

        tree = new TreeView<>(root);
        tree.setShowRoot(false);

        tree.setCellFactory(tv -> new TreeCell<>()
        {
            @Override
            protected void updateItem(NavItem item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        });

        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) ->
        {
            if (newSel == null || newSel.getValue() == null || newSel.getValue().panelId() == null)
            {
                return;
            }
            openPanel.accept(newSel.getValue().panelId());
        });

        tree.setOnMouseClicked(e ->
        {
            TreeItem<NavItem> sel = tree.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getValue() == null)
            {
                return;
            }

            if (e.getButton() == MouseButton.SECONDARY)
            {
                tree.getSelectionModel().select(sel);
                NavItem v = sel.getValue();
                openInspector.accept("Details: " + v.label(),
                        "Detail inspector placeholder for: " + v.label() + "\n\n(Details-first; journal is a drill-down.)");
            }
        });

        tree.setOnKeyPressed(e ->
        {
            if (e.getCode() != KeyCode.ENTER)
            {
                return;
            }

            TreeItem<NavItem> sel = tree.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getValue() == null || sel.getValue().panelId() == null)
            {
                return;
            }
            openPanel.accept(sel.getValue().panelId());
        });

        getChildren().add(tree);
    }

    public void highlight(AppPanelId id)
    {
        TreeItem<NavItem> ti = index.get(id);
        if (ti != null)
        {
            tree.getSelectionModel().select(ti);
            tree.scrollTo(tree.getRow(ti));
        }
    }

    private TreeItem<NavItem> group(TreeItem<NavItem> parent, String label)
    {
        TreeItem<NavItem> g = new TreeItem<>(new NavItem(null, label));
        g.setExpanded(true);
        parent.getChildren().add(g);
        return g;
    }


    EnumSet<AppPanelId> indexedPanelIds()
    {
        return EnumSet.copyOf(index.keySet());
    }

    private void add(TreeItem<NavItem> parent, AppPanelId id, String label)
    {
        TreeItem<NavItem> ti = new TreeItem<>(new NavItem(id, label));
        parent.getChildren().add(ti);
        index.put(id, ti);
    }

    public record NavItem(AppPanelId panelId, String label) {}
}
