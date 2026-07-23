package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
public class PeriodCloseRangeService
{
    private static final String RANGE_COLUMNS = """
            CAST(id AS VARCHAR), company_code, start_date, end_date, range_kind, status,
            closed_at, closed_by, close_reason, reopened_at, reopened_by, reopen_reason, company_id
            """;

    private final Jpa jpa;

    public PeriodCloseRangeService(Jpa jpa)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
    }

    public PeriodCloseRangeView closeRange(
            String companyCode,
            LocalDate startDate,
            LocalDate endDate,
            String rangeKind,
            String actor,
            String reason)
    {
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

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company companyEntity = requireCompany(em, company);
                Number overlaps = (Number) em.createNativeQuery("""
                        SELECT COUNT(*)
                        FROM period_close_range
                        WHERE company_id = ?
                          AND status = 'CLOSED'
                          AND start_date <= ?
                          AND end_date >= ?
                        """)
                        .setParameter(1, companyEntity.getId())
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
                            (id, company_id, company_code, start_date, end_date, range_kind, status,
                             closed_at, closed_by, close_reason, created_at, updated_at)
                        VALUES (CAST(? AS UUID), ?, ?, ?, ?, ?, 'CLOSED', ?, ?, NULLIF(?, ''), ?, ?)
                        """)
                        .setParameter(1, id.toString())
                        .setParameter(2, companyEntity.getId())
                        .setParameter(3, company)
                        .setParameter(4, Date.valueOf(start))
                        .setParameter(5, Date.valueOf(end))
                        .setParameter(6, kind)
                        .setParameter(7, Timestamp.from(now))
                        .setParameter(8, cleanActor)
                        .setParameter(9, cleanReason == null ? "" : cleanReason)
                        .setParameter(10, Timestamp.from(now))
                        .setParameter(11, Timestamp.from(now))
                        .executeUpdate();

                insertEvent(em, eventId, id, companyEntity.getId(), company, "CLOSED", cleanActor, cleanReason, now);
                em.persist(audit(
                        companyEntity,
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
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Object[] current = singleRangeRow(em, id)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown close range: " + id));
                String company = String.valueOf(current[1]);
                Long companyId = ((Number) current[12]).longValue();
                Company companyEntity = em.find(Company.class, companyId);
                if (companyEntity == null)
                {
                    throw new IllegalStateException("Close range " + id + " has no valid company owner");
                }
                String status = String.valueOf(current[5]);
                if (!"CLOSED".equals(status))
                {
                    throw new IllegalStateException("Close range " + id + " is already reopened");
                }

                int updated = em.createNativeQuery("""
                        UPDATE period_close_range
                        SET status = 'REOPENED', reopened_at = ?, reopened_by = ?,
                            reopen_reason = NULLIF(?, ''), updated_at = ?
                        WHERE id = CAST(? AS UUID) AND status = 'CLOSED'
                        """)
                        .setParameter(1, Timestamp.from(now))
                        .setParameter(2, cleanActor)
                        .setParameter(3, cleanReason == null ? "" : cleanReason)
                        .setParameter(4, Timestamp.from(now))
                        .setParameter(5, id.toString())
                        .executeUpdate();
                if (updated != 1)
                {
                    throw new IllegalStateException("Close range " + id + " could not be reopened");
                }

                insertEvent(em, eventId, id, companyId, company, "REOPENED", cleanActor, cleanReason, now);
                em.persist(audit(
                        companyEntity,
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
        try (EntityManager em = jpa.em())
        {
            Company companyEntity = requireCompany(em, company);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("SELECT " + RANGE_COLUMNS + """
                    FROM period_close_range
                    WHERE company_id = ?
                    ORDER BY start_date DESC, end_date DESC, closed_at DESC
                    """)
                    .setParameter(1, companyEntity.getId())
                    .getResultList();
            return mapRanges(rows);
        }
    }

    public List<PeriodCloseEventView> listEvents(String companyCode)
    {
        String company = normalizeCompanyCode(companyCode);
        try (EntityManager em = jpa.em())
        {
            Company companyEntity = requireCompany(em, company);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery("""
                    SELECT CAST(id AS VARCHAR), CAST(close_range_id AS VARCHAR),
                           company_code, event_type, actor, reason, event_at
                    FROM period_close_event
                    WHERE company_id = ?
                    ORDER BY event_at DESC, id DESC
                    """)
                    .setParameter(1, companyEntity.getId())
                    .getResultList();
            List<PeriodCloseEventView> result = new ArrayList<>();
            for (Object[] row : rows)
            {
                result.add(new PeriodCloseEventView(
                        UUID.fromString(String.valueOf(row[0])),
                        UUID.fromString(String.valueOf(row[1])),
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
        try (EntityManager em = jpa.em())
        {
            return findClosedRange(em, company, target);
        }
    }

    public PeriodCloseRangeView loadRange(UUID rangeId)
    {
        UUID id = Objects.requireNonNull(rangeId, "rangeId");
        try (EntityManager em = jpa.em())
        {
            return singleRangeRow(em, id)
                    .map(PeriodCloseRangeService::mapRange)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown close range: " + id));
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
        Long companyId = requireCompanyId(em, company);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT " + RANGE_COLUMNS + """
                FROM period_close_range
                WHERE company_id = ?
                  AND status = 'CLOSED'
                  AND start_date <= ?
                  AND end_date >= ?
                ORDER BY start_date, end_date, id
                """)
                .setParameter(1, companyId)
                .setParameter(2, Date.valueOf(date))
                .setParameter(3, Date.valueOf(date))
                .setMaxResults(2)
                .getResultList();
        if (rows.size() > 1)
        {
            throw new IllegalStateException(
                    "Multiple active close ranges contain " + date + " for company " + company);
        }
        return rows.stream().findFirst().map(PeriodCloseRangeService::mapRange);
    }

    private static Optional<Object[]> singleRangeRow(EntityManager em, UUID id)
    {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("SELECT " + RANGE_COLUMNS + """
                FROM period_close_range
                WHERE id = CAST(? AS UUID)
                """)
                .setParameter(1, id.toString())
                .setMaxResults(1)
                .getResultList();
        return rows.stream().findFirst();
    }

    private static List<PeriodCloseRangeView> mapRanges(List<Object[]> rows)
    {
        List<PeriodCloseRangeView> result = new ArrayList<>();
        for (Object[] row : rows)
        {
            result.add(mapRange(row));
        }
        return result;
    }

    private static PeriodCloseRangeView mapRange(Object[] row)
    {
        return new PeriodCloseRangeView(
                UUID.fromString(String.valueOf(row[0])),
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
            Long companyId,
            String company,
            String eventType,
            String actor,
            String reason,
            Instant eventAt)
    {
        em.createNativeQuery("""
                INSERT INTO period_close_event
                    (id, close_range_id, company_id, company_code, event_type, actor, reason, event_at)
                VALUES (CAST(? AS UUID), CAST(? AS UUID), ?, ?, ?, ?, NULLIF(?, ''), ?)
                """)
                .setParameter(1, eventId.toString())
                .setParameter(2, rangeId.toString())
                .setParameter(3, companyId)
                .setParameter(4, company)
                .setParameter(5, eventType)
                .setParameter(6, actor)
                .setParameter(7, reason == null ? "" : reason)
                .setParameter(8, Timestamp.from(eventAt))
                .executeUpdate();
    }

    private static AuditEvent audit(
            Company company,
            String actor,
            String action,
            UUID rangeId,
            String summary,
            String reason)
    {
        AuditEvent event = new AuditEvent();
        event.setCompany(company);
        event.setActor(actor);
        event.setActionType(action);
        event.setEntityType("PeriodCloseRange");
        event.setEntityId(rangeId.toString());
        event.setSummary(summary);
        event.setReason(reason);
        return event;
    }

    private static Company requireCompany(EntityManager em, String companyCode)
    {
        return em.createQuery("from Company c where upper(c.code) = :code", Company.class)
                .setParameter("code", normalizeCompanyCode(companyCode))
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Company does not exist: " + companyCode));
    }

    private static Long requireCompanyId(EntityManager em, String companyCode)
    {
        return requireCompany(em, companyCode).getId();
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
        String text = String.valueOf(value);
        return LocalDate.parse(text.length() > 10 ? text.substring(0, 10) : text);
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
            return localDateTime.toInstant(ZoneOffset.UTC);
        }

        String text = String.valueOf(value).trim();
        try
        {
            return Instant.parse(text);
        }
        catch (RuntimeException ignored)
        {
            String iso = text.replace(' ', 'T');
            try
            {
                return OffsetDateTime.parse(iso).toInstant();
            }
            catch (RuntimeException ignoredOffset)
            {
                return LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC);
            }
        }
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }
}
