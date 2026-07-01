package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Collapsible categorized navigation matching the dashboard reference layout. */
public class NavigationPane extends BorderPane
{
    private final Map<AppPanelId, Button> buttons = new EnumMap<>(AppPanelId.class);
    private final Consumer<AppPanelId> openPanel;
    private final BiConsumer<String, String> openInspector;
    private final Supplier<InspectorContext> inspectorContextSupplier;

    public NavigationPane(
            Consumer<AppPanelId> openPanel,
            BiConsumer<String, String> openInspector,
            Supplier<InspectorContext> inspectorContextSupplier)
    {
        this(openPanel, openInspector, inspectorContextSupplier, () -> { });
    }

    public NavigationPane(
            Consumer<AppPanelId> openPanel,
            BiConsumer<String, String> openInspector,
            Supplier<InspectorContext> inspectorContextSupplier,
            Runnable collapseAction)
    {
        this.openPanel = openPanel;
        this.openInspector = openInspector;
        this.inspectorContextSupplier = inspectorContextSupplier;

        getStyleClass().add("navigation-pane");
        setTop(buildHeading());
        setCenter(buildNavigation());
        setBottom(buildCollapseButton(collapseAction));
        select(AppPanelId.DASHBOARD);
    }

    EnumSet<AppPanelId> indexedPanelIds()
    {
        return buttons.isEmpty()
                ? EnumSet.noneOf(AppPanelId.class)
                : EnumSet.copyOf(buttons.keySet());
    }

    private HBox buildHeading()
    {
        Label menu = new Label("☰");
        menu.getStyleClass().add("navigation-heading-icon");
        Label title = new Label("Workspace");
        title.getStyleClass().add("navigation-heading");
        HBox heading = new HBox(10, menu, title);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.setPadding(new Insets(10, 12, 8, 12));
        return heading;
    }

    private ScrollPane buildNavigation()
    {
        VBox content = new VBox(3);
        content.setPadding(new Insets(0, 7, 8, 7));

        content.getChildren().add(nav(AppPanelId.DASHBOARD, "⌂", "Dashboard"));
        addSection(content, "Accounting",
                item(AppPanelId.LEDGER_REGISTER, "▤", "Ledger Register"),
                item(AppPanelId.TXN_EDITOR, "✎", "Transaction Editor"),
                item(AppPanelId.RECONCILIATION_RUNS, "⌂", "Banking & Reconciliation"),
                item(AppPanelId.BANK_TRANSACTIONS, "▥", "Bank Transactions"),
                item(AppPanelId.SCHEDULES, "▣", "Schedules"));
        addSection(content, "Planning",
                item(AppPanelId.BUDGET_EDITOR, "▧", "Budget Editor"),
                item(AppPanelId.BUDGET_VS_ACTUAL, "▥", "Budget vs Actual"));
        addSection(content, "Assets & Inventory",
                item(AppPanelId.ASSETS_REGISTER, "⌂", "Fixed Assets"),
                item(AppPanelId.DEPRECIATION_RUNS, "▥", "Depreciation Runs"),
                item(AppPanelId.INVENTORY, "▦", "Inventory"));
        addSection(content, "Import & Oversight",
                item(AppPanelId.IMPORT_PREVIEW, "⇩", "Import Preview"),
                item(AppPanelId.IMPORT_EXPORT_JOBS, "⇅", "Import / Export Jobs"),
                item(AppPanelId.APPROVAL_AUDIT, "✓", "Approval Audit"));
        addSection(content, "Period Close",
                item(AppPanelId.PERIOD_CLOSE_RUNS, "▣", "Period Close"));
        addSection(content, "Reports",
                item(AppPanelId.REPORT_LIBRARY, "▧", "Reports Center"));
        addSection(content, "Administration",
                item(AppPanelId.CHART_OF_ACCOUNTS, "▤", "Chart of Accounts"),
                item(AppPanelId.FUNDS, "▥", "Funds"),
                item(AppPanelId.SETTINGS, "⚙", "Settings"));
        addSection(content, "System",
                item(AppPanelId.DIAGNOSTICS, "◉", "Diagnostics"),
                item(AppPanelId.HELP, "?", "Help"));

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("navigation-scroll");
        return scrollPane;
    }

    private Button buildCollapseButton(Runnable collapseAction)
    {
        Button collapse = new Button("‹   Collapse");
        collapse.setMaxWidth(Double.MAX_VALUE);
        collapse.setAlignment(Pos.CENTER_LEFT);
        collapse.getStyleClass().add("navigation-collapse");
        collapse.setOnAction(event -> collapseAction.run());
        return collapse;
    }

    private NavDefinition item(AppPanelId panelId, String glyph, String label)
    {
        return new NavDefinition(panelId, glyph, label);
    }

    private void addSection(VBox content, String title, NavDefinition... definitions)
    {
        Separator separator = new Separator();
        separator.getStyleClass().add("navigation-separator");
        Label heading = new Label(title);
        heading.getStyleClass().add("navigation-section");
        content.getChildren().addAll(separator, heading);
        for (NavDefinition definition : definitions)
        {
            content.getChildren().add(nav(
                    definition.panelId(),
                    definition.glyph(),
                    definition.label()));
        }
    }

    private Button nav(AppPanelId panelId, String glyph, String label)
    {
        Label icon = new Label(glyph);
        icon.getStyleClass().add("navigation-item-icon");
        Label text = new Label(label);
        HBox graphic = new HBox(10, icon, text);
        graphic.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button button = new Button();
        button.setGraphic(graphic);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.getStyleClass().add("navigation-item");
        button.setOnAction(event ->
        {
            select(panelId);
            openPanel.accept(panelId);
        });
        button.setOnContextMenuRequested(event ->
        {
            InspectorContext context = inspectorContextSupplier.get();
            openInspector.accept("Details: " + label, inspectorBody(label, context));
            event.consume();
        });
        buttons.putIfAbsent(panelId, button);
        return button;
    }

    private void select(AppPanelId panelId)
    {
        buttons.values().forEach(
                button -> button.getStyleClass().remove("navigation-item-selected"));
        Button selected = buttons.get(panelId);
        if (selected != null)
        {
            selected.getStyleClass().add("navigation-item-selected");
        }
    }

    private static String inspectorBody(String label, InspectorContext context)
    {
        return label
                + "\n\nDatabase: " + context.databasePath()
                + "\nPeriod: " + context.activePeriod()
                + "\n" + context.capabilities();
    }

    private record NavDefinition(AppPanelId panelId, String glyph, String label)
    {
    }

    public record InspectorContext(String databasePath, String activePeriod, String capabilities)
    {
    }
}
