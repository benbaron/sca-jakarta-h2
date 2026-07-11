package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.repository.PeriodCloseRunRecord;
import org.nonprofitbookkeeping.repository.PeriodCloseRunRepository;
import org.nonprofitbookkeeping.repository.WorkflowRunStatus;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative service for company-scoped closed date ranges and factual
 * close/reopen history.
 */
public class PeriodCloseService
{
    private final Jpa jpa;
    private final PeriodCloseRunRepository legacyRunRepository;

    public PeriodCloseService(Jpa jpa)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.legacyRunRepository = null;
    }

    /**
     * Compatibility constructor for the retired run-artifact API. New code must
     * use {@link #PeriodCloseService(Jpa)}.
     */
    @Deprecated
    public PeriodCloseService(PeriodCloseRunRepository runRepository)
    {
        this.jpa = null;
        this.legacyRunRepository = Objects.requireNonNull(runRepository, "runRepository");
    }

    /** Compatibility bridge retained while old repository tests are migrated. */
    @Deprecated
    public PeriodCloseRunRecord recordCompletedClose(String groupCode,
                                                      LocalDate closeDate,
                                                      UUID producedTransactionId,
                                                      String notes)
    {
        if (legacyRunRepository == null)
        {
            throw new IllegalStateException("Legacy period-close run recording is not available");
        }
        PeriodCloseRunRecord run = new PeriodCloseRunRecord(
                UUID.randomUUID(),
                groupCode,
                closeDate,
                WorkflowRunStatus.COMPLETED,
                producedTransactionId,
                notes);
        legacyRunRepository.append(run);
        return run;
    }

    public PeriodCloseRangeView closeRange(
            String companyCode,
            LocalDate startDate,
            LocalDate endDate,
            String rangeKind,
            String actor,
            String reason)
    {
        Jpa store = requireJpa();
        String company = normalizeCompanyCode(companyCode);
        LocalDate start = requireDate(startDate, "startDate");
        LocalDate end = requireDate(endDate, "endDate");
        if (end.isBefore(start))
        {
            throw new IllegalArgumentException("endDate must be on or after startDate");
        }
        String kind = normalizeKind(rangeKind);
        String cleanActor = requireText(actor, "actor");
        String cleanReason = blankToNull(reason);
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        try (EntityManager em = store.em())
        {
            em.getTransaction().begin();
            try
            {
                Number overlaps = (Number) em.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM period_close_range
                        WHERE company_code = ?
                          AND status = 'CLOSED'
                          AND start_date <= ?
                          AND end_date >= ?
                        """)
                        .setParameter(1, company)
                        .setParameter(2, Date.valueOf(end))
                        .setParameter(3, Date.valueOf(start))
                        .getSingleResult();
                if (overlaps.longValue() > 0)
                {
                    throw new IllegalArgumentException(
                            "The requested close range overlaps an existing closed range for " + company);
                }

                em.createNativeQuery("""
                        INSERT INTO period_close_range
                            (id, company_code, start_date, end_date, range_kind, status,
                             closed_at, closed_by, close_reason, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, 'CLOSED', ?, ?, ?, ?, ?)
                        """)
                        .setParameter(1, id)
                        .setParameter(2, company)
                        .setParameter(3, Date.valueOf(start))
                        .setParameter(4, Date.valueOf(end))
                        .setParameter(5, kind)
                        .setParameter(6, Timestamp.from(now))
                        .setParameter(7, cleanActor)
                        .setParameter(8, cleanReason)
                        .setParameter(9, Timestamp.from(now))
                        .setParameter(10, Timestamp.from(now))
                        .executeUpdate();

                insertEvent(em, eventId, id, company, "CLOSED", cleanActor, cleanReason, now);
                em.persist(audit(
                        cleanActor,
                        "PERIOD_RANGE_CLOSED",
                        id,
                        "Closed " + start + " through " + end + " for " + company,
                        cleanReason));
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
        return loadRange(id);
    }

    public PeriodCloseRangeView reopenRange(
            UUID rangeId,
            String actor,
            String reason,
            ClosedPeriodPolicy policy,
            boolean requireReason)
    {
        Jpa store = requireJpa();
        UUID id = Objects.requireNonNull(rangeId, "rangeId");
        String cleanActor = requireText(actor, "actor");
        ClosedPeriodPolicy effectivePolicy = Objects.requireNonNull(policy, "policy");
        String cleanReason = blankToNull(reason);
        if (effectivePolicy == ClosedPeriodPolicy.REQUIRE_FORMAL_ADJUSTMENT)
        {
            throw new IllegalStateException(
                    "Close range " + id + " requires a formal adjustment workflow and cannot be reopened directly");
        }
        if ((requireReason || effectivePolicy == ClosedPeriodPolicy.REQUIRE_REASON) && cleanReason == null)
        {
            throw new IllegalArgumentException("A reopening reason is required");
        }
        Instant now = Instant.now();
        UUID eventId = UUID.randomUUID();

        try (EntityManager em = store.em())
        {
            em.getTransaction().begin();
            try
            {
                Object[] current = singleRangeRow(em, id)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown close range: " + id));
                String company = String.valueOf(current[1]);
                String status = String.valueOf(current[5]);
                if (!"CLOSED".equals(status))
                {
                    throw new IllegalStateException("Close range " + id + " is already reopened");
                }

                int updated = em.createNativeQuery("""
                        UPDATE period_close_range
                        SET status = 'REOPENED', reopened_at = ?, reopened_by = ?,
                            reopen_reason = ?, updated_at = ?
                        WHERE id = ? AND status = 'CLOSED'
                        """)
                        .setParameter(1, Timestamp.from(now))
                        .setParameter(2, cleanActor)
                        .setParameter(3, cleanReason)
                        .setParameter(4, Timestamp.from(now))
                        .setParameter(5, id)
                        .executeUpdate();
                if (updated != 1)
                {
                    throw new IllegalStateException("Close range " + id + " could not be reopened");
                }

                insertEvent(em, eventId, id, company, "REOPENED", cleanActor, cleanReason, now);
                em.persist(audit(
                        cleanActor,
                        "PERIOD_RANGE_REOPENED",
                        id,
                        "Reopened close range " + id + " for " + company,
                        cleanReason));
                em.getTransaction().commit();
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
        return loadRange(id);
    }

    public List<PeriodCloseRangeView> listRanges(String companyCode)
    {
        String company = normalizeCompanyCode(companyCode);
        try (EntityManager em = requireJpa().em())
        {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                    SELECT id, company_code, start_date, end_date, range_kind, status,
                           closed_at, closed_by, close_reason,
                           reopened_at, reopened_by, reopen_reason
                    FROM period_close_range
                    WHERE company_code = ?
                    ORDER BY start_date DESC, end_date DESC, closed_at DESC
                    """)
                    .setParameter(1, company)
                    .getResultList();
            List<PeriodCloseRangeView> result = new ArrayList<>();
            for (Object[] row : rows)
            {
                result.add(mapRange(row));
            }
            return result;
        }
    }

    public List<PeriodCloseEventView> listEvents(String companyCode)
    {
        String company = normalizeCompanyCode(companyCode);
        try (EntityManager em = requireJpa().em())
        {
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                    SELECT id, close_range_id, company_code, event_type, actor, reason, event_at
                    FROM period_close_event
                    WHERE company_code = ?
                    ORDER BY event_at DESC, id DESC
                    """)
                    .setParameter(1, company)
                    .getResultList();
            List<PeriodCloseEventView> result = new ArrayList<>();
            for (Object[] row : rows)
            {
                result.add(new PeriodCloseEventView(
                        toUuid(row[0]),
                        toUuid(row[1]),
                        String.valueOf(row[2]),
                        String.valueOf(row[3]),
                        String.valueOf(row[4]),
                        nullableString(row[5]),
                        toInstant(row[6])));
            }
            return result;
        }
    }

    public Optional<PeriodCloseRangeView> findClosedRange(String companyCode, LocalDate date)
    {
        String company = normalizeCompanyCode(companyCode);
        LocalDate target = requireDate(date, "date");
        try (EntityManager em = requireJpa().em())
        {
            return findClosedRange(em, company, target);
        }
    }

    public PeriodCloseRangeView loadRange(UUID rangeId)
    {
        try (EntityManager em = requireJpa().em())
        {
            return singleRangeRow(em, rangeId)
                    .map(PeriodCloseService::mapRange)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown close range: " + rangeId));
        }
    }

    public static void requireOpen(
            EntityManager em,
            String companyCode,
            LocalDate date,
            String operation)
    {
        String company = normalizeCompanyCode(companyCode);
        LocalDate target = requireDate(date, "date");
        Optional<PeriodCloseRangeView> closed = findClosedRange(em, company, target);
        if (closed.isPresent())
        {
            throw new ClosedPeriodRangeException(closed.get().id(), company, target, operation);
        }
    }

    private static Optional<PeriodCloseRangeView> findClosedRange(
            EntityManager em,
            String company,
            LocalDate date)
    {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, company_code, start_date, end_date, range_kind, status,
                       closed_at, closed_by, close_reason,
                       reopened_at, reopened_by, reopen_reason
                FROM period_close_range
                WHERE company_code = ?
                  AND status = 'CLOSED'
                  AND start_date <= ?
                  AND end_date >= ?
                ORDER BY start_date, end_date, id
                """)
                .setParameter(1, company)
                .setParameter(2, Date.valueOf(date))
                .setParameter(3, Date.valueOf(date))
                .setMaxResults(2)
                .getResultList();
        if (rows.size() > 1)
        {
            throw new IllegalStateException(
                    "Multiple active close ranges contain " + date + " for company " + company);
        }
        return rows.stream().findFirst().map(PeriodCloseService::mapRange);
    }

    private static Optional<Object[]> singleRangeRow(EntityManager em, UUID id)
    {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT id, company_code, start_date, end_date, range_kind, status,
                       closed_at, closed_by, close_reason,
                       reopened_at, reopened_by, reopen_reason
                FROM period_close_range
                WHERE id = ?
                """)
                .setParameter(1, id)
                .setMaxResults(1)
                .getResultList();
        return rows.stream().findFirst();
    }

    private static PeriodCloseRangeView mapRange(Object[] row)
    {
        return new PeriodCloseRangeView(
                toUuid(row[0]),
                String.valueOf(row[1]),
                toLocalDate(row[2]),
                toLocalDate(row[3]),
                String.valueOf(row[4]),
                String.valueOf(row[5]),
                toInstant(row[6]),
                String.valueOf(row[7]),
                nullableString(row[8]),
                toInstant(row[9]),
                nullableString(row[10]),
                nullableString(row[11]));
    }

    private static void insertEvent(
            EntityManager em,
            UUID eventId,
            UUID rangeId,
            String company,
            String eventType,
            String actor,
            String reason,
            Instant eventAt)
    {
        em.createNativeQuery("""
                INSERT INTO period_close_event
                    (id, close_range_id, company_code, event_type, actor, reason, event_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
                .setParameter(1, eventId)
                .setParameter(2, rangeId)
                .setParameter(3, company)
                .setParameter(4, eventType)
                .setParameter(5, actor)
                .setParameter(6, reason)
                .setParameter(7, Timestamp.from(eventAt))
                .executeUpdate();
    }

    private static AuditEvent audit(
            String actor,
            String action,
            UUID rangeId,
            String summary,
            String reason)
    {
        AuditEvent event = new AuditEvent();
        event.setActor(actor);
        event.setActionType(action);
        event.setEntityType("PeriodCloseRange");
        event.setEntityId(rangeId.toString());
        event.setSummary(summary);
        event.setReason(reason);
        return event;
    }

    private Jpa requireJpa()
    {
        if (jpa == null)
        {
            throw new IllegalStateException("Authoritative period-close operations require Jpa");
        }
        return jpa;
    }

    private static String normalizeCompanyCode(String value)
    {
        String clean = requireText(value, "companyCode").toUpperCase(Locale.ROOT);
        if (clean.length() > 80)
        {
            throw new IllegalArgumentException("companyCode must not exceed 80 characters");
        }
        return clean;
    }

    private static String normalizeKind(String value)
    {
        String clean = requireText(value, "rangeKind").toUpperCase(Locale.ROOT);
        if (!"CALCULATED".equals(clean) && !"CUSTOM".equals(clean))
        {
            throw new IllegalArgumentException("rangeKind must be CALCULATED or CUSTOM");
        }
        return clean;
    }

    private static LocalDate requireDate(LocalDate value, String label)
    {
        if (value == null)
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String nullableString(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private static UUID toUuid(Object value)
    {
        if (value instanceof UUID uuid)
        {
            return uuid;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private static LocalDate toLocalDate(Object value)
    {
        if (value instanceof LocalDate date)
        {
            return date;
        }
        if (value instanceof Date date)
        {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static Instant toInstant(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Instant instant)
        {
            return instant;
        }
        if (value instanceof Timestamp timestamp)
        {
            return timestamp.toInstant();
        }
        if (value instanceof OffsetDateTime offsetDateTime)
        {
            return offsetDateTime.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime)
        {
            return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        return Instant.parse(String.valueOf(value));
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }
}
