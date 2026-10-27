package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.FunctionalExpenseTestFixture.*;

class FunctionalExpenseTest
{
    @TempDir Path directory;

    @Test
    void explicitMixedPurposesShareNaturalDimensionsAndSurviveRetryRestartAndLimitedReports() throws Exception
    {
        Path database = directory.resolve("mixed"); long transaction;
        try (Jpa jpa = new Jpa(database))
        {
            var f = FunctionalExpenseTestFixture.seed(jpa); var entry = f.entry(jpa); UUID request = UUID.randomUUID();
            transaction = entry.enter(f.mixed(), request).id(); assertEquals(transaction, entry.enter(f.mixed(), request).id());
            var saved = entry.load(transaction);
            assertEquals(List.of(ExpenseFunction.AR, ExpenseFunction.OA, ExpenseFunction.FR), saved.lines().subList(0, 3).stream().map(TransactionView.Line::expenseFunction).toList());
            assertNull(saved.lines().get(3).expenseFunction());
            assertTrue(saved.lines().subList(0, 3).stream().allMatch(l -> l.accountId() == f.base().expense() && l.activityId() == f.event() && l.budgetCategoryId() == f.category()));
            var result = query(jpa).query(DATE, DATE, "GENERAL", f.event());
            assertTrue(result.ready()); amount("700", result.totals().get(ExpenseFunction.AR)); amount("200", result.totals().get(ExpenseFunction.OA)); amount("100", result.totals().get(ExpenseFunction.FR)); amount("1000", result.gl());
            try (EntityManager em = jpa.em())
            {
                amount("1000", em.createQuery("select sum(s.amountSigned) from TxnSplit s where s.account.id = :expense", BigDecimal.class).setParameter("expense", f.base().expense()).getSingleResult());
                assertEquals(2L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertTrue(em.createQuery("select a.afterValue from AuditEvent a where a.actionType = 'TRANSACTION_ENTERED'", String.class).getResultList().stream().anyMatch(v -> v.contains(":AR") && v.contains(":OA") && v.contains(":FR")));
            }
        }
        try (Jpa jpa = new Jpa(database))
        {
            assertEquals(ExpenseFunction.AR, new TransactionEntryService(jpa).load(transaction).lines().get(0).expenseFunction());
            var execution = new ReportExecutionService(new FinancialReportService(jpa, () -> COMPANY), FinancialReportDisplayFormat.plain());
            var report = execution.execute(new ReportRequest(ReportDefinition.FUNCTIONAL_EXPENSES, DATE, DATE, ReportFundOption.ALL_FUNDS, 1));
            assertTrue(report.text().contains("Coverage: COMPLETE")); assertTrue(report.text().contains("Showing 1 of 3 expense lines"));
            var control = report.tableModel().rows().stream().filter(r -> "GL CONTROL".equals(r.value("record"))).findFirst().orElseThrow(); amount("1000", (BigDecimal)control.value("net"));
            assertTrue(report.csv().contains("AR — Events")); assertTrue(report.tableModel().rows().stream().anyMatch(r -> Objects.equals(transaction, r.value("transaction"))));
            byte[] spreadsheet = new PoiXlsxFinancialReportAdapter().render("Functional Expenses", report.text(), report.csv(), report.tableModel());
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(spreadsheet)))
            {
                var rows = java.util.stream.StreamSupport.stream(workbook.getSheetAt(0).spliterator(), false).toList();
                var gl = rows.stream().filter(row -> row.getCell(0) != null && "GL CONTROL".equals(row.getCell(0).getStringCellValue())).findFirst().orElseThrow();
                amount("1000", new BigDecimal(gl.getCell(9).getStringCellValue()));
                assertTrue(rows.stream().anyMatch(row -> row.getCell(0) != null && "COVERAGE".equals(row.getCell(0).getStringCellValue())));
            }
            byte[] pdf = new JasperPdfFinancialReportAdapter().render("Functional Expenses", report.text(), report.csv(), report.tableModel());
            assertTrue(new String(pdf, 0, 8, java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF-1."));
        }
    }

    @Test
    void netZeroUnknownsStayIncompleteAndContraRefundsFundsAndEventsUseActualGlSigns()
    {
        try (Jpa jpa = new Jpa(directory.resolve("scope")))
        {
            var f = FunctionalExpenseTestFixture.seed(jpa); var entry = f.entry(jpa); var b = f.base();
            entry.enter(f.expense(DATE, b.expense(), b.source(), f.event(), "100", false, null));
            entry.enter(f.expense(DATE, b.expense(), b.source(), f.event(), "100", true, null));
            entry.enter(f.expense(DATE, f.contra(), b.source(), f.otherEvent(), "25.1234", true, ExpenseFunction.FR));
            entry.enter(f.expense(DATE, f.contra(), b.source(), f.otherEvent(), "30.1234", false, ExpenseFunction.AR));
            entry.enter(f.expense(DATE, b.expense(), b.destination(), f.event(), "12", false, ExpenseFunction.OA));
            var result = query(jpa).query(DATE, DATE, "GENERAL", f.event());
            assertFalse(result.ready()); amount("0", result.gl()); amount("0", result.unclassified()); amount("200", result.unknownGross());
            result = query(jpa).query(DATE, DATE, "GENERAL", f.otherEvent());
            assertTrue(result.ready()); amount("5", result.gl()); amount("-25.1234", result.totals().get(ExpenseFunction.FR)); amount("30.1234", result.totals().get(ExpenseFunction.AR));
            amount("12", query(jpa).query(DATE, DATE, "DESIGNATED", null).gl());
            amount("0", query(jpa).query(DATE.minusDays(1), DATE.minusDays(1), null, null).gl());
            var request = new ReportRequest(ReportDefinition.FUNCTIONAL_EXPENSES, DATE, DATE, ReportFundOption.ALL_FUNDS, 1, new ReportDomainFilter.EventSelection(f.event()));
            var report = new ReportExecutionService(new FinancialReportService(jpa, () -> COMPANY), FinancialReportDisplayFormat.plain()).execute(request);
            assertTrue(report.text().contains("Coverage: INCOMPLETE")); assertTrue(report.text().contains("Showing 1 of 3 expense lines"));
        }
    }

    @Test
    void reversalsAndReplacementRetainPurposesAndEarlierCutoffsWhileNativeReplayReturnsCurrentRecord()
    {
        try (Jpa jpa = new Jpa(directory.resolve("correction")))
        {
            var f = FunctionalExpenseTestFixture.seed(jpa); var entry = f.entry(jpa); UUID key = UUID.randomUUID(); long source = entry.enter(f.mixed(), key).id();
            var earlier = query(jpa).query(DATE, DATE, null, null);
            var correction = new TransactionCorrectionService(jpa).reverse(source, DATE.plusDays(1), "tester", "Correct expense", true);
            assertEquals(earlier, query(jpa).query(DATE, DATE, null, null));
            for (long id : List.of(correction.reversalTransactionId(), correction.replacementTransactionId()))
            {
                assertEquals(ExpenseFunction.AR, entry.load(id).lines().get(0).expenseFunction());
            }
            amount("0", query(jpa).query(DATE.plusDays(1), DATE.plusDays(1), null, null).gl());
            assertThrows(PostingException.class, () -> entry.update(correction.reversalTransactionId(),
                    f.expense(DATE.plusDays(1), f.base().expense(), f.base().source(), f.event(), "1000", true, ExpenseFunction.OA)));
            long audits = count(jpa, "select count(a) from AuditEvent a");
            assertEquals("REVERSED", entry.enter(f.mixed(), key).status()); assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
        }
    }

    @Test
    void oldUnclassifiedRequestRemainsV1AfterAnExplicitReclassificationAndChangedAcceptedPurposeRejects()
    {
        try (Jpa jpa = new Jpa(directory.resolve("receipt")))
        {
            var f = FunctionalExpenseTestFixture.seed(jpa); var b = f.base(); var entry = f.entry(jpa); UUID key = UUID.randomUUID();
            var legacy = f.expense(DATE, b.expense(), b.source(), f.event(), "10", false, null);
            var classified = f.expense(DATE, b.expense(), b.source(), f.event(), "10", false, ExpenseFunction.AR);
            long id = entry.enter(legacy, key).id();
            try (EntityManager em = jpa.em())
            {
                assertTrue(em.createQuery("from AuditEvent a where a.portableId = :key", AuditEvent.class).setParameter("key", key).getSingleResult().getReason().startsWith("Journal create request v1 SHA-256:"));
            }
            entry.update(id, classified);
            long audits = count(jpa, "select count(a) from AuditEvent a");
            assertEquals(ExpenseFunction.AR, entry.enter(legacy, key).lines().get(0).expenseFunction());
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            assertThrows(PostingException.class, () -> entry.enter(classified, key));
            UUID newKey = UUID.randomUUID(); long second = entry.enter(classified, newKey).id();
            assertEquals(second, entry.enter(classified, newKey).id());
            assertThrows(PostingException.class, () -> entry.enter(f.expense(DATE, b.expense(), b.source(), f.event(), "10", false, ExpenseFunction.FR), newKey));
            new TransactionCorrectionService(jpa).delete(second, "tester", "Remove classified duplicate");
            long afterDelete = count(jpa, "select count(a) from AuditEvent a");
            assertThrows(PostingException.class, () -> entry.enter(classified, newKey));
            assertEquals(afterDelete, count(jpa, "select count(a) from AuditEvent a"));
            try (EntityManager em = jpa.em())
            {
                assertTrue(em.createQuery("select a.beforeValue from AuditEvent a where a.actionType = 'TRANSACTION_DELETED'", String.class)
                        .getSingleResult().contains(":AR"));
            }
        }
    }

    @Test
    void nonexpenseForeignPermissionClosedAndReconciledWritesRejectAndAccountRetypingCannotEraseHistory()
    {
        try (Jpa jpa = new Jpa(directory.resolve("guards")))
        {
            var f = FunctionalExpenseTestFixture.seed(jpa); var entry = f.entry(jpa); UUID key = UUID.randomUUID();
            assertThrows(PostingException.class, () -> entry.enter(f.expense(DATE, f.base().income(), f.base().source(), f.event(), "10", false, ExpenseFunction.AR)));
            assertEquals(1, count(jpa, "select count(t) from Txn t"));
            long id = entry.enter(f.mixed(), key).id();
            Instant now = Instant.now(); var guard = new AuthorizationGuard(jpa, () -> Optional.of(new AuthenticatedUserSession(99L, "viewer", "Viewer", COMPANY, Set.of(ReservedSecurityRole.VIEWER), now, now)));
            assertThrows(AuthorizationException.class, () -> new TransactionEntryService(jpa, () -> COMPANY, guard).enter(f.mixed(), key));
            long foreignEvent;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin(); Company other = new Company(); other.setCode("OTHER"); other.setDisplayName("Other"); other.setDefaultCurrency("USD"); em.persist(other);
                Activity event = new Activity(); event.setCompany(other); event.setCode("FOREIGN"); event.setName("Foreign"); em.persist(event); em.getTransaction().commit(); foreignEvent = event.getId();
            }
            assertThrows(CompanyOwnershipException.class, () -> query(jpa).query(DATE, DATE, null, foreignEvent));
            assertThrows(CompanyOwnershipException.class, () -> entry.enter(f.expense(DATE, f.base().expense(), f.base().source(), foreignEvent, "10", false, ExpenseFunction.AR)));
            var admin = new AccountAdminService(jpa, () -> COMPANY);
            assertThrows(IllegalArgumentException.class, () -> admin.save(new AccountCommand(f.base().expense(), "5000", "Changed", AccountType.ASSET, null, NormalBalance.DEBIT, null, null, true)));
            assertThrows(IllegalArgumentException.class, () -> admin.save(new AccountCommand(f.base().expense(), "5000", "Changed sign", AccountType.EXPENSE, null, NormalBalance.CREDIT, null, null, true)));
            try (EntityManager em = jpa.em())
            {
                UUID run = UUID.randomUUID(); em.getTransaction().begin();
                em.createNativeQuery("INSERT INTO reconciliation_run (id, group_code, statement_ending_on, bank_format, imported_transaction_count, status, notes) VALUES (?, 'DEFAULT', DATE '2026-02-28', 'OFX', 1, 'COMPLETED', 'Synthetic')").setParameter(1, run).executeUpdate();
                em.createNativeQuery("INSERT INTO txn_reconciliation_protection (txn_id, reconciliation_run_id, protected_by, notes) VALUES (?, ?, 'tester', 'Synthetic')").setParameter(1, id).setParameter(2, run).executeUpdate(); em.getTransaction().commit();
            }
            assertThrows(PostingException.class, () -> entry.update(id, f.mixed()));
            org.nonprofitbookkeeping.service.PeriodCloseTestFixture.historical(jpa,COMPANY, DATE, DATE, "CALCULATED", "tester", "Synthetic close");
            assertThrows(RuntimeException.class, () -> entry.enter(f.mixed(), UUID.randomUUID()));
            assertEquals(id, entry.enter(f.mixed(), key).id());
        }
    }

