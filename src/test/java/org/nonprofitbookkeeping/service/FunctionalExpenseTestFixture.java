package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Synthetic purpose facts independent of one natural account/Fund/Event/Budget. */
public record FunctionalExpenseTestFixture(FundTransferTestFixture base, long event, long otherEvent, long category, long contra)
{
    public static final String COMPANY = "DEFAULT";
    public static final LocalDate DATE = LocalDate.of(2026, 2, 1);

    public static FunctionalExpenseTestFixture seed(Jpa jpa)
    {
        var base = FundTransferTestFixture.seed(jpa);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.createQuery("from Company c where c.code = 'DEFAULT'", Company.class).getSingleResult();
            Activity first = activity(em, company, "EVENT"), second = activity(em, company, "OTHER_EVENT");
            BudgetCategory category = new BudgetCategory(); category.setCompany(company); category.setCode("OPERATIONS"); category.setName("Operations"); em.persist(category);
            Account contra = new Account(); contra.setChart(em.find(Account.class, base.expense()).getChart());
            contra.setCode("5100"); contra.setName("Contra expense"); contra.setAccountType(AccountType.EXPENSE);
            contra.setNormalBalance(NormalBalance.CREDIT); contra.setPosting(true); contra.setActive(true); em.persist(contra);
            em.getTransaction().commit(); return new FunctionalExpenseTestFixture(base, first.getId(), second.getId(), category.getId(), contra.getId());
        }
    }

    private static Activity activity(EntityManager em, Company company, String code)
    {
        Activity event = new Activity(); event.setCompany(company); event.setCode(code); event.setName(code); event.setActive(true); em.persist(event); return event;
    }

    public TransactionLineCommand line(long account, long fund, Long eventId, String debit, String credit, ExpenseFunction function)
    {
        return new TransactionLineCommand(account, fund, category, eventId, null,
                new BigDecimal(debit), new BigDecimal(credit), false, null, null, function);
    }

    public TransactionCommand mixed()
    {
        return new TransactionCommand(DATE, null, "Explicit functions", null, List.of(
                line(base.expense(), base.source(), event, "700", "0", ExpenseFunction.AR),
                line(base.expense(), base.source(), event, "200", "0", ExpenseFunction.OA),
                line(base.expense(), base.source(), event, "100", "0", ExpenseFunction.FR),
                line(base.bank(), base.source(), event, "0", "1000", null)));
    }

    public TransactionCommand expense(LocalDate date, long account, long fund, long eventId, String amount, boolean refund, ExpenseFunction function)
    {
        return new TransactionCommand(date, null, "Explicit expense", null, List.of(
                line(account, fund, eventId, refund ? "0" : amount, refund ? amount : "0", function),
                line(base.bank(), fund, eventId, refund ? amount : "0", refund ? "0" : amount, null)));
    }

    public TransactionEntryService entry(Jpa jpa)
    {
        return new TransactionEntryService(jpa, () -> COMPANY);
    }
}
