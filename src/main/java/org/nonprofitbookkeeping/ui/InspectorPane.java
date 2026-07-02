package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.dashboard.DashboardSnapshot;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** Context inspector with dashboard organization, period, balance, and note cards. */
public class InspectorPane extends VBox
{
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final VBox inspectorContent = new VBox(8);
    private final VBox alertsContent = new VBox(8);

    public InspectorPane()
    {
        getStyleClass().add("inspector-pane");
        setPadding(Insets.EMPTY);
        setMinWidth(0);
        setPrefWidth(255);

        Tab inspector = new Tab("Inspector", scroll(inspectorContent));
        Tab alerts = new Tab("Alerts", scroll(alertsContent));
        inspector.setClosable(false);
        alerts.setClosable(false);

        TabPane tabs = new TabPane(inspector, alerts);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("inspector-tabs");
        VBox.setVgrow(tabs, Priority.ALWAYS);
        getChildren().add(tabs);

        show("Inspector", "Select an item to see context-aware details.");
        alertsContent.getChildren().setAll(card("Alerts", new Label("No active alerts.")));
        DashboardSnapshotPublisher.register(snapshot ->
                showDashboard(snapshot.organization().displayName(), snapshot));
    }

    public void show(String title, String body)
    {
        Label text = new Label(body == null ? "" : body);
        text.setWrapText(true);
        inspectorContent.getChildren().setAll(card(
                title == null || title.isBlank() ? "Inspector" : title,
                text));
    }

    public void showDashboard(String organization, DashboardSnapshot snapshot)
    {
        if (snapshot == null)
        {
            clear();
            return;
        }

        inspectorContent.getChildren().setAll(
                organizationCard(organization, snapshot.organization()),
                periodCard(snapshot.asOfDate(), snapshot.period()),
                balanceCard(snapshot),
                notesCard());
    }

    public void clear()
    {
        show("Inspector", "Select an item to see context-aware details.");
    }

    private static ScrollPane scroll(VBox content)
    {
        content.setPadding(new Insets(8));
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scrollPane;
    }

    private static VBox organizationCard(
            String fallbackOrganization,
            DashboardSnapshot.OrganizationSummary organization)
    {
        String displayName = organization.displayName();
        if (displayName == null || displayName.isBlank())
        {
            displayName = fallbackOrganization == null || fallbackOrganization.isBlank()
                    ? organization.code()
                    : fallbackOrganization;
        }

        VBox values = new VBox(8);
        values.getChildren().add(strong(displayName));
        addLine(values, "Organization code", organization.code());
        if (!organization.branchType().isBlank())
        {
            addLine(values, "Branch type", organization.branchType());
        }
        if (!organization.parentOrganization().isBlank())
        {
            addLine(values, "Parent organization", organization.parentOrganization());
        }
        addLine(values, "Currency", organization.currency());
        addLine(values, "Status", organization.active() ? "Active" : "Not configured");
        return card("Organization", values);
    }

    private static VBox periodCard(
            LocalDate asOf,
            DashboardSnapshot.PeriodSummary period)
    {
        GridPane values = new GridPane();
        values.setVgap(8);
        values.setHgap(12);
        add(values, 0, "As of", DATE.format(asOf));

        if (period.startDate().isPresent() && period.endDate().isPresent())
        {
            add(values, 1, "Period range",
                    DATE.format(period.startDate().orElseThrow())
                            + " – "
                            + DATE.format(period.endDate().orElseThrow()));
        }
        else
        {
            YearMonth month = YearMonth.from(asOf);
            add(values, 1, "Period range",
                    DATE.format(month.atDay(1))
                            + " – "
                            + DATE.format(month.atEndOfMonth()));
        }

        int nextRow = 2;
        if (period.fiscalYear().isPresent())
        {
            add(values, nextRow++, "Fiscal year",
                    Integer.toString(period.fiscalYear().orElseThrow()));
        }
        if (period.periodNumber().isPresent())
        {
            add(values, nextRow++, "Period",
                    Integer.toString(period.periodNumber().orElseThrow()));
        }
        add(values, nextRow, "Status", displayStatus(period.status()));
        return card("Period Information", values);
    }

    private static VBox balanceCard(DashboardSnapshot snapshot)
    {
        GridPane values = new GridPane();
        values.setVgap(8);
        values.setHgap(12);
        add(values, 0, "Book cash", DashboardValueFormatter.money(snapshot.bookCash()));
        add(values, 1, "Reconciled cash", snapshot.reconciledCash()
                .map(DashboardValueFormatter::money)
                .orElse("Not available"));
        add(values, 2, "Unreconciled difference", snapshot.unreconciledDifference()
                .map(DashboardValueFormatter::money)
                .orElse("Not available"));
        add(values, 3, "YTD surplus (deficit)",
                DashboardValueFormatter.money(snapshot.yearToDateSurplus()));
        return card("Balances (All Funds)", values);
    }

    private static VBox notesCard()
    {
        Label empty = new Label("No notes for this period.");
        empty.setWrapText(true);
        return card("Notes", empty);
    }

    private static VBox card(String titleText, Node content)
    {
        Label title = new Label(titleText);
        title.getStyleClass().add("inspector-card-title");
        Separator separator = new Separator();
        VBox card = new VBox(8, title, separator, content);
        card.getStyleClass().add("inspector-card");
        return card;
    }

    private static Label strong(String text)
    {
        Label label = new Label(text == null ? "" : text);
        label.setWrapText(true);
        label.getStyleClass().add("inspector-strong");
        return label;
    }

    private static void add(GridPane grid, int row, String key, String value)
    {
        Label keyLabel = new Label(key);
        Label valueLabel = new Label(value == null ? "" : value);
        valueLabel.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox line = new HBox(8, keyLabel, spacer, valueLabel);
        line.setAlignment(Pos.CENTER_LEFT);
        grid.add(line, 0, row);
    }

    private static void addLine(VBox values, String key, String value)
    {
        Label keyLabel = new Label(key + ":");
        Label valueLabel = new Label(value == null ? "" : value);
        valueLabel.setWrapText(true);
        HBox line = new HBox(6, keyLabel, valueLabel);
        line.setAlignment(Pos.CENTER_LEFT);
        values.getChildren().add(line);
    }

    private static String displayStatus(String status)
    {
        if (status == null || status.isBlank() || "UNCONFIGURED".equalsIgnoreCase(status))
        {
            return "Not configured";
        }
        String normalized = status.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
