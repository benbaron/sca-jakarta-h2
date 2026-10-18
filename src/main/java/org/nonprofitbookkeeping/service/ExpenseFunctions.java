package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.*;

/** Validation and factual audit projection for the single canonical expense purpose. */
public final class ExpenseFunctions
{
    private ExpenseFunctions()
    {
    }

    public static void requireEligible(Account account, ExpenseFunction function)
    {
        if (function != null && account.getAccountType() != AccountType.EXPENSE)
        {
            throw new PostingException("Expense function requires an EXPENSE account; bank, asset, liability and income lines remain unclassified.");
        }
    }

    static String snapshot(EntityManager em, Txn txn)
    {
        return em.createQuery("from TxnSplit s where s.txn = :txn and s.expenseFunction is not null order by s.id", TxnSplit.class)
                .setParameter("txn", txn).getResultList().stream()
                .map(s -> s.getId() + ":" + s.getExpenseFunction().name()).toList().toString();
    }
}