    @Test
    void lateAuditFailureLeavesNoClassifiedExpenseAndRetryCanCorrectUnacceptedPurpose()
    {
        try (Jpa jpa = new Jpa(directory.resolve("rollback")))
        {
            var f = FunctionalExpenseTestFixture.seed(jpa); var entry = f.entry(jpa); UUID key = UUID.randomUUID();
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin(); em.createNativeQuery("ALTER TABLE audit_event ADD CONSTRAINT synthetic_function_failure CHECK (portable_id <> UUID '" + key + "')").executeUpdate(); em.getTransaction().commit();
            }
            assertThrows(RuntimeException.class, () -> entry.enter(f.mixed(), key));
            assertEquals(1, count(jpa, "select count(t) from Txn t")); amount("0", query(jpa).query(DATE, DATE, null, null).gl());
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, em.createQuery("select count(s) from TxnSplit s where s.expenseFunction is not null", Long.class).getSingleResult());
                em.getTransaction().begin(); em.createNativeQuery("ALTER TABLE audit_event DROP CONSTRAINT synthetic_function_failure").executeUpdate(); em.getTransaction().commit();
            }
            var corrected = f.expense(DATE, f.base().expense(), f.base().source(), f.event(), "1000", false, ExpenseFunction.OA);
            long id = entry.enter(corrected, key).id(); assertEquals(id, entry.enter(corrected, key).id()); amount("1000", query(jpa).query(DATE, DATE, null, null).totals().get(ExpenseFunction.OA));
        }
    }

    @Test
    void unclassifiedV1FingerprintIsByteCompatibleWithThePreFunctionProtocol()
    {
        try (Jpa jpa = new Jpa(directory.resolve("golden")); EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("insert into company(id,code,display_name,default_currency) values (7,'GOLD','Golden','USD')").executeUpdate();
            em.getTransaction().commit();
            var owner = em.find(Company.class, 7L);
            var command = new TransactionCommand(DATE, null, "Legacy receipt", null, List.of(
                    new TransactionLineCommand(101L, 303L, null, null, null, new BigDecimal("10"), BigDecimal.ZERO, false, null),
                    new TransactionLineCommand(202L, 303L, null, null, null, BigDecimal.ZERO, new BigDecimal("10"), false, null)));
            assertEquals("Journal create request v1 SHA-256: 190b8766ba4fdb605a1c0024630ad7b85d39912105ab879c46ad96a4a66ea218", JournalCreateRequests.reason(owner, command));
        }
    }

    @Test
    void concurrentClassifiedCreateRequestsCommitOneExpense() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("concurrent")))
        {
            var f = FunctionalExpenseTestFixture.seed(jpa); var entry = f.entry(jpa); UUID key = UUID.randomUUID();
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            var start = new java.util.concurrent.CountDownLatch(1);
            try
            {
                java.util.concurrent.Callable<Long> save = () -> { start.await(); return entry.enter(f.mixed(), key).id(); };
                var first = pool.submit(save); var second = pool.submit(save); start.countDown();
                assertEquals(first.get(30, java.util.concurrent.TimeUnit.SECONDS), second.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            finally
            {
                pool.shutdownNow();
            }
            assertEquals(2, count(jpa, "select count(t) from Txn t")); amount("1000", query(jpa).query(DATE, DATE, null, null).gl());
        }
    }

    private static FunctionalExpenseQueryService query(Jpa jpa)
    {
        return new FunctionalExpenseQueryService(jpa, () -> COMPANY);
    }

    private static long count(Jpa jpa, String query)
    {
        try (EntityManager em = jpa.em()) { return em.createQuery(query, Long.class).getSingleResult(); }
    }

    private static void amount(String expected, BigDecimal actual)
    {
        assertNotNull(actual); assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " vs " + actual);
    }
}
