package org.nonprofitbookkeeping.service;

/** Stable-ID command for creating or updating one company profile. */
public record CompanyCommand(
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
    public CompanyCommand(
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
}
