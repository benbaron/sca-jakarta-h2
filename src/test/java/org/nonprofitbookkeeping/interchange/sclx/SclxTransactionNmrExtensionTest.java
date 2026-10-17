package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SclxTransactionNmrExtensionTest
{
    private ObjectNode document() throws Exception
    {
        return (ObjectNode) new ObjectMapper().readTree("""
                {"transactions":[{"transactionId":"T1","lines":[
                  {"lineId":"L1","debit":"100","credit":"0"},
                  {"lineId":"L2","debit":"0","credit":"100"}]}],
                 "extensions":{"scaJakartaH2":{"transactionNmr":{"version":1,"lineIds":["L2"]}}}}
                """);
    }

    @Test
    void flagsAffectOnlyMarkedLineAndParentIdentityWithoutMutatingInput() throws Exception
    {
        var source = document();
        var identity = SclxTransactionNmrExtension.identityDocument(source);
        assertEquals(Set.of("L2"), SclxTransactionNmrExtension.parse(source));
        assertEquals(source.path("transactions").get(0).path("lines").get(0),
                identity.path("transactions").get(0).path("lines").get(0));
        assertNotEquals(source.path("transactions").get(0), identity.path("transactions").get(0));
        assertFalse(source.path("transactions").get(0).path("lines").get(1).has("_scaNmr"));
        assertTrue(identity.path("transactions").get(0).path("lines").get(1).path("_scaNmr").booleanValue());
        ((ObjectNode) source.path("extensions").path("scaJakartaH2")).remove("transactionNmr");
        assertTrue(SclxTransactionNmrExtension.parse(source).isEmpty());
        assertEquals(source, SclxTransactionNmrExtension.identityDocument(source));
    }

    @Test
    void rejectsMalformedVersionsShapesAndUnknownFacts() throws Exception
    {
        for (String invalid : List.of("null", "[]", "{}", "{\"version\":2,\"lineIds\":[]}",
                "{\"version\":4294967297,\"lineIds\":[]}", "{\"version\":\"1\",\"lineIds\":[]}",
                "{\"version\":1,\"lineIds\":null}", "{\"version\":1,\"lineIds\":[],\"future\":true}"))
        {
            var root = document();
            ((ObjectNode) root.path("extensions").path("scaJakartaH2"))
                    .set("transactionNmr", new ObjectMapper().readTree(invalid));
            assertThrows(IllegalStateException.class, () -> SclxTransactionNmrExtension.parse(root), invalid);
        }
        for (String invalid : List.of("[\"L2\",\"L2\"]", "[\"missing\"]", "[null]", "[true]", "[1]", "[\"\"]", "[\" L2 \"]"))
        {
            var root = document();
            ((ObjectNode) root.path("extensions").path("scaJakartaH2").path("transactionNmr"))
                    .set("lineIds", new ObjectMapper().readTree(invalid));
            assertThrows(IllegalStateException.class, () -> SclxTransactionNmrExtension.parse(root), invalid);
        }
    }

    @Test
    void rejectsZeroTwoSidedNegativeAmbiguousAndReservedIdentityLines() throws Exception
    {
        for (String[] amounts : List.of(new String[]{"0", "0"}, new String[]{"1", "1"},
                new String[]{"-1", "0"}, new String[]{"invalid", "10"}))
        {
            var root = document();
            var line = (ObjectNode) root.path("transactions").get(0).path("lines").get(1);
            line.put("debit", amounts[0]);
            line.put("credit", amounts[1]);
            assertThrows(IllegalStateException.class, () -> SclxTransactionNmrExtension.parse(root));
        }
        var duplicate = document();
        var lines = (ArrayNode) duplicate.path("transactions").get(0).path("lines");
        lines.add(lines.get(1).deepCopy());
        assertThrows(IllegalStateException.class, () -> SclxTransactionNmrExtension.parse(duplicate));
        var root = document();
        ((ObjectNode) root.path("transactions").get(0).path("lines").get(0)).put("_scaNmr", true);
        var reserved = root;
        assertThrows(IllegalStateException.class, () -> SclxTransactionNmrExtension.identityDocument(reserved));
    }
}
