package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.util.List;

@ApplicationScoped
/**
 * FundAdminService component.
 */
public class FundAdminService
{
    @Inject
    Jpa jpa;

    public FundAdminService()
    {
    }

    public FundAdminService(Jpa jpa)
    {
        this.jpa = jpa;
    }

    public Fund upsert(String code, String name, FundType fundType, boolean active)
    {
        String cleanCode = requireText(code, "Fund code");
        String cleanName = requireText(name, "Fund name");
        if (fundType == null)
        {
            throw new IllegalArgumentException("Fund type is required.");
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                List<Fund> existingMatches = em.createQuery(
                                "from Fund f where f.code = :code",
                                Fund.class)
                        .setParameter("code", cleanCode)
                        .setMaxResults(2)
                        .getResultList();

                Fund fund;
                if (existingMatches.isEmpty())
                {
                    fund = new Fund();
                }
                else
                {
                    fund = existingMatches.get(0);
                }

                fund.setCode(cleanCode);
                fund.setName(cleanName);
                fund.setFundType(fundType);
                fund.setActive(active);
                fund.touchUpdatedAt();

                if (fund.getId() == null)
                {
                    em.persist(fund);
                }
                else
                {
                    fund = em.merge(fund);
                }

                em.getTransaction().commit();
                return fund;
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive())
                {
                    em.getTransaction().rollback();
                }
                throw mapPersistenceError(ex, cleanCode);
            }
        }
    }

    private static RuntimeException mapPersistenceError(RuntimeException ex, String code)
    {
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("uq_fund_code") || message.contains("unique") || message.contains("constraint"))
        {
            return new IllegalArgumentException("Fund code already exists: " + code + ".", ex);
        }
        return ex;
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }
}
