package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeriodCloseRangeServiceTest
{
    @Test
    void nativeRangeRowCanBeReadThroughService(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("native-close-range")))
        {
            UUID id = UUID.randomUUID();
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                String insert = """
                        INSERT INTO period_close_range
                            (id, company_code, start_date, end_date, range_kind, status,
                             closed_by, close_reason)
                        VALUES (CAST('%s' AS UUID), 'DEFAULT',
                                DATE '2026-04-01', DATE '2026-04-30',
                                'CUSTOM', 'CLOSED', 'treasurer', 'Manual insert')
                        """.formatted(id);
                em.createNativeQuery(insert).executeUpdate();
                em.getTransaction().commit();
            }

            PeriodCloseRangeService service = new PeriodCloseRangeService(jpa);
            PeriodCloseRangeView range = service.findClosedRange(
                            "DEFAULT", LocalDate.of(2026, 4, 10))
                    .orElseThrow();
            assertEquals(id, range.id());
            assertEquals("CLOSED", range.status());
            assertTrue(range.closed());
        }
    }
}
