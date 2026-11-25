package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.ChartStatus;

/** Read-only chart-template choice; includes other companies as explicitly requested. */
public record ChartSource(long id, String companyCode, String companyName, String name, String version, ChartStatus status)
{
    @Override
    public String toString()
    {
        return companyCode + " — " + name + " / " + version + " [" + status + "] (Chart ID " + id + ")";
    }
}
