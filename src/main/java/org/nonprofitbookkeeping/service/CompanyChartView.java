package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.ChartStatus;

/** Detached company-owned Chart of Accounts selection used by the Chart of Accounts workspace. */
public record CompanyChartView(
        Long id,
        String name,
        String version,
        ChartStatus status,
        boolean activeForCompany)
{
    @Override
    public String toString()
    {
        return name + " — " + version + " [" + status + "]"
                + (activeForCompany ? " — current" : "");
    }
}
