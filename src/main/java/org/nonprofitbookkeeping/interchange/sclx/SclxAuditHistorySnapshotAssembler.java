package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Maps authoritative selected-company factual audit events into SCLX. */
final class SclxAuditHistorySnapshotAssembler
{
    Map<String, Object> assemble(String companyCode, Company company, List<AuditEvent> events)
    {
        Objects.requireNonNull(companyCode, "companyCode");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(events, "events");
        if (!companyCode.equals(company.getCode()))
        {
            throw new IllegalArgumentException("audit-history company code does not match the selected company");
        }

        List<Map<String, Object>> exportedEvents = events.stream()
                .peek(event -> requireOwnership(event, company))
                .sorted(Comparator.comparing(AuditEvent::getOccurredAt)
                        .thenComparing(event -> event.getPortableId().toString()))
                .map(event -> SclxAuditHistoryExtension.eventEntry(
                        SclxPortableIdentity.auditEvent(
                                companyCode,
                                Objects.requireNonNull(event.getPortableId(),
                                        "audit event portableId").toString()),
                        Objects.requireNonNull(event.getOccurredAt(), "audit event occurredAt"),
                        requireText(event.getActor(), "audit event actor"),
                        requireText(event.getActionType(), "audit event actionType"),
                        requireText(event.getEntityType(), "audit event entityType"),
                        event.getEntityId(),
                        requireText(event.getSummary(), "audit event summary"),
                        event.getBeforeValue(),
                        event.getAfterValue(),
                        event.getReason()))
                .toList();
        return SclxAuditHistoryExtension.value(exportedEvents);
    }

    private static void requireOwnership(AuditEvent event, Company company)
    {
        Objects.requireNonNull(event, "audit event");
        if (event.getCompany() != company)
        {
            throw new IllegalArgumentException("audit event is outside the selected company");
        }
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
