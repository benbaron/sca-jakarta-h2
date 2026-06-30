package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.dashboard.DashboardSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Structured right-side inspector matching the dashboard reference. */
public class InspectorPane extends VBox
{
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final Label title = new Label("Inspector");
    private final VBox content = new VBox(10);

    public InspectorPane()
    {
        getStyleClass().addAll("inspector-pane", "inspector");
        setMinWidth(0);
        setPrefWidth(246);
        setSpacing(8);
        setPadding(new Insets(12));

        title.getStyleClass().add("inspector-title");
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.getStyleClass().add("inspector-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().addAll(title, scrollPane);

        ActivePeriodContext.activeDateProperty().addListener(
                (observable, oldDate, newDate) -> refreshDashboard());
        MainWindow.sharedSessionState().onMultiCompanyChanged(ignored -> refreshDashboard());
        refreshDashboard();
    }

    public void show(String heading, String body)
    {
        title.setText(heading == null || heading.isBlank() ? "Inspector" : heading);
        content.getChildren().setAll(messageCard(body));
    }

    public void clear()
    {
        refreshDashboard();
    }

    void refreshDashboard()
    {
        title.setText("Inspector");
        content.getChildren().setAll(loadingCard());
        UiAsync.run(
                "dashboard-inspector-load",
                () -> UiServiceRegistry.dashboardQuery().load(
                        MainWindow.sharedSessionState().multiCompany().activeCompanyCode(),
                        ActivePeriodContext.get(),
                        1),
                this::showDashboard,
                ex -> content.getChildren().setAll(messageCard(
                        "Dashboard details are unavailable until a database is selected or repaired.\n\n"
                                + UiErrors.safeMessage(ex))));
    }

    void showDashboard(DashboardSnapshot snapshot)
    {
        title.setText("Inspector");
        content.getChildren().setAll(
                organizationCard(snapshot.organization()),
                periodCard(snapshot.period(), snapshot.asOfDate()),
                balancesCard(snapshot),
                notesCard());
    }

    private static Node organizationCard(DashboardSnapshot.OrganizationSummary organization)
    {
        Label name = new Label(organization.displayName());
        name.getStyleClass().add("inspector-primary-value");
        name.setWrapText(true);

        String detail = organization.code();
        if (organization.branchType() != null && !organization.branchType().isBlank())
        {
            detail = organization.branchType() + " · " + detail;
        }
        Label metadata = muted(detail);
        metadata.setWrapText(true);

        Label parent = muted(organization.parentOrganization());
        parent.setWrapText(true);
        parent.setManaged(!organization.parentOrganization().isBlank());
        parent.setVisible(parent.isManaged());

        Label status = new Label(organization.active() ? "Active" : "Inactive");
        status.getStyleClass().addAll(
                "status-pill",
                organization.active() ? "status-success" : "status-danger");

        VBox values = new VBox(3, name, metadata, parent, status);
        return inspectorCard("Organization", UiIcons.Glyph.ACCOUNTS, "accent-blue", values);
    }

    private static Node periodCard(
            DashboardSnapshot.PeriodSummary period,
            LocalDate asOfDate)
    {
        VBox rows = new VBox(6);
        rows.getChildren().add(valueRow("As of", DATE_FORMAT.format(asOfDate)));
        period.fiscalYear().ifPresent(year ->
                rows.getChildren().add(valueRow("Fiscal year", Integer.toString(year))));
        period.periodNumber().ifPresent(number ->
                rows.getChildren().add(valueRow("Period", Integer.toString(number))));
        if (period.startDate().isPresent() && period.endDate().isPresent())
        {
            rows.getChildren().add(valueRow(
                    "Dates",
                    DATE_FORMAT.format(period.startDate().orElseThrow())
                            + " – "
                            + DATE_FORMAT.format(period.endDate().orElseThrow())));
        }

        Label status = new Label(titleCase(period.status()));
        status.getStyleClass().addAll(
                "status-pill",
                "OPEN".equalsIgnoreCase(period.status())
                        ? "status-success"
                        : "CLOSED".equalsIgnoreCase(period.status())
                                ? "status-danger"
                                : "status-neutral");
        rows.getChildren().add(status);
        return inspectorCard("Period Information", UiIcons.Glyph.CALENDAR, "accent-purple", rows);
    }

    private static Node balancesCard(DashboardSnapshot snapshot)
    {
        Map<String, BigDecimal> totals = snapshot.fundClassTotals();
        BigDecimal restricted = totals.getOrDefault("TEMP_RESTRICTED", BigDecimal.ZERO)
                .add(totals.getOrDefault("PERM_RESTRICTED", BigDecimal.ZERO));

        VBox rows = new VBox(
                6,
                valueRow("Cash", DashboardValueFormatter.money(snapshot.bookCash())),
                valueRow(
                        "Unrestricted",
                        DashboardValueFormatter.money(
                                totals.getOrDefault("UNRESTRICTED", BigDecimal.ZERO))),
                valueRow("Restricted", DashboardValueFormatter.money(restricted)),
                valueRow(
                        "Designated",
                        DashboardValueFormatter.money(
                                totals.getOrDefault("DESIGNATED", BigDecimal.ZERO))));
        return inspectorCard("Balances", UiIcons.Glyph.WALLET, "accent-green", rows);
    }

    private static Node notesCard()
    {
        Label note = muted("No organization notes are available in the current database.");
        note.setWrapText(true);
        return inspectorCard("Notes", UiIcons.Glyph.NOTE, "accent-amber", note);
    }

    private static Node loadingCard()
    {
        return messageCard("Loading organization details...");
    }

    private static Node messageCard(String body)
    {
        Label message = new Label(body == null ? "" : body);
        message.setWrapText(true);
        message.getStyleClass().add("muted");
        return inspectorCard("Details", UiIcons.Glyph.NOTE, "accent-blue", message);
    }

    private static Node inspectorCard(
            String heading,
            UiIcons.Glyph glyph,
            String accentClass,
            Node body)
    {
        Label label = new Label(heading);
        label.getStyleClass().add("inspector-card-title");
        HBox header = new HBox(7, iconBadge(glyph, accentClass), label);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(9, header, body);
        card.getStyleClass().add("inspector-card");
        return card;
    }

    private static Node iconBadge(UiIcons.Glyph glyph, String accentClass)
    {
        HBox badge = new HBox(UiIcons.icon(glyph, 14, accentClass));
        badge.setAlignment(Pos.CENTER);
        badge.getStyleClass().addAll("dashboard-icon-badge", accentClass);
        return badge;
    }

    private static Node valueRow(String heading, String valueText)
    {
        Label key = muted(heading);
        Label value = new Label(valueText == null ? "" : valueText);
        value.getStyleClass().add("inspector-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, key, spacer, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label muted(String text)
    {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("muted");
        return label;
    }

    private static String titleCase(String value)
    {
        if (value == null || value.isBlank())
        {
            return "Not configured";
        }
        String normalized = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
