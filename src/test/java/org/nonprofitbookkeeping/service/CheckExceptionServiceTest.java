package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.FundTransferTestFixture.*;
import static org.nonprofitbookkeeping.service.PaymentReferenceServiceTest.*;

class CheckExceptionServiceTest
{
    @TempDir Path directory;

    private CheckExceptionService service(Jpa jpa)
    {
        return new CheckExceptionService(jpa, () -> "DEFAULT", null);
    }

    @Test
    void reissueRetainsReferencesAndAsOfHistoryWithoutDoubleExpense()
    {
        try (Jpa jpa = new Jpa(directory.resolve("reissue")))
        {
            var f = seed(jpa); var entries = new TransactionEntryService(jpa);
            long original = entries.enter(payment(f, f.bank(), check("000123"))).id();
            var correction = new TransactionCorrectionService(jpa).reissueCheck(original, DATE.plusDays(10), "Tester",
                    "Lost instrument; obligation still valid", new PaymentReference(PaymentReference.Method.CHECK,
                            "000124", DATE.plusDays(10), null));
            assertEquals("000123", entries.load(correction.reversalTransactionId()).lines().get(1).payment().reference());
            assertEquals("000124", entries.load(correction.replacementTransactionId()).lines().get(1).payment().reference());
            assertEquals("OUTSTANDING", service(jpa).report(null, DATE.plusDays(9), DATE).get(0).lifecycle());
            var rows = service(jpa).report(null, DATE.plusDays(10), DATE);
            assertEquals(2, rows.size()); assertEquals("REISSUED", rows.get(0).lifecycle());
            assertEquals(correction.replacementTransactionId(), rows.get(0).replacementId());
            assertEquals(original, rows.get(1).replacesId()); assertTrue(rows.get(0).exceptions().contains("Cancelled pair"));
            assertEquals(0, total(jpa, f.expense()).compareTo(BigDecimal.TEN));
            assertThrows(IllegalStateException.class, () -> entries.update(correction.replacementTransactionId(), payment(f, f.bank(), check("000125"))));
            assertThrows(IllegalStateException.class, () -> new TransactionCorrectionService(jpa).delete(correction.replacementTransactionId(), "Tester", "Erase"));
            assertThrows(IllegalArgumentException.class, () -> entries.enter(payment(f, f.bank(), check("000123"))));
            new TransactionCorrectionService(jpa).reverse(correction.reversalTransactionId(), DATE.plusDays(11), "Tester", "Undo cancellation for review", false);
            assertEquals("CANCELLATION_REVERSED", service(jpa).report(null, DATE.plusDays(11), DATE).get(0).lifecycle());
        }
    }

