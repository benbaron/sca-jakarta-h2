package org.nonprofitbookkeeping.ui.customer;

import org.nonprofitbookkeeping.architecture.CustomerPanelId;

import java.util.List;
import java.util.Objects;

/**
 * Customer UI panel descriptor with role requirements and key workflows.
 */
public record CustomerPanelDescriptor(
        CustomerPanelId panelId,
        String title,
        UserRole minimumRole,
        List<String> workflows)
{
    public CustomerPanelDescriptor
    {
        panelId = Objects.requireNonNull(panelId, "panelId");
        title = requireText(title, "title");
        minimumRole = Objects.requireNonNull(minimumRole, "minimumRole");
        workflows = List.copyOf(Objects.requireNonNull(workflows, "workflows"));

        if (workflows.isEmpty())
        {
            throw new IllegalArgumentException("workflows cannot be empty");
        }
        if (workflows.stream().map(String::trim).anyMatch(String::isEmpty))
        {
            throw new IllegalArgumentException("workflow entries cannot be blank");
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
