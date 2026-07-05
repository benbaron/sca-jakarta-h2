package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/** Command for one planned budget amount within a draft budget plan. */
public record BudgetLineCommand(
        Long budgetCategoryId,
        Long fundId,
        YearMonth periodMonth,
        BigDecimal amount,
        String notes)
{
    public BudgetLineCommand
    {
        Objects.requireNonNull(budgetCategoryId, "budgetCategoryId");
        Objects.requireNonNull(amount, "amount");
        if (amount.scale() > 4)
        {
            throw new IllegalArgumentException("amount supports at most four decimal places");
        }
        notes = notes == null ? "" : notes.trim();
    }
}
