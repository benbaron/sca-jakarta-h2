package org.nonprofitbookkeeping.service;

/** Stable-ID command for creating or updating one company profile. */
public record CompanyCommand(
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
}
