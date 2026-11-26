package org.nonprofitbookkeeping.service;

import java.util.List;

/** Snapshot-bound effects and blockers for removing an entire unused draft chart. */
public record ChartDeletionPreview(long chartId, String companyCode, String name, String version,
    long accounts, long aliases, long reportMappings, long scheduleRequirements, long importMappings,
    String revision, List<String> blockers)
{
    public ChartDeletionPreview
    {
        blockers = List.copyOf(blockers);
    }

    public boolean canDelete()
    {
        return blockers.isEmpty();
    }

    public String effects()
    {
        return accounts + " accounts, " + aliases + " aliases, " + reportMappings + " report mappings, "
        + scheduleRequirements + " schedule requirements and " + importMappings + " import mappings";
    }
}
