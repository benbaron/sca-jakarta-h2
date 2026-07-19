package org.nonprofitbookkeeping.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Dashboard fallback shown when the selected database cannot be opened. It
 * keeps the application shell usable so another database can be selected,
 * created, or explicitly retried for repair.
 */
final class DatabaseRecoveryPanel implements AppPanel
{
    private final BorderPane root = new BorderPane();

    DatabaseRecoveryPanel(
            Path databasePath,
            Throwable failure,
            DatabaseRecoveryCommandHandler commandHandler)
    {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(commandHandler, "commandHandler");

        root.getStyleClass().add("dashboard-experiment-root");
        root.setTop(buildHeader());
        root.setCenter(buildContent(
                databasePath,
                failure,
                commandHandler));
    }

    @Override
    public String title()
    {
        return "Dashboard";
    }

    @Override
    public Node root()
    {
        return root;
    }

    private static Node buildHeader()
    {
        Label title = new Label("Dashboard");
        title.getStyleClass().add("panel-title");
        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 14, 10, 14));
        header.getStyleClass().add("dashboard-header");
        return new VBox(header, new Separator());
    }

    private static Node buildContent(
            Path databasePath,
            Throwable failure,
            DatabaseRecoveryCommandHandler commandHandler)
    {
        Label heading = new Label("Database attention required");
        heading.getStyleClass().add("panel-title");

        Label explanation = new Label(
                "The selected database could not be opened. The application workspace remains available so you can retry or repair this database, select another database, or create a new one.");
        explanation.setWrapText(true);

        Label pathHeading = new Label("Selected database");
        pathHeading.getStyleClass().add("card-title");
        Label path = new Label(databasePath.toAbsolutePath().normalize().toString());
        path.setWrapText(true);

        Label errorHeading = new Label("Startup error");
        errorHeading.getStyleClass().add("card-title");
        Label error = new Label(safeMessage(failure));
        error.setWrapText(true);
        error.getStyleClass().add("database-error-message");

        Button repair = new Button("Retry / Repair Current Database");
        repair.setOnAction(event -> commandHandler.execute(DatabaseRecoveryCommand.RETRY_CURRENT));
        Button select = new Button("Select Existing Database…");
        select.setOnAction(event -> commandHandler.execute(DatabaseRecoveryCommand.SELECT_EXISTING));
        Button create = new Button("Create New Database…");
        create.setOnAction(event -> commandHandler.execute(DatabaseRecoveryCommand.CREATE_NEW));

        HBox actions = new HBox(10, repair, select, create);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(
                12,
                heading,
                explanation,
                new Separator(),
                pathHeading,
                path,
                errorHeading,
                error,
                actions);
        card.getStyleClass().add("dashboard-card");
        card.setMaxWidth(900);

        Region horizontalSpacer = new Region();
        HBox.setHgrow(horizontalSpacer, Priority.ALWAYS);
        HBox centered = new HBox(horizontalSpacer, card, new Region());
        HBox.setHgrow(centered.getChildren().get(2), Priority.ALWAYS);
        centered.setPadding(new Insets(24));
        centered.setAlignment(Pos.TOP_CENTER);
        return centered;
    }

    static String safeMessage(Throwable failure)
    {
        if (failure == null)
        {
            return "The database could not be opened.";
        }
        String message = UiErrors.safeMessage(failure);
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }
}
