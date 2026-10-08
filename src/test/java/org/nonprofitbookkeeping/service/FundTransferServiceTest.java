package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.SemanticAccountingReportQueryService;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.FundTransferTestFixture.*;

class FundTransferServiceTest
{
    @TempDir Path directory;

    @Test
    void saveBalancesBothFundsAndCompanyWithoutBankOrIncomeMovement()
    {
        try (Jpa jpa = new Jpa(directory.resolve("balanced")))
        {
            var f = seed(jpa);
            var service = new FundTransferService(jpa);
            var saved = service.save(f.command(UUID.randomUUID(), "75"));
            try (EntityManager em = jpa.em())
            {
                List<TxnSplit> lines = em.createQuery("from TxnSplit s where s.txn.id = :id", TxnSplit.class)
                        .setParameter("id", saved.transactionId()).getResultList();
                assertEquals(4, lines.size());
                assertTrue(lines.stream().allMatch(s -> Set.of(f.allocation(), f.equity()).contains(s.getAccount().getId())));
                assertMoney("-75", balance(em, f.source(), f.allocation(), DATE));
                assertMoney("-75", balance(em, f.source(), f.equity(), DATE));
                assertMoney("75", balance(em, f.destination(), f.allocation(), DATE));
                assertMoney("75", balance(em, f.destination(), f.equity(), DATE));
                assertMoney("200", balance(em, f.source(), f.bank(), DATE));
                assertMoney("200", balance(em, f.source(), f.income(), DATE));
                assertEquals(1L, em.createQuery("select count(a) from AuditEvent a where a.actionType = 'FUND_TRANSFER_CREATED'", Long.class).getSingleResult());
            }
            assertEquals(1, new SemanticAccountingReportQueryService(jpa, () -> "DEFAULT").postedFundTransfers(OPENING, DATE, 100).size());
            assertEquals(1, service.list().size());
            var reports = new FinancialReportService(jpa, () -> "DEFAULT");
            assertMoney("200", reports.balanceSheet(DATE, null).totalAssets());
            assertMoney("200", reports.balanceSheet(DATE, null).liabilitiesAndEquity());
            assertMoney("125", reports.balanceSheet(DATE, "GENERAL").totalAssets());
            assertMoney("75", reports.balanceSheet(DATE, "DESIGNATED").totalAssets());
            assertTrue(reports.balanceSheet(DATE, "GENERAL").isBalanced());
            assertTrue(reports.balanceSheet(DATE, "DESIGNATED").isBalanced());
        }
    }

    @Test
    void requestIdentitySurvivesRestartAndRejectsChangedOrForeignReuse()
    {
        Path database = directory.resolve("retry");
        UUID request = UUID.randomUUID();
        FundTransferTestFixture f;
        FundTransferView first;
        try (Jpa jpa = new Jpa(database))
        {
            f = seed(jpa);
            first = new FundTransferService(jpa).save(f.command(request, "75"));
        }
        try (Jpa jpa = new Jpa(database))
        {
            var service = new FundTransferService(jpa);
            assertEquals(first.id(), service.save(f.command(request, "75.0000")).id());
            assertThrows(IllegalArgumentException.class, () -> service.save(f.command(request, "76")));
            change(jpa, em ->
            {
                Company other = new Company();
                other.setCode("OTHER");
                other.setDisplayName("Other");
                em.persist(other);
            });
            assertThrows(CompanyOwnershipException.class, () -> new FundTransferService(jpa, () -> "OTHER", null).save(f.command(request, "75")));
            assertEquals(2L, count(jpa, "Txn"));
            assertEquals(1L, count(jpa, "FundTransfer"));
        }
    }

