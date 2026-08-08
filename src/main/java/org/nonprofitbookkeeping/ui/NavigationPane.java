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
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Icon-led, categorized navigation pane for the production workspace. */
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
        this(openPanel, openInspector, inspectorContextSupplier, null);
    }

    public NavigationPane(
            Consumer<AppPanelId> openPanel,
            BiConsumer<String, String> openInspector,
            Supplier<InspectorContext> inspectorContextSupplier,
            Runnable collapseAction)
    {
        this.openPanel = Objects.requireNonNull(openPanel, "openPanel");
        this.openInspector = Objects.requireNonNull(openInspector, "openInspector");
        this.inspectorContextSupplier = Objects.requireNonNull(
                inspectorContextSupplier,
                "inspectorContextSupplier");

        getStyleClass().addAll("navigation-pane", "nav");
        setMinWidth(0);
        setPrefWidth(222);
        setSpacing(4);

        Label applicationName = new Label("SCA Ledger");
        applicationName.getStyleClass().add("navigation-brand");
        Label applicationCaption = new Label("Nonprofit Bookkeeping");
        applicationCaption.getStyleClass().add("navigation-brand-caption");
        VBox brand = new VBox(1, applicationName, applicationCaption);
        brand.setPadding(new Insets(8, 10, 10, 10));

        VBox content = new VBox(2);
        content.getStyleClass().add("navigation-content");
        addItem(content, AppPanelId.DASHBOARD, "Dashboard", UiIcons.Glyph.DASHBOARD);

        section(content, "ACCOUNTING");
        addItem(content, AppPanelId.JOURNAL_PANE, "Journal", UiIcons.Glyph.LEDGER);
        addItem(content, AppPanelId.BANKING, "Banking", UiIcons.Glyph.BANK);
        addItem(content, AppPanelId.RECONCILIATION_RUNS, "Bank Reconciliation", UiIcons.Glyph.BANK);
        addItem(content, AppPanelId.BANK_TRANSACTIONS, "Bank Transactions", UiIcons.Glyph.CREDIT_CARD);

        section(content, "PLANNING");
        addItem(content, AppPanelId.BUDGET_EDITOR, "Budget Editor", UiIcons.Glyph.BUDGET);
        addItem(content, AppPanelId.BUDGET_VS_ACTUAL, "Budget vs Actual", UiIcons.Glyph.CHART);

        section(content, "ASSETS & INVENTORY");
        addItem(content, AppPanelId.ASSETS_REGISTER, "Asset Register", UiIcons.Glyph.ACCOUNTS);
        addItem(content, AppPanelId.DEPRECIATION_RUNS, "Depreciation Runs", UiIcons.Glyph.CALENDAR);
        addItem(content, AppPanelId.INVENTORY, "Inventory", UiIcons.Glyph.FUNDS);

        section(content, "IMPORT & OVERSIGHT");
        addItem(content, AppPanelId.IMPORT_PREVIEW, "Import Preview", UiIcons.Glyph.IMPORT);
        addItem(content, AppPanelId.APPROVAL_AUDIT, "Audit History", UiIcons.Glyph.NOTE);
        addItem(content, AppPanelId.PERIOD_CLOSE_RUNS, "Period Close", UiIcons.Glyph.CLOCK);

        section(content, "REPORTS");
        addItem(content, AppPanelId.REPORT_LIBRARY, "Report Library", UiIcons.Glyph.REPORT);

        section(content, "ADMINISTRATION");
        addItem(content, AppPanelId.CHART_OF_ACCOUNTS, "Chart of Accounts", UiIcons.Glyph.ACCOUNTS);
        addItem(content, AppPanelId.FUNDS, "Funds", UiIcons.Glyph.FUNDS);
        addItem(content, AppPanelId.SETTINGS, "Administration", UiIcons.Glyph.SETTINGS);
        addItem(content, AppPanelId.DIAGNOSTICS, "Diagnostics", UiIcons.Glyph.DIAGNOSTICS);
        addItem(content, AppPanelId.HELP, "Help", UiIcons.Glyph.HELP);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.getStyleClass().add("navigation-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(brand, scrollPane);
        if (collapseAction != null)
        {
            Button collapse = new Button("‹  Collapse");
            collapse.setMaxWidth(Double.MAX_VALUE);
            collapse.setAlignment(Pos.CENTER_LEFT);
            collapse.getStyleClass().add("navigation-collapse");
            collapse.setOnAction(event -> collapseAction.run());
            getChildren().add(collapse);
        }

        highlight(AppPanelId.DASHBOARD);
    }

    /** Selects the navigation item that corresponds to the active workspace panel. */
    public void highlight(AppPanelId id)
    {
        AppPanelId canonicalId = PanelHost.canonicalPanelId(id);
        if (highlightedPanel != null)
        {
            Button previous = index.get(highlightedPanel);
            if (previous != null)
            {
                previous.getStyleClass().remove("navigation-item-selected");
            }
        }

        highlightedPanel = canonicalId;
        Button selected = index.get(canonicalId);
        if (selected != null
                && !selected.getStyleClass().contains("navigation-item-selected"))
        {
            selected.getStyleClass().add("navigation-item-selected");
        }
    }

    /** Returns the panel ids currently exposed by navigation. */
    public EnumSet<AppPanelId> visiblePanels()
    {
        return index.isEmpty()
                ? EnumSet.noneOf(AppPanelId.class)
                : EnumSet.copyOf(index.keySet());
    }

    EnumSet<AppPanelId> indexedPanelIds()
    {
        return visiblePanels();
    }

    static String inspectorBody(NavItem item, InspectorContext context)
    {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(context, "context");
        if (item.panelId() == null)
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

    private void addItem(VBox target, AppPanelId id, String label, UiIcons.Glyph glyph)
    {
        Button button = new Button(label, UiIcons.icon(glyph, 16));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.getStyleClass().add("navigation-item");
        button.setOnAction(event -> {
            highlight(id);
            openPanel.accept(id);
        });
        button.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY)
            {
                NavItem item = new NavItem(id, label);
                openInspector.accept(
                        "Details: " + label,
                        inspectorBody(item, inspectorContextSupplier.get()));
                event.consume();
            }
        });
        index.put(id, button);
        target.getChildren().add(button);
    }

    private static void section(VBox target, String title)
    {
        Label section = new Label(title);
        section.getStyleClass().add("navigation-section");
        section.setPadding(new Insets(10, 8, 2, 8));
        target.getChildren().add(section);
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
