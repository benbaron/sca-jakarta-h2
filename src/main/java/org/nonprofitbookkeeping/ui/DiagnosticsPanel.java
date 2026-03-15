package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Basic runtime diagnostics center for operator troubleshooting.
 */
public class DiagnosticsPanel implements AppPanel
{
    private final VBox root = new VBox(8);
    private final Label runtime = new Label();
    private final Label javaVersion = new Label();
    private final Label activeCompany = new Label();
    private final Label activeDatabase = new Label();
    private final Label datasource = new Label();
    private final Label status = new Label();

    public DiagnosticsPanel()
    {
        root.setPadding(new Insets(8));

        Label title = new Label("Diagnostics Center");
        title.getStyleClass().add("panel-title");

        Button refresh = new Button("Refresh Diagnostics");
        refresh.setOnAction(e -> reload());

        root.getChildren().addAll(
                title,
                refresh,
                new Separator(),
                runtime,
                javaVersion,
                activeCompany,
                activeDatabase,
                datasource,
                status);

        reload();
    }

    @Override
    public String title()
    {
        return "Diagnostics";
    }

    @Override
    public Node root()
    {
        return root;
    }

    @Override
    public void onNew()
    {
        reload();
    }

    private void reload()
    {
        runtime.setText("Runtime timestamp: " + Instant.now());
        javaVersion.setText("Java version: " + System.getProperty("java.version"));
        activeCompany.setText("Active company: " + MainWindow.sharedSessionState().multiCompany().activeCompanyCode());

        String db = MainWindow.sharedSessionState().databaseSelection().activeDatabasePath();
        activeDatabase.setText("Active database file: " + Path.of(db).toAbsolutePath());

        try
        {
            UiDataSources.forCurrentSessionDatabase().getConnection().close();
            datasource.setText("Datasource check: OK");
            status.setText("Diagnostics refreshed.");
        }
        catch (Exception ex)
        {
            datasource.setText("Datasource check: FAILED");
            status.setText("Datasource issue: " + UiErrors.safeMessage(ex));
        }
    }
}
