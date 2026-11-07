package org.nonprofitbookkeeping.ui;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.nonprofitbookkeeping.model.DatabaseSelectionState;
import org.nonprofitbookkeeping.model.MultiCompanyState;
import org.nonprofitbookkeeping.service.ReservedSecurityRole;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock("ui-service-registry")
class JournalSupplementalAvailabilityTest
{
    @Test
    void accountantCanEnableAndEditEverySupplementalKindThroughProductionFactory(@TempDir Path directory)
    {
        FxTestSupport.initToolkitOrSkip();
        var session = MainWindow.sharedSessionState();
        var originalDatabase = session.databaseSelection();
        var originalCompany = session.multiCompany();
        var originalUser = session.authenticatedUser();
        Path database = directory.resolve("supplemental-availability");
        session.setDatabaseSelection(new DatabaseSelectionState(database.toString(), List.of(database.toString())));
        session.setMultiCompany(new MultiCompanyState("DEFAULT", List.of("DEFAULT")));
        UiServiceRegistry.reconnectToDatabase(database);
        try
        {
            FxTestSupport.onFx(() ->
            {
                Platform.setImplicitExit(false);
                session.setAuthenticatedUser(UiPermissionTestSessions.session(Set.of(ReservedSecurityRole.ACCOUNTANT)));
                AppPanel panel = new PanelFactory().create(AppPanelId.JOURNAL_PANE);
                Parent root = (Parent) panel.root();
                Stage stage = new Stage();
                try
                {
                    stage.setScene(new Scene(root, 1100, 800));
                    stage.show();
                    root.applyCss();
                    root.layout();
                    ((TabPane) root.lookup("#journalWorkPanes")).getSelectionModel().select(3);
                    root.applyCss();
                    root.layout();
                    TabPane details = (TabPane) root.lookup("#journalSupplementalTabs");
                    String[] kinds = { "RECEIVABLE", "PAYABLE", "PREPAID_EXPENSE", "DEFERRED_REVENUE", "OTHER_ASSET", "OTHER_LIABILITY" };
                    assertEquals(kinds.length, details.getTabs().size());
                    for (int index = 0; index < kinds.length; index++)
                    {
                        String kind = kinds[index];
                        CheckBox selection = (CheckBox) root.lookup("#journalSupplemental" + kind);
                        Tab tab = details.getTabs().get(index);
                        TableView<?> table = (TableView<?>) root.lookup("#journalWorkspaceSupplemental" + kind + "Table");
                        assertFalse(selection.isSelected());
                        assertTrue(tab.isDisable());
                        selection.fire();
                        details.getSelectionModel().select(tab);
                        root.applyCss();
                        root.layout();
                        assertFalse(tab.isDisabled(), kind);
                        assertFalse(table.isDisabled(), kind + " table must be usable when checked");
                        Button add = button(tab, "Add Open Item Detail");
                        assertFalse(add.isDisabled(), kind + " Add must be usable when checked");
                        add.fire();
                        assertEquals(1, table.getItems().size(), kind + " Add must reach the live draft");
                        Button remove = button(tab, "Remove Detail");
                        assertFalse(remove.isDisabled());
                        remove.fire();
                        assertTrue(table.getItems().isEmpty());
                        assertTrue(remove.isDisabled(), "Selection-based disabling remains intact");
                        selection.fire();
                        assertTrue(table.isDisabled(), "Unchecked supplemental kinds remain unavailable");
                        selection.fire();
                        assertFalse(table.isDisabled(), "Rechecking must enable the actual inner editor");
                    }
                    assertTrue(panel.hasUnsavedChanges());
                }
                finally
                {
                    stage.close();
                }
                return null;
            });
        }
        finally
        {
            FxTestSupport.onFx(() ->
            {
                originalUser.ifPresentOrElse(session::setAuthenticatedUser, session::clearAuthenticatedUser);
                session.setDatabaseSelection(originalDatabase);
                session.setMultiCompany(originalCompany);
                return null;
            });
            UiServiceRegistry.reconnectToDatabase(Path.of(originalDatabase.activeDatabasePath()));
        }
    }

    private static Button button(Tab tab, String text)
    {
        return tab.getContent().lookupAll(".button").stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(button -> text.equals(button.getText()))
                .findFirst().orElseThrow();
    }
}
