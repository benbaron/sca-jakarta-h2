package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxAuditHistoryExportIntegrationTest
{
    @Test
    void mapsValidatesAndCountsCompanyOwnedAuditFacts()
    {
        Company company = company("TEST");
        AuditEvent event = event(company);

        Map<String, Object> value = new SclxAuditHistorySnapshotAssembler()
                .assemble("TEST", company, List.of(event));
        SclxExportDocument document = document(value);

        new SclxExportDocumentValidator().validate(document);
        SclxAuditHistoryExtension.EventEntry exported =
                SclxAuditHistoryExtension.data(document.extensions()).events().get(0);
        assertEquals(SclxPortableIdentity.auditEvent("TEST", event.getPortableId().toString()),
                exported.auditEventId());
        assertEquals("treasurer", exported.actor());
        assertEquals("UPDATED", exported.actionType());
        assertEquals("Transaction", exported.entityType());
        assertEquals("txn-1", exported.entityId());
        assertEquals("Corrected transaction memo", exported.summary());
        assertEquals("old memo", exported.beforeValue());
        assertEquals("new memo", exported.afterValue());
        assertEquals("clerical correction", exported.reason());

        SclxExportCounts counts = SclxExportCounts.from(document, 0L, 0L);
        assertEquals(1L, counts.auditEvents());
        assertEquals(2L, counts.totalEntities());
        assertTrue(SclxExportSection.AUDIT_HISTORY.includedByCurrentSnapshot());
        assertFalse(SclxExportSection.AUDIT_HISTORY.deferred());
    }

    @Test
    void rejectsCrossCompanyAndDuplicateAuditFacts()
    {
        Company selected = company("TEST");
        AuditEvent foreign = event(company("OTHER"));
        assertThrows(IllegalArgumentException.class,
                () -> new SclxAuditHistorySnapshotAssembler().assemble("TEST", selected, List.of(foreign)));

        Map<String, Object> entry = SclxAuditHistoryExtension.eventEntry(
                SclxPortableIdentity.auditEvent("TEST", "11111111-1111-1111-1111-111111111111"),
                Instant.EPOCH, "actor", "UPDATED", "Transaction", "txn-1", "summary",
                null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> new SclxExportDocumentValidator().validate(
                        document(SclxAuditHistoryExtension.value(List.of(entry, entry)))));
    }

    private static Company company(String code)
    {
        Company company = new Company();
        company.setCode(code);
        company.setDisplayName(code + " Company");
        return company;
    }

    private static AuditEvent event(Company company)
    {
        AuditEvent event = new AuditEvent();
        event.setCompany(company);
        event.setActor("treasurer");
        event.setActionType("UPDATED");
        event.setEntityType("Transaction");
        event.setEntityId("txn-1");
        event.setSummary("Corrected transaction memo");
        event.setBeforeValue("old memo");
        event.setAfterValue("new memo");
        event.setReason("clerical correction");
        return event;
    }

    private static SclxExportDocument document(Map<String, Object> auditHistory)
    {
        return new SclxExportDocument(
                "SCLX", "1.3", Instant.EPOCH,
                new SclxExportDocument.Organization(
                        SclxPortableIdentity.organization("TEST"), "TEST", "Test", "USD",
                        LocalDate.of(2026, 1, 1)),
                List.of(), List.of(), List.of(), List.of(),
                new SclxExportDocument.Extensions(1, Map.of(
                        SclxAuditHistoryExtension.KEY, auditHistory)));
    }
}
