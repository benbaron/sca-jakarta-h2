package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Canonical write boundary for selected-company factual audit history. */
public final class AuditHistoryService
{
    private static final int MAX_LOB_TEXT = 1_048_576;

    /**
     * Restores already-authoritative factual history inside a caller-owned
     * interchange transaction without replaying the underlying business commands.
     */
    public ImportedAuditHistory importForInterchange(
            EntityManager em,
            Company company,
            List<AuditEventImport> events)
    {
        Objects.requireNonNull(em, "em");
        Company owner = Objects.requireNonNull(company, "company");
        List<AuditEventImport> source = List.copyOf(Objects.requireNonNull(events, "events"));
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Audit-history interchange import requires an active transaction");
        }
        if (!em.contains(owner) || owner.getId() == null)
        {
            throw new IllegalArgumentException("Audit-history interchange company must be managed and persisted");
        }

        Set<UUID> portableIds = source.stream()
                .map(AuditEventImport::portableId)
                .collect(Collectors.toSet());
        if (portableIds.size() != source.size())
        {
            throw new IllegalArgumentException("Audit-history interchange import contains duplicate portable IDs");
        }
        if (!portableIds.isEmpty())
        {
            long existing = em.createQuery(
                            "select count(a) from AuditEvent a where a.portableId in :portableIds", Long.class)
                    .setParameter("portableIds", portableIds)
                    .getSingleResult();
            if (existing != 0L)
            {
                throw new IllegalStateException("Audit-history interchange portable identity already exists");
            }
        }

        Map<String, AuditEvent> imported = new LinkedHashMap<>();
        for (AuditEventImport value : source)
        {
            if (imported.containsKey(value.externalId()))
            {
                throw new IllegalArgumentException(
                        "Duplicate audit-history external identity: " + value.externalId());
            }
            AuditEvent event = new AuditEvent();
            event.initializeImportMetadata(value.portableId(), value.occurredAt());
            event.setCompany(owner);
            event.setActor(requireText(value.actor(), "actor", 200));
            event.setActionType(requireText(value.actionType(), "actionType", 80));
            event.setEntityType(requireText(value.entityType(), "entityType", 120));
            event.setEntityId(optionalText(value.entityId(), "entityId", 120));
            event.setSummary(requireText(value.summary(), "summary", 500));
            event.setBeforeValue(optionalText(value.beforeValue(), "beforeValue", MAX_LOB_TEXT));
            event.setAfterValue(optionalText(value.afterValue(), "afterValue", MAX_LOB_TEXT));
            event.setReason(optionalText(value.reason(), "reason", 1000));
            em.persist(event);
            imported.put(value.externalId(), event);
        }
        return new ImportedAuditHistory(imported);
    }

    private static String requireText(String value, String field, int limit)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException("Audit-event " + field + " is required");
        }
        return bounded(value, field, limit);
    }

    private static String optionalText(String value, String field, int limit)
    {
        return value == null ? null : bounded(value, field, limit);
    }

    private static String bounded(String value, String field, int limit)
    {
        if (value.codePointCount(0, value.length()) > limit)
        {
            throw new IllegalArgumentException(
                    "Audit-event " + field + " exceeds " + limit + " characters");
        }
        return value;
    }

    public record AuditEventImport(
            String externalId,
            UUID portableId,
            Instant occurredAt,
            String actor,
            String actionType,
            String entityType,
            String entityId,
            String summary,
            String beforeValue,
            String afterValue,
            String reason)
    {
        public AuditEventImport
        {
            externalId = Objects.requireNonNull(externalId, "externalId");
            portableId = Objects.requireNonNull(portableId, "portableId");
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    public record ImportedAuditHistory(Map<String, AuditEvent> events)
    {
        public ImportedAuditHistory
        {
            events = Map.copyOf(Objects.requireNonNull(events, "events"));
        }
    }
}
