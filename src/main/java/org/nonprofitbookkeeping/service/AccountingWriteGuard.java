package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.nonprofitbookkeeping.persistence.AccountingWriteGuardTrigger;

/** Acquires the database transaction guard before close/period validation; lock survives until commit/rollback. */
final class AccountingWriteGuard
{
    private AccountingWriteGuard() { }

    static void lock(EntityManager em)
    {
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Accounting write guard requires an active transaction");
        }
        em.unwrap(Session.class).doWork(AccountingWriteGuardTrigger::lock);
    }
}
