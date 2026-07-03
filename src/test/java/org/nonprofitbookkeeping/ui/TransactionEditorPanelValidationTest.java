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

    @Test
    public void splitRow_settersUpdateEditableCellValues()
    {
        TransactionEditorPanel.SplitRow row = new TransactionEditorPanel.SplitRow("", "", "", "", "", "", "", "", "", "");

        row.setAccount(TransactionLineEditorModel.option(10L, "1000", "Cash"));
        row.setFund(TransactionLineEditorModel.option(20L, "F01", "General"));
        row.setDebit("25.00");
        row.setCredit("");
        row.setNotes("Entered from editable table cell");

        assertEquals("1000 — Cash", row.account());
        assertEquals(10L, row.accountId());
        assertEquals("F01 — General", row.fund());
        assertEquals(20L, row.fundId());
        assertEquals("25.00", row.debit());
        assertEquals("", row.credit());
        assertEquals("Entered from editable table cell", row.notes());
    }

    @Test
    public void validateSplits_ignoresBlankAddedRows()
    {
        TransactionEditorPanel.ValidationResult result = TransactionEditorPanel.validateSplits(
                List.of(
                        new TransactionEditorPanel.SplitRow("1000", "F01", "50", "", "", "", ""),
                        new TransactionEditorPanel.SplitRow("2000", "F01", "-50", "", "", "", ""),
                        new TransactionEditorPanel.SplitRow("", "", "", "", "", "", "", "", "", "")),
                Set.of("1000", "2000"),
                Set.of("F01"));

        assertEquals(2, result.rowCount());
        assertEquals(2, result.validCount());
        assertEquals(0, result.errorCount());
        assertEquals("0", result.netAmount().toPlainString());
    }
}
