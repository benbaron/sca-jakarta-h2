package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;

import org.nonprofitbookkeeping.service.TransactionCommandValidator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Implements the production workspace JavaFX behavior checks from the test plan. */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("javafx-runtime")
public class ProductionWorkspaceJavaFxBehaviorTest
{
    @Test
    public void dashboardOpensFirstAndRemainsAvailableAfterClosingWorkTabs()
    {
        FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();

            host.show(AppPanelId.DASHBOARD);
            assertEquals(AppPanelId.DASHBOARD, host.activePanelId());
            assertFalse(host.isClosable(AppPanelId.DASHBOARD));

            host.show(AppPanelId.HELP);
            assertEquals(2, host.openPanelCount());

            int closed = host.closeAllClosableTabs();

            assertEquals(1, closed);
            assertTrue(host.isOpen(AppPanelId.DASHBOARD));
            assertEquals(AppPanelId.DASHBOARD, host.activePanelId());
            assertEquals("Dashboard", host.getActiveTitle());
            return null;
        });
    }

    @Test
    public void selectingAnAlreadyOpenDestinationActivatesItsReusableTab()
    {
        FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();

            host.show(AppPanelId.LEDGER_REGISTER);
            Tab firstLedgerTab = host.getSelectionModel().getSelectedItem();
            host.show(AppPanelId.TXN_EDITOR);

            host.show(AppPanelId.LEDGER_REGISTER);

            assertEquals(2, host.openPanelCount());
            assertSame(firstLedgerTab, host.getSelectionModel().getSelectedItem());
            assertEquals(AppPanelId.LEDGER_REGISTER, host.activePanelId());
            return null;
        });
    }

    @Test
    public void dirtyClosableTabsAreReportedBeforeOrganizationSwitchOrBulkClose()
    {
        FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();
            host.show(AppPanelId.DASHBOARD);
            host.showReplacement(AppPanelId.HELP, new StubPanel("Help", true));

            List<String> dirtyTitles = host.dirtyClosablePanelTitles();

            assertEquals(List.of("Help"), dirtyTitles);
            return null;
        });
    }

    @Test
    public void ledgerRegisterTabAppearsBeforeTransactionEditorWhenOpenedInWorkflowOrder()
    {
        FxTestSupport.onFx(() -> {
            PanelHost host = new PanelHost();

            host.show(AppPanelId.DASHBOARD);
            host.show(AppPanelId.LEDGER_REGISTER);
            host.show(AppPanelId.TXN_EDITOR);

            assertTrue(indexOf(host, AppPanelId.LEDGER_REGISTER) < indexOf(host, AppPanelId.TXN_EDITOR));
            assertEquals(AppPanelId.TXN_EDITOR, host.activePanelId());
            return null;
        });
    }

    @Test
    public void transactionEditorUsesTheCommonLineEditorModelContract()
    {
        TransactionLineEditorModel model = new TransactionLineEditorModel(new TransactionCommandValidator());

        assertEquals(2, model.rows().size());
        assertFalse(model.isDirty());

        model.addRow();

        assertEquals(3, model.rows().size());
        assertTrue(model.isDirty());
    }

    private static int indexOf(PanelHost host, AppPanelId id)
    {
        for (int index = 0; index < host.getTabs().size(); index++)
        {
            if (host.getTabs().get(index).getUserData() == id)
            {
                return index;
            }
        }
        return -1;
    }

    private record StubPanel(String title, boolean hasUnsavedChanges) implements AppPanel
    {
        @Override
        public Node root()
        {
            return new Label(title);
        }
    }
}
