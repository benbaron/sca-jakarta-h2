package org.nonprofitbookkeeping.ui;

import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock("ui-service-registry")
class CompanyDialogUiComplianceTest
{
    @Test
    void installAppliesTooltipAndCompanyOwnedTablePolicy(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("dialog-ui-compliance");
        UiSessionState session = MainWindow.sharedSessionState();
        DatabaseSelectionState originalDatabase = session.databaseSelection();
        MultiCompanyState originalCompany = session.multiCompany();
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);

        try
        {
            FxTestSupport.onFx(() ->
            {
                DialogPane pane = new DialogPane();
                Label label = new Label("Lifecycle status details");
                TableView<String> table = new TableView<>();
                table.setId("dialogRecords");
                TableColumn<String, String> status = new TableColumn<>("Status");
                status.setUserData("status");
                status.setSortable(false);
                status.setResizable(false);
                status.setReorderable(false);
                table.getColumns().setAll(status);
                pane.setContent(new VBox(label, table));

                CompanyDialogUiCompliance.install(pane, AppPanelId.ASSETS_REGISTER);

                assertEquals("Lifecycle status details", label.getTooltip().getText());
                assertTrue(CompanyTableStateBinder.isCompanyStateOwned(table));
                assertSame(TableView.UNCONSTRAINED_RESIZE_POLICY, table.getColumnResizePolicy());
                assertTrue(status.isSortable());
                assertTrue(status.isResizable());
                assertTrue(status.isReorderable());
                return null;
            });
        }
        finally
        {
            session.setDatabaseSelection(originalDatabase);
            session.setMultiCompany(originalCompany);
            UiServiceRegistry.reconnectToDatabase(Path.of(originalDatabase.activeDatabasePath()));
        }
    }
}
