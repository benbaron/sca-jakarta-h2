package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final VBox inspectorContent = new VBox(8);
    private final VBox alertsContent = new VBox(8);

    public InspectorPane()
    {
        getStyleClass().add("inspector-pane");
        setPadding(Insets.EMPTY);

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
    }

    public void show(String title, String body)
    {
        Label text = new Label(body == null ? "" : body);
        text.setWrapText(true);
        inspectorContent.getChildren().setAll(card(title, text));
    }

    public void showDashboard(String organization, DashboardSnapshot snapshot)
    {
        LocalDate asOf = snapshot.asOfDate();
        YearMonth month = YearMonth.from(asOf);
        long daysRemaining = Math.max(0, month.atEndOfMonth().toEpochDay() - asOf.toEpochDay());

        inspectorContent.getChildren().setAll(
                organizationCard(organization),
                periodCard(asOf, month, daysRemaining),
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

    private static VBox organizationCard(String organization)
    {
        String name = organization == null || organization.isBlank() ? "Default Organization" : organization;
        VBox values = new VBox(
                10,
                strong(name),
                new Label("Organization code: " + name),
                new Label("Fiscal Year: Jan – Dec"),
                new Label("Currency: USD"));
        return card("Organization", values);
    }

    private static VBox periodCard(LocalDate asOf, YearMonth month, long daysRemaining)
    {
        GridPane values = new GridPane();
        values.setVgap(8);
        values.setHgap(12);
        add(values, 0, "Current Period", MONTH.format(asOf));
        add(values, 1, "Period Range", DATE.format(month.atDay(1)) + " – " + DATE.format(month.atEndOfMonth()));
        add(values, 2, "Status", asOf.isAfter(month.atEndOfMonth()) ? "Closed" : "Open");
        add(values, 3, "Days Remaining", Long.toString(daysRemaining));
        return card("Period Information", values);
    }

    private static VBox balanceCard(DashboardSnapshot snapshot)
    {
        GridPane values = new GridPane();
        values.setVgap(8);
        values.setHgap(12);
        add(values, 0, "Book Cash", DashboardValueFormatter.money(snapshot.bookCash()));
        add(values, 1, "Reconciled Cash", snapshot.reconciledCash()
                .map(DashboardValueFormatter::money)
                .orElse("Not available"));
        add(values, 2, "Unreconciled Difference", snapshot.unreconciledDifference()
                .map(DashboardValueFormatter::money)
                .orElse("Not available"));
        add(values, 3, "YTD Surplus (Deficit)", DashboardValueFormatter.money(snapshot.yearToDateSurplus()));
        return card("Balances (All Funds)", values);
    }

    private static VBox notesCard()
    {
        Label empty = new Label("No notes for this period.");
        Button add = new Button("Add Note");
        add.setDisable(true);
        return card("Notes", new VBox(10, empty, add));
    }

    private static VBox card(String titleText, javafx.scene.Node content)
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
        Label label = new Label(text);
        label.getStyleClass().add("inspector-strong");
        return label;
    }

    private static void add(GridPane grid, int row, String key, String value)
    {
        Label keyLabel = new Label(key);
        Label valueLabel = new Label(value);
        valueLabel.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox line = new HBox(8, keyLabel, spacer, valueLabel);
        line.setAlignment(Pos.CENTER_LEFT);
        grid.add(line, 0, row);
    }
}
