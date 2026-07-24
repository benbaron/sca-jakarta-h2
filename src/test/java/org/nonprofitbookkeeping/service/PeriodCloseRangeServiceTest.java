package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodCloseRangeServiceTest
{
    private static final long SCA_COMPANY_ID = 20_001L;
    private static final long OTHER_COMPANY_ID = 20_002L;

    @Test
    void closeAndReopenPersistWithFactualHistory(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("period-close");
        UUID rangeId;

        try (Jpa jpa = new Jpa(database))
        {
            seedCompany(jpa, SCA_COMPANY_ID, "SCA", "SCA Company");
            PeriodCloseRangeService service = new PeriodCloseRangeService(jpa);
            PeriodCloseRangeView closed = service.closeRange(
                    "sca",
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    "CALCULATED",
                    "treasurer",
                    "March close");
            rangeId = closed.id();

            assertEquals("SCA", closed.companyCode());
            assertEquals("CLOSED", closed.status());
            assertTrue(service.findClosedRange("SCA", LocalDate.of(2026, 3, 15)).isPresent());
            assertEquals(1, service.listEvents("SCA").size());
            assertEquals("CLOSED", service.listEvents("SCA").get(0).eventType());
            assertThrows(IllegalArgumentException.class, () -> service.closeRange(
                    "SCA",
                    LocalDate.of(2026, 3, 15),
                    LocalDate.of(2026, 4, 15),
                    "CUSTOM",
                    "treasurer",
                    null));

            try (EntityManager em = jpa.em())
            {
                assertThrows(ClosedPeriodRangeException.class, () ->
                        PeriodCloseRangeService.requireOpen(
                                em,
                                "SCA",
                                LocalDate.of(2026, 3, 20),
                                "enter transaction"));
                assertEquals(1L, em.createQuery("""
                        select count(a)
                        from AuditEvent a
                        where a.entityType = 'PeriodCloseRange'
                          and a.actionType = 'PERIOD_RANGE_CLOSED'
                        """, Long.class).getSingleResult());
            }
        }

        try (Jpa reopenedJpa = new Jpa(database))
        {
            PeriodCloseRangeService service = new PeriodCloseRangeService(reopenedJpa);
            assertEquals("CLOSED", service.loadRange(rangeId).status());
            assertThrows(IllegalArgumentException.class, () -> service.reopenRange(
                    rangeId,
                    "treasurer",
                    null,
                    ClosedPeriodPolicy.REQUIRE_REASON,
                    false));

            PeriodCloseRangeView reopened = service.reopenRange(
                    rangeId,
                    "treasurer",
                    "Corrected after review",
                    ClosedPeriodPolicy.REQUIRE_REASON,
                    false);
            assertEquals("REOPENED", reopened.status());
            assertFalse(service.findClosedRange("SCA", LocalDate.of(2026, 3, 15)).isPresent());
            assertEquals(2, service.listEvents("SCA").size());
            assertEquals("REOPENED", service.listEvents("SCA").get(0).eventType());

            try (EntityManager em = reopenedJpa.em())
            {
                assertEquals(2L, em.createQuery("""
                        select count(a)
                        from AuditEvent a
                        where a.entityType = 'PeriodCloseRange'
                        """, Long.class).getSingleResult());
            }
        }
    }

    @Test
    void closeRangesAreCompanyScoped(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("company-scoped-close")))
        {
            seedCompany(jpa, OTHER_COMPANY_ID, "OTHER", "Other Company");
            PeriodCloseRangeService service = new PeriodCloseRangeService(jpa);
            service.closeRange(
                    "OTHER",
                    LocalDate.of(2026, 4, 1),
                    LocalDate.of(2026, 4, 30),
                    "CUSTOM",
                    "treasurer",
                    null);

            assertTrue(service.findClosedRange("OTHER", LocalDate.of(2026, 4, 10)).isPresent());
            assertFalse(service.findClosedRange("DEFAULT", LocalDate.of(2026, 4, 10)).isPresent());
        }
    }

    @Test
    void formalAdjustmentPolicyPreventsDirectReopen(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("formal-adjustment")))
        {
            PeriodCloseRangeService service = new PeriodCloseRangeService(jpa);
            PeriodCloseRangeView range = service.closeRange(
                    "DEFAULT",
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 6, 30),
                    "CUSTOM",
                    "treasurer",
                    null);

            assertThrows(IllegalStateException.class, () -> service.reopenRange(
                    range.id(),
                    "treasurer",
                    "Adjustment needed",
                    ClosedPeriodPolicy.REQUIRE_FORMAL_ADJUSTMENT,
                    false));
            assertEquals("CLOSED", service.loadRange(range.id()).status());
        }
    }

    private static void seedCompany(Jpa jpa, long id, String code, String displayName)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO company (id, code, display_name) VALUES (?, ?, ?)")
                    .setParameter(1, id)
                    .setParameter(2, code)
                    .setParameter(3, displayName)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }
}
