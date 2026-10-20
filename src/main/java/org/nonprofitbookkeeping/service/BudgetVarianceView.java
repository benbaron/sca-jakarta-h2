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
        Optional<Long> activityId,
        Optional<String> activityCode,
        Optional<String> activityName,
        BigDecimal budget,
        BigDecimal actual,
        BigDecimal variance)
{
    public BudgetVarianceView
    {
        fundId = fundId == null ? Optional.empty() : fundId;
        fundCode = fundCode == null ? Optional.empty() : fundCode;
        fundName = fundName == null ? Optional.empty() : fundName;
        activityId = activityId == null ? Optional.empty() : activityId;
        activityCode = activityCode == null ? Optional.empty() : activityCode;
        activityName = activityName == null ? Optional.empty() : activityName;
    }

    public BudgetVarianceView(
            String budgetCategoryCode,
            String budgetCategoryName,
            Optional<Long> fundId,
            Optional<String> fundCode,
            Optional<String> fundName,
            BigDecimal budget,
            BigDecimal actual,
            BigDecimal variance)
    {
        this(budgetCategoryCode, budgetCategoryName, fundId, fundCode, fundName,
                Optional.empty(), Optional.empty(), Optional.empty(), budget, actual, variance);
    }
}
