package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Represents the SettingsPanel component in the nonprofit bookkeeping application.
 */
public class SettingsPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();
    private final Label status = new Label("Settings are applied in-memory for this session.");

    public SettingsPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Settings");
        title.getStyleClass().add("panel-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(4));

        ComboBox<String> fiscalStart = new ComboBox<>();
        fiscalStart.getItems().addAll("January", "April", "July", "October");
        fiscalStart.getSelectionModel().select("January");

        CheckBox includeNmr = new CheckBox("Include NMR in dashboard summaries");
        includeNmr.setSelected(true);

        CheckBox compactTables = new CheckBox("Use compact table density");
        compactTables.setSelected(false);

        int row = 0;
        grid.add(new Label("Fiscal year starts"), 0, row);
        grid.add(fiscalStart, 1, row++);
        grid.add(includeNmr, 0, row++, 2, 1);
        grid.add(compactTables, 0, row++, 2, 1);

        fiscalStart.valueProperty().addListener((obs, oldV, newV) -> status.setText("Fiscal year start set to " + newV + " (session only)."));
        includeNmr.selectedProperty().addListener((obs, oldV, newV) -> status.setText("NMR inclusion toggled to " + newV + " (session only)."));
        compactTables.selectedProperty().addListener((obs, oldV, newV) -> status.setText("Compact table density set to " + newV + " (session only)."));

        root.setTop(new VBox(6, title, status, new Separator()));
        root.setCenter(grid);
    }

    @Override public String title() { return "Settings"; }
    @Override public Node root() { return root; }
}
