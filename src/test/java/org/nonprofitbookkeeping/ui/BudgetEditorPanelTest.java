package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.service.BudgetLineView;
import org.nonprofitbookkeeping.service.BudgetPlanView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BudgetEditorPanelTest
{
    @Test
    void parseTargetAmount_rejectsInvalidValues()
    {
        assertThrows(IllegalArgumentException.class, () -> BudgetEditorPanel.parseTargetAmount(""));
        assertThrows(IllegalArgumentException.class, () -> BudgetEditorPanel.parseTargetAmount("abc"));
        assertThrows(IllegalArgumentException.class, () -> BudgetEditorPanel.parseTargetAmount("-1"));
        assertThrows(IllegalArgumentException.class, () -> BudgetEditorPanel.parseTargetAmount("12.34567"));
    }

    @Test
    void parseTargetAmount_acceptsFourDecimalPlaces()
    {
        assertEquals(new BigDecimal("12.3456"), BudgetEditorPanel.parseTargetAmount("12.3456"));
    }

    @Test
    void scopedSaveReplacesOnlySelectedFundEventAndPreservesOtherScopes()
    {
        BudgetPlanView plan = new BudgetPlanView(
                9L, "FY 2026", 2026, "DRAFT-1", BudgetPlan.Status.DRAFT,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                null, null, "", List.of(
                line(1L, null, null, "100.0000"),
                line(2L, 2L, 2L, "200.0000"),
                line(3L, 1L, 1L, "25.0000")));

        var commands = BudgetEditorPanel.mergeScopeCommands(
                plan,
                List.of(new BudgetEditorPanel.CategoryBudgetRow(1L, "PROGRAM", "Program", new BigDecimal("25.0000"))),
                1L,
                new BigDecimal("50.0000"),
                1L,
                1L);

        assertEquals(3, commands.size());
        assertEquals(new BigDecimal("100.0000"), commands.stream()
                .filter(command -> command.fundId() == null && command.activityId() == null)
                .findFirst().orElseThrow().amount());
        assertEquals(new BigDecimal("200.0000"), commands.stream()
                .filter(command -> Long.valueOf(2L).equals(command.fundId()))
                .findFirst().orElseThrow().amount());
        assertEquals(new BigDecimal("50.0000"), commands.stream()
                .filter(command -> Long.valueOf(1L).equals(command.fundId())
                        && Long.valueOf(1L).equals(command.activityId()))
                .findFirst().orElseThrow().amount());
    }

    private static BudgetLineView line(Long id, Long fundId, Long activityId, String amount)
    {
        return new BudgetLineView(id, 1L, "PROGRAM", "Program", fundId,
                fundId == null ? "" : "F" + fundId, fundId == null ? "" : "Fund " + fundId,
                activityId, activityId == null ? "" : "E" + activityId,
                activityId == null ? "" : "Event " + activityId,
                null, new BigDecimal(amount), "");
    }
}
