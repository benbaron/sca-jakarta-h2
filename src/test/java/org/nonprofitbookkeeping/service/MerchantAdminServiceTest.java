package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.interchange.sclx.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MerchantAdminServiceTest
{
    @TempDir Path directory;

    @Test
    void renameAndDeactivationPreserveMixedLinesPortableIdentityAuditAndInterchangeAcrossRestart() throws Exception
    {
        Path database = directory.resolve("merchant-history");
        Long id;
        Long second;
        Long transaction;
        UUID portable;
        try (Jpa jpa = new Jpa(database))
        {
            var service = new MerchantAdminService(jpa);
            var created = service.save(new MerchantCommand(null, "  Supplies  ", " Original ", true));
            id = created.id();
            second = service.save(command(null, "Processor", true)).id();
            assertEquals("Supplies", created.name());
            assertEquals("Original", created.notes());
            try (EntityManager em = jpa.em())
            {
                portable = em.find(Merchant.class, id).getPortableId();
            }
            transaction = enterMixed(jpa, id, second);
            service.save(new MerchantCommand(id, "Historic Supplies", "Retained", false));
            var refs = new TransactionReferenceDataService(jpa).loadActiveReferenceData();
            assertEquals(java.util.List.of(second), refs.merchants().stream().map(o -> o.id()).toList());
            assertEquals("Historic Supplies", refs.retainedMerchants().stream().filter(o -> id.equals(o.id())).findFirst().orElseThrow().name());
            assertEquals(2, service.listForMaintenance().size());
            assertEquals(3L, audits(jpa));
            try (EntityManager em = jpa.em())
            {
                var audit = em.createQuery("from AuditEvent a where a.actionType = 'MERCHANT_UPDATED'",
                        org.nonprofitbookkeeping.model.AuditEvent.class).getSingleResult();
                assertTrue(audit.getBeforeValue().contains("name=Supplies;"));
                assertTrue(audit.getAfterValue().contains("active=false"));
                assertEquals("MERCHANT", audit.getEntityType());
            }
        }
        try (Jpa jpa = new Jpa(database); Jpa target = new Jpa(directory.resolve("merchant-import")))
        {
            try (EntityManager em = jpa.em())
            {
                var merchant = em.find(Merchant.class, id);
                assertEquals(portable, merchant.getPortableId());
                assertEquals("Historic Supplies", merchant.getName());
                assertFalse(merchant.isActive());
            }
            var entry = new TransactionEntryService(jpa);
            var loaded = entry.load(transaction);
            assertEquals(java.util.List.of(id, second), loaded.lines().stream().map(TransactionView.Line::merchantId).toList());
            assertEquals("Historic Supplies", loaded.lines().get(0).merchantName());
            assertEquals(loaded.debitTotal(), loaded.creditTotal());
            var reversed = new TransactionCorrectionService(jpa).reverse(transaction,
                    LocalDate.of(2026, 1, 2), "test", "Preserve mixed Merchant history", false);
            assertEquals(java.util.List.of(id, second), entry.load(reversed.reversalTransactionId()).lines().stream()
                    .map(TransactionView.Line::merchantId).toList());
            Path file = directory.resolve("merchant.sclx");
            java.nio.file.Files.write(file, new org.nonprofitbookkeeping.interchange.sclx.SclxJsonSerializer().serialize(
                    new SclxCoreSnapshotQueryService(jpa).query(Instant.parse("2026-02-01T00:00:00Z"))));
            var preview = new SclxImportPreviewService(target, () -> "DEFAULT").preview(file);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            var result = new SclxImportCommitService(target, () -> "DEFAULT").commit(file, preview, "tester", true, true, false);
            assertTrue(result.committed(), () -> result.messages().toString());
            try (EntityManager em = target.em())
            {
                var imported = em.createQuery("from Merchant m where m.portableId = :id", Merchant.class)
                        .setParameter("id", portable).getSingleResult();
                assertFalse(imported.isActive());
                assertEquals("Historic Supplies", imported.getName());
                assertEquals("Retained", imported.getNotes());
                var links = em.createQuery("from TxnSplit s where s.merchant = :merchant", org.nonprofitbookkeeping.model.TxnSplit.class)
                        .setParameter("merchant", imported).getResultList();
                assertEquals(2, links.size(), "Original and reversal retain portable line Merchant links");
            }
            serviceReactivate(jpa, id);
        }
    }

    private static void serviceReactivate(Jpa jpa, Long id)
    {
        new MerchantAdminService(jpa).save(command(id, "Historic Supplies", true));
        assertTrue(new TransactionReferenceDataService(jpa).loadActiveReferenceData().merchants().stream().anyMatch(o -> id.equals(o.id())));
    }

    private static Long enterMixed(Jpa jpa, Long first, Long second)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            var company = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
            var chart = new org.nonprofitbookkeeping.model.ChartOfAccounts();
            chart.setCompany(company); chart.setName("Merchant fixture"); chart.setVersion("1");
            chart.setStatus(org.nonprofitbookkeeping.model.ChartStatus.ACTIVE); em.persist(chart);
            company.setActiveChartOfAccounts(chart);
            var expense = new org.nonprofitbookkeeping.model.Account();
            expense.setChart(chart); expense.setCode("5000"); expense.setName("Expense");
            expense.setAccountType(org.nonprofitbookkeeping.model.AccountType.EXPENSE);
            expense.setNormalBalance(org.nonprofitbookkeeping.model.NormalBalance.DEBIT); em.persist(expense);
            var cash = new org.nonprofitbookkeeping.model.Account();
            cash.setChart(chart); cash.setCode("1000"); cash.setName("Cash");
            cash.setAccountType(org.nonprofitbookkeeping.model.AccountType.ASSET);
            cash.setNormalBalance(org.nonprofitbookkeeping.model.NormalBalance.DEBIT); em.persist(cash);
            var fund = new org.nonprofitbookkeeping.model.Fund();
            fund.setCompany(company); fund.setCode("OPERATING"); fund.setName("Operating");
            fund.setFundType(org.nonprofitbookkeeping.model.FundType.UNRESTRICTED); em.persist(fund);
            em.flush(); em.getTransaction().commit();
            return new TransactionEntryService(jpa).enter(new TransactionCommand(LocalDate.of(2026, 1, 1), null,
                    "Mixed Merchants", null, java.util.List.of(
                    new TransactionLineCommand(expense.getId(), fund.getId(), null, null, first,
                            java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO, false, "Supplies"),
                    new TransactionLineCommand(cash.getId(), fund.getId(), null, null, second,
                            java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN, false, "Processor")))).id();
        }
    }

    @Test
    void invalidAndDuplicateSavesChangeNeitherMasterNorAudit()
    {
        try (Jpa jpa = new Jpa(directory.resolve("party-validation")))
        {
            var service = new MerchantAdminService(jpa);
            var first = service.save(command(null, "Existing", false));
            var second = service.save(command(null, "Second", true));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(null, " existing ", true)));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(second.id(), "EXISTING", true)));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(null, " ", true)));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(null, "x".repeat(201), true)));
            assertThrows(IllegalArgumentException.class, () -> service.save(command(Long.MAX_VALUE, "Unknown", true)));
            assertEquals(2L, audits(jpa));
            assertEquals("Existing", service.listForMaintenance().get(0).name());
            assertFalse(service.listForMaintenance().get(0).active());
        }
    }

    @Test
    void auditFailureRollsBackAnAlreadyFlushedMerchantChange()
    {
        try (Jpa jpa = new Jpa(directory.resolve("merchant-audit-rollback")))
        {
            var service = new MerchantAdminService(jpa);
            var original = service.save(new MerchantCommand(null, "Original", "Keep", true));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("ALTER TABLE audit_event ADD CONSTRAINT reject_merchant_update "
                        + "CHECK (action_type <> 'MERCHANT_UPDATED')").executeUpdate();
                em.getTransaction().commit();
            }
            assertThrows(RuntimeException.class, () -> service.save(
                    new MerchantCommand(original.id(), "Changed", "Replace", false)));
            assertEquals(original, service.listForMaintenance().get(0));
            assertEquals(1L, audits(jpa));
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
            var service = new MerchantAdminService(jpa, company::get, guard);
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
                assertEquals("Alice", em.find(Merchant.class, alice.id()).getName());
                assertEquals("operator", em.createQuery("select a.actor from AuditEvent a where a.actionType = 'MERCHANT_CREATED'",
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
                        new MerchantAdminService(jpa).save(command(null, "Same Party", true));
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
                assertEquals(1, new MerchantAdminService(jpa).listForMaintenance().size());
                assertEquals(1L, audits(jpa));
            }
            finally
            {
                pool.shutdownNow();
            }
        }
    }

    private static MerchantCommand command(Long id, String name, boolean active)
    {
        return new MerchantCommand(id, name, null, active);
    }

    private static long audits(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(a) from AuditEvent a where a.entityType = 'MERCHANT'", Long.class)
                    .getSingleResult();
        }
    }

    private static AuthenticatedUserSession session(long userId, String company, ReservedSecurityRole role)
    {
        Instant now = Instant.parse("2026-10-06T22:00:00Z");
        return new AuthenticatedUserSession(userId, "operator", "Operator", company, Set.of(role), now, now);
    }
}
