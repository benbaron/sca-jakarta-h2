package org.nonprofitbookkeeping.service;

/** Detached company profile used by administration and company selection UI. */
public record CompanyView(
        Long id,
        String code,
        String displayName,
        String legalName,
        String branchType,
        String parentOrganization,
        String ein,
        boolean active,
        int fiscalYearStartMonth,
        int fiscalYearStartDay,
        String defaultCurrency)
{
    public CompanyView(
            Long id,
            String code,
            String displayName,
            String legalName,
            String branchType,
            String parentOrganization,
            boolean active,
            int fiscalYearStartMonth,
            int fiscalYearStartDay,
            String defaultCurrency)
    {
        this(id, code, displayName, legalName, branchType, parentOrganization, null,
                active, fiscalYearStartMonth, fiscalYearStartDay, defaultCurrency);
    }

    @Override
    public String toString()
    {
        return code + " — " + displayName + (active ? "" : " (inactive)");
    }
}
