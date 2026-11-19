package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SclxJournalInformationTest
{
    private static ObjectNode document() throws Exception
    {
        return (ObjectNode) new ObjectMapper().readTree("""
                {"transactions":[{"transactionId":"t1","transactionDate":"2026-02-01","lines":[]}],
                "extensions":{"scaJakartaH2":{"journalInformation":{"version":1,"transactions":[
                {"transactionId":"t1","transferOrCheckReference":"000042","statementDate":"2026-02-06","incomingDate":"2026-01-30"}]}}}}
                """);
    }

    @Test
    void optionalFactsParticipateInTransactionIdentityWithoutMutatingSourceOrInferringClearing() throws Exception
    {
        var source = document();
        var identity = SclxJournalInformation.identityDocument(source);
        var facts = SclxJournalInformation.parse(source).get("t1");
        assertEquals("000042", facts.transferOrCheckReference());
        assertEquals(LocalDate.of(2026, 1, 30), facts.incomingDate());
        assertFalse(source.path("transactions").get(0).has("_scaJournalInformation"));
        var record = (ObjectNode) source.path("extensions").path("scaJakartaH2").path("journalInformation").path("transactions").get(0);
        record.put("statementDate", "2026-02-07");
        assertNotEquals(identity.path("transactions"), SclxJournalInformation.identityDocument(source).path("transactions"));
        ((ObjectNode) source.path("extensions").path("scaJakartaH2")).remove("journalInformation");
        assertTrue(SclxJournalInformation.parse(source).isEmpty());
    }

    @Test
    void malformedVersionsFieldsDatesAndDanglingOrDuplicateTransactionsReject() throws Exception
    {
        for (String mutation : List.of("version", "overflow", "field", "date", "reference", "type", "dangling", "duplicate"))
        {
            var source = document();
            var extension = (ObjectNode) source.path("extensions").path("scaJakartaH2").path("journalInformation");
            var records = (ArrayNode) extension.path("transactions");
            var fact = (ObjectNode) records.get(0);
            switch (mutation)
            {
                case "version" -> extension.put("version", 2);
                case "overflow" -> extension.put("version", 4294967297L);
                case "field" -> fact.put("bankCleared", true);
                case "date" -> fact.put("statementDate", "2026-02-30");
                case "reference" -> fact.put("transferOrCheckReference", "x".repeat(201));
                case "type" -> fact.put("transferOrCheckReference", 42);
                case "dangling" -> fact.put("transactionId", "other-company-transaction");
                case "duplicate" -> records.add(fact.deepCopy());
                default -> throw new AssertionError(mutation);
            }
            assertThrows(IllegalStateException.class, () -> SclxJournalInformation.parse(source), mutation);
        }
    }
}
