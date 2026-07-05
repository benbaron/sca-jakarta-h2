package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.BudgetPlan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Immutable projection of a budget plan and its lines. */
public record BudgetPlanView(
        Long id,
        String name,
        int fiscalYear,
        String versionCode,
        BudgetPlan.Status status,
        LocalDate periodStart,
        LocalDate periodEnd,
        Instant activatedAt,
        Instant archivedAt,
        String notes,
        List<BudgetLineView> lines)
{
    public BudgetPlanView
    {
        lines = List.copyOf(lines == null ? List.of() : lines);
    }
}
