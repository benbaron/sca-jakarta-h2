package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Additional native account-definition fields; omitted details preserve existing values. */
public record AccountDefinitionDetails(boolean posting, LocalDate effectiveFrom, LocalDate effectiveTo,
        BigDecimal openingBalance, String description)
{
    public AccountDefinitionDetails
    {
        openingBalance = openingBalance == null ? BigDecimal.ZERO : openingBalance;
        description = description == null || description.isBlank() ? null : description.trim();
    }
}
