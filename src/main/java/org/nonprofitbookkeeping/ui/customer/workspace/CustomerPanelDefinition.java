package org.nonprofitbookkeeping.ui.customer.workspace;

import org.nonprofitbookkeeping.architecture.CustomerPanelId;
import org.nonprofitbookkeeping.ui.customer.UserRole;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Defines a customer-facing accounting panel and access requirements.
 */
public record CustomerPanelDefinition(
        CustomerPanelId panelId,
        String title,
        UserRole minimumRole,
        Set<PanelAction> actions)
{
    public CustomerPanelDefinition
    {
        panelId = Objects.requireNonNull(panelId, "panelId");
        title = requireText(title, "title");
        minimumRole = Objects.requireNonNull(minimumRole, "minimumRole");
        actions = Set.copyOf(Objects.requireNonNull(actions, "actions"));

        if (actions.isEmpty())
        {
            throw new IllegalArgumentException("actions cannot be empty");
        }
    }

    public boolean isAllowedFor(UserRole role)
    {
        if (minimumRole == UserRole.USER)
        {
            return role == UserRole.USER || role == UserRole.SUPERVISOR;
        }
        return role == UserRole.SUPERVISOR;
    }

    public static Set<PanelAction> actions(PanelAction first, PanelAction... rest)
    {
        return EnumSet.of(first, rest);
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
