package org.nonprofitbookkeeping.service;

import java.time.LocalDate;
import java.util.Objects;

/** Command for creating a draft budget plan header. */
public record BudgetPlanCommand(
        String name,
        int fiscalYear,
        String versionCode,
        LocalDate periodStart,
        LocalDate periodEnd,
        String notes)
{
    public BudgetPlanCommand
    {
        name = requireText(name, "name");
        versionCode = requireText(versionCode, "versionCode");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        notes = notes == null ? "" : notes.trim();
        if (fiscalYear < 1900 || fiscalYear > 9999)
        {
            throw new IllegalArgumentException("fiscalYear must be a four-digit year");
        }
        if (periodEnd.isBefore(periodStart))
        {
            throw new IllegalArgumentException("periodEnd must be on or after periodStart");
        }
    }

    private static String requireText(String value, String field)
    {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty())
        {
            throw new IllegalArgumentException(field + " is required");
        }
        return text;
    }
}
