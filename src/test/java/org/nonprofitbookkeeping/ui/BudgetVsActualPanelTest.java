package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.FundBalanceRow;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BudgetVsActualPanelTest component.
 */
class BudgetVsActualPanelTest
{
    @Test
    void mergeBudgetAndActual_appliesTargetsAndVariance()
    {
        List<BudgetVsActualPanel.BudgetActualRow> rows = BudgetVsActualPanel.mergeBudgetAndActual(
                List.of(new FundBalanceRow("F2", "Fund 2", BigDecimal.valueOf(40)),
                        new FundBalanceRow("F1", "Fund 1", BigDecimal.valueOf(100))),
                Map.of("F1", BigDecimal.valueOf(70)));

        assertEquals(2, rows.size());
        assertEquals("F1", rows.get(0).fundCode());
        assertEquals(BigDecimal.valueOf(70), rows.get(0).budget());
        assertEquals(BigDecimal.valueOf(100), rows.get(0).actual());
        assertEquals(BigDecimal.valueOf(30), rows.get(0).variance());

        assertEquals("F2", rows.get(1).fundCode());
        assertEquals(BigDecimal.ZERO, rows.get(1).budget());
        assertEquals(BigDecimal.valueOf(40), rows.get(1).variance());
    }
}
