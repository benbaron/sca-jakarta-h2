package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxJsonSerializerTest
{
    private final SclxJsonSerializer serializer = new SclxJsonSerializer();

    @Test
    void emitsByteIdenticalGovernedJsonWithFixedOrderingAndPlainDecimals() throws Exception
    {
        SclxExportDocument document = document();

        byte[] first = serializer.serialize(document);
        byte[] second = serializer.serialize(document);
        String json = new String(first, java.nio.charset.StandardCharsets.UTF_8);
        JsonNode root = new ObjectMapper().readTree(first);

        assertEquals(List.of("format", "version", "exportedAt", "organization", "chartOfAccounts",
                        "funds", "budgets", "transactions", "extensions"),
                iterable(root.fieldNames()));
        assertEquals("1000", root.path("chartOfAccounts").get(0).path("openingBalance").textValue());
        assertEquals("0", root.path("transactions").get(0).path("lines").get(0).path("debit").textValue());
        assertEquals("25", root.path("transactions").get(0).path("lines").get(0).path("credit").textValue());
        assertEquals(List.of("activities", "alpha", "supplementalDetails", "zeta"),
                iterable(root.path("extensions").path("scaJakartaH2").fieldNames()));
        JsonNode activity = root.path("extensions").path("scaJakartaH2").path("activities").get(0);
        assertEquals("activity:TEST:EVENT", activity.path("activityId").textValue());
        assertEquals("EVENT", activity.path("code").textValue());
        assertTrue(activity.path("active").booleanValue());
        JsonNode supplemental = root.path("extensions").path("scaJakartaH2")
                .path("supplementalDetails").get(0);
        assertEquals(transactionId(), supplemental.path("transactionId").textValue());
        assertEquals("125.5", supplemental.path("amount").textValue());
        assertEquals("2026-08-15", supplemental.path("dueDate").textValue());
        assertEquals("2026-07-27T02:00:00Z", root.path("exportedAt").textValue());
        assertTrue(json.endsWith("\n"));
        assertFalse(json.contains("\r"));
        assertEquals(new String(first, java.nio.charset.StandardCharsets.UTF_8),
                new String(second, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void rejectsExtensionValuesWithoutAGovernedDeterministicEncoding()
    {
        SclxExportDocument base = document();
        SclxExportDocument invalid = new SclxExportDocument(
                base.format(),
                base.version(),
                base.exportedAt(),
                base.organization(),
                base.chartOfAccounts(),
                base.funds(),
                base.budgets(),
                base.transactions(),
                new SclxExportDocument.Extensions(1, Map.of("unsupported", 1.25d)));

        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(invalid));
    }

    private static List<String> iterable(java.util.Iterator<String> iterator)
    {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static String transactionId()
    {
        return "transaction:TEST:11111111-1111-1111-1111-111111111111";
    }

    static SclxExportDocument document()
    {
        String cashId = "account:TEST:1010";
        String expenseId = "account:TEST:6100";
        String fundId = "fund:TEST:GENERAL";
        String transactionId = transactionId();
        LinkedHashMap<String, Object> extensionValues = new LinkedHashMap<>();
        extensionValues.put("zeta", "last");
        extensionValues.put("alpha", new BigDecimal("1.2300"));
        extensionValues.put(SclxActivityExtension.KEY, List.of(SclxActivityExtension.entry(
                "activity:TEST:EVENT", "EVENT", "Annual Event", true)));
        extensionValues.put(SclxSupplementalDetailExtension.KEY, List.of(
                SclxSupplementalDetailExtension.entry(
                        SclxPortableIdentity.supplementalDetail(transactionId, 1),
                        transactionId,
                        0,
                        "RECEIVABLE",
                        "line 1",
                        "Donor",
                        "Pledge receivable",
                        "INV-100",
                        new BigDecimal("125.5000"),
                        LocalDate.of(2026, 8, 15),
                        null,
                        null,
                        "Expected payment")));

        return SclxExportDocument.version13(
                Instant.parse("2026-07-27T02:00:00Z"),
                new SclxExportDocument.Organization(
                        "organization:TEST", "TEST", "Test Company", "USD", LocalDate.of(2026, 1, 1)),
                List.of(
                        new SclxExportDocument.Account(
                                expenseId, "6100", "Supplies", "EXPENSE", null, "DEBIT", null,
                                "USD", new BigDecimal("0.0000"), true, true),
                        new SclxExportDocument.Account(
                                cashId, "1010", "Cash", "ASSET", "BANK", "DEBIT", null,
                                "USD", new BigDecimal("1000.0000"), true, true)),
                List.of(new SclxExportDocument.Fund(
                        fundId, "GENERAL", "General Fund", "UNRESTRICTED", null,
                        true, null, null, null)),
                List.of(new SclxExportDocument.Budget(
                        "budget:TEST:2026:ADOPTED", "Operating", 2026, "ADOPTED", true,
                        List.of(new SclxExportDocument.BudgetLine(
                                "budget-line:1", null, fundId, "SUPPLIES", "2026-07",
                                new BigDecimal("500.0000"))))),
                List.of(new SclxExportDocument.Transaction(
                        transactionId,
                        LocalDate.of(2026, 7, 1),
                        "Office supplies",
                        null,
                        "ENTERED",
                        null,
                        null,
                        List.of(
                                new SclxExportDocument.TransactionLine(
                                        "transaction-line:2", expenseId, fundId, null, null,
                                        new BigDecimal("25.0000"), BigDecimal.ZERO, null),
                                new SclxExportDocument.TransactionLine(
                                        "transaction-line:1", cashId, fundId, null, null,
                                        BigDecimal.ZERO, new BigDecimal("25.0000"), null)))),
                new SclxExportDocument.Extensions(1, extensionValues));
    }
}
