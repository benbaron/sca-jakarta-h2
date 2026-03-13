package org.nonprofitbookkeeping.ui;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.service.FundBalanceRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Represents the ReportLibraryPanel component in the nonprofit bookkeeping application.
 */
public class ReportLibraryPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final ListView<String> reportList = new ListView<>();
    private final TextArea preview = new TextArea();
    private final Label status = new Label();

    public ReportLibraryPanel()
    {
        root.setPadding(new Insets(8));
        Label title = new Label("Reports Library");
        Label range = new Label();
        range.textProperty().bind(Bindings.createStringBinding(() -> "Date Range: " + DateRangeContext.get(), DateRangeContext.selectedProperty()));
        title.getStyleClass().add("panel-title");

        Button run = new Button("Run");
        Button export = new Button("Export");
        HBox actions = new HBox(8, run, export);

        root.setTop(new VBox(6, title, range, actions, status, new Separator()));

        reportList.getItems().addAll(
                "Balance Sheet",
                "Income Statement",
                "Fund Summary",
                "Budget vs Actual",
                "Transaction Detail",
                "Schedule Detail"
        );
        reportList.getSelectionModel().select(0);

        preview.setEditable(false);
        preview.setWrapText(false);

        VBox right = new VBox(8,
                new Label("Report Parameters"),
                new Label("Current period: " + DateRangeContext.get()),
                new Label("Data source: live database records"),
                new Separator(),
                new Label("Preview"),
                preview);
        right.setPadding(new Insets(8));

        SplitPane sp = new SplitPane(reportList, right);
        sp.setDividerPositions(0.30);
        root.setCenter(sp);

        run.setOnAction(e -> runReport());
        export.setOnAction(e -> status.setText("Export is available after preview generation."));
        reportList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> runReport());

        runReport();
    }

    private void runReport()
    {
        String reportName = reportList.getSelectionModel().getSelectedItem();
        if (reportName == null)
        {
            return;
        }

        status.setText("Generating " + reportName + "...");
        UiAsync.run("report-preview-" + reportName,
                this::loadRows,
                rows -> {
                    preview.setText(renderPreview(reportName, rows));
                    status.setText("Preview ready for " + reportName + ".");
                },
                ex -> {
                    preview.setText("Could not generate preview: " + UiErrors.safeMessage(ex));
                    status.setText("Preview failed.");
                });
    }

    private List<FundBalanceRow> loadRows()
    {
        return UiServiceRegistry.fundBalance().balancesAsOf(LocalDate.now());
    }

    private String renderPreview(String reportName, List<FundBalanceRow> rows)
    {
        BigDecimal total = rows.stream().map(FundBalanceRow::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder sb = new StringBuilder();
        sb.append(reportName).append("\n");
        sb.append("As of ").append(LocalDate.now()).append("\n");
        sb.append("Fund rows: ").append(rows.size()).append("\n");
        sb.append("Net balance: ").append(total.toPlainString()).append("\n\n");

        rows.stream()
                .sorted(Comparator.comparing(FundBalanceRow::getFundCode))
                .limit(50)
                .forEach(r -> sb.append(r.getFundCode())
                        .append(" | ")
                        .append(r.getFundName())
                        .append(" | ")
                        .append(r.getBalance().toPlainString())
                        .append("\n"));

        if (rows.size() > 50)
        {
            sb.append("\n...").append(rows.size() - 50).append(" more row(s)");
        }

        return sb.toString();
    }

    @Override public String title() { return "Reports Library"; }
    @Override public Node root() { return root; }
}
