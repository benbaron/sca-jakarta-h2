package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.persistence.Jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Queries fund balances by summing signed split amounts “as of” a date.
 *
 * IMPORTANT:
 * This query sums *all* splits regardless of account type. You can refine it to
 * include only selected account types (e.g., cash-only, balance-sheet only, etc.)
 * once you decide which “headline fund balance” you want to show users.
 */
@ApplicationScoped
public class FundBalanceService
{
    @Inject
    private Jpa jpa;

    public List<FundBalanceRow> balancesAsOf(LocalDate asOf)
    {
        try (EntityManager em = jpa.em())
        {
            List<Object[]> rows = em.createQuery(
                "select f.code, f.name, coalesce(sum(s.amountSigned), 0) " +
                "from TxnSplit s " +
                "join s.txn t " +
                "join s.fund f " +
                "where t.txnDate <= :asOf " +
                "group by f.code, f.name " +
                "order by f.code", Object[].class)
                .setParameter("asOf", asOf)
                .getResultList();

            List<FundBalanceRow> out = new ArrayList<>();
            for (Object[] r : rows)
            {
                out.add(new FundBalanceRow((String) r[0], (String) r[1], (BigDecimal) r[2]));
            }
            return out;
        }
    }
}
