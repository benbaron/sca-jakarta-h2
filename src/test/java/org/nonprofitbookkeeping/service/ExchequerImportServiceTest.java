package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.exchequer.ExchequerCsvFixture;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ExchequerImportServiceTest
{
    @TempDir Path directory;

    @Test
    void editedLiteralDateAndAmountsCommitOnlyRemainingRowsToCanonicalJournalWithSourceAudit() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("edited")))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            var service = new ExchequerImportService(jpa, "DEFAULT", null);
            Path path = ExchequerCsvFixture.write(directory.resolve("2026-Q1.csv"), "12/31/2025", "1/1/2027");
            byte[] bytes = Files.readAllBytes(path);
            var preview = service.preview(path);
            assertEquals(2, preview.entries().size());
            assertEquals(LocalDate.of(2025, 12, 31), preview.entries().get(0).command().date());
            assertEquals(LocalDate.of(2027, 1, 1), preview.entries().get(1).command().date());
            assertNull(preview.entries().get(0).command().lines().get(0).accountId());
            assertEquals(1L, count(jpa, "Txn"));
            var retained = mapped(preview.entries().get(1), fixture, "Edited memo", "12.3456");
            var result = service.commit(preview, List.of(retained), false);
            assertEquals(1, result.transactionIds().size());
            var saved = new TransactionEntryService(jpa).load(result.transactionIds().get(0));
            assertEquals("Edited memo", saved.memo());
            assertEquals(LocalDate.of(2027, 1, 1), saved.date());
            assertEquals(new BigDecimal("12.3456"), saved.debitTotal());
            assertEquals(2L, count(jpa, "Txn"));
            assertArrayEquals(bytes, Files.readAllBytes(path));
            assertEquals(Set.of(retained.sourceLine()), service.importedSourceLines(preview));
            try (var em = jpa.em())
            {
                var audit = em.createQuery("from AuditEvent a where a.actionType='EXCHEQUER_CSV_IMPORTED'", AuditEvent.class).getSingleResult();
                assertTrue(audit.getBeforeValue().contains("Example source payee 2"));
                assertTrue(audit.getAfterValue().contains("Edited memo"));
                assertEquals("system", audit.getActor());
            }
        }
    }

    @Test
    void proposalsPreserveNegativeExpenseAdjustmentsAndZeroNetSplitsWithoutPostingNotes() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("directions")))
        {
            FundTransferTestFixture.seed(jpa);
            var records = ExchequerCsvFixture.header();
            var credit = ExchequerCsvFixture.row(1, "2/1/2026", "25");
            ExchequerCsvFixture.split(credit, 0, "-25", "", "Expense credit");
            records.add(credit);
            var zero = ExchequerCsvFixture.row(2, "2/2/2026", "0");
            ExchequerCsvFixture.split(zero, 0, "10", "Income", "");
            ExchequerCsvFixture.split(zero, 1, "10", "", "Expense");
            records.add(zero);
            var note = ExchequerCsvFixture.row(3, "", "0");
            note.set(8, "Cancelled source note");
            records.add(note);
            Path path = directory.resolve("directions.csv");
            Files.writeString(path, ExchequerCsvFixture.csv(records));
            var preview = new ExchequerImportService(jpa, "DEFAULT", null).preview(path);
            assertEquals(3, preview.source().rows().size());
            assertEquals(2, preview.entries().size());
            var first = preview.entries().get(0).command().lines();
            assertEquals(new BigDecimal("25"), first.get(0).debit());
            assertEquals(new BigDecimal("25"), first.get(1).credit());
            var second = preview.entries().get(1).command().lines();
            assertEquals(2, second.size());
            assertTrue(second.stream().noneMatch(line -> line.notes().startsWith("Bank:")));
            assertEquals(new BigDecimal("10"), second.get(0).credit());
            assertEquals(new BigDecimal("10"), second.get(1).debit());
            assertEquals(1L, count(jpa, "Txn"));
        }
    }

    @Test
    void invalidLastEntryRollsBackEarlierTransactionAndAllReceipts() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("rollback")))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            var service = new ExchequerImportService(jpa, "DEFAULT", null);
            var preview = service.preview(ExchequerCsvFixture.write(directory.resolve("rollback.csv"), "2/1/2026", "2/2/2026"));
            var first = mapped(preview.entries().get(0), fixture, "Valid", "10");
            var invalid = preview.entries().get(1);
            long before = count(jpa, "AuditEvent");
            assertTrue(assertThrows(IllegalArgumentException.class,
                    () -> service.commit(preview, List.of(first, invalid), true)).getMessage().contains("Source line"));
            assertEquals(1L, count(jpa, "Txn"));
            assertEquals(before, count(jpa, "AuditEvent"));
            assertTrue(service.importedSourceLines(preview).isEmpty());
        }
    }

    @Test
    void receiptBlocksReplayAcrossRestartAndRenamedSourceEvenAfterCanonicalDeletion() throws Exception
    {
        Path db = directory.resolve("replay");
        Path path = ExchequerCsvFixture.write(directory.resolve("original.csv"), "2/1/2026");
        try (Jpa jpa = new Jpa(db))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            var service = new ExchequerImportService(jpa, "DEFAULT", null);
            var preview = service.preview(path);
            var proposal = mapped(preview.entries().get(0), fixture, "Import", "10");
            long id = service.commit(preview, List.of(proposal), false).transactionIds().get(0);
            assertThrows(IllegalArgumentException.class, () -> service.commit(preview, List.of(proposal), true));
            new TransactionCorrectionService(jpa).delete(id, "operator", "Delete imported test entry");
        }
        Path renamed = Files.copy(path, directory.resolve("renamed.csv"));
        try (Jpa jpa = new Jpa(db))
        {
            var service = new ExchequerImportService(jpa, "DEFAULT", null);
            var preview = service.preview(renamed);
            assertEquals(Set.of(preview.entries().get(0).sourceLine()), service.importedSourceLines(preview));
            assertThrows(IllegalArgumentException.class, () -> service.commit(preview, preview.entries(), true));
            assertEquals(1L, count(jpa, "Txn"));
        }
    }

    @Test
    void changedFileWrongScopeDuplicateRowsAndStaleAccountRejectWithoutWrites() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("stale")))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            var service = new ExchequerImportService(jpa, "DEFAULT", null);
            Path path = ExchequerCsvFixture.write(directory.resolve("stale.csv"), "2/1/2026");
            var preview = service.preview(path);
            var proposal = mapped(preview.entries().get(0), fixture, "Import", "10");
            assertThrows(IllegalArgumentException.class, () -> service.commit(preview, List.of(proposal, proposal), true));
            assertThrows(IllegalArgumentException.class, () -> new ExchequerImportService(jpa, "OTHER", null).commit(preview, List.of(proposal), true));
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.find(Account.class, fixture.bank()).setActive(false);
                em.getTransaction().commit();
            }
            assertTrue(assertThrows(IllegalArgumentException.class, () -> service.commit(preview, List.of(proposal), true))
                    .getMessage().contains("active posting"));
            Files.writeString(path, Files.readString(path).replace("Example source payee", "Changed source payee"));
            assertTrue(assertThrows(IllegalArgumentException.class, () -> service.commit(preview, List.of(proposal), true))
                    .getMessage().contains("source CSV changed"));
            assertEquals(1L, count(jpa, "Txn"));
            assertTrue(service.importedSourceLines(preview).isEmpty());
        }
    }

    @Test
    void foreignFundRejectsThroughCanonicalOwnershipBeforeAnyBatchEffects() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("foreign")))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            long foreignFund;
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                Company other = new Company();
                other.setCode("OTHER");
                other.setDisplayName("Other company");
                em.persist(other);
                Fund fund = new Fund();
                fund.setCompany(other);
                fund.setCode("GENERAL");
                fund.setName("Other General");
                fund.setFundType(FundType.UNRESTRICTED);
                fund.setActive(true);
                em.persist(fund);
                em.flush();
                foreignFund = fund.getId();
                em.getTransaction().commit();
            }
            var service = new ExchequerImportService(jpa, "DEFAULT", null);
            var preview = service.preview(ExchequerCsvFixture.write(directory.resolve("foreign.csv"), "2/1/2026"));
            var command = new TransactionCommand(LocalDate.of(2026, 2, 1), null, "Foreign fund", null,
                    List.of(FundTransferTestFixture.line(fixture.bank(), foreignFund, "10", "0"),
                            FundTransferTestFixture.line(fixture.income(), fixture.source(), "0", "10")));
            assertThrows(IllegalArgumentException.class, () -> service.commit(preview,
                    List.of(new ExchequerImportService.ProposedEntry(preview.entries().get(0).sourceLine(), command)), true));
            assertEquals(1L, count(jpa, "Txn"));
            assertTrue(service.importedSourceLines(preview).isEmpty());
        }
    }

    @Test
    void probableDuplicateRequiresExplicitAcknowledgement() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("similar")))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            var service = new ExchequerImportService(jpa, "DEFAULT", null);
            var preview = service.preview(ExchequerCsvFixture.write(directory.resolve("similar.csv"), "1/1/2026"));
            var proposal = mapped(preview.entries().get(0), fixture, "Similar opening", "200");
            assertTrue(assertThrows(IllegalArgumentException.class, () -> service.commit(preview, List.of(proposal), false))
                    .getMessage().contains("date and amount"));
            assertEquals(1L, count(jpa, "Txn"));
            assertEquals(1, service.commit(preview, List.of(proposal), true).transactionIds().size());
        }
    }

    @Test
    void closedPeriodAndChangedChartRollBackTheWholeBatch() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("closed")))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            var service = new ExchequerImportService(jpa, "DEFAULT", null);
            var preview = service.preview(ExchequerCsvFixture.write(directory.resolve("closed.csv"), "2/1/2026", "3/1/2026"));
            PeriodCloseTestFixture.historical(jpa, "DEFAULT", LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
                    "CALCULATED", "treasurer", "Closed March");
            var proposals = preview.entries().stream().map(row -> mapped(row, fixture, "Import", "10")).toList();
            assertTrue(assertThrows(IllegalArgumentException.class, () -> service.commit(preview, proposals, true))
                    .getMessage().toLowerCase().contains("closed"));
            assertEquals(1L, count(jpa, "Txn"));
            assertTrue(service.importedSourceLines(preview).isEmpty());
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var company = em.createQuery("from Company c where c.code='DEFAULT'", Company.class).getSingleResult();
                ChartOfAccounts other = new ChartOfAccounts();
                other.setCompany(company);
                other.setName("Other selected chart");
                other.setVersion("1");
                other.setStatus(ChartStatus.ACTIVE);
                em.persist(other);
                company.setActiveChartOfAccounts(other);
                em.getTransaction().commit();
            }
            assertTrue(assertThrows(IllegalArgumentException.class, () -> service.commit(preview, List.of(proposals.get(0)), true))
                    .getMessage().contains("selected chart"));
            assertEquals(1L, count(jpa, "Txn"));
        }
    }

    @Test
    void authorizationPrecedesValidationAndUsesLiveSessionForActorAndCompany() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("authorization")))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            new SecurityBootstrapService(jpa).initializeIfUnambiguous();
            long user;
            try (var em = jpa.em())
            {
                user = em.createQuery("from AppUser u", AppUser.class).setMaxResults(1).getSingleResult().getId();
            }
            AtomicReference<Optional<AuthenticatedUserSession>> current = new AtomicReference<>(Optional.empty());
            var service = new ExchequerImportService(jpa, "DEFAULT", new AuthorizationGuard(jpa, current::get));
            assertThrows(AuthorizationException.class, () -> service.commit(null, null, false));
            current.set(Optional.of(session(user, "DEFAULT", ReservedSecurityRole.VIEWER)));
            assertThrows(AuthorizationException.class, () -> service.commit(null, null, false));
            current.set(Optional.of(session(user, "OTHER", ReservedSecurityRole.ACCOUNTANT)));
            assertThrows(AuthorizationException.class, () -> service.commit(null, null, false));
            current.set(Optional.of(session(user, "DEFAULT", ReservedSecurityRole.ACCOUNTANT)));
            var preview = service.preview(ExchequerCsvFixture.write(directory.resolve("auth.csv"), "2/1/2026"));
            service.commit(preview, List.of(mapped(preview.entries().get(0), fixture, "Authorized", "10")), true);
            try (var em = jpa.em())
            {
                assertEquals("review-operator", em.createQuery("from AuditEvent a where a.actionType='EXCHEQUER_CSV_IMPORTED'", AuditEvent.class)
                        .getSingleResult().getActor());
            }
        }
    }

    private static AuthenticatedUserSession session(long id, String company, ReservedSecurityRole role)
    {
        return new AuthenticatedUserSession(id, "review-operator", "Review Operator", company, Set.of(role), Instant.now(), Instant.now());
    }

    private static ExchequerImportService.ProposedEntry mapped(ExchequerImportService.ProposedEntry source,
            FundTransferTestFixture fixture, String memo, String amount)
    {
        return new ExchequerImportService.ProposedEntry(source.sourceLine(), new TransactionCommand(source.command().date(),
                null, memo, null, List.of(FundTransferTestFixture.line(fixture.bank(), fixture.source(), amount, "0"),
                FundTransferTestFixture.line(fixture.income(), fixture.source(), "0", amount))));
    }

    private static long count(Jpa jpa, String type)
    {
        try (var em = jpa.em())
        {
            return em.createQuery("select count(x) from " + type + " x", Long.class).getSingleResult();
        }
    }
}
