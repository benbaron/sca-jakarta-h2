package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.interchange.sclx.*;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.FundTransferTestFixture.*;

class PaymentReferenceServiceTest
{
    @TempDir Path directory;

    static PaymentReference check(String reference)
    {
        return new PaymentReference(PaymentReference.Method.CHECK, reference, DATE, DATE.plusDays(1));
    }

    static TransactionCommand payment(FundTransferTestFixture f, long bank, PaymentReference reference)
    {
        return new TransactionCommand(DATE, null, "Narrative only", null, List.of(
                line(f.expense(), f.source(), "10", "0"),
                new TransactionLineCommand(bank, f.source(), null, null, null,
                        BigDecimal.ZERO, BigDecimal.TEN, false, null, reference)));
    }

    @Test
    void leadingZerosDatesSearchAndRestartPreserveIdentity()
    {
        Path path = directory.resolve("restart");
        long id;
        try (Jpa jpa = new Jpa(path))
        {
            var f = seed(jpa);
            var service = new TransactionEntryService(jpa);
            var saved = service.enter(payment(f, f.bank(), check("000123")));
            id = saved.id();
            assertEquals(check("000123"), saved.lines().get(1).payment());
            assertEquals(1, service.search(null, null, "000123", 20).size());
            assertEquals(1, service.search(null, null, "000123", null, null, "1000", 20).size());
            assertTrue(service.search(null, null, "000123", null, null, "5000", 20).isEmpty());
            assertEquals(1, new FinancialReportService(jpa).generalLedgerDetail(DATE, DATE, null, 100)
                    .stream().filter(row -> "000123".equals(row.paymentReference())).count());
        }
        try (Jpa jpa = new Jpa(path))
        {
            assertEquals(check("000123"), new TransactionEntryService(jpa).load(id).lines().get(1).payment());
        }
    }

    @Test
    void duplicatePolicyIsAccountScopedAndMultipleBankLinesStayIndependent()
    {
        try (Jpa jpa = new Jpa(directory.resolve("multiple")))
        {
            var f = seed(jpa);
            long bank2 = secondBank(jpa, f);
            var service = new TransactionEntryService(jpa);
            var saved = service.enter(payment(f, f.bank(), check("000123")));
            long before = count(jpa, "Txn");
            assertThrows(IllegalArgumentException.class, () -> service.enter(payment(f, f.bank(), check("000123"))));
            assertEquals(before, count(jpa, "Txn"));
            service.update(saved.id(), payment(f, f.bank(), check("000123")));
            service.enter(new TransactionCommand(DATE, null, "Two instruments", null, List.of(
                    line(f.expense(), f.source(), "20", "0"),
                    payment(f, f.bank(), check("000124")).lines().get(1),
                    payment(f, bank2, check("000123")).lines().get(1))));
            assertEquals(1, service.search(null, null, "000124", null, null, "1000", 20).size());
            assertTrue(service.search(null, null, "000124", null, null, "1010", 20).isEmpty());
            assertEquals(1, service.search(null, null, "000123", null, null, "1010", 20).size());
            // EFT references are text, repeated processor batch references are allowed.
            var eft = new PaymentReference(PaymentReference.Method.EFT, "ACH-A00Z", null, null);
            service.enter(payment(f, f.bank(), eft));
            service.enter(payment(f, f.bank(), eft));
            assertEquals(2, service.search(null, null, "ACH-A00Z", 20).size());
        }
    }

    @Test
    void oneCheckMayAllocateItsBankEffectAcrossFundsButFactsMustAgree()
    {
        try (Jpa jpa = new Jpa(directory.resolve("allocated-check")))
        {
            var f = seed(jpa);
            var service = new TransactionEntryService(jpa);
            var firstBank = payment(f, f.bank(), check("000123")).lines().get(1);
            var secondBank = new TransactionLineCommand(f.bank(), f.destination(), null, null, null,
                    BigDecimal.ZERO, BigDecimal.TEN, false, null, check("000123"));
            var saved = service.enter(new TransactionCommand(DATE, null, "One check, two funds", null, List.of(
                    line(f.expense(), f.source(), "10", "0"), firstBank,
                    line(f.expense(), f.destination(), "10", "0"), secondBank)));
            assertEquals(2, saved.lines().stream().filter(line -> line.payment() != null).count());
            assertEquals(1L, number(jpa, "select count(*) from txn_split where payment_check_key='000123'"));
            var conflicting = new TransactionLineCommand(f.bank(), f.destination(), null, null, null,
                    BigDecimal.ZERO, BigDecimal.TEN, false, null, new PaymentReference(PaymentReference.Method.CHECK, "000123", DATE, DATE.plusDays(2)));
            assertThrows(IllegalArgumentException.class, () -> service.update(saved.id(), new TransactionCommand(DATE,
                    null, "Conflicting delivery", null, List.of(line(f.expense(), f.source(), "20", "0"), firstBank, conflicting))));
            assertEquals(check("000123"), service.load(saved.id()).lines().get(3).payment());
        }
    }

