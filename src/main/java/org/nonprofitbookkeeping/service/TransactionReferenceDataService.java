package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.ui.TransactionLineEditorModel;

import java.util.Objects;
import java.util.function.Supplier;

/** Query service for company-owned transaction-editor reference choices. */
public class TransactionReferenceDataService
{
    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;

    public TransactionReferenceDataService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public TransactionReferenceDataService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
    }

    public TransactionLineEditorModel.ReferenceData loadActiveReferenceData()
    {
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCodeSupplier.get());
            return new TransactionLineEditorModel.ReferenceData(
                    em.createQuery("from Account a where a.chart.company = :company and a.active = true and a.posting = true order by a.code", Account.class)
                            .setParameter("company", company).getResultList().stream()
                            .map(a -> TransactionLineEditorModel.option(a.getId(), a.getCode(), a.getName())).toList(),
                    em.createQuery("from Fund f where f.company = :company and f.active = true order by f.code", Fund.class)
                            .setParameter("company", company).getResultList().stream()
                            .map(f -> TransactionLineEditorModel.option(f.getId(), f.getCode(), f.getName())).toList(),
                    em.createQuery("from BudgetCategory b where b.company = :company and b.active = true order by b.code", BudgetCategory.class)
                            .setParameter("company", company).getResultList().stream()
                            .map(b -> TransactionLineEditorModel.option(b.getId(), b.getCode(), b.getName())).toList(),
                    em.createQuery("from Activity a where a.company = :company and a.active = true order by a.code", Activity.class)
                            .setParameter("company", company).getResultList().stream()
                            .map(a -> TransactionLineEditorModel.option(a.getId(), a.getCode(), a.getName())).toList(),
                    em.createQuery("from Merchant m where m.company = :company and m.active = true order by m.name", Merchant.class)
                            .setParameter("company", company).getResultList().stream()
                            .map(m -> TransactionLineEditorModel.option(m.getId(), "", m.getName())).toList(),
                    em.createQuery("from Counterparty c where c.company = :company and c.active = true order by c.displayName", Counterparty.class)
                            .setParameter("company", company).getResultList().stream()
                            .map(c -> TransactionLineEditorModel.option(c.getId(), "", c.getDisplayName())).toList());
        }
    }
}
