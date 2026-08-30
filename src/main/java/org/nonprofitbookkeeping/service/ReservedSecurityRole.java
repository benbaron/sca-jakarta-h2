package org.nonprofitbookkeeping.service;

import java.util.Locale;
import java.util.Optional;

/** Reserved application security roles with fixed runtime meaning. */
public enum ReservedSecurityRole
{
    ADMIN("Administrator"),
    MANAGER("Manager"),
    ACCOUNTANT("Accountant"),
    VIEWER("Viewer");

    private final String displayName;

    ReservedSecurityRole(String displayName)
    {
        this.displayName = displayName;
    }

    public String roleCode()
    {
        return name();
    }

    public String reservedUsername()
    {
        return name();
    }

    public String displayName()
    {
        return displayName;
    }

    public static Optional<ReservedSecurityRole> fromCode(String value)
    {
        if (value == null || value.isBlank())
        {
            return Optional.empty();
        }
        try
        {
            return Optional.of(valueOf(value.strip().toUpperCase(Locale.ROOT)));
        }
        catch (IllegalArgumentException ex)
        {
            return Optional.empty();
        }
    }
}
