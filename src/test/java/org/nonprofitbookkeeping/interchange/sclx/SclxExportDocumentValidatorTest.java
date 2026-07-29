package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxExportDocumentValidatorTest
{
    private final SclxExportDocumentValidator validator = new SclxExportDocumentValidator();

    @Test
    void acceptsBalancedResolvedSnapshot()
    {
        assertDoesNotThrow(() -> validator.validate(document(
                List.of(line("line-1", "acct-expense", "fund-general", "25.00", "0"),
                        line("line-2", "acct-cash", "fund-general", "0", "25.00")))));
    }


    @Test
    void acceptsResolvedActivityReference()
    {
        assertDoesNotThrow(() -> validator.validate(document(
                List.of(line("line-1", "acct-expense", "fund-general", "activity:TEST:EVENT", "25.00", "0"),
                        line("line-2", "acct-cash", "fund-general", null, "0", "25.00")),
                Map.of(SclxActivityExtension.KEY, List.of(SclxActivityExtension.entry(
                        "activity:TEST:EVENT", "EVENT", "Annual Event", true))))));
    }

    @Test
    void rejectsMissingActivityReference()
    {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(document(
                List.of(line("line-1", "acct-expense", "fund-general", "activity:TEST:MISSING", "25.00", "0"),
                        line("line-2", "acct-cash", "fund-general", null, "0", "25.00")),
                Map.of(SclxActivityExtension.KEY, List.of(SclxActivityExtension.entry(
                        "activity:TEST:EVENT", "EVENT", "Annual Event", true))))));
    }

    @Test
    void rejectsDuplicateActivityPortableIdentity()
    {
        Map<String, Object> activity = SclxActivityExtension.entry(
                "activity:TEST:EVENT", "EVENT", "Annual Event", true);
        assertThrows(IllegalArgumentException.class, () -> validator.validate(document(
                List.of(line("line-1", "acct-expense", "fund-general", "25.00", "0"),
                        line("line-2", "acct-cash", "fund-general", "0", "25.00")),
                Map.of(SclxActivityExtension.KEY, List.of(activity, activity)))));
    }


    @Test
    void acceptsResolvedCounterpartyAndMerchantReferences()
    {
        String counterpartyId = "counterparty:TEST:11111111-1111-1111-1111-111111111111";
        String merchantId = "merchant:TEST:22222222-2222-2222-2222-222222222222";
        Map<String, Object> partyValue = SclxPartyExtension.value(
                List.of(SclxPartyExtension.counterpartyEntry(
                        counterpartyId, "Vendor", "ORG", null, null, null, true)),
                List.of(SclxPartyExtension.merchantEntry(
                        merchantId, "Store", null, true)),
                List.of(SclxPartyExtension.transactionLineMerchantEntry("line-1", merchantId)));

        assertDoesNotThrow(() -> validator.validate(document(
                List.of(line("line-1", "acct-expense", "fund-general", null, counterpartyId, "25.00", "0"),
                        line("line-2", "acct-cash", "fund-general", null, counterpartyId, "0", "25.00")),
                Map.of(SclxPartyExtension.KEY, partyValue))));
    }

    @Test
    void rejectsMissingCounterpartyReference()
    {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(document(
                List.of(line("line-1", "acct-expense", "fund-general", null,
                                "counterparty:TEST:MISSING", "25.00", "0"),
                        line("line-2", "acct-cash", "fund-general", null, null, "0", "25.00")))));
    }

    @Test
    void rejectsMissingMerchantReference()
    {
        Map<String, Object> partyValue = SclxPartyExtension.value(
                List.of(),
                List.of(),
                List.of(SclxPartyExtension.transactionLineMerchantEntry(
                        "line-1", "merchant:TEST:MISSING")));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(document(
                List.of(line("line-1", "acct-expense", "fund-general", "25.00", "0"),
                        line("line-2", "acct-cash", "fund-general", "0", "25.00")),
                Map.of(SclxPartyExtension.KEY, partyValue))));
    }


    @Test
    void acceptsResolvedFixedAssetAndDepreciationRunReferences()
    {
        Map<String, Object> fixedAssets = SclxFixedAssetsExtension.value(
                List.of(SclxFixedAssetsExtension.assetEntry(
                        "fixed-asset:TEST:A1", "Laptop", LocalDate.of(2026, 1, 2),
                        new BigDecimal("1200.00"), new BigDecimal("100.00"), 36,
                        "STRAIGHT_LINE", BigDecimal.ZERO, "ACTIVE", "Office asset",
                        "account:1010", "account:1010", "account:6100", "fund-general",
                        Instant.parse("2026-01-02T12:00:00Z"), Instant.parse("2026-01-02T12:00:00Z"))),
                List.of(SclxFixedAssetsExtension.depreciationRunEntry(
                        "fixed-asset-depreciation-run:TEST:R1", "fixed-asset:TEST:A1",
                        LocalDate.of(2026, 2, 28), new BigDecimal("30.56"),
                        "transaction:TX-1", "February", Instant.parse("2026-02-28T12:00:00Z"))));

        assertDoesNotThrow(() -> validator.validate(document(
                List.of(line("line-1", "acct-expense", "fund-general", "25.00", "0"),
                        line("line-2", "acct-cash", "fund-general", "0", "25.00")),
                Map.of(SclxFixedAssetsExtension.KEY, fixedAssets))));
    }

    @Test
    void rejectsDepreciationRunWithoutExportedTransaction()
    {
        Map<String, Object> fixedAssets = SclxFixedAssetsExtension.value(
                List.of(SclxFixedAssetsExtension.assetEntry(
                        "fixed-asset:TEST:A1", "Laptop", LocalDate.of(2026, 1, 2),
                        new BigDecimal("1200.00"), BigDecimal.ZERO, 36,
                        "STRAIGHT_LINE", BigDecimal.ZERO, "ACTIVE", null,
                        "account:1010", "account:1010", "account:6100", "fund-general",
                        Instant.parse("2026-01-02T12:00:00Z"), Instant.parse("2026-01-02T12:00:00Z"))),
                List.of(SclxFixedAssetsExtension.depreciationRunEntry(
                        "fixed-asset-depreciation-run:TEST:R1", "fixed-asset:TEST:A1",
                        LocalDate.of(2026, 2, 28), new BigDecimal("30.56"),
                        "transaction:MISSING", null, Instant.parse("2026-02-28T12:00:00Z"))));

        assertThrows(IllegalArgumentException.class, () -> validator.validate(document(
                List.of(line("line-1", "acct-expense", "fund-general", "25.00", "0"),
                        line("line-2", "acct-cash", "fund-general", "0", "25.00")),
                Map.of(SclxFixedAssetsExtension.KEY, fixedAssets))));
    }

    @Test
    void rejectsUnbalancedTransaction()
    {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(document(
                        List.of(line("line-1", "acct-expense", "fund-general", "25.00", "0"),
                                line("line-2", "acct-cash", "fund-general", "0", "20.00")))));

        assertTrue(exception.getMessage().contains("not balanced"));
    }

    @Test
    void rejectsMissingAccountReference()
    {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(document(
                        List.of(line("line-1", "acct-missing", "fund-general", "25.00", "0"),
                                line("line-2", "acct-cash", "fund-general", "0", "25.00")))));
    }

    @Test
    void rejectsDuplicatePortableLineIdentity()
    {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(document(
                        List.of(line("line-1", "acct-expense", "fund-general", "25.00", "0"),
                                line("line-1", "acct-cash", "fund-general", "0", "25.00")))));
    }

    private static SclxExportDocument document(List<SclxExportDocument.TransactionLine> lines)
    {
        return document(lines, Map.of());
    }

    private static SclxExportDocument document(
            List<SclxExportDocument.TransactionLine> lines,
            Map<String, Object> extensionValues)
    {
        return SclxExportDocument.version13(
                Instant.parse("2026-07-26T18:00:00Z"),
                new SclxExportDocument.Organization(
                        "company:TEST", "TEST", "Test Company", "USD", LocalDate.of(2026, 1, 1)),
                List.of(
                        new SclxExportDocument.Account(
                                "account:1010", "1010", "Cash", "ASSET", null, "DEBIT", null,
                                "USD", BigDecimal.ZERO, true, true),
                        new SclxExportDocument.Account(
                                "account:6100", "6100", "Expense", "EXPENSE", null, "DEBIT", null,
                                "USD", BigDecimal.ZERO, true, true)),
                List.of(new SclxExportDocument.Fund(
                        "fund-general", "GENERAL", "General Fund", "UNRESTRICTED", null,
                        true, null, null, null)),
                List.of(),
                List.of(new SclxExportDocument.Transaction(
                        "transaction:TX-1", LocalDate.of(2026, 7, 1), "Test transaction", null, null, lines)),
                new SclxExportDocument.Extensions(1, extensionValues));
    }

    private static SclxExportDocument.TransactionLine line(
            String id, String accountId, String fundId, String debit, String credit)
    {
        return line(id, accountId, fundId, null, debit, credit);
    }

    private static SclxExportDocument.TransactionLine line(
            String id,
            String accountId,
            String fundId,
            String activityId,
            String debit,
            String credit)
    {
        return line(id, accountId, fundId, activityId, null, debit, credit);
    }

    private static SclxExportDocument.TransactionLine line(
            String id,
            String accountId,
            String fundId,
            String activityId,
            String counterpartyId,
            String debit,
            String credit)
    {
        String resolvedAccountId = switch (accountId)
        {
            case "acct-cash" -> "account:1010";
            case "acct-expense" -> "account:6100";
            default -> accountId;
        };
        return new SclxExportDocument.TransactionLine(
                id, resolvedAccountId, fundId, activityId, counterpartyId,
                new BigDecimal(debit), new BigDecimal(credit), null);
    }
}
