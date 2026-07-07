package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.CorrectionMethod;
import org.nonprofitbookkeeping.service.TransactionCommand;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TransactionEditorPanelCommandMappingTest component.
 */
public class TransactionEditorPanelCommandMappingTest
{
    @Test
    public void toTransactionCommand_mapsOptionBackedSplitRows()
    {
        TransactionEditorPanel.SplitRow cash = new TransactionEditorPanel.SplitRow("", "", "", "125.00", "", "", "", "", "false", "cash");
        cash.setAccount(TransactionLineEditorModel.option(1L, "1000", "Cash"));
        cash.setFund(TransactionLineEditorModel.option(10L, "OPERATING", "Operating"));
        TransactionEditorPanel.SplitRow income = new TransactionEditorPanel.SplitRow("", "", "", "", "125.00", "", "", "", "false", "income");
        income.setAccount(TransactionLineEditorModel.option(2L, "4000", "Income"));
        income.setFund(TransactionLineEditorModel.option(10L, "OPERATING", "Operating"));

        TransactionCommand command = TransactionEditorPanel.toTransactionCommand(
                "2026-07-04",
                "Gift",
                List.of(cash, income),
                List.of(),
                List.of());

        assertEquals(LocalDate.of(2026, 7, 4), command.date());
        assertEquals("Gift", command.memo());
        assertEquals(2, command.lines().size());
        assertEquals(1L, command.lines().get(0).accountId());
        assertEquals(10L, command.lines().get(0).fundId());
        assertEquals("125.00", command.lines().get(0).debit().toPlainString());
        assertEquals(2L, command.lines().get(1).accountId());
        assertEquals("125.00", command.lines().get(1).credit().toPlainString());
    }
    @Test
    public void deleteConfirmationText_followsCorrectionMethodPolicy()
    {
        assertEquals("Delete", TransactionEditorPanel.deleteActionLabel(CorrectionMethod.DIRECT_EDIT));
        assertEquals("Reverse instead of deleting", TransactionEditorPanel.deleteActionLabel(CorrectionMethod.REVERSAL_AND_REPLACEMENT));
        assertEquals("Delete transaction #42?", TransactionEditorPanel.deleteConfirmationHeader(42L));
        assertTrue(TransactionEditorPanel.directDeleteConfirmationBody().contains("audit snapshot"));
        assertEquals("Reverse transaction #42 instead of deleting?", TransactionEditorPanel.reversalConfirmationHeader(42L));
        assertTrue(TransactionEditorPanel.reversalConfirmationBody().contains("active period date"));
    }

}
