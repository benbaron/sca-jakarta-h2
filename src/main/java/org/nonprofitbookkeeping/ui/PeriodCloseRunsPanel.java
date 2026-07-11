package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.service.PeriodCloseEventView;
import org.nonprofitbookkeeping.service.PeriodCloseRangeService;
import org.nonprofitbookkeeping.service.PeriodCloseRangeView;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Authoritative period-close, reopen, and factual-history workspace. */
public class PeriodCloseRunsPanel implements AppPanel
{
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ISO_INSTANT;

    private final BorderPane root = new BorderPane();
    private final DatePicker startDate = new DatePicker();
    private final DatePicker endDate = new DatePicker();
    private final ComboBox<String> rangeKind = new ComboBox<>();
    private final ComboBox<ClosedPeriodPolicy> reopenPolicy = new ComboBox<>();
    private final CheckBox requireReason = new CheckBox("Require reopening reason");
    private final TextField actor = new TextField("ui-operator");
    private final TextField reason = new TextField();
    private final TableView<PeriodCloseRangeView> ranges = new TableView<>();
    private final TableView<PeriodCloseEventView> history = new TableView<>();
    private final Label status = new Label();

    public PeriodCloseRunsPanel()
    {
        root.setPadding(new Insets(10));
        root.setMinSize(0, 0);

        Label title = new Label("Period Close");
        title.getStyleClass().add("panel-title");
        status.setWrapText(true);

        rangeKind.getItems().setAll("CALCULATED", "CUSTOM");
        rangeKind.setValue("CALCULATED");
        reopenPolicy.getItems().setAll(ClosedPeriodPolicy.values());
        reopenPolicy.setValue(ClosedPeriodPolicy.WARN_AND_REOPEN);
        reason.setPromptText("Optional close/reopen reason");

        Button useActiveMonth = new Button("Use Active Month");
        useActiveMonth.setOnAction(event -> setCalculatedMonth());
        Button close = new Button("Close Range");
        close.setOnAction(event -> closeRange());
        Button reopen = new Button("Reopen Selected");
        reopen.setOnAction(event -> reopenSelected());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> reload());

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.add(new Label("Start date"), 0, 0);
        form.add(startDate, 1, 0);
        form.add(new Label("End date"), 2, 0);
        form.add(endDate, 3, 0);
        form.add(new Label("Range type"), 0, 1);
        form.add(rangeKind, 1, 1);
        form.add(new Label("Actor"), 2, 1);
        form.add(actor, 3, 1);
        form.add(new Label("Reopen policy"), 0, 2);
        form.add(reopenPolicy, 1, 2);
        form.add(requireReason, 2, 2, 2, 1);
        form.add(new Label("Reason"), 0, 3);
        form.add(reason, 1, 3, 3, 1);

        HBox actions = new HBox(8, useActiveMonth, close, reopen, refresh);
        actions.getStyleClass().add("panel-action-row");
        root.setTop(new VBox(8, title, form, actions, status, new Separator()));

        configureRangeTable();
        configureHistoryTable();

        VBox rangePane = new VBox(6, new Label("Close ranges"), ranges);
        VBox historyPane = new VBox(6, new Label("Factual close/reopen history"), history);
        rangePane.setPadding(new Insets(8));
        historyPane.setPadding(new Insets(8));
        VBox.setVgrow(ranges, Priority.ALWAYS);
        VBox.setVgrow(history, Priority.ALWAYS);

