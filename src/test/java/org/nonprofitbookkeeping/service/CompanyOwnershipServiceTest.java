package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FundType;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompanyOwnershipServiceTest
{
    @Test
    void singleCompanyLegacyRowCanBeAdoptedFromDatabaseEvidence(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("ownership-single")); EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Fund fund = new Fund();
            fund.setCode("LEGACY");
            fund.setName("Legacy Fund");
            fund.setFundType(FundType.UNRESTRICTED);
            em.persist(fund);
            em.flush();

            CompanyOwnershipService service = new CompanyOwnershipService(jpa);
            Company company = service.requireCompany(em, "default");
            service.ensureOwnedBy(em, company, fund, "Fund");
            em.getTransaction().commit();

            assertEquals(company.getId(), fund.getCompany().getId());
        }
    }

    @Test
    void multiCompanyUnownedAndCrossCompanyRowsAreRejected(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("ownership-multi")); EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company other = new Company();
            other.setCode("OTHER");
            other.setDisplayName("Other Company");
            em.persist(other);

            Fund unowned = new Fund();
            unowned.setCode("UNOWNED");
            unowned.setName("Unowned Fund");
            unowned.setFundType(FundType.UNRESTRICTED);
            em.persist(unowned);

            Fund otherFund = new Fund();
            otherFund.setCompany(other);
            otherFund.setCode("OTHER");
            otherFund.setName("Other Fund");
            otherFund.setFundType(FundType.UNRESTRICTED);
            em.persist(otherFund);
            em.flush();

            CompanyOwnershipService service = new CompanyOwnershipService(jpa);
            Company defaultCompany = service.requireCompany(em, "DEFAULT");
            assertThrows(CompanyOwnershipException.class,
                    () -> service.ensureOwnedBy(em, defaultCompany, unowned, "Unowned fund"));
            assertThrows(CompanyOwnershipException.class,
                    () -> service.ensureOwnedBy(em, defaultCompany, otherFund, "Other fund"));
            em.getTransaction().rollback();
        }
    }
}
