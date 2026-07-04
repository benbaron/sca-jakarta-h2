package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.ui.TransactionLineEditorModel;

import java.util.List;

/**
 * Query service for ID-backed transaction-editor reference choices.
 */
public class TransactionReferenceDataService
{
    private final Jpa jpa;

    public TransactionReferenceDataService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    public TransactionLineEditorModel.ReferenceData loadActiveReferenceData()
    {
        try (EntityManager em = jpa.em())
        {
            return new TransactionLineEditorModel.ReferenceData(
                    em.createQuery("from Account a where a.active = true and a.posting = true order by a.code", Account.class)
                            .getResultList().stream()
                            .map(a -> TransactionLineEditorModel.option(a.getId(), a.getCode(), a.getName()))
                            .toList(),
                    em.createQuery("from Fund f where f.active = true order by f.code", Fund.class)
                            .getResultList().stream()
                            .map(f -> TransactionLineEditorModel.option(f.getId(), f.getCode(), f.getName()))
                            .toList(),
                    em.createQuery("from BudgetCategory b where b.active = true order by b.code", BudgetCategory.class)
                            .getResultList().stream()
                            .map(b -> TransactionLineEditorModel.option(b.getId(), b.getCode(), b.getName()))
                            .toList(),
                    em.createQuery("from Activity a where a.active = true order by a.code", Activity.class)
                            .getResultList().stream()
                            .map(a -> TransactionLineEditorModel.option(a.getId(), a.getCode(), a.getName()))
                            .toList(),
                    em.createQuery("from Merchant m where m.active = true order by m.name", Merchant.class)
                            .getResultList().stream()
                            .map(m -> TransactionLineEditorModel.option(m.getId(), "", m.getName()))
                            .toList(),
                    em.createQuery("from Counterparty c where c.active = true order by c.displayName", Counterparty.class)
                            .getResultList().stream()
                            .map(c -> TransactionLineEditorModel.option(c.getId(), "", c.getDisplayName()))
                            .toList());
        }
    }
}
