package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
}
