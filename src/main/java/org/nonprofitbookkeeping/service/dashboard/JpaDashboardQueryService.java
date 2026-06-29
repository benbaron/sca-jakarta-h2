package org.nonprofitbookkeeping.service.dashboard;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA implementation of the production dashboard projection.
 */
@ApplicationScoped
public class JpaDashboardQueryService implements DashboardQueryService
{
    private final Jpa jpa;

    @Inject
    public JpaDashboardQueryService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    @Override
    public DashboardSnapshot load(LocalDate asOfDate, int recentTransactionLimit)
    {
        if (asOfDate == null)
        {
            throw new IllegalArgumentException("asOfDate is required");
        }
        if (recentTransactionLimit <= 0)
        {
            throw new IllegalArgumentException("recentTransactionLimit must be positive");
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                BigDecimal bookCash = decimal(em.createQuery("""
                        select coalesce(sum(s.amountSigned), 0)
                        from TxnSplit s
                        where s.txn.txnDate <= :asOf
                          and s.txn.status = 'ENTERED'
                          and s.account.accountType = 'BANK'
                        "", BigDecimal.class)
                        .setParameter("asOf", asOfDate)
                        .getSingleResult());

                BigDecimal yearToDate = decimal(em.createQuery("""
                        select coalesce(sum(case
                            when s.account.accountType = 'INCOME' then -s.amountSigned
                            when s.account.accountType = 'EXPENSE' then -s.amountSigned
                            else 0 end), 0)
                        from TxnSplit s
                        where s.txn.txnDate between :start and :asOf
                          and s.txn.status = 'ENTERED'
                        "", BigDecimal.class)
                        .setParameter("start", LocalDate.of(asOfDate.getYear(), 1, 1))
                        .setParameter("asOf", asOfDate)
                        .getSingleResult());

                Map<String, BigDecimal> fundClassTotals = new LinkedHashMap<>();
                List<Object[]> fundRows = em.createQuery("""
                        select f.fundType, coalesce(sum(s.amountSigned), 0)
                        from TxnSplit s join s.fund f
                        where s.txn.txnDate <= :asOf
                          and s.txn.status = 'ENTERED'
                        group by f.fundType
                        order by f.fundType
                        "", Object[].class)
                        .setParameter("asOf", asOfDate)
                        .getResultList();
                for (Object[] row : fundRows)
                {
                    fundClassTotals.put(String.valueOf(row[0]), decimal(row[1]));
                }

                List<DashboardSnapshot.BankAccountBalance> bankAccounts = em.createQuery("""
                        select new org.nonprofitbookkeeping.service.dashboard.DashboardSnapshot$BankAccountBalance(
                            a.id, a.code, a.name, coalesce(sum(s.amountSigned), 0))
                        from TxnSplit s join s.account a
                        where s.txn.txnDate <= :asOf
                          and s.txn.status = 'ENTERED'
                          and a.accountType = 'BANK'
                        group by a.id, a.code, a.name
                        order by abs(sum(s.amountSigned)) desc, a.code
                        "", DashboardSnapshot.BankAccountBalance.class)
                        .setParameter("asOf", asOfDate)
                        .getResultList();

                List<DashboardSnapshot.RecentTransaction> recent = em.createQuery("""
                        select new org.nonprofitbookkeeping.service.dashboard.DashboardSnapshot$RecentTransaction(
                            t.id, t.txnDate, coalesce(t.memo, ''), t.status)
                        from Txn t
                        where t.txnDate <= :asOf
                        order by t.txnDate desc, t.id desc
                        "", DashboardSnapshot.RecentTransaction.class)
                        .setParameter("asOf", asOfDate)
                        .setMaxResults(recentTransactionLimit)
                        .getResultList();

                em.getTransaction().commit();
                BigDecimal reconciledCash = bookCash;
                return new DashboardSnapshot(
                        asOfDate,
                        bookCash,
                        reconciledCash,
                        bookCash.subtract(reconciledCash),
                        yearToDate,
                        Map.copyOf(fundClassTotals),
                        List.copyOf(bankAccounts),
                        List.copyOf(recent));
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                throw ex;
            }
        }
    }

    private static BigDecimal decimal(Object value)
    {
        return value == null ? BigDecimal.ZERO : (BigDecimal) value;
    }
}
