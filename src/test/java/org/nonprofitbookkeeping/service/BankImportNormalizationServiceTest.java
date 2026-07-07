package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankImportNormalizationServiceTest
{
    private final BankImportNormalizationService service = new BankImportNormalizationService();

    @Test
    public void normalizesExternalIdsDatesAndStableFingerprints()
    {
        BankTransactionRecord first = new BankTransactionRecord(" fit-1 ", "20260315000000", new BigDecimal("-25.7500"), "debit", " Vendor ", "Office  supplies");
        BankTransactionRecord sameContent = new BankTransactionRecord("", "20260315000000", new BigDecimal("-25.75"), "DEBIT", "Vendor", "Office supplies");
        BankImportNormalizationService.BankImportNormalizationResult result = service.normalize(List.of(first, sameContent), BankImportNormalizationService.DuplicateContext.empty());

        assertEquals("FIT-1", result.lines().get(0).sourceTransactionId());
        assertEquals(LocalDate.of(2026, 3, 15), result.lines().get(0).transactionDate());
        assertEquals(result.lines().get(0).deterministicFingerprint(), result.lines().get(1).deterministicFingerprint());
        assertFalse(result.lines().get(0).hasErrors());
    }

    @Test
    public void flagsExactDuplicatesByExternalIdAndFingerprintFallback()
    {
        List<BankTransactionRecord> records = List.of(
                new BankTransactionRecord("FIT-1", "20260315000000", new BigDecimal("10.00"), "CREDIT", "Donor", "Gift"),
                new BankTransactionRecord("fit-1", "20260316000000", new BigDecimal("11.00"), "CREDIT", "Other", "Gift"),
                new BankTransactionRecord("", "20260317000000", new BigDecimal("12.00"), "DEBIT", "Store", "Snacks"),
                new BankTransactionRecord("", "20260317000000", new BigDecimal("12.0"), "debit", "Store", "Snacks"));

        BankImportNormalizationService.BankImportNormalizationResult result = service.normalize(records, BankImportNormalizationService.DuplicateContext.empty());

        assertFalse(result.lines().get(0).exactDuplicate());
        assertTrue(result.lines().get(1).exactDuplicate());
        assertFalse(result.lines().get(2).exactDuplicate());
        assertTrue(result.lines().get(3).exactDuplicate());
        assertEquals("EXACT_DUPLICATE", result.lines().get(1).issues().get(0).code());
        assertEquals("EXACT_DUPLICATE", result.lines().get(3).issues().get(0).code());
    }

    @Test
    public void flagsKnownExactDuplicatesAndProbableDuplicateWarnings()
    {
        BankImportNormalizationService.BankImportNormalizationResult seed = service.normalize(
                List.of(new BankTransactionRecord("", "20260317000000", new BigDecimal("12.00"), "DEBIT", "Store", "Snacks")),
                BankImportNormalizationService.DuplicateContext.empty());
        String knownFingerprint = seed.lines().get(0).deterministicFingerprint();

        BankImportNormalizationService.DuplicateContext context = new BankImportNormalizationService.DuplicateContext(
                Set.of("KNOWN-FIT"),
                Set.of(knownFingerprint),
                List.of(new BankImportNormalizationService.ProbableDuplicateCandidate(
                        LocalDate.of(2026, 3, 18), new BigDecimal("-5.00"), "Cafe", "Lunch", 2)));

        BankImportNormalizationService.BankImportNormalizationResult result = service.normalize(List.of(
                new BankTransactionRecord("known-fit", "20260318000000", new BigDecimal("99.00"), "CREDIT", "Known", "Existing"),
                new BankTransactionRecord("", "20260317000000", new BigDecimal("12.0"), "debit", "Store", "Snacks"),
                new BankTransactionRecord("", "20260319000000", new BigDecimal("-5.00"), "DEBIT", "Cafe", "Lunch")), context);

        assertTrue(result.lines().get(0).exactDuplicate());
        assertTrue(result.lines().get(1).exactDuplicate());
        assertTrue(result.lines().get(2).probableDuplicate());
        assertEquals("PROBABLE_DUPLICATE", result.lines().get(2).issues().get(0).code());
    }

    @Test
    public void recordsRowLevelErrorsForBadDateAndZeroAmount()
    {
        BankImportNormalizationService.BankImportNormalizationResult result = service.normalize(
                List.of(new BankTransactionRecord("", "bad", BigDecimal.ZERO, "DEBIT", "Vendor", "Memo")),
                BankImportNormalizationService.DuplicateContext.empty());

        assertTrue(result.lines().get(0).hasErrors());
        assertEquals(2, result.lines().get(0).issues().size());
        assertNotEquals("", result.lines().get(0).deterministicFingerprint());
        assertEquals("INVALID_DATE", result.lines().get(0).issues().get(0).code());
        assertEquals("INVALID_AMOUNT", result.lines().get(0).issues().get(1).code());
    }
}