    @Test
    void duplicateNewNumberAndAuditFailureRollBackTheCompleteReissue()
    {
        try (Jpa jpa = new Jpa(directory.resolve("rollback")))
        {
            var f = seed(jpa); var entries = new TransactionEntryService(jpa);
            long original = entries.enter(payment(f, f.bank(), check("000123"))).id();
            entries.enter(payment(f, f.bank(), check("000124")));
            long before = count(jpa, "Txn"); var corrections = new TransactionCorrectionService(jpa);
            assertThrows(IllegalArgumentException.class, () -> corrections.reissueCheck(original, DATE.plusDays(2), "Tester", "Lost",
                    new PaymentReference(PaymentReference.Method.CHECK, "000124", DATE.plusDays(2), null)));
            assertEquals(before, count(jpa, "Txn")); assertEquals("ENTERED", entries.load(original).status());
            assertThrows(IllegalArgumentException.class, () -> corrections.reissueCheck(original, DATE.plusDays(2), "Tester", "Lost", check("000123")));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("alter table audit_event add constraint fail_reissue check (action_type <> 'CHECK_REISSUED')").executeUpdate();
                em.getTransaction().commit();
            }
            assertThrows(RuntimeException.class, () -> corrections.reissueCheck(original, DATE.plusDays(2), "Tester", "Lost", check("000125")));
            assertEquals(before, count(jpa, "Txn")); assertEquals("ENTERED", entries.load(original).status());
            assertEquals(0, total(jpa, f.expense()).compareTo(new BigDecimal("20")));
        }
    }

    @Test
    void reviewAggregatesFundAllocationsAndAgeDoesNotChangeObligation()
    {
        try (Jpa jpa = new Jpa(directory.resolve("review")))
        {
            var f = seed(jpa); var entries = new TransactionEntryService(jpa);
            var bankLine = payment(f, f.bank(), check("000123")).lines().get(1);
            long id = entries.enter(new TransactionCommand(DATE, null, "Allocated", null, List.of(
                    line(f.expense(), f.source(), "10", "0"), bankLine,
                    line(f.expense(), f.destination(), "10", "0"),
                    new TransactionLineCommand(f.bank(), f.destination(), null, null, null,
                            BigDecimal.ZERO, BigDecimal.TEN, false, null, check("000123"))))).id();
            var row = service(jpa).report(null, DATE.plusMonths(8), DATE).get(0);
            assertEquals(0, row.amount().compareTo(new BigDecimal("20"))); assertTrue(row.exceptions().contains("Age review"));
            long before = count(jpa, "Txn");
            service(jpa).review(id, f.bank(), "000123", DATE.plusDays(1), "receipt-folder/123", DATE.plusMonths(8), "Payee confirms obligation remains", "Tester");
            var loaded = entries.load(id);
            for (var l : loaded.lines().stream().filter(l -> l.payment() != null).toList())
            {
                assertEquals("receipt-folder/123", l.payment().evidenceReference());
                assertEquals("Payee confirms obligation remains", l.payment().reviewNote());
                assertFalse(l.bankCleared());
            }
            assertEquals(before, count(jpa, "Txn")); assertEquals("ENTERED", loaded.status());
            assertEquals(1L, number(jpa, "select count(*) from txn_split where payment_check_key='000123'"));
            var reissue = new TransactionCorrectionService(jpa).reissueCheck(id, DATE.plusMonths(8), "Tester", "Lost; reissue", check("000124"));
            assertEquals(0, total(jpa, f.expense()).compareTo(new BigDecimal("20")));
            assertEquals(2, entries.load(reissue.replacementTransactionId()).lines().stream().filter(l -> l.payment() != null).count());
        }
    }

    @Test
    void reviewProtectsIdentityCompanyClosedPeriodAndFinalizedReconciliation()
    {
        try (Jpa jpa = new Jpa(directory.resolve("protection")))
        {
            var f = seed(jpa); var entries = new TransactionEntryService(jpa);
            long id = entries.enter(payment(f, f.bank(), check("000123"))).id();
            assertThrows(IllegalArgumentException.class, () -> service(jpa).review(id, f.bank(), "wrong", null, "ref", DATE, "Review", "Tester"));
            assertThrows(IllegalArgumentException.class, () -> service(jpa).review(id, f.bank(), "000123", DATE.minusDays(1), null, DATE, "Review", "Tester"));
            assertThrows(RuntimeException.class, () -> new CheckExceptionService(jpa, () -> "OTHER", null).review(id, f.bank(), "000123", null, null, DATE, "Review", "Tester"));
            new PeriodCloseRangeService(jpa).closeRange("DEFAULT", DATE, DATE.plusDays(10), "CUSTOM", "Tester", "Close");
            assertThrows(IllegalStateException.class, () -> service(jpa).review(id, f.bank(), "000123", null, null, DATE.plusDays(1), "Review", "Tester"));
            assertNull(entries.load(id).lines().get(1).payment().reviewNote());
        }
    }

    @Test
    void bankErrorsAndDifferencesAreFactualAndFinalizationBlocksCheckActions()
    {
        try (Jpa jpa = new Jpa(directory.resolve("bank-errors")))
        {
            var f = seed(jpa); var entries = new TransactionEntryService(jpa);
            var saved = entries.enter(payment(f, f.bank(), check("000123")));
            long companyId, bankId, sessionId;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company company = em.createQuery("from Company c where c.code='DEFAULT'", Company.class).getSingleResult();
                companyId = company.getId();
                Bank institution = new Bank(); institution.setCompany(company); institution.setName("Test bank"); em.persist(institution);
                CompanyBankAccount configured = new CompanyBankAccount(); configured.setCompany(company); configured.setBank(institution);
                configured.setAccount(em.find(Account.class, f.bank())); configured.setName("Checking");
                configured.setOpeningDate(OPENING); configured.setOpeningBalance(BigDecimal.ZERO); em.persist(configured); em.flush(); bankId = configured.getId();
                em.createNativeQuery("insert into bank_import_batch(id,company_id,bank_account_id,source_name,source_format,status,total_line_count) values(9001,?,?,'error.csv','CSV','IMPORTED',1)")
                        .setParameter(1, companyId).setParameter(2, bankId).executeUpdate();
                em.createNativeQuery("insert into bank_statement_line(id,batch_id,company_id,bank_account_id,source_row_number,deterministic_fingerprint,transaction_date,amount,status) values(9001,9001,?,?,1,'error-fp',?,-11,'ERROR')")
                        .setParameter(1, companyId).setParameter(2, bankId).setParameter(3, DATE).executeUpdate();
                em.createNativeQuery("insert into bank_statement_line(id,batch_id,company_id,bank_account_id,source_row_number,deterministic_fingerprint,amount,status) values(9002,9001,?,?,2,'undated-error-fp',-11,'ERROR')")
                        .setParameter(1, companyId).setParameter(2, bankId).executeUpdate();
                em.createNativeQuery("insert into bank_reconciliation_session(company_id,bank_account_id,statement_start_date,statement_end_date,status) values(?,?,?,?,'UNRESOLVED')")
                        .setParameter(1, companyId).setParameter(2, bankId).setParameter(3, OPENING).setParameter(4, DATE.plusDays(2)).executeUpdate();
                sessionId = ((Number)em.createNativeQuery("select max(id) from bank_reconciliation_session").getSingleResult()).longValue();
                em.createNativeQuery("insert into bank_reconciliation_match(session_id,txn_split_id,match_status,resolution_note) values(?,?,'AMOUNT_MISMATCH','Bank error remains unresolved')")
                        .setParameter(1, sessionId).setParameter(2, saved.lines().get(1).id()).executeUpdate();
                em.getTransaction().commit();
            }
            var rows = service(jpa).report(bankId, DATE.plusDays(2), DATE);
            assertEquals(4, rows.size());
            assertTrue(rows.stream().anyMatch(r -> "BANK_ERROR".equals(r.lifecycle()) && r.date() == null)); assertTrue(rows.stream().anyMatch(r -> "BANK_ERROR".equals(r.lifecycle())));
            assertTrue(rows.stream().anyMatch(r -> "BANK_DIFFERENCE".equals(r.lifecycle()) && r.sessionId() == sessionId));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("update bank_reconciliation_session set status='FINALIZED' where id=?").setParameter(1, sessionId).executeUpdate();
                em.getTransaction().commit();
            }
            assertThrows(IllegalStateException.class, () -> service(jpa).review(saved.id(), f.bank(), "000123", null, null, DATE.plusDays(2), "Review", "Tester"));
            assertThrows(IllegalStateException.class, () -> new TransactionCorrectionService(jpa).reissueCheck(saved.id(), DATE.plusDays(2), "Tester", "Lost", check("000124")));
            assertEquals(3, service(jpa).report(bankId, DATE.plusDays(2), DATE).size());
            assertNull(entries.load(saved.id()).lines().get(1).payment().reviewNote());
            var snapshot = new BankReconciliationWorkspaceService(jpa).load(sessionId);
            assertTrue(snapshot.ledgerLines().stream().anyMatch(l -> "000123".equals(l.paymentReference())));
        }
    }

    @Test
    void viewerCannotReviewOrReissueAndMixedInstrumentReissueIsRejected()
    {
        try (Jpa jpa = new Jpa(directory.resolve("authorization")))
        {
            var f = seed(jpa); var entries = new TransactionEntryService(jpa);
            long id = entries.enter(payment(f, f.bank(), check("000123"))).id();
            var now = java.time.Instant.now();
            var viewer = new AuthenticatedUserSession(1, "VIEWER", "Viewer", "DEFAULT",
                    java.util.Set.of(ReservedSecurityRole.VIEWER), now, now);
            var guard = new AuthorizationGuard(jpa, () -> java.util.Optional.of(viewer));
            assertThrows(AuthorizationException.class, () -> new CheckExceptionService(jpa, () -> "DEFAULT", guard)
                    .review(id, f.bank(), "000123", null, null, DATE.plusDays(2), "Review", "Forged actor"));
            assertThrows(AuthorizationException.class, () -> new TransactionCorrectionService(jpa, () -> "DEFAULT", guard)
                    .reissueCheck(id, DATE.plusDays(2), "Forged actor", "Lost", check("000124")));
            var first = payment(f, f.bank(), check("000124")).lines().get(1);
            var second = payment(f, f.bank(), check("000125")).lines().get(1);
            long mixed = entries.enter(new TransactionCommand(DATE, null, "Two checks", null, List.of(
                    line(f.expense(), f.source(), "20", "0"), first, second))).id();
            long before = count(jpa, "Txn");
            assertThrows(IllegalArgumentException.class, () -> new TransactionCorrectionService(jpa)
                    .reissueCheck(mixed, DATE.plusDays(2), "Tester", "Lost", check("000126")));
            assertEquals(before, count(jpa, "Txn")); assertEquals("ENTERED", entries.load(mixed).status());
            long ordinary = entries.enter(new TransactionCommand(DATE, null, "Ordinary correction", null, List.of(
                    line(f.expense(), f.source(), "10", "0"), line(f.bank(), f.source(), "0", "10")))).id();
            new TransactionCorrectionService(jpa).reverse(ordinary, DATE.plusDays(2), "Tester", "Ordinary replacement", true);
            assertEquals(0L, number(jpa, "select count(*) from audit_event where action_type='CHECK_REISSUED'"));
        }
    }

    private BigDecimal total(Jpa jpa, long account)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select sum(s.amountSigned) from TxnSplit s where s.account.id=:id", BigDecimal.class)
                    .setParameter("id", account).getSingleResult();
        }
    }
}
