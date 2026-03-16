package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.FundBalanceRow;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BudgetEditorPanelTest component.
 */
class BudgetEditorPanelTest
{
    @Test
    void buildRows_appliesStoredBudgetTargets()
    {
        List<BudgetEditorPanel.FundBudgetRow> rows = BudgetEditorPanel.buildRows(
                List.of(new FundBalanceRow("B", "Fund B", BigDecimal.TEN),
                        new FundBalanceRow("A", "Fund A", BigDecimal.ONE)),
                Map.of("A", BigDecimal.valueOf(33)));

        assertEquals(2, rows.size());
        assertEquals("A", rows.get(0).fundCode());
        assertEquals(BigDecimal.valueOf(33), rows.get(0).budgetTarget());
        assertEquals("B", rows.get(1).fundCode());
        assertEquals(BigDecimal.ZERO, rows.get(1).budgetTarget());
    }

    @Test
    void parseTargetAmount_rejectsInvalidValues()
    {
        assertThrows(IllegalArgumentException.class, () -> BudgetEditorPanel.parseTargetAmount(""));
        assertThrows(IllegalArgumentException.class, () -> BudgetEditorPanel.parseTargetAmount("abc"));
        assertThrows(IllegalArgumentException.class, () -> BudgetEditorPanel.parseTargetAmount("-1"));
        assertThrows(IllegalArgumentException.class, () -> BudgetEditorPanel.parseTargetAmount("12.345"));
    }

    @Test
    void parseTargetAmount_acceptsTwoDecimalPlaces()
    {
        assertEquals(new BigDecimal("12.34"), BudgetEditorPanel.parseTargetAmount("12.34"));
    }
}
