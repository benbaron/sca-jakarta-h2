package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.YearMonth;

/** Immutable projection of one budget line. */
public record BudgetLineView(
        Long id,
        Long budgetCategoryId,
        String budgetCategoryCode,
        String budgetCategoryName,
        Long fundId,
        String fundCode,
        String fundName,
        Long activityId,
        String activityCode,
        String activityName,
        YearMonth periodMonth,
        BigDecimal amount,
        String notes)
{
    public BudgetLineView(
            Long id,
            Long budgetCategoryId,
            String budgetCategoryCode,
            String budgetCategoryName,
            Long fundId,
            String fundCode,
            String fundName,
            YearMonth periodMonth,
            BigDecimal amount,
            String notes)
    {
        this(id, budgetCategoryId, budgetCategoryCode, budgetCategoryName,
                fundId, fundCode, fundName, null, "", "", periodMonth, amount, notes);
    }
}
