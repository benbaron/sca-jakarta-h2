package org.nonprofitbookkeeping.architecture;

import java.util.Objects;

/**
 * Describes a panel's intent and why it exists in the architecture.
 */
public record CustomerPanelBlueprint(CustomerPanelId panelId, String title, String purpose)
{
    public CustomerPanelBlueprint
    {
        panelId = Objects.requireNonNull(panelId, "panelId");
        title = Objects.requireNonNull(title, "title").trim();
        purpose = Objects.requireNonNull(purpose, "purpose").trim();

        if (title.isEmpty())
        {
            throw new IllegalArgumentException("title cannot be blank");
        }
        if (purpose.isEmpty())
        {
            throw new IllegalArgumentException("purpose cannot be blank");
        }
    }
}