    @Test
    void concurrentTransfersCannotOverdrawAndConcurrentRetryCreatesOneFact() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("concurrent")))
        {
            var f = seed(jpa);
            var service = new FundTransferService(jpa);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try
            {
                CountDownLatch start = new CountDownLatch(1);
                Callable<Boolean> task = () ->
                {
                    start.await();
                    try
                    {
                        service.save(f.command(UUID.randomUUID(), "150"));
                        return true;
                    }
                    catch (IllegalArgumentException insufficient)
                    {
                        return false;
                    }
                };
                Future<Boolean> one = executor.submit(task);
                Future<Boolean> two = executor.submit(task);
                start.countDown();
                assertNotEquals(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
                UUID retry = UUID.randomUUID();
                Future<FundTransferView> first = executor.submit(() -> service.save(f.command(retry, "25")));
                Future<FundTransferView> second = executor.submit(() -> service.save(f.command(retry, "25")));
                assertEquals(first.get(30, TimeUnit.SECONDS).id(), second.get(30, TimeUnit.SECONDS).id());
                assertEquals(2L, count(jpa, "FundTransfer"));
            }
            finally
            {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void backdatingCannotConsumeResourcesAlreadySpentOnALaterDate()
    {
        try (Jpa jpa = new Jpa(directory.resolve("future")))
        {
            var f = seed(jpa);
            new TransactionEntryService(jpa).enter(new TransactionCommand(DATE.plusDays(10), null, "Later expense", null,
                    List.of(line(f.expense(), f.source(), "150", "0"), line(f.bank(), f.source(), "0", "150"))));
            assertThrows(IllegalArgumentException.class, () -> new FundTransferService(jpa).save(f.command(UUID.randomUUID(), "75")));
            assertEquals(0L, count(jpa, "FundTransfer"));
            assertEquals(2L, count(jpa, "Txn"));
        }
    }

    @Test
    void journalReversalAndTransferReversalKeepDatedOperationalHistory()
    {
        try (Jpa jpa = new Jpa(directory.resolve("reversal")))
        {
            var f = seed(jpa);
            var service = new FundTransferService(jpa);
            var saved = service.save(f.command(UUID.randomUUID(), "75"));
            // Historical correction remains possible after fund retirement.
            change(jpa, em -> em.find(Fund.class, f.destination()).setActive(false));
            new TransactionCorrectionService(jpa).reverse(saved.transactionId(), DATE.plusDays(5), "Tester", "Correct allocation", false);
            var report = new SemanticAccountingReportQueryService(jpa, () -> "DEFAULT");
            assertEquals(1, report.postedFundTransfers(OPENING, DATE, 100).size());
            assertEquals(2, report.postedFundTransfers(OPENING, DATE.plusDays(5), 100).size());
            var inverse = service.list().stream().filter(row -> !row.id().equals(saved.id())).findFirst().orElseThrow();
            assertEquals("DESIGNATED", inverse.fromFund());
            assertEquals("GENERAL", inverse.toFund());
            assertThrows(IllegalStateException.class, () -> service.reverse(saved.id(), DATE.plusDays(6), "Duplicate correction"));
            var restored = service.reverse(inverse.id(), DATE.plusDays(6), "Undo correction");
            assertEquals("GENERAL", restored.fromFund());
            assertEquals(3L, count(jpa, "FundTransfer"));
            try (EntityManager em = jpa.em())
            {
                assertMoney("0", balance(em, f.destination(), f.allocation(), DATE.plusDays(5)));
                assertMoney("75", balance(em, f.destination(), f.allocation(), DATE.plusDays(6)));
            }
        }
    }

    @Test
    void genericEditDeleteAndReplacementCannotDetachTransferFacts()
    {
        try (Jpa jpa = new Jpa(directory.resolve("guards")))
        {
            var f = seed(jpa);
            var saved = new FundTransferService(jpa).save(f.command(UUID.randomUUID(), "75"));
            var correction = new TransactionCorrectionService(jpa);
            assertThrows(IllegalStateException.class, () -> correction.directEdit(saved.transactionId(), DATE, "Changed", "Change", "Tester"));
            assertThrows(IllegalStateException.class, () -> correction.delete(saved.transactionId(), "Tester", "Delete"));
            assertThrows(IllegalStateException.class, () -> correction.reverse(saved.transactionId(), DATE, "Tester", "Replace", true));
            assertThrows(IllegalStateException.class, () -> new TransactionEntryService(jpa).update(saved.transactionId(),
                    new TransactionCommand(DATE, null, "Change", null, List.of(line(f.bank(), f.source(), "1", "0"), line(f.income(), f.source(), "0", "1")))));
            assertEquals(1L, count(jpa, "FundTransfer"));
            assertEquals(2L, count(jpa, "Txn"));
        }
    }

    @Test
    void rejectsRestrictionsInactiveHierarchyIneffectiveFundsAndBankAccounts()
    {
        try (Jpa jpa = new Jpa(directory.resolve("eligibility")))
        {
            var f = seed(jpa);
            var service = new FundTransferService(jpa);
            for (FundType type : List.of(FundType.TEMP_RESTRICTED, FundType.PERM_RESTRICTED, FundType.EVENT, FundType.OTHER))
            {
                change(jpa, em -> em.find(Fund.class, f.destination()).setFundType(type));
                assertThrows(IllegalArgumentException.class, () -> service.save(f.command(UUID.randomUUID(), "1")));
            }
            change(jpa, em ->
            {
                Fund fund = em.find(Fund.class, f.destination());
                fund.setFundType(FundType.DESIGNATED);
                fund.setRestrictionText("Only donor-approved costs");
            });
            assertThrows(IllegalArgumentException.class, () -> service.save(f.command(UUID.randomUUID(), "1")));
            change(jpa, em ->
            {
                Fund fund = em.find(Fund.class, f.destination());
                fund.setRestrictionText(null);
                fund.setEffectiveFrom(DATE.plusDays(1));
            });
            assertThrows(IllegalArgumentException.class, () -> service.save(f.command(UUID.randomUUID(), "1")));
            change(jpa, em ->
            {
                Fund fund = em.find(Fund.class, f.destination());
                fund.setEffectiveFrom(null);
                fund.setParent(em.find(Fund.class, f.source()));
                em.find(Fund.class, f.source()).setActive(false);
            });
            assertThrows(IllegalArgumentException.class, () -> service.save(f.command(UUID.randomUUID(), "1")));
            change(jpa, em -> em.find(Fund.class, f.source()).setActive(true));
            var c = f.command(UUID.randomUUID(), "1");
            assertThrows(IllegalArgumentException.class, () -> service.save(new FundTransferCommand(c.requestId(), c.date(), c.fromFundId(), c.toFundId(), f.bank(), c.equityAccountId(), c.amount(), c.explanation())));
            change(jpa, em -> em.find(Account.class, f.allocation()).setPosting(false));
            assertThrows(IllegalArgumentException.class, () -> service.save(f.command(UUID.randomUUID(), "1")));
            assertEquals(0L, count(jpa, "FundTransfer"));
            assertEquals(1L, count(jpa, "Txn"));
        }
    }

    @Test
    void closedPeriodAuthorizationAndForeignReferencesRejectAtomicWrites()
    {
        try (Jpa jpa = new Jpa(directory.resolve("boundaries")))
        {
            var f = seed(jpa);
            Instant now = Instant.now();
            var viewer = new AuthenticatedUserSession(1, "VIEWER", "Viewer", "DEFAULT", Set.of(ReservedSecurityRole.VIEWER), now, now);
            var guard = new AuthorizationGuard(jpa, () -> Optional.of(viewer));
            assertThrows(AuthorizationException.class, () -> new FundTransferService(jpa, () -> "DEFAULT", guard).save(f.command(UUID.randomUUID(), "1")));
            change(jpa, em ->
            {
                Company other = new Company();
                other.setCode("OTHER");
                other.setDisplayName("Other");
                em.persist(other);
                em.find(Fund.class, f.destination()).setCompany(other);
            });
            assertThrows(CompanyOwnershipException.class, () -> new FundTransferService(jpa).save(f.command(UUID.randomUUID(), "1")));
            change(jpa, em -> em.find(Fund.class, f.destination()).setCompany(em.createQuery("from Company c where c.code = 'DEFAULT'", Company.class).getSingleResult()));
            var saved = new FundTransferService(jpa).save(f.command(UUID.randomUUID(), "1"));
            new PeriodCloseRangeService(jpa).closeRange("DEFAULT", DATE, DATE.plusDays(10), "CUSTOM", "Tester", "Close");
            assertThrows(IllegalStateException.class, () -> new FundTransferService(jpa).save(f.command(UUID.randomUUID(), "1")));
            assertThrows(IllegalStateException.class, () -> new FundTransferService(jpa).reverse(saved.id(), DATE.plusDays(1), "Closed reversal"));
            assertEquals(1L, count(jpa, "FundTransfer"));
            assertEquals(2L, count(jpa, "Txn"));
        }
    }

    @Test
    void persistenceFailureRollsBackLedgerTransferAndAuditTogether()
    {
        try (Jpa jpa = new Jpa(directory.resolve("rollback")))
        {
            var f = seed(jpa);
            change(jpa, em -> em.createNativeQuery("ALTER TABLE audit_event ADD CONSTRAINT reject_transfer_audit CHECK (action_type <> 'FUND_TRANSFER_CREATED')").executeUpdate());
            assertThrows(RuntimeException.class, () -> new FundTransferService(jpa).save(f.command(UUID.randomUUID(), "75")));
            assertEquals(1L, count(jpa, "Txn"));
            assertEquals(0L, count(jpa, "FundTransfer"));
            assertEquals(1L, count(jpa, "AuditEvent"));
        }
    }

    @Test
    void invalidCommandsAndWrongChartReferencesCreateNoFacts()
    {
        try (Jpa jpa = new Jpa(directory.resolve("validation")))
        {
            var f = seed(jpa);
            var service = new FundTransferService(jpa);
            for (String amount : List.of("0", "-1", "201", "0.00001", "1000000000000000"))
            {
                assertThrows(RuntimeException.class, () -> service.save(f.command(UUID.randomUUID(), amount)), amount);
            }
            var c = f.command(UUID.randomUUID(), "1");
            assertThrows(IllegalArgumentException.class, () -> service.save(new FundTransferCommand(c.requestId(), c.date(), c.fromFundId(), c.fromFundId(), c.allocationAccountId(), c.equityAccountId(), c.amount(), c.explanation())));
            assertThrows(IllegalArgumentException.class, () -> service.save(new FundTransferCommand(c.requestId(), c.date(), c.fromFundId(), c.toFundId(), c.allocationAccountId(), c.equityAccountId(), c.amount(), " ")));
            assertThrows(IllegalArgumentException.class, () -> service.save(new FundTransferCommand(c.requestId(), c.date(), c.fromFundId(), c.toFundId(), null, c.equityAccountId(), c.amount(), c.explanation())));
            change(jpa, em ->
            {
                Company company = em.createQuery("from Company c where c.code = 'DEFAULT'", Company.class).getSingleResult();
                company.setActiveChartOfAccounts(null);
            });
            assertThrows(IllegalArgumentException.class, () -> service.save(f.command(UUID.randomUUID(), "1")));
            change(jpa, em ->
            {
                Company company = em.createQuery("from Company c where c.code = 'DEFAULT'", Company.class).getSingleResult();
                company.setActiveChartOfAccounts(em.find(Account.class, f.allocation()).getChart());
                Company other = new Company();
                other.setCode("FOREIGN");
                other.setDisplayName("Foreign");
                em.persist(other);
                em.find(Account.class, f.allocation()).getChart().setCompany(other);
            });
            assertThrows(CompanyOwnershipException.class, () -> service.save(f.command(UUID.randomUUID(), "1")));
            assertEquals(0L, count(jpa, "FundTransfer"));
            assertEquals(1L, count(jpa, "Txn"));
        }
    }

    @Test
    void reversalRequiresReasonAndPersistenceFailureKeepsOriginalFacts()
    {
        try (Jpa jpa = new Jpa(directory.resolve("reversal-rollback")))
        {
            var f = seed(jpa);
            var service = new FundTransferService(jpa);
            var saved = service.save(f.command(UUID.randomUUID(), "75"));
            assertThrows(IllegalArgumentException.class, () -> new TransactionCorrectionService(jpa).reverse(saved.transactionId(), DATE.plusDays(1), "Tester", null, false));
            change(jpa, em -> em.createNativeQuery("ALTER TABLE audit_event ADD CONSTRAINT reject_reversal_audit CHECK (action_type <> 'FUND_TRANSFER_REVERSED')").executeUpdate());
            assertThrows(RuntimeException.class, () -> service.reverse(saved.id(), DATE.plusDays(1), "Undo allocation"));
            assertEquals(1L, count(jpa, "FundTransfer"));
            assertEquals(2L, count(jpa, "Txn"));
            assertEquals("Saved", service.list().get(0).state());
        }
    }

    static void change(Jpa jpa, Consumer<EntityManager> action)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            action.accept(em);
            em.getTransaction().commit();
        }
    }

    static long count(Jpa jpa, String entity)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
        }
    }

    static BigDecimal balance(EntityManager em, long fund, long account, LocalDate through)
    {
        BigDecimal result = em.createQuery("select sum(s.amountSigned) from TxnSplit s where s.fund.id = :fund and s.account.id = :account and s.txn.txnDate <= :date", BigDecimal.class)
                .setParameter("fund", fund).setParameter("account", account).setParameter("date", through).getSingleResult();
        return result == null ? BigDecimal.ZERO : result;
    }

    static void assertMoney(String expected, BigDecimal actual)
    {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "Expected " + expected + ", got " + actual);
    }
}
