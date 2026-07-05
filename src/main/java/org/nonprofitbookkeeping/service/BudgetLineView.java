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
        YearMonth periodMonth,
        BigDecimal amount,
        String notes)
{
}
