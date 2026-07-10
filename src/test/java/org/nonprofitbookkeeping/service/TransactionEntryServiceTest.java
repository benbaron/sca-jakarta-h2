package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TransactionEntryServiceTest
{
    @Test
    public void enterLoadSearchAndJournalView_roundTripCanonicalTxn(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("transaction-entry")))
        {
            seedMasterData(jpa);
            TransactionEntryService service = new TransactionEntryService(jpa, new TransactionCommandValidator());

            TransactionView entered = service.enter(command("Donation", new BigDecimal("125.00")));

            assertEquals(LocalDate.of(2026, 3, 14), entered.date());
            assertEquals("Donation", entered.memo());
            assertEquals(new BigDecimal("125.0000"), entered.debitTotal());
            assertEquals(new BigDecimal("125.0000"), entered.creditTotal());
            assertEquals(new BigDecimal("125.0000"), storedAmount(jpa, entered.id(), 1L));
            assertEquals(new BigDecimal("125.0000"), storedAmount(jpa, entered.id(), 2L));

            TransactionView loaded = service.load(entered.id());
            assertEquals(2, loaded.lines().size());
            assertEquals(1, service.search(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), "donat", 20).size());

            AccountingJournalProjection journal = service.journalView(entered.id());
            assertEquals(new BigDecimal("125.0000"), journal.debitTotal());
            assertEquals(new BigDecimal("125.0000"), journal.creditTotal());
        }
    }

    @Test
    public void enterAndLoad_persistsSupplementalDetailRows(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("transaction-supplemental")))
        {
            seedMasterData(jpa);
            TransactionEntryService service = new TransactionEntryService(jpa, new TransactionCommandValidator());

            TransactionCommand command = new TransactionCommand(
                    LocalDate.of(2026, 3, 14), 1L, "Invoice with receivable", 1L,
                    balancedLines(new BigDecimal("250.00")),
                    List.of(
                            new TransactionSupplementalLineCommand(
                                    "RECEIVABLE", "line 1", "Donor", "Pledge receivable", "INV-100",
                                    new BigDecimal("250.00"), LocalDate.of(2026, 4, 15), null, null, "Expected payment"),
                            new TransactionSupplementalLineCommand(
                                    "DEFERRED_REVENUE", "line 2", "Donor", "Registration deferred revenue", "REG-1",
                                    new BigDecimal("50.00"), null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31), "Recognize over event period")));

            TransactionView entered = service.enter(command);
            TransactionView loaded = service.load(entered.id());

            assertEquals(2, loaded.supplementalLines().size());
            TransactionSupplementalLineView receivable = loaded.supplementalLines().get(0);
            assertEquals("RECEIVABLE", receivable.kind());
            assertEquals("line 1", receivable.entryRef());
            assertEquals("Pledge receivable", receivable.description());
            assertEquals(new BigDecimal("250.0000"), receivable.amount());
            assertEquals(LocalDate.of(2026, 4, 15), receivable.dueDate());
            assertEquals("Expected payment", receivable.notes());

            TransactionSupplementalLineView deferred = loaded.supplementalLines().get(1);
            assertEquals("DEFERRED_REVENUE", deferred.kind());
            assertEquals(LocalDate.of(2026, 3, 1), deferred.startDate());
            assertEquals(LocalDate.of(2026, 5, 31), deferred.endDate());
        }
    }

    @Test
    public void update_replacesHeaderLinesAndSupplementalRowsUnderEnteredPolicy(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("transaction-update")))
        {
            seedMasterData(jpa);
            TransactionEntryService service = new TransactionEntryService(jpa, new TransactionCommandValidator());
            TransactionView entered = service.enter(new TransactionCommand(
                    LocalDate.of(2026, 3, 14), 1L, "Original", 1L,
                    balancedLines(new BigDecimal("100.00")),
                    List.of(new TransactionSupplementalLineCommand(
                            "PAYABLE", "old", "Vendor", "Old payable", "BILL-1", new BigDecimal("100.00"), LocalDate.of(2026, 3, 31), null, null, null))));

            TransactionView updated = service.update(entered.id(), new TransactionCommand(
                    LocalDate.of(2026, 3, 14), 1L, "Updated", 1L,
                    balancedLines(new BigDecimal("75.00")),
                    List.of(new TransactionSupplementalLineCommand(
                            "OTHER_ASSET", "new", "Custodian", "Updated other asset", "OA-1", new BigDecimal("75.00"), null, null, null, "replacement"))));

            assertEquals("Updated", updated.memo());
            assertEquals(new BigDecimal("75.0000"), updated.debitTotal());
            assertEquals(new BigDecimal("75.0000"), updated.creditTotal());
            assertEquals(1, updated.supplementalLines().size());
            assertEquals("OTHER_ASSET", updated.supplementalLines().get(0).kind());
            assertEquals("Updated other asset", updated.supplementalLines().get(0).description());
            try (EntityManager em = jpa.em())
            {
                Long splitCount = em.createQuery("select count(s) from TxnSplit s where s.txn.id = :id", Long.class)
                        .setParameter("id", entered.id())
                        .getSingleResult();
                Long supplementalCount = em.createQuery("select count(s) from TxnSupplementalLine s where s.txn.id = :id", Long.class)
                        .setParameter("id", entered.id())
                        .getSingleResult();
                assertEquals(2L, splitCount);
                assertEquals(1L, supplementalCount);
            }
        }
    }

    @Test
    public void enter_rejectsInvalidSupplementalRows(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("transaction-supplemental-validation")))
        {
            seedMasterData(jpa);
            TransactionEntryService service = new TransactionEntryService(jpa, new TransactionCommandValidator());
            TransactionCommand command = new TransactionCommand(
                    LocalDate.of(2026, 3, 14), 1L, "Bad supplemental", 1L,
                    balancedLines(new BigDecimal("25.00")),
                    List.of(new TransactionSupplementalLineCommand(
                            "PAYABLE", "line 1", "Vendor", "", "BILL-1", new BigDecimal("25.00"), null, null, null, null)));

            PostingException ex = assertThrows(PostingException.class, () -> service.enter(command));
            assertTrue(ex.getMessage().contains("requires a description"));
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(s) from TxnSupplementalLine s", Long.class).getSingleResult());
            }
        }
    }

    @Test
    public void enter_rollsBackWhenReferencedMasterDataIsMissing(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("transaction-rollback")))
        {
            seedMasterData(jpa);
            TransactionEntryService service = new TransactionEntryService(jpa, new TransactionCommandValidator());
            TransactionCommand command = new TransactionCommand(
                    LocalDate.of(2026, 3, 14), null, "Bad fund", null,
                    List.of(
                            new TransactionLineCommand(1L, 999L, null, null, null, new BigDecimal("10.00"), BigDecimal.ZERO, false, null),
                            new TransactionLineCommand(2L, 1L, null, null, null, BigDecimal.ZERO, new BigDecimal("10.00"), false, null)));

            PostingException ex = assertThrows(PostingException.class, () -> service.enter(command));
            assertTrue(ex.getMessage().contains("Fund not found"));
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(s) from TxnSplit s", Long.class).getSingleResult());
            }
        }
    }

    private static TransactionCommand command(String memo, BigDecimal amount)
    {
        return new TransactionCommand(
                LocalDate.of(2026, 3, 14), 1L, memo, 1L,
                balancedLines(amount));
    }

    private static List<TransactionLineCommand> balancedLines(BigDecimal amount)
    {
        return List.of(
                new TransactionLineCommand(1L, 1L, null, null, null, amount, BigDecimal.ZERO, false, "cash"),
                new TransactionLineCommand(2L, 1L, null, null, null, BigDecimal.ZERO, amount, false, "income"));
    }

    private static BigDecimal storedAmount(Jpa jpa, Long txnId, Long accountId)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                            "select s.amountSigned from TxnSplit s where s.txn.id = :txnId and s.account.id = :accountId", BigDecimal.class)
                    .setParameter("txnId", txnId)
                    .setParameter("accountId", accountId)
                    .getSingleResult();
        }
    }

    private static void seedMasterData(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (1, 'Test', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (1, 1, '1000', 'Cash', 'ASSET', 'DEBIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (2, 1, '4000', 'Income', 'INCOME', 'CREDIT')").executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, code, name, fund_type) VALUES (1, 'OPERATING', 'Operating', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO counterparty (id, display_name, kind) VALUES (1, 'Donor', 'OTHER')").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
