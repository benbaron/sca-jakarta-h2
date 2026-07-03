package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransactionCommandValidatorTest
{
    private final TransactionCommandValidator validator = new TransactionCommandValidator();

    @Test
    public void validate_acceptsBalancedTwoLineCommand()
    {
        TransactionCommand command = new TransactionCommand(
                LocalDate.of(2026, 3, 15),
                10L,
                "Donation deposit",
                20L,
                List.of(
                        new TransactionLineCommand(100L, 1L, null, null, null, new BigDecimal("125.00"), null, false, "Bank"),
                        new TransactionLineCommand(400L, 1L, null, null, null, null, new BigDecimal("125.00"), false, "Income")));

        TransactionValidationResult result = validator.validate(command);

        assertTrue(result.valid(), result.errors().toString());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    public void validate_rejectsOneSidedZeroAndUnbalancedLines()
    {
        TransactionCommand command = new TransactionCommand(
                LocalDate.of(2026, 3, 15),
                null,
                "Invalid",
                null,
                List.of(
                        new TransactionLineCommand(100L, 1L, null, null, null, new BigDecimal("10.00"), new BigDecimal("1.00"), false, "Both"),
                        new TransactionLineCommand(400L, 1L, null, null, null, BigDecimal.ZERO, null, false, "Zero")));

        TransactionValidationResult result = validator.validate(command);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("both debit and credit")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("non-zero debit or credit")));
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("Debits=10.00 Credits=1.00")));
    }
}
