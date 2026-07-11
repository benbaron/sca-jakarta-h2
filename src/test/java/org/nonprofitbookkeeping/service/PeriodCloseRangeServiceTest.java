package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodCloseRangeServiceTest
{
    @Test
    void nativeRangeRowCanBeStored(@TempDir Path tempDir)
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

            try (EntityManager em = jpa.em())
            {
                Number count = (Number) em.createNativeQuery(
                                "SELECT COUNT(*) FROM period_close_range")
                        .getSingleResult();
                assertEquals(1L, count.longValue());
            }
        }
    }
}
