package org.nonprofitbookkeeping.ui.customer.workspace;

import org.nonprofitbookkeeping.architecture.CustomerPanelId;
import org.nonprofitbookkeeping.ui.customer.UserRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stateful session model for customer panel navigation and role-gated access.
 */
public final class CustomerWorkspaceState
{
    private final LoginMode loginMode;
    private UserRole currentRole;
    private String currentUser;
    private String activeGroupCode;
    private CustomerPanelId activePanelId;
    private final List<CustomerPanelId> openHistory = new ArrayList<>();

    public CustomerWorkspaceState(LoginMode loginMode)
    {
        this.loginMode = Objects.requireNonNull(loginMode, "loginMode");
    }

    public void login(String username, UserRole role)
    {
        this.currentUser = requireText(username, "username");
        this.currentRole = Objects.requireNonNull(role, "role");
    }

    public void logout()
    {
        this.currentUser = null;
        this.currentRole = null;
        this.activePanelId = null;
        this.openHistory.clear();
    }

    public void selectGroup(String groupCode)
    {
        this.activeGroupCode = requireText(groupCode, "groupCode");
    }

    public void openPanel(CustomerPanelId panelId)
    {
        requireLoginIfNeeded();
        CustomerPanelDefinition panel = CustomerPanelRegistry.get(panelId);
        UserRole role = resolveRole();

        if (!panel.isAllowedFor(role))
        {
            throw new IllegalStateException("Access denied for role " + role + " to panel " + panelId);
        }

        this.activePanelId = panelId;
        this.openHistory.add(panelId);
    }

    public boolean canAccess(CustomerPanelId panelId)
    {
        if (loginMode == LoginMode.REQUIRED && currentRole == null)
        {
            return false;
        }

        UserRole role = resolveRole();
        return CustomerPanelRegistry.get(panelId).isAllowedFor(role);
    }

    public LoginMode loginMode()
    {
        return loginMode;
    }

    public String currentUser()
    {
        return currentUser;
    }

    public UserRole currentRole()
    {
        return currentRole;
    }

    public String activeGroupCode()
    {
        return activeGroupCode;
    }

    public CustomerPanelId activePanelId()
    {
        return activePanelId;
    }

    public List<CustomerPanelId> openHistory()
    {
        return List.copyOf(openHistory);
    }

    private UserRole resolveRole()
    {
        if (currentRole != null)
        {
            return currentRole;
        }
        return UserRole.USER;
    }

    private void requireLoginIfNeeded()
    {
        if (loginMode == LoginMode.REQUIRED && currentRole == null)
        {
            throw new IllegalStateException("Login required before opening panels");
        }
    }

    private static String requireText(String value, String fieldName)
    {
        String normalized = Objects.requireNonNull(value, fieldName).trim();
        if (normalized.isEmpty())
        {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return normalized;
    }
}
