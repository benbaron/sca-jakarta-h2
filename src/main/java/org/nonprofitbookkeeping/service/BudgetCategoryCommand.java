package org.nonprofitbookkeeping.service;

import java.time.LocalDate;

/** Stable-ID category maintenance; a null ID creates a new row. */
public record BudgetCategoryCommand(Long id, String code, String name, boolean active,
        LocalDate effectiveFrom, LocalDate effectiveTo, String description)
{
}
