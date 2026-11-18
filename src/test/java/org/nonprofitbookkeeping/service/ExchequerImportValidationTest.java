package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.nonprofitbookkeeping.interchange.exchequer.ExchequerCsvFixture;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Regression tests of user-edited proposals at the real atomic import boundary. */
class ExchequerImportValidationTest
{
    @TempDir Path directory;

    enum InvalidEdit
    {
        UNBALANCED("debits must equal credits"), ZERO_LINE("non-zero"), BOTH_SIDES("both debit and credit"),
        NEGATIVE("cannot be negative"), MISSING_FUND("fund is required"), MISSING_DATE("date is required"),
        SINGLE_LINE("at least two"), LONG_MEMO("Memo must be at most 500"), LONG_NOTES("Line notes must be at most 500");

        final String explanation;

        InvalidEdit(String explanation)
        {
            this.explanation = explanation;
        }
    }

    @ParameterizedTest(name = "edited {0} rolls back the batch and permits corrected retry")
    @EnumSource(InvalidEdit.class)
    void invalidEditedEntryRollsBackEarlierEntryAndCanBeCorrectedWithoutRepreview(InvalidEdit fault) throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("edited-" + fault)))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            var service = new ExchequerImportService(jpa, "DEFAULT", null);
            Path csv = ExchequerCsvFixture.write(directory.resolve("entries.csv"), "2/1/2026", "2/2/2026");
            byte[] original = Files.readAllBytes(csv);
            var preview = service.preview(csv);
            var first = proposal(preview, 0, command(fixture, LocalDate.of(2026, 2, 1)));
            var valid = command(fixture, LocalDate.of(2026, 2, 2));
            var bad = proposal(preview, 1, invalidate(valid, fault));
            long auditBefore = count(jpa, "AuditEvent");

            var error = assertThrows(IllegalArgumentException.class,
                    () -> service.commit(preview, List.of(first, bad), true));
            assertTrue(error.getMessage().contains("Source line " + bad.sourceLine()), error.getMessage());
            assertTrue(error.getMessage().contains(fault.explanation), error.getMessage());
            assertEquals(1L, count(jpa, "Txn"), "The earlier valid entry must roll back too");
            assertEquals(auditBefore, count(jpa, "AuditEvent"), "Neither canonical audits nor import receipts may escape rollback");
            assertTrue(service.importedSourceLines(preview).isEmpty());

            var result = service.commit(preview, List.of(first, proposal(preview, 1, valid)), true);
            assertEquals(2, result.transactionIds().size());
            assertEquals(3L, count(jpa, "Txn"));
            assertEquals(Set.of(first.sourceLine(), bad.sourceLine()), service.importedSourceLines(preview));
            assertArrayEquals(original, Files.readAllBytes(csv));
        }
    }

    enum StaleReference
    {
        FUND("fund must be active"), PAYEE("Payee must be active"), BUDGET("Budget category must be active"),
        EVENT("Event must be active"), MERCHANT("Merchant must be active"), NON_POSTING_ACCOUNT("active posting account"),
        HEADER_ACCOUNT("Header bank account must be active");

        final String explanation;

        StaleReference(String explanation)
        {
            this.explanation = explanation;
        }
    }

    @ParameterizedTest(name = "stale {0} is rechecked on commit and recoverable")
    @EnumSource(StaleReference.class)
    void staleMappingRejectsTheWholeBatchAndReactivationAllowsRetry(StaleReference stale) throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("stale-" + stale)))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            var extra = seedOptionalReferences(jpa);
            var service = new ExchequerImportService(jpa, "DEFAULT", null);
            var preview = service.preview(ExchequerCsvFixture.write(directory.resolve("stale.csv"), "2/1/2026", "2/2/2026"));
            var first = proposal(preview, 0, command(fixture, LocalDate.of(2026, 2, 1)));
            var tagged = new TransactionCommand(LocalDate.of(2026, 2, 2), extra.payee(), "Tagged import",
                    stale == StaleReference.HEADER_ACCOUNT ? fixture.allocation() : null,
                    List.of(FundTransferTestFixture.line(fixture.bank(), fixture.destination(), "10", "0"),
                            new TransactionLineCommand(fixture.expense(), fixture.destination(), extra.budget(), extra.activity(),
                                    extra.merchant(), BigDecimal.ZERO, BigDecimal.TEN, false, "Reviewed tags")));
            var second = proposal(preview, 1, tagged);
            long audits = count(jpa, "AuditEvent");
            setAvailable(jpa, fixture, extra, stale, false);

            var error = assertThrows(IllegalArgumentException.class,
                    () -> service.commit(preview, List.of(first, second), true));
            assertTrue(error.getMessage().contains("Source line " + second.sourceLine()), error.getMessage());
            assertTrue(error.getMessage().contains(stale.explanation), error.getMessage());
            assertEquals(1L, count(jpa, "Txn"));
            assertEquals(audits, count(jpa, "AuditEvent"));
            assertTrue(service.importedSourceLines(preview).isEmpty());

            setAvailable(jpa, fixture, extra, stale, true);
            var result = service.commit(preview, List.of(first, second), true);
            var saved = new TransactionEntryService(jpa).load(result.transactionIds().get(1));
            assertEquals(extra.payee(), saved.payeeId());
            var line = saved.lines().stream().filter(value -> value.accountId().equals(fixture.expense())).findFirst().orElseThrow();
            assertEquals(extra.budget(), line.budgetCategoryId());
            assertEquals(extra.activity(), line.activityId());
            assertEquals(extra.merchant(), line.merchantId());
            assertEquals(fixture.destination(), line.fundId());
            assertEquals(2, service.importedSourceLines(preview).size());
        }
    }

    enum InvalidSourceDisposition
    {
        CORRECT, DELETE
    }

    @ParameterizedTest(name = "invalid source proposal may be {0} before acceptance")
    @EnumSource(InvalidSourceDisposition.class)
    void malformedSourceDateCanBeCorrectedOrItsProposalDeleted(InvalidSourceDisposition disposition) throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("source-" + disposition)))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            Path csv = ExchequerCsvFixture.write(directory.resolve("invalid-date.csv"), "2/1/2026", "not-a-date");
            byte[] original = Files.readAllBytes(csv);
            var service = new ExchequerImportService(jpa, "DEFAULT", null);
            var preview = service.preview(csv);
            assertTrue(preview.source().hasBlockingErrors());
            assertEquals(2, preview.entries().size(), "Invalid source rows must remain reviewable");
            assertNull(preview.entries().get(1).command().date());
            var retained = new ArrayList<ExchequerImportService.ProposedEntry>();
            retained.add(proposal(preview, 0, command(fixture, LocalDate.of(2026, 2, 1))));
            if (disposition == InvalidSourceDisposition.CORRECT)
            {
                retained.add(proposal(preview, 1, command(fixture, LocalDate.of(2027, 5, 6))));
            }
            var result = service.commit(preview, retained, false);
            assertEquals(retained.size(), result.transactionIds().size());
            assertEquals(retained.size() + 1L, count(jpa, "Txn"));
            if (disposition == InvalidSourceDisposition.CORRECT)
            {
                assertEquals(LocalDate.of(2027, 5, 6), new TransactionEntryService(jpa).load(result.transactionIds().get(1)).date());
                try (var em = jpa.em())
                {
                    var receipt = em.createQuery("from AuditEvent a where a.actionType='EXCHEQUER_CSV_IMPORTED' and a.entityId=:id", AuditEvent.class)
                            .setParameter("id", result.transactionIds().get(1).toString()).getSingleResult();
                    assertTrue(receipt.getBeforeValue().contains("not-a-date"));
                    assertTrue(receipt.getAfterValue().contains("2027-05-06"));
                }
            }
            else
            {
                assertEquals(Set.of(retained.get(0).sourceLine()), service.importedSourceLines(preview));
            }
            assertArrayEquals(original, Files.readAllBytes(csv));
        }
    }

    @Test
    void unknownSourceRecordAfterAValidProposalRollsBackAndEmptySelectionWritesNothing() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("selection")))
        {
            var fixture = FundTransferTestFixture.seed(jpa);
            var service = new ExchequerImportService(jpa, "DEFAULT", null);
            var preview = service.preview(ExchequerCsvFixture.write(directory.resolve("selection.csv"), "2/1/2026"));
            var valid = proposal(preview, 0, command(fixture, LocalDate.of(2026, 2, 1)));
            var unknown = new ExchequerImportService.ProposedEntry(Integer.MAX_VALUE, valid.command());
            long audits = count(jpa, "AuditEvent");
            assertThrows(IllegalArgumentException.class, () -> service.commit(preview, List.of(valid, unknown), true));
            assertThrows(IllegalArgumentException.class, () -> service.commit(preview, List.of(), true));
            assertEquals(1L, count(jpa, "Txn"));
            assertEquals(audits, count(jpa, "AuditEvent"));
            assertTrue(service.importedSourceLines(preview).isEmpty());
            assertEquals(1, service.commit(preview, List.of(valid), true).transactionIds().size());
        }
    }

    private static TransactionCommand invalidate(TransactionCommand command, InvalidEdit fault)
    {
        List<TransactionLineCommand> lines = new ArrayList<>(command.lines());
        var first = lines.get(0);
        switch (fault)
        {
            case UNBALANCED -> lines.set(0, line(first, first.fundId(), "11", "0", first.notes()));
            case ZERO_LINE -> lines.add(line(first, first.fundId(), "0", "0", "Blank amount"));
            case BOTH_SIDES -> lines.set(0, line(first, first.fundId(), "10", "5", first.notes()));
            case NEGATIVE -> lines.set(0, line(first, first.fundId(), "-10", "0", first.notes()));
            case MISSING_FUND -> lines.set(0, line(first, null, "10", "0", first.notes()));
            case SINGLE_LINE -> lines.remove(1);
            case LONG_NOTES -> lines.set(0, line(first, first.fundId(), "10", "0", "n".repeat(501)));
            default ->
            {
            }
        }
        return new TransactionCommand(fault == InvalidEdit.MISSING_DATE ? null : command.date(), null,
                fault == InvalidEdit.LONG_MEMO ? "m".repeat(501) : command.memo(), null, lines);
    }

    private static TransactionLineCommand line(TransactionLineCommand source, Long fund, String debit, String credit, String notes)
    {
        return new TransactionLineCommand(source.accountId(), fund, null, null, null,
                new BigDecimal(debit), new BigDecimal(credit), false, notes);
    }

    private record OptionalReferences(long payee, long budget, long activity, long merchant)
    {
    }

    private static OptionalReferences seedOptionalReferences(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.createQuery("from Company c where c.code='DEFAULT'", Company.class).getSingleResult();
            Counterparty payee = new Counterparty();
            payee.setCompany(company);
            payee.setDisplayName("Synthetic payee");
            em.persist(payee);
            BudgetCategory budget = new BudgetCategory();
            budget.setCompany(company);
            budget.setCode("SYNTHETIC");
            budget.setName("Synthetic budget");
            em.persist(budget);
            Activity activity = new Activity();
            activity.setCompany(company);
            activity.setCode("SYNTHETIC");
            activity.setName("Synthetic event");
            em.persist(activity);
            Merchant merchant = new Merchant();
            merchant.setCompany(company);
            merchant.setName("Synthetic merchant");
            em.persist(merchant);
            em.flush();
            var result = new OptionalReferences(payee.getId(), budget.getId(), activity.getId(), merchant.getId());
            em.getTransaction().commit();
            return result;
        }
    }

    private static void setAvailable(Jpa jpa, FundTransferTestFixture fixture, OptionalReferences extra,
                                     StaleReference reference, boolean available)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            switch (reference)
            {
                case FUND -> em.find(Fund.class, fixture.destination()).setActive(available);
                case PAYEE -> em.find(Counterparty.class, extra.payee()).setActive(available);
                case BUDGET -> em.find(BudgetCategory.class, extra.budget()).setActive(available);
                case EVENT -> em.find(Activity.class, extra.activity()).setActive(available);
                case MERCHANT -> em.find(Merchant.class, extra.merchant()).setActive(available);
                case NON_POSTING_ACCOUNT -> em.find(Account.class, fixture.expense()).setPosting(available);
                case HEADER_ACCOUNT -> em.find(Account.class, fixture.allocation()).setActive(available);
            }
            em.getTransaction().commit();
        }
    }

    private static TransactionCommand command(FundTransferTestFixture fixture, LocalDate date)
    {
        return new TransactionCommand(date, null, "Reviewed import", null,
                List.of(FundTransferTestFixture.line(fixture.bank(), fixture.source(), "10", "0"),
                        FundTransferTestFixture.line(fixture.income(), fixture.source(), "0", "10")));
    }

    private static ExchequerImportService.ProposedEntry proposal(ExchequerImportService.Preview preview, int index,
                                                               TransactionCommand command)
    {
        return new ExchequerImportService.ProposedEntry(preview.entries().get(index).sourceLine(), command);
    }

    private static long count(Jpa jpa, String entity)
    {
        try (var em = jpa.em())
        {
            return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
        }
    }
}
