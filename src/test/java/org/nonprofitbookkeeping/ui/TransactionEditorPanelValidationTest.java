package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TransactionEditorPanelValidationTest component.
 */
public class TransactionEditorPanelValidationTest
{
    @Test
    public void validateSplits_marksBalancedAndReadyWhenRowsAreValid()
    {
        TransactionEditorPanel.ValidationResult result = TransactionEditorPanel.validateSplits(
                List.of(
                        new TransactionEditorPanel.SplitRow("1000", "F01", "50", "", "", "", ""),
                        new TransactionEditorPanel.SplitRow("2000", "F01", "-50", "", "", "", "")),
                Set.of("1000", "2000"),
                Set.of("F01"));

        assertEquals(2, result.rowCount());
        assertEquals(2, result.validCount());
        assertEquals(0, result.errorCount());
        assertEquals("0", result.netAmount().toPlainString());
        assertTrue(result.message().contains("ready to post"));
    }

    @Test
    public void validateSplits_reportsErrorsForUnknownCodesAndBadAmount()
    {
        TransactionEditorPanel.ValidationResult result = TransactionEditorPanel.validateSplits(
                List.of(new TransactionEditorPanel.SplitRow("9999", "BAD", "not-a-number", "", "", "", "")),
                Set.of("1000"),
                Set.of("F01"));

        assertEquals(1, result.rowCount());
        assertEquals(0, result.validCount());
        assertEquals(1, result.errorCount());
    }
}
