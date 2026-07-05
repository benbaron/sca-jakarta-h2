package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.util.Optional;

/** Planned-versus-actual projection for active budget comparison. */
public record BudgetVarianceView(
        String budgetCategoryCode,
        String budgetCategoryName,
        Optional<Long> fundId,
        Optional<String> fundCode,
        Optional<String> fundName,
        BigDecimal budget,
        BigDecimal actual,
        BigDecimal variance)
{
    public BudgetVarianceView
    {
        fundId = fundId == null ? Optional.empty() : fundId;
        fundCode = fundCode == null ? Optional.empty() : fundCode;
        fundName = fundName == null ? Optional.empty() : fundName;
    }
}
