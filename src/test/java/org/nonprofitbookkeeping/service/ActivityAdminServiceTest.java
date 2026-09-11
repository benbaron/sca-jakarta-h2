package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeFormat;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityAdminServiceTest
{
    @Test
    void stableIdEditPreservesJournalLinkAndActiveReferenceChoices(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("activity-stable-id")))
        {
            new SampleCompanyService(jpa).createOrRefresh();
            ActivityAdminService service = new ActivityAdminService(jpa);
            ActivityView created = service.save(new ActivityCommand(null, "P21-EVENT", "P21 Event", true));
            long splitId = seedTransactionReference(jpa, created.id());

            ActivityView deactivated = service.save(new ActivityCommand(
                    created.id(), "P21-RENAMED", "Renamed P21 Event", false));

            assertEquals(created.id(), deactivated.id());
            assertEquals(created.id(), referencedActivityId(jpa, splitId));
            assertEquals("P21-RENAMED", referencedActivityCode(jpa, splitId));
            assertEquals(1L, activityCount(jpa, created.id()));
            assertFalse(activeReferenceIds(jpa).contains(created.id()));

            ActivityView reactivated = service.save(new ActivityCommand(
                    created.id(), "P21-RENAMED", "Renamed P21 Event", true));
            assertEquals(created.id(), reactivated.id());
            assertTrue(activeReferenceIds(jpa).contains(created.id()));
            assertEquals("P21-RENAMED", activeReferenceCode(jpa, created.id()));

            assertEquals(1L, auditCount(jpa, created.id(), "ACTIVITY_CREATED", "SYSTEM"));
            assertEquals(1L, auditCount(jpa, created.id(), "ACTIVITY_DEACTIVATED", "SYSTEM"));
            assertEquals(1L, auditCount(jpa, created.id(), "ACTIVITY_REACTIVATED", "SYSTEM"));
        }
    }

    @Test
    void protectedDeleteChecksJournalAndInterchangeHistory(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("activity-delete")))
        {
            new SampleCompanyService(jpa).createOrRefresh();
            ActivityAdminService service = new ActivityAdminService(jpa);

            ActivityView unused = service.save(new ActivityCommand(null, "UNUSED-ACT", "Unused Activity", true));
            assertTrue(service.usage(unused.id()).canDelete());
            service.deleteUnused(unused.id());
            assertEquals(0L, activityCount(jpa, unused.id()));
            assertEquals(1L, auditCount(jpa, unused.id(), "ACTIVITY_DELETED", "SYSTEM"));

            ActivityView journalUsed = service.save(new ActivityCommand(null, "JOURNAL-ACT", "Journal Activity", true));
            seedTransactionReference(jpa, journalUsed.id());
            ActivityUsage journalUsage = service.usage(journalUsed.id());
            assertEquals(1L, journalUsage.transactionSplits());
            assertFalse(journalUsage.canDelete());
            assertThrows(IllegalStateException.class, () -> service.deleteUnused(journalUsed.id()));
            ActivityView deactivatedJournal = service.save(new ActivityCommand(
                    journalUsed.id(), journalUsed.code(), journalUsed.name(), false));
            assertFalse(deactivatedJournal.active());

            ActivityView exchanged = service.save(new ActivityCommand(null, "SCLX-ACT", "SCLX Activity", true));
            new InterchangeIdentityService(jpa, new CompanyOwnershipService(jpa)).record(
                    "DEFAULT",
                    InterchangeFormat.SCLX,
                    "P21-test",
                    "ACTIVITY",
                    "activity:DEFAULT:SCLX-ACT",
                    "a".repeat(64),
                    Long.toString(exchanged.id()));
            ActivityUsage interchangeUsage = service.usage(exchanged.id());
            assertEquals(1L, interchangeUsage.interchangeIdentities());
            assertFalse(interchangeUsage.canDelete());
            assertThrows(IllegalStateException.class, () -> service.deleteUnused(exchanged.id()));
            assertFalse(service.save(new ActivityCommand(
                    exchanged.id(), exchanged.code(), exchanged.name(), false)).active());
        }
    }

    @Test
    void validationAndCrossCompanyWritesFailAtomically(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("activity-validation")))
        {
            ActivityAdminService service = new ActivityAdminService(jpa);
            ActivityView alpha = service.save(new ActivityCommand(null, "Alpha", "Alpha Activity", true));
            ActivityView beta = service.save(new ActivityCommand(null, "Beta", "Beta Activity", true));

            assertThrows(IllegalArgumentException.class,
                    () -> service.save(new ActivityCommand(null, " alpha ", "Duplicate", true)));
            assertThrows(IllegalArgumentException.class,
                    () -> service.save(new ActivityCommand(null, "BLANK-NAME", "  ", true)));
            assertThrows(IllegalArgumentException.class,
                    () -> service.save(new ActivityCommand(beta.id(), "ALPHA", "Collision", true)));
            assertEquals("Beta", activityCode(jpa, beta.id()));
            assertEquals("Beta Activity", activityName(jpa, beta.id()));

            long foreignId = seedForeignActivity(jpa);
            assertThrows(CompanyOwnershipException.class,
                    () -> service.save(new ActivityCommand(foreignId, "FOREIGN-EDIT", "Foreign Rewrite", false)));
            assertEquals("FOREIGN", activityCode(jpa, foreignId));
            assertTrue(activityActive(jpa, foreignId));

            ActivityLookupService lookup = new ActivityLookupService(jpa, () -> "DEFAULT");
            assertTrue(lookup.listAllActivities().stream().anyMatch(row -> row.id().equals(alpha.id())));
            assertFalse(lookup.listAllActivities().stream().anyMatch(row -> row.id().equals(foreignId)));
        }
    }

    @Test
    void authorizationTracksCurrentSessionAndAuditUsesAuthenticatedActor(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("activity-authorization")))
        {
            AtomicReference<Optional<AuthenticatedUserSession>> current =
                    new AtomicReference<>(Optional.of(session("DEFAULT", "viewer", ReservedSecurityRole.VIEWER)));
            ActivityAdminService service = new ActivityAdminService(
                    jpa,
                    () -> "DEFAULT",
                    new AuthorizationGuard(jpa, current::get));

            assertThrows(AuthorizationException.class,
                    () -> service.save(new ActivityCommand(null, "AUTH-ACT", "Authorization Activity", true)));
            assertEquals(0L, activityCountByCode(jpa, "AUTH-ACT"));

            current.set(Optional.of(session("DEFAULT", "accountant", ReservedSecurityRole.ACCOUNTANT)));
            ActivityView created = service.save(new ActivityCommand(
                    null, "AUTH-ACT", "Authorization Activity", true));
            assertEquals(1L, auditCount(jpa, created.id(), "ACTIVITY_CREATED", "accountant"));

            current.set(Optional.of(session("DEFAULT", "manager", ReservedSecurityRole.MANAGER)));
            service.save(new ActivityCommand(created.id(), "AUTH-MANAGER", "Manager Edit", true));
            assertEquals("Manager Edit", activityName(jpa, created.id()));
            assertEquals(1L, auditCount(jpa, created.id(), "ACTIVITY_UPDATED", "manager"));

            current.set(Optional.of(session("DEFAULT", "admin", ReservedSecurityRole.ADMIN)));
            service.save(new ActivityCommand(created.id(), "AUTH-ADMIN", "Admin Edit", true));
            assertEquals("Admin Edit", activityName(jpa, created.id()));

            current.set(Optional.of(session(
                    "DEFAULT",
                    "union",
                    Set.of(ReservedSecurityRole.VIEWER, ReservedSecurityRole.ACCOUNTANT))));
            service.save(new ActivityCommand(created.id(), "AUTH-UNION", "Union Edit", true));
            assertEquals("Union Edit", activityName(jpa, created.id()));

            current.set(Optional.of(session("DEFAULT", "viewer", ReservedSecurityRole.VIEWER)));
            assertThrows(AuthorizationException.class,
                    () -> service.save(new ActivityCommand(created.id(), "DENIED", "Viewer Rewrite", false)));
            assertEquals("AUTH-UNION", activityCode(jpa, created.id()));

            current.set(Optional.of(session("OTHER", "accountant", ReservedSecurityRole.ACCOUNTANT)));
            assertThrows(AuthorizationException.class,
                    () -> service.save(new ActivityCommand(created.id(), "WRONG-CO", "Wrong Company", false)));
            assertEquals("AUTH-UNION", activityCode(jpa, created.id()));

            current.set(Optional.empty());
            assertThrows(AuthorizationException.class,
                    () -> service.save(new ActivityCommand(created.id(), "NO-SESSION", "No Session", false)));
            assertEquals("AUTH-UNION", activityCode(jpa, created.id()));
        }
    }

    private static long seedTransactionReference(Jpa jpa, long activityId)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = company(em, "DEFAULT");
            Account account = em.createQuery(
                            "from Account a where a.chart.company = :company order by a.id", Account.class)
                    .setParameter("company", company)
                    .setMaxResults(1)
                    .getSingleResult();
            Fund fund = em.createQuery(
                            "from Fund f where f.company = :company order by f.id", Fund.class)
                    .setParameter("company", company)
                    .setMaxResults(1)
                    .getSingleResult();
            Activity activity = em.find(Activity.class, activityId);

            Txn txn = new Txn();
            txn.setCompany(company);
            txn.setTxnDate(LocalDate.of(2026, 9, 1));
            txn.setMemo("P21 Activity reference");
            em.persist(txn);

            TxnSplit split = new TxnSplit();
            split.setTxn(txn);
            split.setAccount(account);
            split.setFund(fund);
            split.setActivity(activity);
            split.setAmountSigned(BigDecimal.TEN);
            em.persist(split);
            em.flush();
            long id = split.getId();
            em.getTransaction().commit();
            return id;
        }
    }

    private static long seedForeignActivity(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company other = new Company();
            other.setCode("OTHER");
            other.setDisplayName("Other Company");
            other.setDefaultCurrency("USD");
            em.persist(other);
            Activity activity = new Activity();
            activity.setCompany(other);
            activity.setCode("FOREIGN");
            activity.setName("Foreign Activity");
            activity.setActive(true);
            em.persist(activity);
            em.flush();
            long id = activity.getId();
            em.getTransaction().commit();
            return id;
        }
    }

    private static Set<Long> activeReferenceIds(Jpa jpa)
    {
        return new TransactionReferenceDataService(jpa, () -> "DEFAULT")
                .loadActiveReferenceData()
                .activities()
                .stream()
                .map(org.nonprofitbookkeeping.ui.TransactionLineEditorModel.Option::id)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static String activeReferenceCode(Jpa jpa, long activityId)
    {
        return new TransactionReferenceDataService(jpa, () -> "DEFAULT")
                .loadActiveReferenceData()
                .activities()
                .stream()
                .filter(option -> option.id().equals(activityId))
                .findFirst()
                .orElseThrow()
                .code();
    }

    private static Long referencedActivityId(Jpa jpa, long splitId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "select s.activity.id from TxnSplit s where s.id = :id", Long.class)
                    .setParameter("id", splitId)
                    .getSingleResult();
        }
    }

    private static String referencedActivityCode(Jpa jpa, long splitId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "select s.activity.code from TxnSplit s where s.id = :id", String.class)
                    .setParameter("id", splitId)
                    .getSingleResult();
        }
    }

    private static long activityCount(Jpa jpa, long id)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(a) from Activity a where a.id = :id", Long.class)
                    .setParameter("id", id)
                    .getSingleResult();
        }
    }

    private static long activityCountByCode(Jpa jpa, String code)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(a) from Activity a where a.code = :code", Long.class)
                    .setParameter("code", code)
                    .getSingleResult();
        }
    }

    private static String activityCode(Jpa jpa, long id)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select a.code from Activity a where a.id = :id", String.class)
                    .setParameter("id", id)
                    .getSingleResult();
        }
    }

    private static String activityName(Jpa jpa, long id)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select a.name from Activity a where a.id = :id", String.class)
                    .setParameter("id", id)
                    .getSingleResult();
        }
    }

    private static boolean activityActive(Jpa jpa, long id)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select a.active from Activity a where a.id = :id", Boolean.class)
                    .setParameter("id", id)
                    .getSingleResult();
        }
    }

    private static long auditCount(Jpa jpa, long activityId, String action, String actor)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "select count(e) from AuditEvent e where e.entityType = 'ACTIVITY' "
                                    + "and e.entityId = :id and e.actionType = :action and e.actor = :actor",
                            Long.class)
                    .setParameter("id", Long.toString(activityId))
                    .setParameter("action", action)
                    .setParameter("actor", actor)
                    .getSingleResult();
        }
    }

    private static Company company(EntityManager em, String code)
    {
        return em.createQuery("from Company c where c.code = :code", Company.class)
                .setParameter("code", code)
                .getSingleResult();
    }

    private static AuthenticatedUserSession session(
            String companyCode,
            String username,
            ReservedSecurityRole role)
    {
        return session(companyCode, username, Set.of(role));
    }

    private static AuthenticatedUserSession session(
            String companyCode,
            String username,
            Set<ReservedSecurityRole> roles)
    {
        Instant now = Instant.parse("2026-09-07T23:30:00Z");
        return new AuthenticatedUserSession(
                21L,
                username,
                username,
                companyCode,
                roles,
                now,
                now);
    }
}
