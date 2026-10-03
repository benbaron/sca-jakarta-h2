package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SclxTransactionBudgetExtensionTest
{
    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode document() throws Exception
    {
        return (ObjectNode) mapper.readTree("""
                {"transactions":[{"transactionId":"T1","lines":[
                  {"lineId":"L1","debit":"200","credit":"0"},
                  {"lineId":"L2","debit":"0","credit":"200"}]}],
                 "extensions":{"scaJakartaH2":{"transactionBudgets":{"version":1,
                  "lines":[{"lineId":"L1","categoryCode":"COST"}]}}}}
                """);
    }

    @Test
    void tagsParticipateInTransactionAndSplitIdentityWithoutMutatingSource() throws Exception
    {
        var source = document();
        var tagged = SclxTransactionBudgetExtension.identityDocument(source);
        assertNotEquals(source.path("transactions").get(0), tagged.path("transactions").get(0));
        assertNotEquals(source.path("transactions").get(0).path("lines").get(0),
                tagged.path("transactions").get(0).path("lines").get(0));
        assertEquals(source.path("transactions").get(0).path("lines").get(1),
                tagged.path("transactions").get(0).path("lines").get(1));
        ((ObjectNode) source.path("extensions").path("scaJakartaH2")).remove("transactionBudgets");
        assertTrue(SclxTransactionBudgetExtension.parse(source).isEmpty());
        assertEquals(source, SclxTransactionBudgetExtension.identityDocument(source));
    }

    @Test
    void rejectsUnknownVersionsDuplicateDanglingAndZeroValueRelationships() throws Exception
    {
        var source = document();
        var extension = (ObjectNode) source.path("extensions").path("scaJakartaH2").path("transactionBudgets");
        extension.put("version", 2);
        assertThrows(IllegalStateException.class, () -> SclxTransactionBudgetExtension.parse(source));
        extension.put("version", 1);
        var links = (com.fasterxml.jackson.databind.node.ArrayNode) extension.path("lines");
        links.add(links.get(0).deepCopy());
        assertThrows(IllegalStateException.class, () -> SclxTransactionBudgetExtension.parse(source));
        links.remove(1);
        ((ObjectNode) links.get(0)).put("lineId", "missing");
        assertThrows(IllegalStateException.class, () -> SclxTransactionBudgetExtension.parse(source));
        ((ObjectNode) links.get(0)).put("lineId", "L1");
        ((ObjectNode) source.path("transactions").get(0).path("lines").get(0)).put("debit", "0");
        assertThrows(IllegalStateException.class, () -> SclxTransactionBudgetExtension.parse(source));
    }
}
