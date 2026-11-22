package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.UserManualProcedureFixture.line;

@DisplayName("Manual: Understand the records / Enter journal transactions")
class UserManualJournalRulesTest
{
    @ParameterizedTest(name = "{0}")
    @CsvSource({"Receive income,100.00,1000,4000", "Pay expense,50.00,5000,1000",
            "Move money between banks,100.00,1010,1000"})
    void manualDebitCreditExamplesAreValid(String activity, String amount, long debit, long credit)
    {
        var command = command(List.of(line(debit, 1, amount, "0"), line(credit, 1, "0", amount)));
        assertTrue(new TransactionCommandValidator().validate(command).valid(), activity);
        assertEquals(0, command.lines().stream().map(TransactionLineCommand::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).signum());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "50.00", "100.10", "100.1000"})
    void wholeCentsAreAcceptedWithoutChangingInput(String amount)
    {
        var command = command(List.of(line(1, 1, amount, "0"), line(2, 1, "0", amount)));
        assertTrue(new TransactionCommandValidator().validate(command).valid());
        assertEquals(new BigDecimal(amount), command.lines().get(0).debit());
    }

    @ParameterizedTest
    @CsvSource({"0,0,must contain a non-zero", "-1,0,cannot be negative",
            "1,1,cannot contain both", "1.001,0,fractional cents", "1,0,debits must equal credits"})
    void invalidEntryCannotBeSavedEvenWhenOtherLinesAppearValid(String debit, String credit, String message)
    {
        var result = new TransactionCommandValidator().validate(command(List.of(
                line(1, 1, debit, credit), line(2, 1, "0", "2"))));
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains(message)), result.errors().toString());
    }

    @Test
    void fourAllocationsBalanceWithinOneEntryAndOppositeEntryErrorsCannotCancel()
    {
        var valid = command(List.of(line(1, 1, "100", "0"), line(2, 1, "0", "10"),
                line(3, 1, "0", "20"), line(4, 2, "0", "30"), line(5, 2, "0", "40")));
        var validator = new TransactionCommandValidator();
        assertTrue(validator.validate(valid).valid());
        assertFalse(validator.validate(command(List.of(line(1, 1, "100", "0"), line(2, 1, "0", "99.99")))).valid());
        assertFalse(validator.validate(command(List.of(line(1, 1, "99.99", "0"), line(2, 1, "0", "100")))).valid());
    }

    private static TransactionCommand command(List<TransactionLineCommand> lines)
    {
        return new TransactionCommand(LocalDate.of(2026, 3, 14), null, "Manual example", null, lines);
    }
}
