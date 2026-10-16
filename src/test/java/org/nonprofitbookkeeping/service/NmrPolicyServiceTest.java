package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.h2.api.Trigger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NmrPolicyServiceTest
{
    @TempDir Path directory;
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);

    public static NmrPolicyTerms terms(String category, LocalDate from, LocalDate through, String rate)
    {
        return new NmrPolicyTerms("test-policy", category, from, through,
                rate == null ? null : new BigDecimal(rate), "usd", "Event date", "Source rounding rule",
                "Source eligibility", "Source exemptions", "Source recipient", "Source classification",
                "Source payment path", "Test source reference", "Test authority", FROM.minusDays(1));
    }

    private static AuthenticatedUserSession session(String company, ReservedSecurityRole role)
    {
        Instant now = Instant.now();
        return new AuthenticatedUserSession(9001L, "policy-operator", "Policy Operator", company, Set.of(role), now, now);
    }

    private static NmrPolicyService service(Jpa jpa, String company, ReservedSecurityRole role)
    {
        return new NmrPolicyService(jpa, () -> company,
                new AuthorizationGuard(jpa, () -> Optional.of(session(company, role))));
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            for (String code : new String[]{"DEFAULT", "OTHER"})
            {
                if (em.createQuery("from Company where code = :code", Company.class).setParameter("code", code).getResultList().isEmpty())
                {
                    Company company = new Company();
                    company.setCode(code);
                    company.setDisplayName(code);
                    em.persist(company);
                }
            }
            em.getTransaction().commit();
        }
    }

    private static long count(Jpa jpa, String entity)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
        }
    }

    private static long audits(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(a) from AuditEvent a where a.entityType = 'NMR_POLICY_VERSION'", Long.class).getSingleResult();
        }
    }

    @Test
    void explicitTermsAndRetrySurviveRestartWithoutAccountingWrites()
    {
        Path database = directory.resolve("restart");
        UUID request = UUID.randomUUID();
        var input = terms("STANDARD", FROM, null, "15.1250");
        Long id;
        try (Jpa jpa = new Jpa(database))
        {
            seed(jpa);
            var service = service(jpa, "DEFAULT", ReservedSecurityRole.MANAGER);
            assertTrue(service.list().isEmpty(), "Migration must not seed a policy or rate.");
            id = service.save(request, input).id();
            assertEquals(id, service.save(request, input).id());
            assertEquals(1, audits(jpa));
            for (String entity : new String[]{"Txn", "TxnSplit", "FundTransfer", "TxnSupplementalLine"})
            {
                assertEquals(0, count(jpa, entity), entity + " must remain unchanged");
            }
        }
        try (Jpa jpa = new Jpa(database))
        {
            var service = service(jpa, "DEFAULT", ReservedSecurityRole.ADMIN);
            var saved = service.list().get(0);
            assertEquals(id, saved.id());
            assertEquals(request, saved.portableId());
            assertEquals(new BigDecimal("15.1250"), saved.terms().rate());
            assertEquals("USD", saved.terms().currency());
            assertEquals("TEST-POLICY", saved.terms().policyCode());
            assertEquals("Test source reference", saved.terms().sourceReference());
            assertEquals("policy-operator", saved.savedBy());
            assertNotNull(saved.savedAt());
            assertEquals(id, service.save(request, input).id());
            assertThrows(IllegalArgumentException.class, () -> service.save(request, terms("STANDARD", FROM, null, "15.12")));
            assertEquals(1, count(jpa, "NmrPolicyVersion"));
            assertEquals(1, audits(jpa));
        }
    }

    @Test
    void inclusiveIntervalsAreIndependentByCategoryAndCompanyAndEndRetainsTerms()
    {
        try (Jpa jpa = new Jpa(directory.resolve("dates")))
        {
            seed(jpa);
            var service = service(jpa, "DEFAULT", ReservedSecurityRole.MANAGER);
            UUID request = UUID.randomUUID();
            var original = terms("STANDARD", FROM, null, "0");
            var first = service.save(request, original);
            assertThrows(IllegalArgumentException.class, () -> service.save(UUID.randomUUID(), terms("STANDARD", FROM.plusYears(1), null, "1")));
            assertThrows(IllegalArgumentException.class, () -> service.endVersion(first.id(), FROM, " "));
            assertThrows(IllegalArgumentException.class, () -> service.endVersion(first.id(), FROM.minusDays(1), "reason"));
            var ended = service.endVersion(first.id(), FROM.plusDays(9), "Source superseded");
            assertEquals(new BigDecimal("0.0000"), ended.terms().rate());
            assertEquals(original.sourceReference(), ended.terms().sourceReference());
            assertEquals(ended.id(), service.save(request, original).id(), "Exact original retry remains valid after interval shortening.");
            service.endVersion(first.id(), FROM.plusDays(9), "Repeat");
            assertEquals(2, audits(jpa));
            assertThrows(IllegalArgumentException.class, () -> service.endVersion(first.id(), FROM.plusDays(10), "Cannot extend"));
            assertThrows(IllegalArgumentException.class, () -> service.save(UUID.randomUUID(), terms("STANDARD", FROM.plusDays(9), null, "1")));
            service.save(UUID.randomUUID(), terms("STANDARD", FROM.plusDays(10), FROM.plusDays(20), "1"));
            service.save(UUID.randomUUID(), terms("STANDARD", FROM.plusDays(30), null, "2"));
            service.save(UUID.randomUUID(), terms("EXPLICIT-OTHER-CATEGORY", FROM, null, "3"));
            var other = service(jpa, "OTHER", ReservedSecurityRole.ADMIN);
            other.save(UUID.randomUUID(), original);
            assertEquals(4, service.list().size());
            assertEquals(1, other.list().size());
            assertThrows(CompanyOwnershipException.class, () -> other.save(request, original));
            assertThrows(CompanyOwnershipException.class, () -> other.endVersion(first.id(), FROM, "Foreign"));
            try (EntityManager em = jpa.em())
            {
                AuditEvent audit = em.createQuery("from AuditEvent where actionType = 'NMR_POLICY_ENDED'", AuditEvent.class).getSingleResult();
                assertEquals("Source superseded", audit.getReason());
                assertTrue(audit.getBeforeValue().contains("validTo=null"));
                assertTrue(audit.getAfterValue().contains("validTo=2026-01-10"));
                assertEquals("policy-operator", audit.getActor());
                assertEquals("DEFAULT", audit.getCompany().getCode());
            }
        }
    }

    @Test
    void invalidTermsLeaveNoPolicyOrFactualAuditAndExplicitZeroIsAllowed()
    {
        try (Jpa jpa = new Jpa(directory.resolve("invalid")))
        {
            seed(jpa);
            var service = service(jpa, "DEFAULT", ReservedSecurityRole.MANAGER);
            for (String rate : new String[]{null, "-0.01", "1.00001", "1000000000000000"})
            {
                assertThrows(IllegalArgumentException.class, () -> service.save(UUID.randomUUID(), terms("STANDARD", FROM, null, rate)));
            }
            assertThrows(IllegalArgumentException.class, () -> service.save(UUID.randomUUID(), terms(" ", FROM, null, "1")));
            assertThrows(IllegalArgumentException.class, () -> service.save(UUID.randomUUID(), terms("X".repeat(81), FROM, null, "1")));
            assertThrows(IllegalArgumentException.class, () -> service.save(UUID.randomUUID(), terms("STANDARD", FROM, FROM.minusDays(1), "1")));
            var valid = terms("STANDARD", FROM, null, "0");
            for (String currency : new String[]{"", "ZZZ", "USDD"})
            {
                var bad = new NmrPolicyTerms(valid.policyCode(), valid.category(), valid.validFrom(), null, valid.rate(), currency,
                        valid.dateBasis(), valid.roundingRule(), valid.eligibility(), valid.exemptions(), valid.recipient(), valid.reporting(),
                        valid.merchantPath(), valid.sourceReference(), valid.authority(), valid.sourceDate());
                assertThrows(IllegalArgumentException.class, () -> service.save(UUID.randomUUID(), bad));
            }
            assertEquals(0, count(jpa, "NmrPolicyVersion"));
            assertEquals(0, audits(jpa));
            assertEquals(new BigDecimal("0.0000"), service.save(UUID.randomUUID(), valid).terms().rate());
        }
    }

    @Test
    void serviceEnforcesRolesSessionAndCompanyForReadsAndWrites()
    {
        try (Jpa jpa = new Jpa(directory.resolve("permissions")))
        {
            seed(jpa);
            var input = terms("STANDARD", FROM, null, "1");
            var saved = service(jpa, "DEFAULT", ReservedSecurityRole.MANAGER).save(UUID.randomUUID(), input);
            for (ReservedSecurityRole role : new ReservedSecurityRole[]{ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT})
            {
                var readOnly = service(jpa, "DEFAULT", role);
                assertEquals(1, readOnly.list().size());
                assertThrows(AuthorizationException.class, () -> readOnly.save(UUID.randomUUID(), input));
                assertThrows(AuthorizationException.class, () -> readOnly.endVersion(saved.id(), FROM, "reason"));
            }
            var session = new AtomicReference<>(Optional.of(session("OTHER", ReservedSecurityRole.ADMIN)));
            var guarded = new NmrPolicyService(jpa, () -> "DEFAULT", new AuthorizationGuard(jpa, session::get));
            assertThrows(AuthorizationException.class, guarded::list);
            assertThrows(AuthorizationException.class, () -> guarded.save(UUID.randomUUID(), input));
            session.set(Optional.empty());
            assertThrows(AuthorizationException.class, guarded::list);
            assertThrows(AuthorizationException.class, () -> guarded.endVersion(saved.id(), FROM, "reason"));
            assertEquals(1, count(jpa, "NmrPolicyVersion"));
            assertEquals(1, audits(jpa));
        }
    }

    @Test
    void sourceEvidenceIsRequiredAndInactiveCompanyCannotWrite()
    {
        try (Jpa jpa = new Jpa(directory.resolve("evidence")))
        {
            seed(jpa);
            var service = service(jpa, "DEFAULT", ReservedSecurityRole.ADMIN);
            var valid = terms("STANDARD", FROM, null, "1");
            var missingSource = new NmrPolicyTerms(valid.policyCode(), valid.category(), valid.validFrom(), null,
                    valid.rate(), valid.currency(), valid.dateBasis(), valid.roundingRule(), valid.eligibility(),
                    valid.exemptions(), valid.recipient(), valid.reporting(), valid.merchantPath(), " ",
                    valid.authority(), valid.sourceDate());
            assertThrows(IllegalArgumentException.class, () -> service.save(UUID.randomUUID(), missingSource));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createQuery("update Company set active = false where code = 'DEFAULT'").executeUpdate();
                em.getTransaction().commit();
            }
            assertThrows(IllegalStateException.class, () -> service.save(UUID.randomUUID(), valid));
            assertTrue(service.list().isEmpty());
            assertEquals(0, audits(jpa));
        }
    }

    @Test
    void concurrentOverlappingSavesHaveOnlyOneWinner() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("concurrent")))
        {
            seed(jpa);
            var service = service(jpa, "DEFAULT", ReservedSecurityRole.MANAGER);
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            Callable<Boolean> save = () ->
            {
                ready.countDown();
                assertTrue(go.await(10, TimeUnit.SECONDS));
                try
                {
                    service.save(UUID.randomUUID(), terms("STANDARD", FROM, null, "1"));
                    return true;
                }
                catch (IllegalArgumentException expected)
                {
                    return false;
                }
            };
            try
            {
                Future<Boolean> one = pool.submit(save);
                Future<Boolean> two = pool.submit(save);
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                go.countDown();
                assertNotEquals(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
                assertEquals(1, count(jpa, "NmrPolicyVersion"));
                assertEquals(1, audits(jpa));
            }
            finally
            {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void auditFailureRollsBackCreateAndEndTogether()
    {
        try (Jpa jpa = new Jpa(directory.resolve("rollback")))
        {
            seed(jpa);
            var service = service(jpa, "DEFAULT", ReservedSecurityRole.MANAGER);
            var first = service.save(UUID.randomUUID(), terms("STANDARD", FROM, null, "1"));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("CREATE TRIGGER fail_policy_audit BEFORE INSERT ON audit_event FOR EACH ROW CALL \""
                        + FailingAudit.class.getName() + "\"").executeUpdate();
                em.getTransaction().commit();
            }
            assertThrows(RuntimeException.class, () -> service.save(UUID.randomUUID(), terms("OTHER", FROM, null, "2")));
            assertThrows(RuntimeException.class, () -> service.endVersion(first.id(), FROM, "must rollback"));
            assertNull(service.list().get(0).terms().validTo());
            assertEquals(1, count(jpa, "NmrPolicyVersion"));
            assertEquals(1, audits(jpa));
        }
    }

    public static class FailingAudit implements Trigger
    {
        @Override
        public void fire(Connection connection, Object[] before, Object[] after) throws SQLException
        {
            throw new SQLException("Intentional audit failure");
        }
    }
}
