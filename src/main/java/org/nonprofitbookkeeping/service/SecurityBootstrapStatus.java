package org.nonprofitbookkeeping.service;

import java.util.List;

/** Security bootstrap readiness and any owner-resolution conflicts. */
public record SecurityBootstrapStatus(boolean initialized, List<String> conflicts)
{
    public SecurityBootstrapStatus
    {
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
    }

    public boolean hasConflicts()
    {
        return !conflicts.isEmpty();
    }

    public boolean ready()
    {
        return initialized && conflicts.isEmpty();
    }
}
