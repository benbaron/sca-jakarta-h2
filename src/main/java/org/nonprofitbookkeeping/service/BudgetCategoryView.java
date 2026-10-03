package org.nonprofitbookkeeping.service;

import java.time.LocalDate;

/** Company-scoped category maintenance projection. */
public record BudgetCategoryView(Long id, String code, String name, boolean active,
        LocalDate effectiveFrom, LocalDate effectiveTo, String description)
{
}
