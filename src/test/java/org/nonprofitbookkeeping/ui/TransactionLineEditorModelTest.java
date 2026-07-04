package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.TransactionCommand;
import org.nonprofitbookkeeping.service.TransactionCommandValidator;
import org.nonprofitbookkeeping.service.TransactionValidationResult;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransactionLineEditorModelTest
{
    @Test
    public void totals_useSeparateDebitAndCreditColumns()
    {
        TransactionLineEditorModel model = new TransactionLineEditorModel(new TransactionCommandValidator());
        model.rows().get(0).setDebit(new BigDecimal("125.25"));
        model.rows().get(1).setCredit(new BigDecimal("25.25"));

        TransactionLineEditorModel.Totals totals = model.totals();

        assertEquals(new BigDecimal("125.25"), totals.debitTotal());
        assertEquals(new BigDecimal("25.25"), totals.creditTotal());
        assertEquals(new BigDecimal("100.00"), totals.difference());
    }

    @Test
    public void toCommand_mapsIdBackedRowsAndSkipsBlankRows()
    {
        TransactionLineEditorModel model = new TransactionLineEditorModel(new TransactionCommandValidator());
        TransactionLineEditorModel.Row row = model.rows().get(0);
        row.setAccountId(100L);
        row.setFundId(10L);
        row.setBudgetCategoryId(20L);
        row.setActivityId(30L);
        row.setMerchantId(40L);
        row.setDebit(new BigDecimal("9.50"));
        row.setNotes("Supplies");

        TransactionCommand command = model.toCommand(LocalDate.of(2026, 7, 3), 50L, "Memo", 60L);

        assertEquals(1, command.lines().size());
        assertEquals(100L, command.lines().get(0).accountId());
        assertEquals(10L, command.lines().get(0).fundId());
        assertEquals(20L, command.lines().get(0).budgetCategoryId());
        assertEquals(30L, command.lines().get(0).activityId());
        assertEquals(40L, command.lines().get(0).merchantId());
        assertEquals(new BigDecimal("9.50"), command.lines().get(0).debit());
    }

    @Test
    public void validationRejectsBothSidedAndUnbalancedInput()
    {
        TransactionLineEditorModel model = new TransactionLineEditorModel(new TransactionCommandValidator());
        model.rows().get(0).setAccountId(1L);
        model.rows().get(0).setFundId(1L);
        model.rows().get(0).setDebit(BigDecimal.TEN);
        model.rows().get(0).setCredit(BigDecimal.ONE);
        model.rows().get(1).setAccountId(2L);
        model.rows().get(1).setFundId(1L);
        model.rows().get(1).setCredit(BigDecimal.ONE);

        TransactionValidationResult result = model.validate(LocalDate.of(2026, 7, 3), null, "Memo", null);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("both debit and credit")));
    }

    @Test
    public void dirtyStateTracksRowsAndCanBeCleared()
    {
        TransactionLineEditorModel model = new TransactionLineEditorModel(new TransactionCommandValidator());
        model.markClean();

        assertFalse(model.isDirty());

        model.rows().get(0).setNotes("Changed");

        assertTrue(model.isDirty());

        model.markClean();

        assertFalse(model.isDirty());
    }

    @Test
    public void addRow_returnsEditableBlankRowAndMarksModelDirty()
    {
        TransactionLineEditorModel model = new TransactionLineEditorModel(new TransactionCommandValidator());
        model.markClean();

        TransactionLineEditorModel.Row row = model.addRow();
        row.setAccountId(100L);
        row.setFundId(10L);
        row.setDebit(new BigDecimal("15.00"));

        assertEquals(3, model.rows().size());
        assertEquals(100L, model.rows().get(2).accountId());
        assertEquals(10L, model.rows().get(2).fundId());
        assertEquals(new BigDecimal("15.00"), model.rows().get(2).debit());
        assertTrue(model.isDirty());
    }

    @Test
    public void removeRow_removesRequestedRowAndPreservesMinimumEditorRows()
    {
        TransactionLineEditorModel model = new TransactionLineEditorModel(new TransactionCommandValidator());
        model.rows().get(0).setNotes("first");
        model.rows().get(1).setNotes("second");
        TransactionLineEditorModel.Row third = model.addRow();
        third.setNotes("third");

        assertTrue(model.removeRow(1));

        assertEquals(2, model.rows().size());
        assertEquals("first", model.rows().get(0).notes());
        assertEquals("third", model.rows().get(1).notes());
        assertFalse(model.removeRow(1));
        assertEquals(2, model.rows().size());
    }

    @Test
    public void rowFieldErrorRejectsBothSidedOrNegativeAmounts()
    {
        TransactionLineEditorModel.Row bothSided = new TransactionLineEditorModel.Row();
        bothSided.setDebit(BigDecimal.TEN);
        bothSided.setCredit(BigDecimal.ONE);

        assertTrue(bothSided.fieldError().orElse("").contains("either debit or credit"));

        TransactionLineEditorModel.Row negative = new TransactionLineEditorModel.Row();
        negative.setDebit(new BigDecimal("-1.00"));

        assertTrue(negative.fieldError().orElse("").contains("cannot be negative"));
    }
}
