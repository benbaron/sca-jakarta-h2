package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Headless unit tests: no JavaFX toolkit, JPA bootstrap or database required. */
class ExchequerCandidateValidationTest
{
    @Test
    void bankAndFourAllocationsMustHaveAnExactZeroDebitMinusCredit()
    {
        var candidate = candidate(line("20", "0"), line("0", "20"), line("0", "30"), line("25", "0"), line("5", "0"));
        assertTrue(ExchequerImportService.validateCandidate(candidate).valid());
        var bad = candidate(line("20.01", "0"), line("0", "20"), line("0", "30"), line("25", "0"), line("5", "0"));
        assertFalse(ExchequerImportService.validateCandidate(bad).valid(), "Even a one-cent imbalance must fail");
    }

    @Test
    void individuallyBalancedValuesThatRequireStorageRoundingAreRejected()
    {
        var candidate = candidate(line("0.005", "0"), line("0.005", "0"), line("0", "0.01"));
        assertEquals(0, new BigDecimal("0.005").add(new BigDecimal("0.005")).compareTo(new BigDecimal("0.01")), "Input arithmetic alone is balanced");
        var validation = ExchequerImportService.validateCandidate(candidate);
        assertFalse(validation.valid());
        assertTrue(validation.errors().stream().anyMatch(error -> error.contains("fractional cents")));
    }

    @Test
    void canonicalJournalRejectsFractionalCentsOnEitherSideEvenWhenBalanced()
    {
        for (String amount : List.of("10.001", "0.005", "10.9999"))
        {
            var result = new TransactionCommandValidator().validate(candidate(line(amount, "0"), line("0", amount)));
            assertFalse(result.valid());
            assertTrue(result.errors().stream().anyMatch(error -> error.contains("two decimal places")));
        }
        assertTrue(new TransactionCommandValidator().validate(candidate(line("10.1200", "0"), line("0", "10.12"))).valid());
    }

    @Test
    void trailingZerosAreAcceptedWhenAllValuesFitTheLedgerExactly()
    {
        assertTrue(ExchequerImportService.validateCandidate(candidate(line("10.10000", "0"), line("0", "10.1"))).valid());
    }

    @Test
    void balancedTotalsCannotBypassMaximumLedgerPrecisionOrMissingCandidateValidation()
    {
        assertFalse(ExchequerImportService.validateCandidate(candidate(line("1000000000000000", "0"), line("0", "1000000000000000"))).valid());
        assertFalse(ExchequerImportService.validateCandidate(null).valid());
    }

    private static TransactionCommand candidate(TransactionLineCommand... lines)
    {
        return new TransactionCommand(LocalDate.of(2026, 2, 1), null, "Synthetic candidate", null, List.of(lines));
    }

    private static TransactionLineCommand line(String debit, String credit)
    {
        return new TransactionLineCommand(1L, 1L, null, null, null, new BigDecimal(debit), new BigDecimal(credit), false, null);
    }
}
