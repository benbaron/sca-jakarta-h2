package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Canonical selected-company factual audit-history query and interchange-write boundary. */
public final class AuditHistoryService
{
    private static final int MAX_LOB_TEXT = 1_048_576;
    private static final int MAX_QUERY_ROWS = 1000;

    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;

    public AuditHistoryService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    /** Returns immutable factual history for the authoritative active company. */
    public List<AuditEventView> listRecent(AuditHistoryFilter filter, int maxRows)
    {
        return listRecent(companyCodeSupplier.get(), filter, maxRows);
    }

    /** Returns immutable factual history for one explicit company code. */
    public List<AuditEventView> listRecent(String companyCode, AuditHistoryFilter filter, int maxRows)
    {
        String ownerCode = requireText(companyCode, "companyCode", 64).trim().toLowerCase(Locale.ROOT);
        AuditHistoryFilter query = filter == null ? AuditHistoryFilter.empty() : filter.normalized();
        int limit = Math.max(1, Math.min(MAX_QUERY_ROWS, maxRows));

        StringBuilder jpql = new StringBuilder(
                "select e from AuditEvent e join fetch e.company c where lower(c.code) = :companyCode");
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("companyCode", ownerCode);

        if (!query.action().isBlank())
        {
            jpql.append(" and lower(e.actionType) like :action");
            parameters.put("action", containsPattern(query.action()));
        }
        if (!query.entity().isBlank())
        {
            jpql.append(" and (lower(e.entityType) like :entity or lower(coalesce(e.entityId, '')) like :entity)");
            parameters.put("entity", containsPattern(query.entity()));
        }
        if (!query.actor().isBlank())
        {
            jpql.append(" and lower(e.actor) like :actor");
            parameters.put("actor", containsPattern(query.actor()));
        }
        if (query.fromDate() != null)
        {
            jpql.append(" and e.occurredAt >= :fromInstant");
            parameters.put("fromInstant", startOfDay(query.fromDate()));
        }
        if (query.toDate() != null)
        {
            jpql.append(" and e.occurredAt < :toExclusive");
            parameters.put("toExclusive", startOfDay(query.toDate().plusDays(1)));
        }
        jpql.append(" order by e.occurredAt desc, e.id desc");

        try (EntityManager em = jpa.em())
        {
            var typed = em.createQuery(jpql.toString(), AuditEvent.class);
            parameters.forEach(typed::setParameter);
            return typed.setMaxResults(limit)
                    .getResultList()
                    .stream()
                    .map(AuditHistoryService::view)
                    .toList();
        }
    }

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

    private static AuditEventView view(AuditEvent event)
    {
        return new AuditEventView(
                event.getId(),
                event.getPortableId(),
                event.getOccurredAt(),
                event.getActor(),
                event.getActionType(),
                event.getEntityType(),
                event.getEntityId(),
                event.getSummary(),
                event.getBeforeValue(),
                event.getAfterValue(),
                event.getReason());
    }

    private static Instant startOfDay(LocalDate value)
    {
        return value.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private static String containsPattern(String value)
    {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
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

    public record AuditHistoryFilter(
            String action,
            String entity,
            String actor,
            LocalDate fromDate,
            LocalDate toDate)
    {
        public AuditHistoryFilter
        {
            action = action == null ? "" : action;
            entity = entity == null ? "" : entity;
            actor = actor == null ? "" : actor;
            if (fromDate != null && toDate != null && fromDate.isAfter(toDate))
            {
                throw new IllegalArgumentException("Audit-history From date must not be after To date");
            }
        }

        public static AuditHistoryFilter empty()
        {
            return new AuditHistoryFilter("", "", "", null, null);
        }

        private AuditHistoryFilter normalized()
        {
            return new AuditHistoryFilter(action.trim(), entity.trim(), actor.trim(), fromDate, toDate);
        }
    }

    public record AuditEventView(
            Long id,
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
        public AuditEventView
        {
            portableId = Objects.requireNonNull(portableId, "portableId");
            occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
            actor = Objects.requireNonNull(actor, "actor");
            actionType = Objects.requireNonNull(actionType, "actionType");
            entityType = Objects.requireNonNull(entityType, "entityType");
            summary = Objects.requireNonNull(summary, "summary");
        }
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
