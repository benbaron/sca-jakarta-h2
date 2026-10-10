package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SclxPaymentReferencesTest
{
    private ObjectNode document() throws Exception
    {
        return (ObjectNode) new ObjectMapper().readTree("""
                {"chartOfAccounts":[{"accountId":"bank1","type":"BANK"}],"transactions":[{"transactionId":"t1","lines":[{"lineId":"l1","accountId":"bank1","debit":"0","credit":"10"}]}],
                 "extensions":{"scaJakartaH2":{"paymentReferences":{"version":1,"lines":[
                 {"lineId":"l1","method":"CHECK","reference":"000123","issuedOn":"2026-02-01","deliveredOn":"2026-02-02"}]}}}}
                """);
    }
    @Test
    void factsParticipateInIdentityWithoutChangingSourceOrInferringMemo() throws Exception
    {
        var root = document();
        var first = SclxPaymentReferences.identityDocument(root);
        assertFalse(root.path("transactions").get(0).path("lines").get(0).has("_scaPaymentReference"));
        assertEquals("000123", SclxPaymentReferences.parse(root).get("l1").reference());
        ((ObjectNode) root.path("extensions").path("scaJakartaH2").path("paymentReferences").path("lines").get(0)).put("reference", "000124");
        assertNotEquals(first, SclxPaymentReferences.identityDocument(root));
        ((ObjectNode) root.path("extensions").path("scaJakartaH2")).remove("paymentReferences");
        assertTrue(SclxPaymentReferences.parse(root).isEmpty());
    }
    @Test
    void invalidDatesUnknownMethodsDanglingAndDuplicateLinesFail() throws Exception
    {
        for (String field : new String[] {"lineId", "method", "deliveredOn"})
        {
            var root = document();
            var fact = (ObjectNode) root.path("extensions").path("scaJakartaH2").path("paymentReferences").path("lines").get(0);
            fact.put(field, switch (field) { case "lineId" -> "missing"; case "method" -> "WIRE_UNKNOWN"; default -> "2026-01-01"; });
            assertThrows(IllegalStateException.class, () -> SclxPaymentReferences.parse(root));
        }
        var root = document();
        var lines = (com.fasterxml.jackson.databind.node.ArrayNode) root.path("extensions").path("scaJakartaH2").path("paymentReferences").path("lines");
        lines.add(lines.get(0).deepCopy());
        assertThrows(IllegalStateException.class, () -> SclxPaymentReferences.parse(root));
    }
    @Test
    void unsupportedVersionExtraFieldsAndZeroPostingCannotSilentlyDisappear() throws Exception
    {
        var root = document();
        var extension = (ObjectNode) root.path("extensions").path("scaJakartaH2").path("paymentReferences");
        extension.put("version", 4294967297L);
        assertThrows(IllegalStateException.class, () -> SclxPaymentReferences.parse(root));
        extension.put("version", 1); extension.put("futureFact", "unsupported");
        assertThrows(IllegalStateException.class, () -> SclxPaymentReferences.parse(root));
        extension.remove("futureFact");
        ((ObjectNode) root.path("transactions").get(0).path("lines").get(0)).put("credit", "0");
        assertThrows(IllegalStateException.class, () -> SclxPaymentReferences.parse(root));
    }
}
