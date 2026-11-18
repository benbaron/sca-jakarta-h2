package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.exchequer.ExchequerCsvFixture;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.TransactionInformation;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JournalInformationServiceTest
{
    @TempDir Path directory;
    private static final LocalDate DATE = LocalDate.of(2026, 2, 1);
    private static final TransactionInformation FACTS = new TransactionInformation("000042", DATE.plusDays(5), DATE.minusDays(2));

    @Test
    void entryUpdateSearchRestartAndAuditPreserveInformationWithoutClearingBankLines() throws Exception
    {
        Path database = directory.resolve("information");
        long transactionId;
        try (Jpa jpa = new Jpa(database))
        {
            var f = FundTransferTestFixture.seed(jpa);
            var service = new TransactionEntryService(jpa);
            var saved = service.enter(command(f, FACTS));
            transactionId = saved.id();
            assertEquals(FACTS, saved.information());
            assertEquals(new BigDecimal("10.0000"), saved.debitTotal());
            assertTrue(saved.lines().stream().noneMatch(TransactionView.Line::bankCleared));
            assertTrue(saved.lines().stream().allMatch(line -> line.bankClearedOn() == null));
            assertEquals(1, service.search(null, null, "000042", 100).size());
            var edited = new TransactionInformation("Transfer-0009", DATE.plusDays(9), DATE.minusDays(4));
            service.update(saved.id(), command(f, edited));
            assertEquals(edited, service.load(saved.id()).information());
            try (var em = jpa.em())
            {
                var audit = em.createQuery("from AuditEvent a where a.actionType='TRANSACTION_UPDATED' and a.entityId=:id", AuditEvent.class)
                        .setParameter("id", Long.toString(saved.id())).getSingleResult();
                assertTrue(audit.getBeforeValue().contains("000042"));
                assertTrue(audit.getAfterValue().contains("Transfer-0009"));
            }
        }
        try (Jpa jpa = new Jpa(database))
        {
            var saved = new TransactionEntryService(jpa).load(transactionId);
            assertEquals("Transfer-0009", saved.information().transferOrCheckReference());
            assertEquals(DATE.plusDays(9), saved.information().statementDate());
            assertEquals(DATE.minusDays(4), saved.information().incomingDate());
            assertTrue(saved.lines().stream().noneMatch(TransactionView.Line::bankCleared));
        }
    }

    @Test
    void nativeReplayIncludesNonemptyInformationAndRetainsLegacyEmptyCommandIdentity()
    {
        try (Jpa jpa = new Jpa(directory.resolve("replay")))
        {
            var f = FundTransferTestFixture.seed(jpa);
            var service = new TransactionEntryService(jpa);
            UUID request = UUID.randomUUID();
            var command = command(f, FACTS);
            var first = service.enterJournalDraft(command, request);
            assertEquals(first.id(), service.enterJournalDraft(command, request).id());
            assertThrows(PostingException.class, () -> service.enterJournalDraft(command(f,
                    new TransactionInformation("Changed", FACTS.statementDate(), FACTS.incomingDate())), request));
            UUID legacy = UUID.randomUUID();
            var old = command(f, TransactionInformation.empty());
            var legacyCommand = new TransactionCommand(old.date(), old.payeeId(), old.memo(), old.bankAccountId(), old.lines());
            var entered = service.enterJournalDraft(legacyCommand, legacy);
            assertEquals(entered.id(), service.enterJournalDraft(old, legacy).id());
        }
    }

    @Test
    void csvProjectsAllFourSplitsAndInformationIntoTheSavedJournalWithoutGuessingClearance() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("csv")))
        {
            var f = FundTransferTestFixture.seed(jpa);
            var records = ExchequerCsvFixture.header();
            var row = ExchequerCsvFixture.row(1, "2/1/2026", "20");
            row.set(4, "2/6/2026");
            row.set(5, "000042");
            row.set(6, "1/30/2026");
            ExchequerCsvFixture.split(row, 0, "20", "Income A", "");
            ExchequerCsvFixture.split(row, 1, "30", "Income B", "");
            ExchequerCsvFixture.split(row, 2, "25", "", "Expense C");
            ExchequerCsvFixture.split(row, 3, "5", "", "Expense D");
            records.add(row);
            Path csv = directory.resolve("four-splits.csv");
            Files.writeString(csv, ExchequerCsvFixture.csv(records));
            var importer = new ExchequerImportService(jpa, "DEFAULT", null);
            var preview = importer.preview(csv);
            var proposal = preview.entries().get(0);
            assertEquals(5, proposal.command().lines().size());
            assertEquals(FACTS, proposal.command().information());
            List<TransactionLineCommand> mapped = new ArrayList<>();
            int index = 0;
            for (var line : proposal.command().lines())
            {
                long account = index == 0 ? f.bank() : index <= 2 ? f.income() : f.expense();
                mapped.add(new TransactionLineCommand(account, f.source(), null, null, null, line.debit(), line.credit(), false, line.notes()));
                index++;
            }
            var command = new TransactionCommand(proposal.command().date(), null, proposal.command().memo(), null,
                    mapped, List.of(), proposal.command().information());
            var result = importer.commit(preview, List.of(new ExchequerImportService.ProposedEntry(proposal.sourceLine(), command)), false);
            var saved = new TransactionEntryService(jpa).load(result.transactionIds().get(0));
            assertEquals(FACTS, saved.information());
            assertEquals(5, saved.lines().size());
            assertEquals(List.of("Bank: Checking", "Category: Income A", "Category: Income B", "Category: Expense C", "Category: Expense D"),
                    saved.lines().stream().map(TransactionView.Line::notes).toList());
            assertEquals(new BigDecimal("50.0000"), saved.debitTotal());
            assertEquals(saved.debitTotal(), saved.creditTotal());
            assertTrue(saved.lines().stream().noneMatch(TransactionView.Line::bankCleared));
        }
    }

    @Test
    void overlongInformationAndClosedAccountingDatesRejectWithoutWrites()
    {
        try (Jpa jpa = new Jpa(directory.resolve("guard")))
        {
            var f = FundTransferTestFixture.seed(jpa);
            var service = new TransactionEntryService(jpa);
            assertThrows(PostingException.class, () -> service.enter(command(f,
                    new TransactionInformation("x".repeat(201), DATE, DATE))));
            var fractional = new TransactionCommand(DATE, null, "Fractional cents", null,
                    List.of(FundTransferTestFixture.line(f.bank(), f.source(), "10.001", "0"),
                            FundTransferTestFixture.line(f.income(), f.source(), "0", "10.001")));
            assertThrows(PostingException.class, () -> service.enter(fractional));
            assertThrows(PostingException.class, () -> service.update(service.search(null, null, "", 100).get(0).id(), fractional));
            PeriodCloseTestFixture.historical(jpa, "DEFAULT", DATE, DATE.plusDays(27), "CUSTOM", "tester", "Close date");
            assertThrows(ClosedPeriodRangeException.class, () -> service.enter(command(f, FACTS)));
            assertEquals(1, service.search(null, null, "", 100).size());
        }
    }

    @Test
    void informationWritesUseCurrentRoleAndAuthenticatedActor()
    {
        try (Jpa jpa = new Jpa(directory.resolve("authorization")))
        {
            var f = FundTransferTestFixture.seed(jpa);
            long userId;
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var user = new org.nonprofitbookkeeping.model.AppUser();
                user.setUsername("information-operator");
                user.setDisplayName("Information Operator");
                em.persist(user);
                em.flush();
                userId = user.getId();
                em.getTransaction().commit();
            }
            var role = new java.util.concurrent.atomic.AtomicReference<>(ReservedSecurityRole.VIEWER);
            var guard = new AuthorizationGuard(jpa, () -> java.util.Optional.of(new AuthenticatedUserSession(userId,
                    "information-operator", "Information Operator", "DEFAULT", java.util.Set.of(role.get()),
                    java.time.Instant.now(), java.time.Instant.now())));
            var service = new TransactionEntryService(jpa, () -> "DEFAULT", guard);
            assertThrows(AuthorizationException.class, () -> service.enter(command(f,
                    new TransactionInformation("x".repeat(201), DATE, DATE))));
            role.set(ReservedSecurityRole.ACCOUNTANT);
            var saved = service.enter(command(f, FACTS));
            role.set(ReservedSecurityRole.VIEWER);
            assertThrows(AuthorizationException.class, () -> service.update(saved.id(), command(f, TransactionInformation.empty())));
            assertEquals(FACTS, service.load(saved.id()).information());
            try (var em = jpa.em())
            {
                var event = em.createQuery("from AuditEvent a where a.actionType='TRANSACTION_ENTERED' and a.entityId=:id", AuditEvent.class)
                        .setParameter("id", saved.id().toString()).getSingleResult();
                assertEquals("information-operator", event.getActor());
                assertTrue(event.getAfterValue().contains("000042"));
            }
        }
    }

    @Test
    void offsettingImbalancesAcrossCsvRowsCannotPassEvenWhenTheBatchBalances() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("row-balance")))
        {
            var f = FundTransferTestFixture.seed(jpa);
            var records = ExchequerCsvFixture.header();
            var over = ExchequerCsvFixture.row(1, "2/1/2026", "10.01");
            var under = ExchequerCsvFixture.row(2, "2/2/2026", "9.99");
            ExchequerCsvFixture.split(over, 0, "10", "Income", "");
            ExchequerCsvFixture.split(under, 0, "10", "Income", "");
            records.add(over);
            records.add(under);
            Path source = directory.resolve("offsetting.csv");
            Files.writeString(source, ExchequerCsvFixture.csv(records));
            var importer = new ExchequerImportService(jpa, "DEFAULT", null);
            var preview = importer.preview(source);
            assertEquals(2, preview.source().messages().stream().filter(message -> message.code().equals("EXCHEQUER_SPLIT_BALANCE")).count());
            List<ExchequerImportService.ProposedEntry> reviewed = new ArrayList<>();
            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            for (var proposal : preview.entries())
            {
                List<TransactionLineCommand> mapped = new ArrayList<>();
                int index = 0;
                for (var line : proposal.command().lines())
                {
                    mapped.add(new TransactionLineCommand(index++ == 0 ? f.bank() : f.income(), f.source(), null, null, null,
                            line.debit(), line.credit(), false, line.notes()));
                    totalDebit = totalDebit.add(line.debit());
                    totalCredit = totalCredit.add(line.credit());
                }
                var candidate = new TransactionCommand(proposal.command().date(), null, "Unbalanced candidate", null, mapped);
                assertFalse(new TransactionCommandValidator().validate(candidate).valid());
                reviewed.add(new ExchequerImportService.ProposedEntry(proposal.sourceLine(), candidate));
            }
            assertEquals(0, totalDebit.compareTo(totalCredit), "Batch balance must never hide individually unbalanced candidates");
            var error = assertThrows(IllegalArgumentException.class, () -> importer.commit(preview, reviewed, true));
            assertTrue(error.getMessage().contains("Source line " + reviewed.get(0).sourceLine()));
            assertTrue(error.getMessage().contains("debits must equal credits"));
            assertTrue(importer.importedSourceLines(preview).isEmpty());
            assertEquals(1, new TransactionEntryService(jpa).search(null, null, "", 100).size());
        }
    }

    private static TransactionCommand command(FundTransferTestFixture f, TransactionInformation information)
    {
        return new TransactionCommand(DATE, null, "Information", null,
                List.of(FundTransferTestFixture.line(f.bank(), f.source(), "10", "0"),
                        FundTransferTestFixture.line(f.income(), f.source(), "0", "10")), List.of(), information);
    }
}