    @Test
    void reversalRetainsReferenceWithoutReissuingAndReplacementRequiresNewIdentity()
    {
        try (Jpa jpa = new Jpa(directory.resolve("reverse")))
        {
            var f = seed(jpa);
            var service = new TransactionEntryService(jpa);
            var saved = service.enter(payment(f, f.bank(), check("000123")));
            var corrections = new TransactionCorrectionService(jpa);
            assertThrows(IllegalStateException.class, () -> corrections.reverse(saved.id(), DATE.plusDays(2), "Tester", "Replace", true));
            var reversed = corrections.reverse(saved.id(), DATE.plusDays(2), "Tester", "Void", false);
            assertEquals(check("000123"), service.load(reversed.reversalTransactionId()).lines().get(1).payment());
            assertEquals(2, service.search(null, null, "000123", 20).size());
            assertThrows(IllegalArgumentException.class, () -> service.enter(payment(f, f.bank(), check("000123"))));
            assertEquals(1L, number(jpa, "select count(*) from txn_split where payment_check_key = '000123'"));
        }
    }

    @Test
    void validationOwnershipAuthorizationAndClosedDatesRejectAtomically()
    {
        assertThrows(IllegalArgumentException.class, () -> check(" "));
        assertThrows(IllegalArgumentException.class, () -> check("x".repeat(81)));
        assertThrows(IllegalArgumentException.class, () -> new PaymentReference(PaymentReference.Method.EFT, "a", null, DATE));
        assertThrows(IllegalArgumentException.class, () -> new PaymentReference(PaymentReference.Method.CHECK, "1", DATE, DATE.minusDays(1)));
        try (Jpa jpa = new Jpa(directory.resolve("validation")))
        {
            var f = seed(jpa);
            var service = new TransactionEntryService(jpa);
            assertThrows(IllegalArgumentException.class, () -> service.enter(payment(f, f.expense(), check("1"))));
            Instant now = Instant.now();
            var viewer = new AuthenticatedUserSession(1, "VIEWER", "Viewer", "DEFAULT", Set.of(ReservedSecurityRole.VIEWER), now, now);
            var guard = new AuthorizationGuard(jpa, () -> Optional.of(viewer));
            assertThrows(AuthorizationException.class, () -> new TransactionEntryService(jpa, () -> "DEFAULT", guard).enter(payment(f, f.bank(), check("1"))));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company other = new Company(); other.setCode("OTHER"); other.setDisplayName("Other company"); em.persist(other);
                em.getTransaction().commit();
            }
            assertThrows(CompanyOwnershipException.class, () -> new TransactionEntryService(jpa, () -> "OTHER").enter(payment(f, f.bank(), check("1"))));
            assertThrows(IllegalArgumentException.class, () -> service.enter(new TransactionCommand(DATE, null, "Incoming check", null,
                    List.of(new TransactionLineCommand(f.bank(), f.source(), null, null, null,
                            BigDecimal.TEN, BigDecimal.ZERO, false, null, check("1")),
                            line(f.income(), f.source(), "0", "10")))));
            new PeriodCloseRangeService(jpa).closeRange("DEFAULT", DATE, DATE.plusDays(10), "CUSTOM", "Tester", "Close");
            assertThrows(RuntimeException.class, () -> service.enter(payment(f, f.bank(), check("1"))));
            assertEquals(1L, count(jpa, "Txn"));
        }
    }

    @Test
    void auditFailureRollsBackChangedPaymentAndReservation()
    {
        try (Jpa jpa = new Jpa(directory.resolve("audit")))
        {
            var f = seed(jpa);
            var service = new TransactionEntryService(jpa);
            var saved = service.enter(payment(f, f.bank(), check("000123")));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("alter table audit_event add constraint reject_payment_update check(action_type <> 'TRANSACTION_UPDATED')").executeUpdate();
                em.getTransaction().commit();
            }
            assertThrows(RuntimeException.class, () -> service.update(saved.id(), payment(f, f.bank(), check("000124"))));
            assertEquals(check("000123"), service.load(saved.id()).lines().get(1).payment());
            assertEquals(0L, number(jpa, "select count(*) from txn_split where payment_check_key = '000124'"));
        }
    }

    @Test
    void concurrentCheckCreationAllowsExactlyOneInstrument() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("concurrent")))
        {
            var f = seed(jpa);
            var executor = Executors.newFixedThreadPool(2);
            var start = new CountDownLatch(1);
            Callable<Boolean> save = () ->
            {
                start.await();
                try { new TransactionEntryService(jpa).enter(payment(f, f.bank(), check("000123"))); return true; }
                catch (IllegalArgumentException ex) { return false; }
            };
            try
            {
                var first = executor.submit(save); var second = executor.submit(save); start.countDown();
                assertNotEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
                assertEquals(2L, count(jpa, "Txn"));
            }
            finally { executor.shutdownNow(); }
        }
    }

    @Test
    void sclxRoundTripRetainsReversalDatesAndAlphanumericReferencesIdempotently() throws Exception
    {
        try (Jpa source = new Jpa(directory.resolve("source")); Jpa target = new Jpa(directory.resolve("target")))
        {
            var f = seed(source);
            var service = new TransactionEntryService(source);
            var saved = service.enter(payment(f, f.bank(), check("000123")));
            new CheckExceptionService(source, () -> "DEFAULT", null).review(saved.id(), f.bank(), "000123",
                    DATE.plusDays(1), "receipt-folder/123", DATE.plusDays(1), "Delivery verified; obligation valid", "Tester");
            service.enter(payment(f, f.bank(), new PaymentReference(PaymentReference.Method.EFT, "ACH-A00Z", null, null)));
            new TransactionCorrectionService(source).reissueCheck(saved.id(), DATE.plusDays(2), "Tester", "Lost; reissue obligation",
                    new PaymentReference(PaymentReference.Method.CHECK, "000124", DATE.plusDays(2), null));
            Path file = directory.resolve("payments.sclx");
            new SclxFileExportService(new SclxCoreSnapshotQueryService(source), () -> directory.resolve("source"))
                    .export(new SclxExportRequest(file, Instant.now(), false));
            var previews = new SclxImportPreviewService(target, () -> "DEFAULT");
            var preview = previews.preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            var commit = new SclxImportCommitService(target, () -> "DEFAULT");
            var imported = commit.commit(file, preview, "Tester");
            assertTrue(imported.committed(), imported.toString());
            var loaded = new TransactionEntryService(target);
            assertEquals(2, loaded.search(null, null, "000123", 20).size());
            assertEquals(1, loaded.search(null, null, "ACH-A00Z", 20).size());
            var checks = new CheckExceptionService(target, () -> "DEFAULT", null).report(null, DATE.plusDays(2), DATE);
            assertEquals(2, checks.size()); assertEquals("REISSUED", checks.get(0).lifecycle());
            assertNotNull(checks.get(0).replacementId()); assertNotNull(checks.get(1).replacesId());
            assertEquals(1L, number(target, "select count(*) from txn_split where payment_check_key = '000123'"));
            assertEquals("receipt-folder/123", loaded.search(null, null, "000123", 20).get(0).lines().stream()
                    .filter(line -> line.payment() != null).findFirst().orElseThrow().payment().evidenceReference());
            long before = count(target, "Txn");
            assertTrue(commit.commit(file, previews.preview(file), "Tester").committed());
            assertEquals(before, count(target, "Txn"));
        }
    }

    static long secondBank(Jpa jpa, FundTransferTestFixture f)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Account bank = new Account(); bank.setChart(em.find(Account.class, f.bank()).getChart());
            bank.setCode("1010"); bank.setName("Secondary bank"); bank.setAccountType(AccountType.ASSET);
            bank.setAccountFunction(AccountFunction.BANK); bank.setNormalBalance(NormalBalance.DEBIT);
            bank.setPosting(true); bank.setActive(true); em.persist(bank); em.flush();
            long id = bank.getId(); em.getTransaction().commit(); return id;
        }
    }
    static long count(Jpa jpa, String entity)
    {
        try (EntityManager em = jpa.em()) { return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult(); }
    }
    static long number(Jpa jpa, String sql)
    {
        try (EntityManager em = jpa.em()) { return ((Number) em.createNativeQuery(sql).getSingleResult()).longValue(); }
    }
}
