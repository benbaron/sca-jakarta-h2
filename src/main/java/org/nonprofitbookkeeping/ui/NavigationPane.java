package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Icon-led navigation rail matching the production dashboard reference. */
public class NavigationPane extends VBox
{
    private final Map<AppPanelId, Button> index = new EnumMap<>(AppPanelId.class);
    private final Consumer<AppPanelId> openPanel;
    private final BiConsumer<String, String> openInspector;
    private final Supplier<InspectorContext> inspectorContextSupplier;
    private AppPanelId highlightedPanel;

    public NavigationPane(
            Consumer<AppPanelId> openPanel,
            BiConsumer<String, String> openInspector,
            Supplier<InspectorContext> inspectorContextSupplier)
    {
        this.openPanel = openPanel;
        this.openInspector = openInspector;
        this.inspectorContextSupplier = inspectorContextSupplier;

        getStyleClass().addAll("navigation-pane", "nav");
        setMinWidth(0);
        setPrefWidth(222);

        Label applicationName = new Label("SCA Ledger");
        applicationName.getStyleClass().add("navigation-brand");
        Label applicationCaption = new Label("Nonprofit Bookkeeping");
        applicationCaption.getStyleClass().add("navigation-brand-caption");
        VBox brand = new VBox(1, applicationName, applicationCaption);
        brand.setPadding(new Insets(4, 8, 12, 8));

        VBox content = new VBox(2);
        content.getStyleClass().add("navigation-content");
        addItem(content, AppPanelId.DASHBOARD, "Dashboard", UiIcons.Glyph.DASHBOARD);

        section(content, "TRANSACTIONS");
        addItem(content, AppPanelId.LEDGER_REGISTER, "Ledger Register", UiIcons.Glyph.LEDGER);
        addItem(content, AppPanelId.TXN_EDITOR, "New Transaction", UiIcons.Glyph.ADD);
        addItem(content, AppPanelId.SCHEDULES, "Scheduled Transactions", UiIcons.Glyph.CALENDAR);

        section(content, "BUDGETING");
        addItem(content, AppPanelId.BUDGET_EDITOR, "Budget Editor", UiIcons.Glyph.BUDGET);
        addItem(content, AppPanelId.BUDGET_VS_ACTUAL, "Budget vs Actual", UiIcons.Glyph.CHART);

        section(content, "ASSETS");
        addItem(content, AppPanelId.ASSETS_REGISTER, "Asset Register", UiIcons.Glyph.ACCOUNTS);
        addItem(content, AppPanelId.DEPRECIATION_RUNS, "Depreciation Runs", UiIcons.Glyph.CALENDAR);
        addItem(content, AppPanelId.INVENTORY, "Inventory", UiIcons.Glyph.FUNDS);

        section(content, "BANKING");
        addItem(content, AppPanelId.RECONCILIATION_RUNS, "Reconciliation", UiIcons.Glyph.BANK);
        addItem(content, AppPanelId.BANK_TRANSACTIONS, "Bank Transactions", UiIcons.Glyph.CREDIT_CARD);
        addItem(content, AppPanelId.PERIOD_CLOSE_RUNS, "Period Close", UiIcons.Glyph.CLOCK);

        section(content, "REPORTS");
        addItem(content, AppPanelId.REPORT_LIBRARY, "Report Library", UiIcons.Glyph.REPORT);

        section(content, "ADMINISTRATION");
        addItem(content, AppPanelId.CHART_OF_ACCOUNTS, "Chart of Accounts", UiIcons.Glyph.ACCOUNTS);
        addItem(content, AppPanelId.FUNDS, "Funds", UiIcons.Glyph.FUNDS);
        addItem(content, AppPanelId.IMPORT_PREVIEW, "Import Preview", UiIcons.Glyph.IMPORT);
        addItem(content, AppPanelId.APPROVAL_AUDIT, "Approval Audit", UiIcons.Glyph.CHECK);
        addItem(content, AppPanelId.IMPORT_EXPORT_JOBS, "Import / Export Jobs", UiIcons.Glyph.IMPORT);
        addItem(content, AppPanelId.SETTINGS, "Settings", UiIcons.Glyph.SETTINGS);

        section(content, "SYSTEM");
        addItem(content, AppPanelId.DIAGNOSTICS, "Diagnostics", UiIcons.Glyph.DIAGNOSTICS);
        addItem(content, AppPanelId.HELP, "Help", UiIcons.Glyph.HELP);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.getStyleClass().add("navigation-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().addAll(brand, scrollPane);
        highlight(AppPanelId.DASHBOARD);
    }

    public void highlight(AppPanelId id)
    {
        if (highlightedPanel != null)
        {
            Button previous = index.get(highlightedPanel);
            if (previous != null)
            {
                previous.getStyleClass().remove("navigation-item-selected");
            }
        }
        highlightedPanel = id;
        Button selected = index.get(id);
        if (selected != null && !selected.getStyleClass().contains("navigation-item-selected"))
        {
            selected.getStyleClass().add("navigation-item-selected");
        }
    }

    static String inspectorBody(NavItem item)
    {
        return inspectorBody(
                item,
                new InspectorContext(
                        "(unknown)",
                        String.valueOf(DateRangeContext.get()),
                        "(unspecified)"));
    }

    static String inspectorBody(NavItem item, InspectorContext context)
    {
        if (item == null || item.panelId() == null)
        {
            return InspectorPresentationModel.navigationGroupBody(
                    context.activeCompany(),
                    context.dateRange());
        }
        return InspectorPresentationModel.panelBody(
                item.label(),
                item.panelId().name(),
                context.activeCompany(),
                context.dateRange(),
                context.panelCapabilities(),
                "single-select, Enter, or double-click.",
                "use toolbar Find/Journal for cross-panel queries.");
    }

    EnumSet<AppPanelId> indexedPanelIds()
    {
        return EnumSet.copyOf(index.keySet());
    }

    private void section(VBox parent, String text)
    {
        Label label = new Label(text);
        label.getStyleClass().add("navigation-section");
        parent.getChildren().add(label);
    }

    private void addItem(
            VBox parent,
            AppPanelId panelId,
            String label,
            UiIcons.Glyph glyph)
    {
        Button button = new Button(label, UiIcons.icon(glyph, 16, "navigation-icon"));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setGraphicTextGap(10);
        button.getStyleClass().add("navigation-item");
        button.setOnAction(event ->
        {
            highlight(panelId);
            openPanel.accept(panelId);
        });
        button.setOnMouseClicked(event ->
        {
            if (event.getButton() == MouseButton.SECONDARY)
            {
                NavItem item = new NavItem(panelId, label);
                openInspector.accept(
                        "Details: " + label,
                        inspectorBody(item, inspectorContextSupplier.get()));
                event.consume();
            }
        });
        index.put(panelId, button);
        parent.getChildren().add(button);
    }

    public record NavItem(AppPanelId panelId, String label)
    {
    }

    public record InspectorContext(
            String activeCompany,
            String dateRange,
            String panelCapabilities)
    {
    }
}
