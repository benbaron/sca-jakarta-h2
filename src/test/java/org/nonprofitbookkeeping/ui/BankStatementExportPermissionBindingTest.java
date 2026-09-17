package org.nonprofitbookkeeping.ui;

import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.control.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankStatementExportPermissionBindingTest
{
    private Optional<AuthenticatedUserSession> previousSession;

    @BeforeAll
    static void setupFx()
    {
        FxTestSupport.initToolkitOrSkip();
    }

    @BeforeEach
    void captureSession()
    {
        previousSession = MainWindow.sharedSessionState().authenticatedUser();
    }

    @AfterEach
    void restoreSession()
    {
        UiSessionState session = MainWindow.sharedSessionState();
        if (previousSession != null && previousSession.isPresent())
        {
            session.setAuthenticatedUser(previousSession.orElseThrow());
        }
        else
        {
            session.clearAuthenticatedUser();
        }
    }

    @Test
    void exportButtonCombinesPermissionAndBusyStateWithoutConflictingDisableWriters()
    {
        FxTestSupport.onFx(() ->
        {
            UiSessionState session = MainWindow.sharedSessionState();
            session.clearAuthenticatedUser();
            ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(false);
            Button button = new Button("Export Bank CSV…");

            BankTransactionsPanel.bindExportButtonAvailability(
                    button, busy.getReadOnlyProperty(), "Export bank CSV");

            assertTrue(button.disableProperty().isBound());
            assertTrue(button.isDisable());
            assertNotNull(button.getTooltip());
            assertTrue(button.getTooltip().getText().contains("EXPORT"));

            session.setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertFalse(button.isDisable());
            assertEquals("Export Bank CSV…", button.getTooltip().getText());

            busy.set(true);
            assertTrue(button.isDisable());
            assertEquals("Export Bank CSV…", button.getTooltip().getText());

            busy.set(false);
            assertFalse(button.isDisable());

            session.clearAuthenticatedUser();
            assertTrue(button.isDisable());
            assertTrue(button.getTooltip().getText().contains("EXPORT"));
            return null;
        });
    }
}