        SplitPane split = new SplitPane(rangePane, historyPane);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.58);
        split.setMinSize(0, 0);
        root.setCenter(split);

        setCalculatedMonth();
        reload();
    }

    @Override
    public String title()
    {
        return "Period Close";
    }

    @Override
    public Node root()
    {
        return root;
    }

    private void configureRangeTable()
    {
        ranges.setId("periodCloseRangeTable");
        ranges.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        ranges.setPlaceholder(new Label("No close ranges exist for the active company."));

        TableColumn<PeriodCloseRangeView, String> start = column("Start", "start", 110,
                row -> String.valueOf(row.startDate()));
        TableColumn<PeriodCloseRangeView, String> end = column("End", "end", 110,
                row -> String.valueOf(row.endDate()));
        TableColumn<PeriodCloseRangeView, String> kind = column("Type", "kind", 110,
                PeriodCloseRangeView::rangeKind);
        TableColumn<PeriodCloseRangeView, String> rangeStatus = column("Status", "status", 110,
                PeriodCloseRangeView::status);
        TableColumn<PeriodCloseRangeView, String> closedBy = column("Closed By", "closedBy", 140,
                PeriodCloseRangeView::closedBy);
        TableColumn<PeriodCloseRangeView, String> closedAt = column("Closed At", "closedAt", 190,
                row -> row.closedAt() == null ? "" : EVENT_TIME.format(row.closedAt()));
        TableColumn<PeriodCloseRangeView, String> reopenedBy = column("Reopened By", "reopenedBy", 140,
                row -> blank(row.reopenedBy()));
        TableColumn<PeriodCloseRangeView, String> reopenedAt = column("Reopened At", "reopenedAt", 190,
                row -> row.reopenedAt() == null ? "" : EVENT_TIME.format(row.reopenedAt()));
        TableColumn<PeriodCloseRangeView, String> closeReason = column("Close Reason", "closeReason", 220,
                row -> blank(row.closeReason()));
        TableColumn<PeriodCloseRangeView, String> reopenReason = column("Reopen Reason", "reopenReason", 220,
                row -> blank(row.reopenReason()));

        ranges.getColumns().addAll(
                start, end, kind, rangeStatus, closedBy, closedAt,
                reopenedBy, reopenedAt, closeReason, reopenReason);
    }

    private void configureHistoryTable()
    {
        history.setId("periodCloseHistoryTable");
        history.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        history.setPlaceholder(new Label("No period-close history exists for the active company."));

        TableColumn<PeriodCloseEventView, String> eventAt = column("Event At", "eventAt", 190,
                row -> row.eventAt() == null ? "" : EVENT_TIME.format(row.eventAt()));
        TableColumn<PeriodCloseEventView, String> eventType = column("Event", "eventType", 110,
                PeriodCloseEventView::eventType);
        TableColumn<PeriodCloseEventView, String> range = column("Range ID", "rangeId", 260,
                row -> row.closeRangeId().toString());
        TableColumn<PeriodCloseEventView, String> eventActor = column("Actor", "actor", 150,
                PeriodCloseEventView::actor);
        TableColumn<PeriodCloseEventView, String> eventReason = column("Reason", "reason", 280,
                row -> blank(row.reason()));

        history.getColumns().addAll(eventAt, eventType, range, eventActor, eventReason);
    }

    private void setCalculatedMonth()
    {
        LocalDate active = ActivePeriodContext.get();
        if (active == null)
        {
            active = LocalDate.now();
        }
        startDate.setValue(active.withDayOfMonth(1));
        endDate.setValue(active.withDayOfMonth(active.lengthOfMonth()));
        rangeKind.setValue("CALCULATED");
    }

    private void closeRange()
    {
        LocalDate start = startDate.getValue();
        LocalDate end = endDate.getValue();
        status.setText("Closing range...");
        UiAsync.run("period-close-close-range", () -> service().closeRange(
                        activeCompanyCode(),
                        start,
                        end,
                        rangeKind.getValue(),
                        actor.getText(),
                        reason.getText()),
                closed -> {
                    status.setText("Closed " + closed.startDate() + " through " + closed.endDate()
                            + " for " + closed.companyCode() + ".");
                    reason.clear();
                    reload();
                },
                ex -> status.setText("Could not close range: " + UiErrors.safeMessage(ex)));
    }

    private void reopenSelected()
    {
        PeriodCloseRangeView selected = ranges.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            status.setText("Select a closed range before reopening it.");
            return;
        }
        if (!selected.closed())
        {
            status.setText("The selected range is already reopened.");
            return;
        }

        status.setText("Reopening selected range...");
        UiAsync.run("period-close-reopen-range", () -> service().reopenRange(
                        selected.id(),
                        actor.getText(),
                        reason.getText(),
                        reopenPolicy.getValue(),
                        requireReason.isSelected()),
                reopened -> {
                    status.setText("Reopened " + reopened.startDate() + " through " + reopened.endDate()
                            + " for " + reopened.companyCode() + ".");
                    reason.clear();
                    reload();
                },
                ex -> status.setText("Could not reopen range: " + UiErrors.safeMessage(ex)));
    }

    private void reload()
    {
        String company = activeCompanyCode();
        status.setText("Loading period close state for " + company + "...");
        UiAsync.run("period-close-load", () -> new PeriodCloseData(
                        service().listRanges(company),
                        service().listEvents(company)),
                data -> {
                    ranges.getItems().setAll(data.ranges());
                    history.getItems().setAll(data.events());
                    status.setText("Loaded " + data.ranges().size() + " close range(s) and "
                            + data.events().size() + " history event(s) for " + company + ".");
                },
                ex -> status.setText("Could not load period close state: " + UiErrors.safeMessage(ex)));
    }

    private static PeriodCloseRangeService service()
    {
        return UiServiceRegistry.periodCloseRangeService();
    }

    private static String activeCompanyCode()
    {
        String company = MainWindow.sharedSessionState().multiCompany().activeCompanyCode();
        return company == null || company.isBlank()
                ? "DEFAULT"
                : company.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> TableColumn<T, String> column(
            String title,
            String key,
            double width,
            java.util.function.Function<T, String> value)
    {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setId(key);
        column.setUserData(key);
        column.setPrefWidth(width);
        column.setMinWidth(72);
        column.setSortable(true);
        column.setResizable(true);
        column.setReorderable(true);
        column.setCellValueFactory(cell -> new SimpleStringProperty(blank(value.apply(cell.getValue()))));
        return column;
    }

    private static String blank(String value)
    {
        return value == null ? "" : value;
    }

    private record PeriodCloseData(
            java.util.List<PeriodCloseRangeView> ranges,
            java.util.List<PeriodCloseEventView> events)
    {
    }
}
