package org.nonprofitbookkeeping.service;

/** Assignment references that determine whether a role can be deactivated. */
public record AppRoleUsage(long activeAssignments, long historicalAssignments)
{
    public long totalAssignments()
    {
        return activeAssignments + historicalAssignments;
    }

    public boolean canDeactivate()
    {
        return activeAssignments == 0;
    }
}
