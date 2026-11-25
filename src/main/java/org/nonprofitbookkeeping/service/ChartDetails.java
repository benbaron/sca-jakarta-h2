package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.ChartStatus;

/** Detached, revision-bound native chart details. Opening amounts are never cloned. */
public record ChartDetails(long id, String companyCode, String companyName, String name, String version,
        ChartStatus status, boolean currentForCompany, long accountCount, long nonzeroOpeningCount, String revision)
{
    @Override
    public String toString()
    {
        return companyCode + " — " + name + " / " + version + " [" + status + "] (Chart ID " + id + ")";
    }
}
