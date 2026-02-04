package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.persistence.Jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;

/**
 * Minimal account lookup for UI.
 *
 * In the real app, this will be scoped by:
 * - current branch
 * - selected chart-of-accounts version / effective date
 * - fund restrictions, etc.
 */
@ApplicationScoped
public class AccountLookupService
{
    @Inject
    Jpa jpa;

    /**
     * List active posting accounts ordered by code.
     */
    public List<Account> listActivePostingAccounts()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                    "from Account a where a.isActive = true and a.isPosting = true order by a.code",
                    Account.class)
                .getResultList();
        }
    }
}
