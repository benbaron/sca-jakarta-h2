package org.nonprofitbookkeeping.ui;

import javafx.scene.control.Button;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.ApplicationPermission;
import org.nonprofitbookkeeping.service.AuthenticatedUserSession;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiPermissionGateTest
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
    void fixedRolePolicyIsReflectedInPresentationGate()
    {
        FxTestSupport.onFx(() ->
        {
            UiSessionState session = MainWindow.sharedSessionState();
            session.setAuthenticatedUser(UiPermissionTestSessions.viewer());

            assertTrue(UiPermissionGate.allows(ApplicationPermission.EXPORT));
            assertTrue(UiPermissionGate.allows(ApplicationPermission.UI_PREFERENCE_WRITE));
            assertFalse(UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE));
            assertFalse(UiPermissionGate.allows(ApplicationPermission.COMPANY_ADMIN));
            assertFalse(UiPermissionGate.allows(ApplicationPermission.SECURITY_ADMIN));
            assertFalse(UiPermissionGate.allows(ApplicationPermission.DATABASE_ADMIN));

            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            assertTrue(UiPermissionGate.allows(ApplicationPermission.BOOKKEEPING_WRITE));
            assertTrue(UiPermissionGate.allows(ApplicationPermission.COMPANY_ADMIN));
            assertTrue(UiPermissionGate.allows(ApplicationPermission.EXPORT));
            assertFalse(UiPermissionGate.allows(ApplicationPermission.SECURITY_ADMIN));
            assertFalse(UiPermissionGate.allows(ApplicationPermission.DATABASE_ADMIN));
            return null;
        });
    }

    @Test
    void unboundControlCombinesLocalAndPermissionDisableStateAcrossRoleChanges()
    {
        FxTestSupport.onFx(() ->
        {
            UiSessionState session = MainWindow.sharedSessionState();
            session.setAuthenticatedUser(UiPermissionTestSessions.viewer());

            Button button = new Button("Save");
            UiPermissionGate.gate(button, ApplicationPermission.BOOKKEEPING_WRITE, "Save the record");
            assertTrue(button.isDisable());
            assertTrue(button.getTooltip().getText().contains("BOOKKEEPING_WRITE"));

            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            assertFalse(button.isDisable());

            button.setDisable(true);
            session.setAuthenticatedUser(UiPermissionTestSessions.viewer());
            session.setAuthenticatedUser(UiPermissionTestSessions.manager());
            assertTrue(button.isDisable());

            button.setDisable(false);
            assertFalse(button.isDisable());

            session.setAuthenticatedUser(UiPermissionTestSessions.viewer());
            assertTrue(button.isDisable());
            return null;
        });
    }
}
