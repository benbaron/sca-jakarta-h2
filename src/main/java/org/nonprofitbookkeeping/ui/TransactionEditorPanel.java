package org.nonprofitbookkeeping.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Fund;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Represents the TransactionEditorPanel component in the nonprofit bookkeeping application.
 */
public class TransactionEditorPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<SplitRow> splitTable = new TableView<>();
    private final Label status = new Label("Prepare split lines, then validate before posting.");

    public TransactionEditorPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Transaction Editor");
        title.getStyleClass().add("panel-title");

        Button save = new Button("Save");
        Button post = new Button("Post / Validate");
        Button journal = new Button("Journal View");
        HBox actions = new HBox(8, save, post, journal);

        VBox top = new VBox(6, title, actions, status, new Separator(), buildHeaderForm());
        root.setTop(top);

        buildSplitTable();
        root.setCenter(buildSplitEditor());

        save.setOnAction(e -> onSave());
        post.setOnAction(e -> validateOrPost());
        journal.setOnAction(e -> showJournal());
    }

    private Node buildHeaderForm()
    {
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(8);
        g.setPadding(new Insets(8, 0, 8, 0));

        TextField date = new TextField();
        TextField payee = new TextField();
        TextField memo = new TextField();
        TextField bank = new TextField();

        int r = 0;
        g.add(new Label("Date"), 0, r);
        g.add(date, 1, r);
        g.add(new Label("Payee"), 2, r);
        g.add(payee, 3, r);
        r++;
        g.add(new Label("Memo"), 0, r);
        g.add(memo, 1, r, 3, 1);
        r++;
        g.add(new Label("Bank"), 0, r);
        g.add(bank, 1, r);

        g.getColumnConstraints().addAll(
                new ColumnConstraints(70),
                new ColumnConstraints(220),
                new ColumnConstraints(70),
                new ColumnConstraints(220)
        );
        g.getColumnConstraints().get(1).setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().get(3).setHgrow(Priority.ALWAYS);

        return g;
    }

    private void buildSplitTable()
    {
        splitTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        splitTable.getColumns().add(col("Account", SplitRow::account));
        splitTable.getColumns().add(col("Fund", SplitRow::fund));
        splitTable.getColumns().add(col("Amount", SplitRow::amount));
        splitTable.getColumns().add(col("Activity", SplitRow::activity));
        splitTable.getColumns().add(col("Merchant", SplitRow::merchant));
        splitTable.getColumns().add(col("NMR", SplitRow::nmr));
        splitTable.getColumns().add(col("Notes", SplitRow::notes));

        splitTable.getItems().addAll(
                new SplitRow("", "", "", "", "", "", ""),
                new SplitRow("", "", "", "", "", "", "")
        );
    }

    private Node buildSplitEditor()
    {
        Label lbl = new Label("Splits");
        lbl.getStyleClass().add("subheader");

        Button addLine = new Button("+ Add Line");
        Button removeLine = new Button("– Remove");
        ToolBar tb = new ToolBar(addLine, removeLine);

        addLine.setOnAction(e -> splitTable.getItems().add(new SplitRow("", "", "", "", "", "", "")));
        removeLine.setOnAction(e -> {
            SplitRow sel = splitTable.getSelectionModel().getSelectedItem();
            if (sel != null)
            {
                splitTable.getItems().remove(sel);
            }
        });

        VBox box = new VBox(6, lbl, tb, splitTable);
        VBox.setVgrow(splitTable, Priority.ALWAYS);
        return box;
    }

    private TableColumn<SplitRow, String> col(String name, java.util.function.Function<SplitRow, String> getter)
    {
        TableColumn<SplitRow, String> c = new TableColumn<>(name);
        c.setCellValueFactory(v -> new SimpleStringProperty(getter.apply(v.getValue())));
        return c;
    }

    private void validateOrPost()
    {
        status.setText("Validating split rows against current account/fund catalogs...");
        UiAsync.run("txn-editor-validate", this::validateAgainstReferenceData,
                result -> status.setText(result.message()),
                ex -> status.setText("Validation failed: " + UiErrors.safeMessage(ex)));
    }

    private ValidationResult validateAgainstReferenceData()
    {
        Set<String> accountCodes = UiServiceRegistry.accountLookup().listActivePostingAccounts()
                .stream()
                .map(Account::getCode)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> fundCodes = UiServiceRegistry.fundLookup().listActiveFunds()
                .stream()
                .map(Fund::getCode)
                .collect(java.util.stream.Collectors.toSet());
        return validateSplits(splitTable.getItems(), accountCodes, fundCodes);
    }

    static ValidationResult validateSplits(List<SplitRow> rows, Set<String> accountCodes, Set<String> fundCodes)
    {
        int nonEmpty = 0;
        int valid = 0;
        int errors = 0;
        BigDecimal net = BigDecimal.ZERO;

        for (SplitRow row : rows)
        {
            boolean hasData = !(isBlank(row.account()) && isBlank(row.fund()) && isBlank(row.amount()));
            if (!hasData)
            {
                continue;
            }
            nonEmpty++;

            boolean rowValid = true;
            if (isBlank(row.account()) || !accountCodes.contains(row.account().trim()))
            {
                rowValid = false;
            }
            if (isBlank(row.fund()) || !fundCodes.contains(row.fund().trim()))
            {
                rowValid = false;
            }

            BigDecimal amount = parseAmount(row.amount());
            if (amount == null)
            {
                rowValid = false;
            }
            else
            {
                net = net.add(amount);
            }

            if (rowValid)
            {
                valid++;
            }
            else
            {
                errors++;
            }
        }

        if (nonEmpty == 0)
        {
            return new ValidationResult("Validation result: no split rows entered.", 0, 0, 0, BigDecimal.ZERO);
        }

        String message = "Validation result: rows=" + nonEmpty
                + ", valid=" + valid
                + ", errors=" + errors
                + ", net=" + net.toPlainString();
        if (errors == 0 && net.compareTo(BigDecimal.ZERO) == 0)
        {
            message += " (ready to post)";
        }
        else if (errors == 0)
        {
            message += " (warning: not balanced)";
        }
        return new ValidationResult(message, nonEmpty, valid, errors, net);
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.isBlank();
    }

    private static BigDecimal parseAmount(String value)
    {
        if (isBlank(value))
        {
            return null;
        }
        try
        {
            return new BigDecimal(value.trim());
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    private void showJournal()
    {
        status.setText("Open Ledger Register to inspect persisted journal lines for posted transactions.");
    }

    @Override
    public String title()
    {
        return "Transaction Editor";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public void onSave()
    {
        long draftedRows = splitTable.getItems().stream()
                .filter(r -> !(isBlank(r.account()) && isBlank(r.fund()) && isBlank(r.amount())))
                .count();
        status.setText("Draft saved in session with " + draftedRows + " populated split row(s).");
    }

    record ValidationResult(String message, int rowCount, int validCount, int errorCount, BigDecimal netAmount)
    {
    }

    public record SplitRow(String account, String fund, String amount, String activity, String merchant, String nmr, String notes)
    {
    }
}
