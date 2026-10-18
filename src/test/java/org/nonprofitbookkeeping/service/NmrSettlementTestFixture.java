package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;

/** Explicit synthetic amounts from the accepted accounting example; no rate/default configuration. */
public record NmrSettlementTestFixture(NmrItemTestFixture base, long receivable, long fee, long merchant)
{
    public record History(UUID openingItem, UUID currentItem, UUID receivableItem, long merchantTransaction, Long branchTransaction)
    {
    }

    public static NmrSettlementTestFixture seed(Jpa jpa)
    {
        var base = NmrItemTestFixture.seed(jpa);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin(); var chart = em.find(Account.class, base.cash()).getChart();
            Account receivable = account(em, chart, "1400", AccountType.ASSET, AccountSubtype.RECEIVABLE);
            Account fee = account(em, chart, "6000", AccountType.EXPENSE, null);
            Merchant merchant = new Merchant(); merchant.setCompany(chart.getCompany()); merchant.setName("Synthetic processor"); merchant.setActive(true); em.persist(merchant);
            em.getTransaction().commit(); return new NmrSettlementTestFixture(base, receivable.getId(), fee.getId(), merchant.getId());
        }
    }

    private static Account account(EntityManager em, ChartOfAccounts chart, String code, AccountType type, AccountSubtype subtype)
    {
        Account account = new Account(); account.setChart(chart); account.setCode(code); account.setName(code); account.setAccountType(type);
        account.setSubtype(subtype); account.setNormalBalance(NormalBalance.DEBIT); account.setActive(true); account.setPosting(true); em.persist(account); return account;
    }

    public TransactionLineCommand line(long account, String debit, String credit, Long merchant)
    {
        return new TransactionLineCommand(account, base.fund(), null, base.event(), merchant,
                new BigDecimal(debit), new BigDecimal(credit), account == base.liability(), null);
    }

    public TransactionSupplementalLineCommand nmr(UUID item, String effect, int index, String amount, String kind, String evidence)
    {
        return new TransactionSupplementalLineCommand("OTHER_LIABILITY", "NMR", "Narrative", "Explicit obligation", null,
                new BigDecimal(amount), null, null, null, null, null, item, effect, index, base.recipient(), kind, evidence);
    }

    public TransactionSupplementalLineCommand receivable(UUID item, String effect, int index, String amount)
    {
        return new TransactionSupplementalLineCommand("RECEIVABLE", "PROCESSOR", "Synthetic processor", "Gross receivable", null,
                new BigDecimal(amount), null, null, null, null, null, item, effect, index);
    }

    public History history(Jpa jpa, boolean confirmed, boolean remit)
    {
        UUID opening = UUID.randomUUID(), current = UUID.randomUUID(), gross = UUID.randomUUID(); var entry = base.entry(jpa);
        entry.enter(new TransactionCommand(DATE.minusDays(1), null, "Prior-period obligation", null, List.of(
                line(base.cash(), "40", "0", null), line(base.liability(), "0", "40", null)),
                List.of(nmr(opening, "INCREASE", 1, "40", "COLLECTION", null))));
        entry.enter(new TransactionCommand(DATE, null, "Gross receipt", null, List.of(
                line(receivable, "1000", "0", merchant), line(base.income(), "0", "900", null), line(base.liability(), "0", "100", null)),
                List.of(receivable(gross, "INCREASE", 0, "1000"), nmr(current, "INCREASE", 2, "100", "COLLECTION", null))));
        TransactionView payout;
        if (confirmed)
        {
            payout = entry.enter(new TransactionCommand(DATE.plusDays(1), null, "Confirmed processor settlement", null, List.of(
                    line(base.cash(), "950", "0", merchant), line(fee, "20", "0", merchant),
                    line(base.liability(), "30", "0", merchant), line(receivable, "0", "1000", merchant)),
                    List.of(nmr(current, "DECREASE", 2, "30", "MERCHANT_DISCHARGE", "processor/settlement/001"), receivable(gross, "DECREASE", 3, "1000"))));
        }
        else
        {
            payout = entry.enter(new TransactionCommand(DATE.plusDays(1), null, "Payout without discharge evidence", null, List.of(
                    line(base.cash(), "950", "0", merchant), line(fee, "20", "0", merchant), line(receivable, "0", "970", merchant)),
                    List.of(receivable(gross, "DECREASE", 2, "970"))));
        }
        Long payment = null;
        if (remit)
        {
            payment = entry.enter(new TransactionCommand(DATE.plusDays(2), null, "Explicit branch remittance", null,
                    List.of(line(base.liability(), "110", "0", null), line(base.cash(), "0", "110", null)),
                    List.of(nmr(opening, "DECREASE", 0, "40", "BRANCH_REMITTANCE", "branch/payment/001"),
                            nmr(current, "DECREASE", 0, "70", "BRANCH_REMITTANCE", "branch/payment/001")))).id();
        }
        return new History(opening, current, gross, payout.id(), payment);
    }
}
