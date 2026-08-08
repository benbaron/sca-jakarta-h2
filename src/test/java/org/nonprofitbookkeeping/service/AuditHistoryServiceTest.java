package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.AuditHistoryService.AuditEventView;
import org.nonprofitbookkeeping.service.AuditHistoryService.AuditHistoryFilter;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuditHistoryServiceTest
{
    @Test
    public void listRecent_returnsOnlySelectedCompanyAndExcludesGlobalEvents(@TempDir Path tempDir)
    {
        Path database = tempDir.resolve("audit-history-company-scope");
        try (Jpa jpa = new Jpa(database))
        {
            seed(jpa);
            AuditHistoryService service = new AuditHistoryService(jpa, () -> "ALPHA");

            List<AuditEventView> rows = service.listRecent(AuditHistoryFilter.empty(), 100);

            assertEquals(6, rows.size());
            assertTrue(rows.stream().allMatch(row -> row.summary().startsWith("alpha-")));
            assertFalse(rows.stream().anyMatch(row -> row.summary().startsWith("beta-")));
            assertFalse(rows.stream().anyMatch(row -> row.summary().startsWith("global-")));
            assertEquals("SCLX_IMPORTED", rows.get(0).actionType());
        }

        try (Jpa restarted = new Jpa(database))
        {
            AuditHistoryService service = new AuditHistoryService(restarted, () -> "ALPHA");
            assertEquals(6, service.listRecent(AuditHistoryFilter.empty(), 100).size());
        }
    }

    @Test
    public void listRecent_filtersActionEntityActorAndDateInsideService(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("audit-history-filters")))
        {
            seed(jpa);
            AuditHistoryService service = new AuditHistoryService(jpa, () -> "ALPHA");
            AuditHistoryFilter filter = new AuditHistoryFilter(
                    "transaction",
                    "txn-2",
                    "alice",
                    LocalDate.of(2026, 4, 2),
                    LocalDate.of(2026, 4, 2));

            List<AuditEventView> rows = service.listRecent(filter, 100);

            assertEquals(1, rows.size());
            assertEquals("TRANSACTION_REVERSED", rows.get(0).actionType());
            assertEquals("txn-2", rows.get(0).entityId());
            assertEquals("alice", rows.get(0).actor());
            assertEquals("before-2", rows.get(0).beforeValue());
            assertEquals("after-2", rows.get(0).afterValue());
            assertEquals("reason-2", rows.get(0).reason());
        }
    }

    @Test
    public void filter_rejectsReversedDateRange()
    {
        assertThrows(IllegalArgumentException.class, () -> new AuditHistoryFilter(
                "", "", "", LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 2)));
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company alpha = company("ALPHA", "Alpha Branch");
            Company beta = company("BETA", "Beta Branch");
            em.persist(alpha);
            em.persist(beta);
            em.flush();

            persist(em, alpha, "TRANSACTION_ENTERED", "TXN", "txn-1", "alice",
                    LocalDate.of(2026, 4, 1), "alpha-transaction-entered", null, "after-1", null);
            persist(em, alpha, "TRANSACTION_REVERSED", "TXN", "txn-2", "alice",
                    LocalDate.of(2026, 4, 2), "alpha-transaction-reversed", "before-2", "after-2", "reason-2");
            persist(em, alpha, "PERIOD_CLOSED", "PERIOD_CLOSE_RANGE", "range-1", "bob",
                    LocalDate.of(2026, 4, 3), "alpha-period-closed", null, "closed", null);
            persist(em, alpha, "BANK_STATEMENT_REVIEW_IMPORTED", "BANK_IMPORT_BATCH", "batch-1", "carol",
                    LocalDate.of(2026, 4, 4), "alpha-bank-imported", null, "rows=4", null);
            persist(em, alpha, "NORMALIZED_BANK_CSV_IMPORTED", "BANK_IMPORT_BATCH", "batch-2", "carol",
                    LocalDate.of(2026, 4, 5), "alpha-normalized-imported", null, "rows=2", null);
            persist(em, alpha, "SCLX_IMPORTED", "COMPANY", "ALPHA", "dana",
                    LocalDate.of(2026, 4, 6), "alpha-sclx-imported", null, "restored", null);

            persist(em, beta, "TRANSACTION_ENTERED", "TXN", "txn-beta", "mallory",
                    LocalDate.of(2026, 4, 7), "beta-transaction", null, "other-company", null);
            persist(em, null, "GLOBAL_EVENT", "SYSTEM", null, "system",
                    LocalDate.of(2026, 4, 8), "global-unresolved", null, "global", null);
            em.getTransaction().commit();
        }
    }

    private static Company company(String code, String name)
    {
        Company company = new Company();
        company.setCode(code);
        company.setDisplayName(name);
        return company;
    }

    private static void persist(
            EntityManager em,
            Company company,
            String action,
            String entityType,
            String entityId,
            String actor,
            LocalDate date,
            String summary,
            String before,
            String after,
            String reason)
    {
        AuditEvent event = new AuditEvent();
        event.initializeImportMetadata(UUID.randomUUID(), date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant());
        event.setCompany(company);
        event.setActionType(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setActor(actor);
        event.setSummary(summary);
        event.setBeforeValue(before);
        event.setAfterValue(after);
        event.setReason(reason);
        em.persist(event);
    }
}
