package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.CounterpartyKind;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CounterpartyAdminServiceTest
{
    @TempDir Path directory;

    @Test
    void renameAndDeactivationPreservePortableIdentityHistoryAndAuditAcrossRestart()
    {
        Path database = directory.resolve("party-history");
        Long id;
        UUID portable;
        try (Jpa jpa = new Jpa(database))
        {
            var service = new CounterpartyAdminService(jpa);
            var created = service.save(new CounterpartyCommand(null, "  Alice  ", CounterpartyKind.PERSON,
                    " alice@example.test ", " 555-0100 ", " Original ", true));
            id = created.id();
            assertEquals("Alice", created.name());
            assertEquals("alice@example.test", created.email());
            try (EntityManager em = jpa.em())
            {
                var party = em.find(Counterparty.class, id);
                portable = party.getPortableId();
                em.getTransaction().begin();
                Txn txn = new Txn();
                txn.setCompany(party.getCompany());
                txn.setTxnDate(LocalDate.of(2026, 1, 1));
                txn.setPayee(party);
                txn.setMemo("Historical payee relationship fixture");
                em.persist(txn);
                em.getTransaction().commit();
            }
            service.save(new CounterpartyCommand(id, "Alice Renamed", CounterpartyKind.ORG,
                    "new@example.test", "555-0101", "Retained", false));
            assertTrue(new TransactionReferenceDataService(jpa).loadActiveReferenceData().counterparties().isEmpty());
            assertEquals(1, service.listForMaintenance().size());
            assertEquals(2L, audits(jpa));
            try (EntityManager em = jpa.em())
            {
                var audit = em.createQuery("from AuditEvent a where a.actionType = 'COUNTERPARTY_UPDATED'",
                        org.nonprofitbookkeeping.model.AuditEvent.class).getSingleResult();
                assertTrue(audit.getBeforeValue().contains("name=Alice;"));
                assertTrue(audit.getAfterValue().contains("active=false"));
                assertEquals("COUNTERPARTY", audit.getEntityType());
            }
        }
        try (Jpa jpa = new Jpa(database); EntityManager em = jpa.em())
        {
            var party = em.find(Counterparty.class, id);
            assertEquals(portable, party.getPortableId());
            assertEquals("Alice Renamed", party.getDisplayName());
            assertFalse(party.isActive());
            var txn = em.createQuery("from Txn t where t.payee.id = :id", Txn.class)
                    .setParameter("id", id).getSingleResult();
            assertEquals(id, txn.getPayee().getId());
            new CounterpartyAdminService(jpa).save(command(id, "Alice Renamed", true));
            assertEquals(id, new TransactionReferenceDataService(jpa).loadActiveReferenceData().counterparties().get(0).id());
        }
    }

    @Test
    void invalidAndDuplicateSavesChangeNeitherMasterNorAudit()
    {
        try (Jpa jpa = new Jpa(directory.resolve("party-validation")))
        {
            var service = new CounterpartyAdminService(jpa);
            var first = service.save(command(null, "Existing", false));
            var second = service.save(command(null, "Second", true));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(null, " existing ", true)));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(second.id(), "EXISTING", true)));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(null, " ", true)));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(null, "x".repeat(201), true)));
            assertThrows(IllegalArgumentException.class, () -> service.save(new CounterpartyCommand(first.id(),
                    "Changed", null, null, null, null, true)));
            assertThrows(IllegalArgumentException.class, () -> service.save(new CounterpartyCommand(first.id(),
                    "Changed", CounterpartyKind.OTHER, "x".repeat(201), null, null, true)));
            assertThrows(IllegalArgumentException.class, () -> service.save(new CounterpartyCommand(first.id(),
                    "Changed", CounterpartyKind.OTHER, null, "x".repeat(41), null, true)));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(Long.MAX_VALUE, "Unknown", true)));
            assertEquals(2L, audits(jpa));
            assertEquals("Existing", service.listForMaintenance().get(0).name());
            assertFalse(service.listForMaintenance().get(0).active());
        }
    }

    @Test
    void ownershipAuthorizationAndCompanySwitchesCannotMutateForeignParties()
    {
        try (Jpa jpa = new Jpa(directory.resolve("party-authorization")))
        {
            long userId;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                var operator = new org.nonprofitbookkeeping.model.AppUser();
                operator.setUsername("operator"); operator.setDisplayName("Operator");
                em.persist(operator); em.flush(); userId = operator.getId();
                Company other = new Company();
                other.setCode("OTHER"); other.setDisplayName("Other Company");
                em.persist(other); em.getTransaction().commit();
            }
            AtomicReference<String> company = new AtomicReference<>("DEFAULT");
            AtomicReference<Optional<AuthenticatedUserSession>> user = new AtomicReference<>(Optional.empty());
            var guard = new AuthorizationGuard(jpa, user::get);
            var service = new CounterpartyAdminService(jpa, company::get, guard);
            assertThrows(AuthorizationException.class, () -> service.save(command(null, "Alice", true)));
            user.set(Optional.of(session(userId, "DEFAULT", ReservedSecurityRole.VIEWER)));
            assertThrows(AuthorizationException.class, () -> service.save(command(null, "Alice", true)));
            user.set(Optional.of(session(userId, "DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            var alice = service.save(command(null, "Alice", true));
            company.set("OTHER");
            assertThrows(AuthorizationException.class, () -> service.save(command(alice.id(), "Changed", true)));
            user.set(Optional.of(session(userId, "OTHER", ReservedSecurityRole.ACCOUNTANT)));
            assertThrows(CompanyOwnershipException.class, () -> service.save(command(alice.id(), "Changed", true)));
            assertTrue(service.listForMaintenance().isEmpty());
            var otherAlice = service.save(command(null, "Alice", true));
            assertNotEquals(alice.id(), otherAlice.id());
            assertEquals(1, service.listForMaintenance().size());
            assertEquals(2L, audits(jpa));
            try (EntityManager em = jpa.em())
            {
                assertEquals("Alice", em.find(Counterparty.class, alice.id()).getDisplayName());
                assertEquals("operator", em.createQuery("select a.actor from AuditEvent a where a.actionType = 'COUNTERPARTY_CREATED'",
                        String.class).getResultList().get(0));
            }
        }
    }

    @Test
    void concurrentDuplicateCreatesSerializeUnderTheCompanyLock() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("party-concurrent")))
        {
            var start = new java.util.concurrent.CountDownLatch(1);
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try
            {
                java.util.concurrent.Callable<Boolean> attempt = () ->
                {
                    start.await();
                    try
                    {
                        new CounterpartyAdminService(jpa).save(command(null, "Same Party", true));
                        return true;
                    }
                    catch (IllegalArgumentException duplicate)
                    {
                        assertTrue(duplicate.getMessage().contains("already exists"));
                        return false;
                    }
                };
                var first = pool.submit(attempt);
                var second = pool.submit(attempt);
                start.countDown();
                assertNotEquals(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                        second.get(15, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(1, new CounterpartyAdminService(jpa).listForMaintenance().size());
                assertEquals(1L, audits(jpa));
            }
            finally
            {
                pool.shutdownNow();
            }
        }
    }

    private static CounterpartyCommand command(Long id, String name, boolean active)
    {
        return new CounterpartyCommand(id, name, CounterpartyKind.OTHER, null, null, null, active);
    }

    private static long audits(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(a) from AuditEvent a where a.entityType = 'COUNTERPARTY'", Long.class)
                    .getSingleResult();
        }
    }

    private static AuthenticatedUserSession session(long userId, String company, ReservedSecurityRole role)
    {
        Instant now = Instant.parse("2026-10-06T22:00:00Z");
        return new AuthenticatedUserSession(userId, "operator", "Operator", company, Set.of(role), now, now);
    }
}
