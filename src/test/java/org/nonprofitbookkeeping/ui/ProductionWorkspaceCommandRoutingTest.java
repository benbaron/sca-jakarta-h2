package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProductionWorkspaceCommandRoutingTest
{
    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @Test
    public void shellRoutesTypedCloseAllTabsCommand()
    {
        ProductionWorkspaceWindow window = FxTestSupport.onFx(ProductionWorkspaceCommandRoutingTest::newWindow);

        FxTestSupport.onFx(() -> {
            window.openPanel(AppPanelId.LEDGER_REGISTER);
            assertTrue(window.panelHost().isOpen(AppPanelId.LEDGER_REGISTER));

            AppPanel.RunCommandResult result = window.executeCommand(AppCommand.CLOSE_ALL_TABS);

            assertTrue(result.handled());
            assertFalse(window.panelHost().isOpen(AppPanelId.LEDGER_REGISTER));
            assertEquals(AppPanelId.DASHBOARD, window.panelHost().activePanelId());
            return null;
        });
    }

    @Test
    public void closeAllTabsMenuUsesRequiredShortcut()
    {
        ProductionWorkspaceWindow window = FxTestSupport.onFx(ProductionWorkspaceCommandRoutingTest::newWindow);

        FxTestSupport.onFx(() -> {
            MenuItem closeAllTabs = closeAllTabsMenuItem(window);

            assertEquals("Close All Tabs", closeAllTabs.getText());
            assertEquals(new KeyCodeCombination(
                    KeyCode.W,
                    KeyCombination.CONTROL_DOWN,
                    KeyCombination.SHIFT_DOWN), closeAllTabs.getAccelerator());
            return null;
        });
    }

    @Test
    public void closeAllTabsPromptsBeforeDiscardingUnsavedEdits()
    {
        ProductionWorkspaceWindow window = FxTestSupport.onFx(ProductionWorkspaceCommandRoutingTest::newWindow);
        AtomicReference<List<String>> promptedTitles = new AtomicReference<>();
        AtomicBoolean discard = new AtomicBoolean(false);

        FxTestSupport.onFx(() -> {
            window.closeAllTabsPromptForTests(titles -> {
                promptedTitles.set(List.copyOf(titles));
                return discard.get();
            });
            window.openPanel(AppPanelId.TXN_EDITOR);
            firstTextField(window.panelHost().activeRoot()).setText("Unsaved payee");

            AppPanel.RunCommandResult cancelled = window.executeCommand(AppCommand.CLOSE_ALL_TABS);

            assertFalse(cancelled.handled());
            assertTrue(window.panelHost().isOpen(AppPanelId.TXN_EDITOR));
            assertEquals(List.of("Transaction Editor"), promptedTitles.get());

            discard.set(true);
            AppPanel.RunCommandResult closed = window.executeCommand(AppCommand.CLOSE_ALL_TABS);

            assertTrue(closed.handled());
            assertFalse(window.panelHost().isOpen(AppPanelId.TXN_EDITOR));
            assertEquals(AppPanelId.DASHBOARD, window.panelHost().activePanelId());
            return null;
        });
    }

    @Test
    public void panelHostRoutesTypedPostValidateCommand()
    {
        AppPanel.RunCommandResult result = FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();
            host.show(AppPanelId.TXN_EDITOR);
            return host.runCommandActive(AppCommand.POST_VALIDATE);
        });

        assertTrue(result.handled());
        assertFalse(result.message().isBlank());
    }

    @Test
    public void newAndSaveAvailabilityFollowsActiveDestination()
    {
        ProductionWorkspaceWindow window = FxTestSupport.onFx(ProductionWorkspaceCommandRoutingTest::newWindow);

        FxTestSupport.onFx(() ->
        {
            assertCommandDisabled(window, "New", false);
            assertCommandDisabled(window, "Save", true);

            window.openPanel(AppPanelId.HELP);
            assertCommandDisabled(window, "New", true);
            assertCommandDisabled(window, "Save", true);
            AppPanel.RunCommandResult unsupported = window.executeCommand(AppCommand.SAVE_ACTIVE);
            assertFalse(unsupported.handled());
            assertTrue(unsupported.message().contains("not available"));

            window.openPanel(AppPanelId.JOURNAL_PANE);
            assertCommandDisabled(window, "New", false);
            assertCommandDisabled(window, "Save", false);

            window.openPanel(AppPanelId.SETTINGS);
            TabPane administrationTabs = findTabPane(
                    window.panelHost().activeRoot(),
                    "administrationTabs");
            administrationTabs.getSelectionModel().select(0);
            assertCommandDisabled(window, "New", true);
            assertCommandDisabled(window, "Save", false);
            administrationTabs.getSelectionModel().select(1);
            assertCommandDisabled(window, "New", true);
            assertCommandDisabled(window, "Save", true);
            administrationTabs.getSelectionModel().select(2);
            assertCommandDisabled(window, "New", false);
            assertCommandDisabled(window, "Save", false);
            return null;
        });
    }

    @Test
    public void productionShellCanonicalizesTransactionEditorDrillThroughToJournal()
    {
        ProductionWorkspaceWindow window = FxTestSupport.onFx(ProductionWorkspaceCommandRoutingTest::newWindow);

        FxTestSupport.onFx(() -> {
            window.openPanel(AppPanelId.LEDGER_REGISTER);

            DrillThroughCoordinator.openTransactionEditorWithContext(LedgerRegisterPanel.editorContext(909L));

            assertTrue(window.panelHost().isOpen(AppPanelId.JOURNAL_PANE));
            assertEquals(AppPanelId.JOURNAL_PANE, window.panelHost().activePanelId());
            return null;
        });
    }

    @Test
    public void ledgerRegisterCanOpenJournalPaneThroughDrillThroughCoordinator()
    {
        ProductionWorkspaceWindow window = FxTestSupport.onFx(ProductionWorkspaceCommandRoutingTest::newWindow);

        FxTestSupport.onFx(() -> {
            DrillThroughCoordinator.openPanelWithContext(AppPanelId.JOURNAL_PANE, JournalPane.centeredContext(909L, "test"));

            assertTrue(window.panelHost().isOpen(AppPanelId.JOURNAL_PANE));
            assertEquals(AppPanelId.JOURNAL_PANE, window.panelHost().activePanelId());
            return null;
        });
    }

    private static ProductionWorkspaceWindow newWindow()
    {
        return new ProductionWorkspaceWindow(
                UserAppStateStore.create(),
                (path, preferred) -> new DatabaseSessionController.PreparedConnection()
                {
                    @Override
                    public Path databasePath()
                    {
                        return path.toAbsolutePath().normalize();
                    }

                    @Override
                    public String activeCompanyCode()
                    {
                        return preferred == null || preferred.isBlank() ? "DEFAULT" : preferred;
                    }

                    @Override
                    public java.util.List<String> activeCompanyCodes()
                    {
                        return java.util.List.of(activeCompanyCode());
                    }

                    @Override
                    public void activate()
                    {
                    }

                    @Override
                    public void close()
                    {
                    }
                });
    }

    private static MenuItem closeAllTabsMenuItem(ProductionWorkspaceWindow window)
    {
        VBox top = (VBox) window.getTop();
        MenuBar menuBar = (MenuBar) top.getChildren().get(0);
        Menu workspace = menuBar.getMenus().stream()
                .filter(menu -> "Workspace".equals(menu.getText()))
                .findFirst()
                .orElseThrow();
        return workspace.getItems().stream()
                .filter(item -> "Close All Tabs".equals(item.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static void assertCommandDisabled(
            ProductionWorkspaceWindow window,
            String label,
            boolean expectedDisabled)
    {
        VBox top = (VBox) window.getTop();
        MenuBar menuBar = (MenuBar) top.getChildren().get(0);
        Menu file = menuBar.getMenus().stream()
                .filter(menu -> "File".equals(menu.getText()))
                .findFirst()
                .orElseThrow();
        MenuItem menuItem = file.getItems().stream()
                .filter(item -> item.getText().startsWith(label))
                .findFirst()
                .orElseThrow();
        ToolBar toolBar = (ToolBar) top.getChildren().get(1);
        Button button = toolBar.getItems().stream()
                .filter(Button.class::isInstance)
                .map(Button.class::cast)
                .filter(candidate -> label.equals(candidate.getText()))
                .findFirst()
                .orElseThrow();

        assertEquals(expectedDisabled, menuItem.isDisable(), label + " menu state");
        assertEquals(expectedDisabled, button.isDisable(), label + " toolbar state");
        if (expectedDisabled)
        {
            assertTrue(menuItem.getText().contains("not available"));
        }
        else
        {
            assertEquals(label, menuItem.getText());
        }
        assertTrue(button.getTooltip().getText().contains(
                expectedDisabled ? "not available" : "is available"));
    }

    private static TextField firstTextField(Node node)
    {
        if (node instanceof TextField textField)
        {
            return textField;
        }
        if (node instanceof Parent parent)
        {
            for (Node child : parent.getChildrenUnmodifiable())
            {
                try
                {
                    return firstTextField(child);
                }
                catch (IllegalStateException ignored)
                {
                    // Continue searching siblings.
                }
            }
        }
        throw new IllegalStateException("No text field found under node " + node);
    }

    private static TabPane findTabPane(Node node, String id)
    {
        if (node instanceof TabPane tabPane && id.equals(tabPane.getId()))
        {
            return tabPane;
        }
        if (node instanceof Parent parent)
        {
            for (Node child : parent.getChildrenUnmodifiable())
            {
                try
                {
                    return findTabPane(child, id);
                }
                catch (IllegalStateException ignored)
                {
                    // Continue searching siblings.
                }
            }
        }
        throw new IllegalStateException("No tab pane with id " + id + " found under node " + node);
    }
}
