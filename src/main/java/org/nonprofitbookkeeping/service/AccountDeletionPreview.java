package org.nonprofitbookkeeping.service;

import java.util.List;

/** Snapshot-bound eligibility and effects of deleting one unused draft definition. */
public record AccountDeletionPreview(long chartId, long accountId, String chartName,
    String accountCode, String accountName, String revision, List<String> blockers,
    long aliases, long reportMappings, long scheduleRequirements, long importMappings)
{
    public AccountDeletionPreview
    {
        blockers = List.copyOf(blockers);
    }

    public boolean canDelete()
    {
        return blockers.isEmpty();
    }

    public String effects()
    {
        return aliases + " aliases, " + reportMappings + " report mappings, "
        + scheduleRequirements + " schedule requirements and " + importMappings + " account import mappings";
    }
}
