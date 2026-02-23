package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.persistence.Jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;

@ApplicationScoped
public class FundLookupService
{
    @Inject
    Jpa jpa;

    public FundLookupService() {}

    public FundLookupService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    public List<Fund> listActiveFunds()
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(
                    "from Fund f where f.active = true order by f.code",
                    Fund.class)
                .getResultList();
        }
    }
}
