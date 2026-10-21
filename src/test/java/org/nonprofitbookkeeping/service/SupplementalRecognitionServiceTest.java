package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.ExpenseFunction;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SupplementalRecognitionServiceTest
{
    private static final LocalDate OPENING = LocalDate.of(2026, 1, 15);

    @Test
    void prepaidTwelveMonthRecognitionIsExactIdempotentAndRetainsDimensions(@TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("prepaid-twelve-month")))
        {
            seed(jpa);
            UUID item = UUID.randomUUID();
            enterOpening(jpa, SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, item,
                    OPENING, new BigDecimal("1200"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
            var service = new SupplementalRecognitionService(jpa, () -> "TEST");
            var request = request(SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, item,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    LocalDate.of(2026, 12, 31), 1006L, ExpenseFunction.OA);

            var preview = service.preview(request);
            assertEquals(12, preview.proposals().size());
            assertTrue(preview.proposals().stream().allMatch(p -> p.amount().compareTo(new BigDecimal("100.0000")) == 0));
            assertEquals(new BigDecimal("1200.0000"), preview.proposed());

            var first = service.save(preview.proposals().get(0));
            assertEquals(first.id(), service.save(preview.proposals().get(0)).id(), "exact retry must return one transaction");
            for (int index = 1; index < preview.proposals().size(); index++)
            {
                service.save(preview.proposals().get(index));
            }

            var balance = new SupplementalOpenItemQueryService(jpa, () -> "TEST")
                    .query(SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, LocalDate.of(2026, 12, 31));
            assertEquals(new BigDecimal("0.0000"), balance.openAmount());
            assertTrue(service.preview(request).proposals().isEmpty(), "completed schedule must not propose duplicates");
            assertEquals(13, new TransactionEntryService(jpa, () -> "TEST").search(null, null, null, 100).size());

            TransactionView saved = new TransactionEntryService(jpa, () -> "TEST").load(first.id());
            assertEquals(2, saved.lines().size());
            assertEquals(1001L, saved.lines().get(0).fundId());
            assertEquals(101L, saved.lines().get(0).activityId());
            assertEquals(1101L, saved.lines().get(0).budgetCategoryId());
            assertEquals(ExpenseFunction.OA, saved.lines().get(0).expenseFunction());
            assertNull(saved.lines().get(1).expenseFunction());
            assertEquals(item, saved.supplementalLines().get(0).itemId());
            assertEquals("DECREASE", saved.supplementalLines().get(0).itemEffect());
        }
    }

    @Test
    void partialMonthsResidualRoundingAndPriorRecognitionAreAppliedInOrder(@TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("partial-and-rounding")))
        {
            seed(jpa);
            UUID item = UUID.randomUUID();
            enterOpening(jpa, SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, item,
                    OPENING, new BigDecimal("1000"), OPENING, LocalDate.of(2026, 3, 15));
            enterManualDecrease(jpa, SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE,
                    item, LocalDate.of(2026, 1, 20), new BigDecimal("100"));

            var preview = new SupplementalRecognitionService(jpa, () -> "TEST").preview(request(
                    SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, item,
                    OPENING, LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 15),
                    1006L, ExpenseFunction.AR));

            assertEquals(List.of(
                            LocalDate.of(2026, 1, 31),
                            LocalDate.of(2026, 2, 28),
                            LocalDate.of(2026, 3, 15)),
                    preview.proposals().stream().map(SupplementalRecognitionService.Proposal::recognitionDate).toList());
            assertEquals(List.of(
                            new BigDecimal("233.3333"),
                            new BigDecimal("333.3333"),
                            new BigDecimal("333.3334")),
                    preview.proposals().stream().map(SupplementalRecognitionService.Proposal::amount).toList());
            assertEquals(new BigDecimal("900.0000"), preview.proposed());
            assertEquals(new BigDecimal("1000.0000"), preview.installments().stream()
                    .map(SupplementalRecognitionService.Installment::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    @Test
    void deferredRecognitionUsesLiabilityDebitAndIncomeCredit(@TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("deferred-direction")))
        {
            seed(jpa);
            UUID item = UUID.randomUUID();
            enterOpening(jpa, SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE, item,
                    OPENING, new BigDecimal("240"), OPENING, LocalDate.of(2026, 2, 28));
            var service = new SupplementalRecognitionService(jpa, () -> "TEST");
            var preview = service.preview(request(SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE,
                    item, OPENING, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 2, 28), 1003L, null));

            assertEquals(2, preview.proposals().size());
            var command = preview.proposals().get(0).command();
            assertEquals(1005L, command.lines().get(0).accountId());
            assertEquals(new BigDecimal("120.0000"), command.lines().get(0).debit());
            assertEquals(1003L, command.lines().get(1).accountId());
            assertEquals(new BigDecimal("120.0000"), command.lines().get(1).credit());
            assertNull(command.lines().get(1).expenseFunction());
            var saved = service.save(preview.proposals().get(0));
            assertEquals(0, saved.debitTotal().compareTo(saved.creditTotal()));
            assertEquals(0, new BigDecimal("120.0000").compareTo(new SupplementalOpenItemQueryService(jpa, () -> "TEST")
                    .query(SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE, LocalDate.of(2026, 1, 31))
                    .openAmount()));
        }
    }

    @Test
    void previewRejectsMissingWrongAndForeignClassificationWithoutWrites(@TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("classification-rejections")))
        {
            seed(jpa);
            UUID item = UUID.randomUUID();
            enterOpening(jpa, SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, item,
                    OPENING, new BigDecimal("100"), OPENING, LocalDate.of(2026, 1, 31));
            var service = new SupplementalRecognitionService(jpa, () -> "TEST");

            assertThrows(PostingException.class, () -> service.preview(request(
                    SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, item,
                    OPENING, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31), 1006L, null)));
            assertThrows(PostingException.class, () -> service.preview(request(
                    SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, item,
                    OPENING, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31), 1003L, ExpenseFunction.OA)));
            assertThrows(CompanyOwnershipException.class, () -> service.preview(request(
                    SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, item,
                    OPENING, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31), 2006L, ExpenseFunction.OA)));
            assertThrows(PostingException.class, () -> service.preview(request(
                    SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, item,
                    LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31),
                    LocalDate.of(2026, 1, 31), 1006L, ExpenseFunction.OA)));
            assertEquals(1, new TransactionEntryService(jpa, () -> "TEST").search(null, null, null, 100).size());
        }
    }

    @Test
    void reversalReopensRecognitionAndStaleConcurrentProposalRollsBack(@TempDir Path dir)
    {
        try (Jpa jpa = new Jpa(dir.resolve("reversal-and-concurrency")))
        {
            seed(jpa);
            UUID item = UUID.randomUUID();
            enterOpening(jpa, SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, item,
                    OPENING, new BigDecimal("100"), OPENING, LocalDate.of(2026, 1, 31));
            var service = new SupplementalRecognitionService(jpa, () -> "TEST");
            var request = request(SupplementalOpenItemQueryService.Kind.PREPAID_EXPENSE, item,
                    OPENING, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31),
                    1006L, ExpenseFunction.FR);
            var firstPreview = service.preview(request);
            var stalePreview = service.preview(request);
            TransactionView saved = service.save(firstPreview.proposals().get(0));
            PostingException stale = assertThrows(PostingException.class,
                    () -> service.save(stalePreview.proposals().get(0)));
            assertTrue(stale.getMessage().contains("over-applied"));
            assertEquals(2, new TransactionEntryService(jpa, () -> "TEST").search(null, null, null, 100).size());

            new TransactionCorrectionService(jpa, () -> "TEST").reverse(
                    saved.id(), LocalDate.of(2026, 2, 1), "tester", "cancel recognition", false);
            var reopened = service.preview(new SupplementalRecognitionService.Request(
                    request.kind(), request.itemId(), request.serviceStart(), request.serviceEnd(),
                    LocalDate.of(2026, 2, 1), request.destinationAccountId(), request.expenseFunction()));
            assertEquals(1, reopened.proposals().size());
            assertEquals(new BigDecimal("100.0000"), reopened.proposals().get(0).amount());
            assertNotEquals(firstPreview.proposals().get(0).requestId(), reopened.proposals().get(0).requestId());
        }
    }

    private static SupplementalRecognitionService.Request request(
            SupplementalOpenItemQueryService.Kind kind, UUID item, LocalDate start, LocalDate end,
            LocalDate through, long accountId, ExpenseFunction function)
    {
        return new SupplementalRecognitionService.Request(kind, item, start, end, through, accountId, function);
    }

    private static TransactionView enterOpening(
            Jpa jpa, SupplementalOpenItemQueryService.Kind kind, UUID item, LocalDate date,
            BigDecimal amount, LocalDate start, LocalDate end)
    {
        boolean deferred = kind == SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE;
        long control = deferred ? 1005L : 1004L;
        List<TransactionLineCommand> lines = deferred
                ? List.of(line(1007L, amount, BigDecimal.ZERO, null), line(control, BigDecimal.ZERO, amount, null))
                : List.of(line(control, amount, BigDecimal.ZERO, null), line(1007L, BigDecimal.ZERO, amount, null));
        int controlIndex = deferred ? 1 : 0;
        return new TransactionEntryService(jpa, () -> "TEST").enter(new TransactionCommand(
                date, null, "opening " + kind, null, lines,
                List.of(new TransactionSupplementalLineCommand(kind.name(), "OPEN", "Counterparty",
                        "Recognition item", "SOURCE", amount, null, start, end, "Opening terms", 0,
                        item, "INCREASE", controlIndex))));
    }

    private static TransactionView enterManualDecrease(
            Jpa jpa, SupplementalOpenItemQueryService.Kind kind, UUID item, LocalDate date, BigDecimal amount)
    {
        long control = kind == SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE ? 1005L : 1004L;
        List<TransactionLineCommand> lines = kind == SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE
                ? List.of(line(control, amount, BigDecimal.ZERO, null), line(1003L, BigDecimal.ZERO, amount, null))
                : List.of(line(1006L, amount, BigDecimal.ZERO, ExpenseFunction.OA),
                        line(control, BigDecimal.ZERO, amount, null));
        int controlIndex = kind == SupplementalOpenItemQueryService.Kind.DEFERRED_REVENUE ? 0 : 1;
        return new TransactionEntryService(jpa, () -> "TEST").enter(new TransactionCommand(
                date, null, "prior recognition", null, lines,
                List.of(new TransactionSupplementalLineCommand(kind.name(), "PRIOR", null,
                        "Prior recognition", null, amount, null, null, null, null, 0,
                        item, "DECREASE", controlIndex))));
    }

    private static TransactionLineCommand line(
            long account, BigDecimal debit, BigDecimal credit, ExpenseFunction function)
    {
        return new TransactionLineCommand(account, 1001L, 1101L, 101L, null,
                debit, credit, false, null, null, function);
    }

    static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("insert into company (id, code, display_name) values (100,'TEST','Test'),(200,'OTHER','Other')").executeUpdate();
            em.createNativeQuery("insert into chart_of_accounts (id, company_id, name, version, status) values (100,100,'Test','1','ACTIVE'),(200,200,'Other','1','ACTIVE')").executeUpdate();
            em.createNativeQuery("update company set active_chart_of_accounts_id = id where id in (100,200)").executeUpdate();
            em.createNativeQuery("""
                    insert into account (id, chart_id, code, name, account_type, subtype, normal_balance)
                    values (1003,100,'4000','Income','INCOME',null,'CREDIT'),
                           (1004,100,'1200','Prepaid','ASSET','PREPAID','DEBIT'),
                           (1005,100,'2200','Deferred','LIABILITY','DEFERRED_REVENUE','CREDIT'),
                           (1006,100,'5000','Expense','EXPENSE',null,'DEBIT'),
                           (1007,100,'1000','Cash','ASSET','CASH','DEBIT'),
                           (2006,200,'5000','Foreign Expense','EXPENSE',null,'DEBIT')
                    """).executeUpdate();
            em.createNativeQuery("insert into fund (id, company_id, code, name, fund_type) values (1001,100,'OPERATING','Operating','UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("insert into activity (id, company_id, code, name, is_active) values (101,100,'EVENT-A','Event A',true)").executeUpdate();
            em.createNativeQuery("insert into budget_category (id, company_id, code, name, is_active) values (1101,100,'PROGRAM','Program',true)").executeUpdate();
            em.getTransaction().commit();
        }
    }
}
