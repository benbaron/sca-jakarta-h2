package org.nonprofitbookkeeping.ui;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TestFX coverage for the cross-cutting UI contract in doc/PLAN.md and doc/ui_design_rules.md.
 */
@ExtendWith(ApplicationExtension.class)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("javafx-runtime")
@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
public class ProductionDesignRulesTestFxTest
{
    private MainWindow mainWindow;

    @Start
    public void start(Stage stage) throws Exception
    {
        mainWindow = new MainWindow(new FileAppStateStore(java.nio.file.Files.createTempFile("sca-ui-test", ".properties")));
        stage.setScene(new Scene(mainWindow, 1280, 800));
        stage.show();
        stage.toFront();
        WaitForAsyncUtils.waitForFxEvents();
    }

    @Test
    public void ledgerRegisterTableFollowsTableAndSplitPaneDesignRules(FxRobot robot)
    {
        robot.interact(() -> mainWindow.openPanel(AppPanelId.LEDGER_REGISTER));
        WaitForAsyncUtils.waitForFxEvents();

        TableView<?> table = lookupAs(robot, "#ledgerRegisterTransactionTable", TableView.class);
        SplitPane splitPane = lookupAs(robot, "#ledgerRegisterMiddleSplitPane", SplitPane.class);
        Button deleteButton = lookupAs(robot, "#ledgerRegisterDeleteCurrentLineButton", Button.class);

        assertSame(TableView.UNCONSTRAINED_RESIZE_POLICY, table.getColumnResizePolicy());
        assertColumnContract(table);
        assertEquals(Orientation.VERTICAL, splitPane.getOrientation());
        assertEquals(2, splitPane.getItems().size());
        assertTrue(splitPane.getItems().stream().anyMatch(item -> contains(item, table)));
        assertFalse(deleteButton.isDisabled(), "Durable transaction surfaces must expose a real Delete affordance or a visible unavailable reason.");
        assertNotNull(robot.lookup("#ledgerRegisterJournalDetails").query());
    }

    @Test
    public void transactionEditorSplitTableFollowsSpreadsheetAndTableDesignRules(FxRobot robot)
    {
        robot.interact(() -> mainWindow.openPanel(AppPanelId.TXN_EDITOR));
        WaitForAsyncUtils.waitForFxEvents();

        TableView<?> table = lookupAs(robot, "#transactionEditorSplitTable", TableView.class);
        SplitPane splitPane = lookupAs(robot, "#transactionEditorSplitEditorSplitPane", SplitPane.class);

        assertTrue(table.isEditable(), "Spreadsheet-like editors must be editable through table cells.");
        assertSame(TableView.UNCONSTRAINED_RESIZE_POLICY, table.getColumnResizePolicy());
        assertColumnContract(table);
        assertEquals(Orientation.VERTICAL, splitPane.getOrientation());
        assertEquals(2, splitPane.getItems().size());
        assertTrue(splitPane.getItems().stream().anyMatch(item -> contains(item, table)));
        assertNotNull(robot.lookup("#transactionEditorSaveButton").query());
        assertNotNull(robot.lookup("#transactionEditorTotalsLabel").query());
    }

    @Test
    public void productionShellUsesStableIdsAndPeriodSelectorWorkflow(FxRobot robot)
    {
        assertNotNull(robot.lookup("#productionPanelHost").query());
        assertNotNull(robot.lookup(".tool-bar").query());

        Set<Node> comboBoxes = robot.lookup((Node node) -> node instanceof ComboBox<?>).queryAll();
        assertTrue(comboBoxes.stream().anyMatch(node -> node instanceof ComboBox<?> comboBox
                && !comboBox.getItems().isEmpty()
                && comboBox.getItems().get(0) instanceof DateRangePreset),
                "The top chrome must expose the plan-governed period/range selection control rather than hiding the active context.");
    }

    private static void assertColumnContract(TableView<?> table)
    {
        assertTrue(table.getColumns().size() >= 2, "Production tables need enough columns to exercise reorder, resize, and sort behavior.");
        for (TableColumn<?, ?> column : table.getColumns())
        {
            assertTrue(column.isSortable(), column.getText() + " must be sortable.");
            assertTrue(column.isResizable(), column.getText() + " must be resizable.");
            assertTrue(column.isReorderable(), column.getText() + " must be reorderable.");
        }
    }

    private static boolean contains(Node root, Node target)
    {
        if (root == target)
        {
            return true;
        }
        if (root instanceof javafx.scene.Parent parent)
        {
            return parent.getChildrenUnmodifiable().stream().anyMatch(child -> contains(child, target));
        }
        return false;
    }

    private static <T> T lookupAs(FxRobot robot, String selector, Class<T> type)
    {
        return assertInstanceOf(type, robot.lookup(selector).query());
    }
}
