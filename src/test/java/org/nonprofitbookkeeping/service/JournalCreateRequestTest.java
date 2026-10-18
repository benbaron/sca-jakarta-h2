package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;
import static org.nonprofitbookkeeping.service.MerchantSettlementTestFixture.*;

class JournalCreateRequestTest
{
    @TempDir Path directory;

    @Test
    void lostCommittedResponseSurvivesRestartWithoutAnotherReceipt()
    {
        Path database = directory.resolve("lost"); UUID request = UUID.randomUUID(); TransactionCommand command; long id;
        try (Jpa jpa = new Jpa(database))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); command = f.receipt();
            var first = new AtomicBoolean(true);
            var service = new TransactionEntryService(jpa, () -> COMPANY)
            {
                @Override public TransactionView load(long transactionId)
                {
                    if (first.getAndSet(false)) { throw new IllegalStateException("Synthetic lost post-commit response"); }
                    return super.load(transactionId);
                }
            };
            assertThrows(IllegalStateException.class, () -> service.enter(command, request));
            try (EntityManager em = jpa.em())
            {
                id = em.createQuery("from Txn t where t.portableId = :id", Txn.class).setParameter("id", request).getSingleResult().getId();
                var receipt = em.createQuery("from AuditEvent a where a.portableId = :id", AuditEvent.class).setParameter("id", request).getSingleResult();
                assertTrue(receipt.getReason().matches("Journal create request v1 SHA-256: [0-9a-f]{64}"));
            }
        }
        try (Jpa jpa = new Jpa(database))
        {
            var service = new TransactionEntryService(jpa, () -> COMPANY);
            assertEquals(id, service.enter(command, request).id());
            assertEquals(id, service.enter(command, request).id());
            assertEquals(1, count(jpa, "select count(t) from Txn t"));
            assertEquals(1, count(jpa, "select count(a) from AuditEvent a where a.actionType = 'TRANSACTION_ENTERED'"));
            assertEquals(2, count(jpa, "select count(d) from TxnSupplementalLine d"));
        }
    }

    @Test
    void concurrentExactRequestsCreateOnceButNewIdentityIsDeliberateNewEntry() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("concurrent")))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var service = f.nmr().base().entry(jpa); UUID request = UUID.randomUUID();
            var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
            try
            {
                Callable<Long> save = () -> { start.await(); return service.enter(f.receipt(), request).id(); };
                var a = pool.submit(save); var b = pool.submit(save); start.countDown();
                assertEquals(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            }
            finally { pool.shutdownNow(); }
            assertEquals(1, count(jpa, "select count(t) from Txn t"));
            assertNotNull(service.enter(f.receipt(), UUID.randomUUID()).id());
            assertEquals(2, count(jpa, "select count(t) from Txn t"));
        }
    }

    @Test
    void competingDistinctRemittancesCannotConsumeSameObligation() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("competing")))
        {
            var f = NmrItemTestFixture.seed(jpa); var service = f.entry(jpa); UUID item = UUID.randomUUID();
            service.enter(f.receipt(item, f.recipient(), f.event()), UUID.randomUUID());
            var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
            try
            {
                Callable<Boolean> save = () ->
                {
                    start.await();
                    try { service.enter(f.payment(item, "15", f.recipient(), f.event()), UUID.randomUUID()); return true; }
                    catch (PostingException expected) { return false; }
                };
                var a = pool.submit(save); var b = pool.submit(save); start.countDown();
                assertNotEquals(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            }
            finally { pool.shutdownNow(); }
            assertEquals(2, count(jpa, "select count(t) from Txn t"));
            assertEquals(2, count(jpa, "select count(a) from AuditEvent a where a.actionType = 'TRANSACTION_ENTERED'"));
            assertEquals(0, new java.math.BigDecimal("5").compareTo(new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryNmr(DATE.plusDays(1)).items().get(0).openBalance()));
        }
    }

    @Test
    void normalizedComponentsReplayButChangedFactsAndForeignOrUnreceiptedIdentitiesReject()
    {
        try (Jpa jpa = new Jpa(directory.resolve("identity")))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var service = f.nmr().base().entry(jpa); service.enter(f.receipt()); UUID key = UUID.randomUUID();
            long id = service.enter(f.payout(), key).id();
            var equivalent = f.payout(facts("SETTLEMENT", "30", "  processor/withheld/001  ", component("FEE", 1), component("PAYOUT", 0)));
            assertEquals(id, service.enter(equivalent, key).id());
            assertThrows(PostingException.class, () -> service.enter(f.payout(facts("SETTLEMENT", "31", "processor/withheld/001", component("PAYOUT", 0), component("FEE", 1))), key));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin(); Company other = new Company(); other.setCode("OTHER"); other.setDisplayName("Other"); other.setDefaultCurrency("USD"); em.persist(other); em.getTransaction().commit();
            }
            assertThrows(PostingException.class, () -> new TransactionEntryService(jpa, () -> "OTHER").enter(f.payout(), key));
            UUID unreceipted;
            try (EntityManager em = jpa.em()) { unreceipted = em.createQuery("from Txn t where t.id <> :id", Txn.class).setParameter("id", id).getSingleResult().getPortableId(); }
            assertThrows(PostingException.class, () -> service.enter(f.receipt(), unreceipted));
            assertEquals(2, count(jpa, "select count(t) from Txn t"));
        }
    }

    @Test
    void retryAcknowledgesCurrentCorrectionButCannotRewriteOrResurrectDeletedEntry()
    {
        try (Jpa jpa = new Jpa(directory.resolve("correction")))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var service = f.nmr().base().entry(jpa); UUID key = UUID.randomUUID(); var command = f.receipt();
            long id = service.enter(command, key).id();
            var revised = new TransactionCommand(command.date(), null, "Reviewed later", null, command.lines(), command.supplementalLines());
            service.update(id, revised); long audits = count(jpa, "select count(a) from AuditEvent a");
            assertEquals("Reviewed later", service.enter(command, key).memo());
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            assertThrows(PostingException.class, () -> service.enter(revised, key));
            var correction = new TransactionCorrectionService(jpa, () -> COMPANY);
            correction.reverse(id, DATE.plusDays(3), "tester", "Synthetic correction", false);
            audits = count(jpa, "select count(a) from AuditEvent a");
            assertEquals("REVERSED", service.enter(command, key).status());
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            UUID deleted = UUID.randomUUID(); long disposable = service.enter(command, deleted).id();
            correction.delete(disposable, "tester", "Remove disposable receipt");
            long transactions = count(jpa, "select count(t) from Txn t");
            assertThrows(PostingException.class, () -> service.enter(command, deleted));
            assertEquals(transactions, count(jpa, "select count(t) from Txn t"));
        }
    }

    @Test
    void permissionsAreRecheckedAndClosedDateAllowsAcknowledgmentOnly()
    {
        try (Jpa jpa = new Jpa(directory.resolve("protected")))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var service = f.nmr().base().entry(jpa); UUID key = UUID.randomUUID(); long id = service.enter(f.receipt(), key).id();
            Instant now = Instant.now();
            var guard = new AuthorizationGuard(jpa, () -> Optional.of(new AuthenticatedUserSession(99L, "viewer", "Viewer", COMPANY, Set.of(ReservedSecurityRole.VIEWER), now, now)));
            assertThrows(AuthorizationException.class, () -> new TransactionEntryService(jpa, () -> COMPANY, guard).enter(f.receipt(), key));
            try (EntityManager em = jpa.em())
            {
                UUID run = UUID.randomUUID(); em.getTransaction().begin();
                em.createNativeQuery("INSERT INTO reconciliation_run (id, group_code, statement_ending_on, bank_format, imported_transaction_count, status, notes) VALUES (?, 'NMR_DETAIL', DATE '2026-06-30', 'OFX', 1, 'COMPLETED', 'Synthetic')").setParameter(1, run).executeUpdate();
                em.createNativeQuery("INSERT INTO txn_reconciliation_protection (txn_id, reconciliation_run_id, protected_by, notes) VALUES (?, ?, 'tester', 'Synthetic')").setParameter(1, id).setParameter(2, run).executeUpdate();
                em.getTransaction().commit();
            }
            assertEquals(id, service.enter(f.receipt(), key).id());
            assertThrows(RuntimeException.class, () -> service.update(id, f.receipt()));
            new PeriodCloseRangeService(jpa).closeRange(COMPANY, DATE, DATE, "CALCULATED", "tester", "Synthetic close");
            long audits = count(jpa, "select count(a) from AuditEvent a");
            assertEquals(id, service.enter(f.receipt(), key).id());
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            assertThrows(RuntimeException.class, () -> service.enter(f.receipt(), UUID.randomUUID()));
            assertThrows(RuntimeException.class, () -> service.update(id, f.receipt()));
        }
    }

    @Test
    void lateAuditFailureRollsBackAllFactsAndSameRequestCanBeCorrectedAndRetried()
    {
        try (Jpa jpa = new Jpa(directory.resolve("rollback")))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var service = f.nmr().base().entry(jpa); service.enter(f.receipt()); UUID key = UUID.randomUUID();
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin(); em.createNativeQuery("ALTER TABLE audit_event ADD CONSTRAINT synthetic_request_failure CHECK (portable_id <> UUID '" + key + "')").executeUpdate(); em.getTransaction().commit();
            }
            assertThrows(RuntimeException.class, () -> service.enter(f.payout(), key));
            assertEquals(1, count(jpa, "select count(t) from Txn t"));
            assertEquals(1, count(jpa, "select count(a) from AuditEvent a where a.actionType = 'TRANSACTION_ENTERED'"));
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, em.createNativeQuery("select count(*) from txn_supplemental_merchant_component", Long.class).getSingleResult());
                em.getTransaction().begin(); em.createNativeQuery("ALTER TABLE audit_event DROP CONSTRAINT synthetic_request_failure").executeUpdate(); em.getTransaction().commit();
            }
            var corrected = f.payout(facts("SETTLEMENT", "30", "corrected evidence", component("PAYOUT", 0), component("FEE", 1)));
            long id = service.enter(corrected, key).id(); assertEquals(id, service.enter(corrected, key).id());
            assertEquals(2, count(jpa, "select count(t) from Txn t"));
        }
    }

    private static long count(Jpa jpa, String query)
    {
        try (EntityManager em = jpa.em()) { return em.createQuery(query, Long.class).getSingleResult(); }
    }
}
