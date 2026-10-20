package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.PropertyCustodyEvent;

import static org.junit.jupiter.api.Assertions.*;

class SclxPropertyCustodyTest
{
    @Test void strictVersionOneParsesCorrectionHistoryAndAccountingEvidence() throws Exception
    {
        String original = SclxPortableIdentity.propertyCustodyEvent("TEST", "original");
        String replacement = SclxPortableIdentity.propertyCustodyEvent("TEST", "replacement");
        String item = SclxPortableIdentity.inventoryItem("TEST", "item");
        String json = """
                {"extensions":{"scaJakartaH2":{"propertyCustody":{"version":1,"events":[
                  {"eventId":"%s","inventoryItemId":"%s","eventType":"ASSIGNMENT","eventDate":"2026-02-01","quantity":3,
                   "condition":"GOOD","confirmationStatus":"CONFIRMED","confirmationDate":"2026-02-01","actor":"tester","accountingTreatment":"CUSTODY_ONLY","createdAt":"2026-02-01T00:00:00Z"},
                  {"eventId":"%s","inventoryItemId":"%s","correctionOfEventId":"%s","eventType":"TRANSFER","eventDate":"2026-02-02","quantity":3,
                   "condition":"GOOD","confirmationStatus":"NOT_REQUIRED","actor":"tester","accountingTreatment":"EXPENSED_NO_VALUE","createdAt":"2026-02-02T00:00:00Z"}
                ]}}}}
                """.formatted(original, item, replacement, item, original);
        var values = SclxPropertyCustody.parse(new ObjectMapper().readTree(json));
        assertEquals(2, values.size());
        assertEquals(original, values.get(1).correctionOfEventId());
        assertEquals("EXPENSED_NO_VALUE", values.get(1).accountingTreatment());
        assertEquals(PropertyCustodyEvent.EventType.TRANSFER, values.get(1).eventType());
    }

    @Test void rejectsUnknownFields() throws Exception
    {
        var root = new ObjectMapper().readTree("""
                {"extensions":{"scaJakartaH2":{"propertyCustody":{"version":1,"events":[{"eventId":"x","inventoryItemId":"y","eventType":"ASSIGNMENT","eventDate":"2026-01-01","quantity":1,"condition":"GOOD","confirmationStatus":"NOT_REQUIRED","actor":"a","accountingTreatment":"CUSTODY_ONLY","createdAt":"2026-01-01T00:00:00Z","surprise":true}]}}}}
                """);
        assertThrows(IllegalStateException.class, () -> SclxPropertyCustody.parse(root));
    }
}
