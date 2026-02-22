package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.beans.property.SimpleStringProperty;

import org.nonprofitbookkeeping.model.Fund;

import java.util.List;

public class FundsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final TableView<Fund> table = new TableView<>();
    private final Label status = new Label();
    private Button refresh;

    public FundsPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Funds");
        title.getStyleClass().add("panel-title");

        Button add = new Button("+ Add");
        add.setOnAction(e -> onNew());

        refresh = new Button("Refresh");
        refresh.setOnAction(e -> reload());

        HBox actions = new HBox(8, add, refresh);
        VBox header = new VBox(6, title, actions, status, new Separator());

        root.setTop(header);

        TableColumn<Fund, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getCode()));

        TableColumn<Fund, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().getName()));

        TableColumn<Fund, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().getFundType())));

        table.getColumns().addAll(code, name, type);
        table.setPlaceholder(new Label("No funds found. Run the seed command to create starter data."));
        root.setCenter(table);

        reload();
    }

    @Override public String title() { return "Funds"; }
    @Override public Node root() { return root; }

    @Override public void onNew() {
        new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
            "Create fund UI is not built yet. This panel is now connected to FundLookupService for read-only listing.").showAndWait();
    }

    private void reload()
    {
        refresh.setDisable(true);
        status.setText("Loading funds...");

        UiAsync.run("fund-load",
            () -> UiServiceRegistry.fundLookup().listActiveFunds(),
            rows -> {
            table.getItems().setAll(rows);
            status.setText("Loaded " + rows.size() + " fund(s).");
            refresh.setDisable(false);
            },
            ex -> {
            status.setText("Failed to load funds: " + safeMessage(ex));
            refresh.setDisable(false);
            });
    }

    private String safeMessage(Throwable ex)
    {
        if (ex == null || ex.getMessage() == null || ex.getMessage().isBlank()) return "unknown error";
        return ex.getMessage();
    }
}
