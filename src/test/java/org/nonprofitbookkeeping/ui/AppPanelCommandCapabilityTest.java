package org.nonprofitbookkeeping.ui;

import javafx.scene.Node;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppPanelCommandCapabilityTest
{
    @Test
    void unsupportedDefaultCannotReportHandledOrInvokeHook()
    {
        AtomicBoolean invoked = new AtomicBoolean();
        AppPanel panel = new StubPanel(Set.of(), invoked, false);

        AppPanel.RunCommandResult result = panel.executeCommand(AppCommand.NEW_ACTIVE);

        assertFalse(result.handled());
        assertFalse(invoked.get());
        assertTrue(result.message().contains("not available"));
    }

    @Test
    void declaredCapabilityReportsHandledOnlyAfterHookReturns()
    {
        AtomicBoolean invoked = new AtomicBoolean();
        AppPanel panel = new StubPanel(Set.of(AppCommand.NEW_ACTIVE), invoked, false);

        AppPanel.RunCommandResult result = panel.executeCommand(AppCommand.NEW_ACTIVE);

        assertTrue(result.handled());
        assertTrue(invoked.get());
    }

    @Test
    void commandFailureIsReportedAsNotHandled()
    {
        AppPanel panel = new StubPanel(
                Set.of(AppCommand.SAVE_ACTIVE),
                new AtomicBoolean(),
                true);

        AppPanel.RunCommandResult result = panel.executeCommand(AppCommand.SAVE_ACTIVE);

        assertFalse(result.handled());
        assertTrue(result.message().contains("Save failed"));
        assertTrue(result.message().contains("test failure"));
    }

    @Test
    void everyProductionDestinationHasAnExplicitCapabilityContract()
    {
        Map<AppPanelId, Set<AppCommand>> expected = new EnumMap<>(AppPanelId.class);
        expected.put(AppPanelId.DASHBOARD, Set.of(AppCommand.NEW_ACTIVE));
        expected.put(AppPanelId.LEDGER_REGISTER, journalCommands());
        expected.put(AppPanelId.TXN_EDITOR, journalCommands());
        expected.put(AppPanelId.JOURNAL_PANE, journalCommands());
        expected.put(AppPanelId.BANKING, editorCommands());
        expected.put(AppPanelId.BUDGET_EDITOR, Set.of(AppCommand.SAVE_ACTIVE));
        expected.put(AppPanelId.BUDGET_VS_ACTUAL, Set.of());
        expected.put(AppPanelId.ASSETS_REGISTER, editorCommands());
        expected.put(AppPanelId.DEPRECIATION_RUNS, Set.of());
        expected.put(AppPanelId.INVENTORY, editorCommands());
        expected.put(AppPanelId.RECONCILIATION_RUNS, Set.of());
        expected.put(AppPanelId.PERIOD_CLOSE_RUNS, Set.of());
        expected.put(AppPanelId.IMPORT_PREVIEW, Set.of());
        expected.put(AppPanelId.APPROVAL_AUDIT, Set.of());
        expected.put(AppPanelId.BANK_TRANSACTIONS, Set.of());
        expected.put(AppPanelId.REPORT_LIBRARY, Set.of());
        expected.put(AppPanelId.CHART_OF_ACCOUNTS, editorCommands());
        expected.put(AppPanelId.FUNDS, editorCommands());
        expected.put(AppPanelId.SETTINGS, Set.of(AppCommand.SAVE_ACTIVE));
        expected.put(AppPanelId.DIAGNOSTICS, Set.of());
        expected.put(AppPanelId.HELP, Set.of());

        FxTestSupport.onFx(() ->
        {
            PanelHost host = new PanelHost();
            expected.forEach((id, commands) ->
            {
                host.show(id);
                assertEquals(commands, host.activeCommandCapabilities(), id.name());
            });
            return null;
        });
    }

    @Test
    void administrationCapabilitiesFollowSelectedTab()
    {
        FxTestSupport.onFx(() ->
        {
            AdministrationPanel panel = new AdministrationPanel();
            AtomicInteger notifications = new AtomicInteger();
            panel.setCommandCapabilitiesChangedListener(notifications::incrementAndGet);

            panel.tabsForTests().getSelectionModel().select(0);
            assertEquals(Set.of(AppCommand.SAVE_ACTIVE), panel.commandCapabilities());

            panel.tabsForTests().getSelectionModel().select(1);
            assertEquals(Set.of(), panel.commandCapabilities());

            panel.tabsForTests().getSelectionModel().select(2);
            assertEquals(editorCommands(), panel.commandCapabilities());

            panel.tabsForTests().getSelectionModel().select(3);
            assertEquals(editorCommands(), panel.commandCapabilities());
            panel.usersForTests().tabsForTests().getSelectionModel().select(3);
            assertEquals(Set.of(), panel.commandCapabilities());
            panel.usersForTests().tabsForTests().getSelectionModel().select(1);
            assertEquals(editorCommands(), panel.commandCapabilities());
            assertTrue(notifications.get() >= 4);
            return null;
        });
    }

    @Test
    void userAdminCapabilitiesFollowMaintenanceAndAuthenticationTabs()
    {
        FxTestSupport.onFx(() ->
        {
            UserAdminPanel panel = new UserAdminPanel();
            AtomicInteger notifications = new AtomicInteger();
            panel.setCommandCapabilitiesChangedListener(notifications::incrementAndGet);

            panel.tabsForTests().getSelectionModel().select(0);
            assertEquals(editorCommands(), panel.commandCapabilities());
            panel.tabsForTests().getSelectionModel().select(1);
            assertEquals(editorCommands(), panel.commandCapabilities());
            panel.tabsForTests().getSelectionModel().select(2);
            assertEquals(editorCommands(), panel.commandCapabilities());
            panel.tabsForTests().getSelectionModel().select(3);
            assertEquals(Set.of(), panel.commandCapabilities());
            assertTrue(notifications.get() >= 4);
            return null;
        });
    }

    private static Set<AppCommand> editorCommands()
    {
        return Set.of(AppCommand.NEW_ACTIVE, AppCommand.SAVE_ACTIVE);
    }

    private static Set<AppCommand> journalCommands()
    {
        return Set.of(
                AppCommand.NEW_ACTIVE,
                AppCommand.SAVE_ACTIVE,
                AppCommand.POST_VALIDATE);
    }

    private static final class StubPanel implements AppPanel
    {
        private final Set<AppCommand> commands;
        private final AtomicBoolean invoked;
        private final boolean fail;

        private StubPanel(Set<AppCommand> commands, AtomicBoolean invoked, boolean fail)
        {
            this.commands = commands;
            this.invoked = invoked;
            this.fail = fail;
        }

        @Override
        public String title()
        {
            return "Stub";
        }

        @Override
        public Node root()
        {
            return null;
        }

        @Override
        public Set<AppCommand> commandCapabilities()
        {
            return commands;
        }

        @Override
        public void onNew()
        {
            invoked.set(true);
        }

        @Override
        public void onSave()
        {
            if (fail)
            {
                throw new IllegalStateException("test failure");
            }
            invoked.set(true);
        }
    }
}
