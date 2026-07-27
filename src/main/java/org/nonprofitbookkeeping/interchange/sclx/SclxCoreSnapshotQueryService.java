package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.CompanyOwnershipService;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Loads the selected company's bounded core entity graph and assembles an immutable SCLX snapshot. */
@ApplicationScoped
public class SclxCoreSnapshotQueryService
{
    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;
    private final SclxCoreSnapshotAssembler assembler;

    @Inject
    public SclxCoreSnapshotQueryService(Jpa jpa)
    {
        this(jpa, () -> "DEFAULT");
    }

    public SclxCoreSnapshotQueryService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, new SclxCoreSnapshotAssembler());
    }

    SclxCoreSnapshotQueryService(
            Jpa jpa,
            Supplier<String> companyCodeSupplier,
            SclxCoreSnapshotAssembler assembler)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
    }

    public SclxExportDocument query(Instant exportedAt)
    {
        Objects.requireNonNull(exportedAt, "exportedAt");
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa)
                    .requireCompany(em, companyCodeSupplier.get());
            ChartOfAccounts activeChart = Objects.requireNonNull(
                    company.getActiveChartOfAccounts(),
                    "selected company has no active chart of accounts");

            List<Account> accounts = em.createQuery(
                            "select a from Account a left join fetch a.parent "
                                    + "where a.chart = :chart order by a.code",
                            Account.class)
                    .setParameter("chart", activeChart)
                    .getResultList();
            List<Fund> funds = em.createQuery(
                            "select f from Fund f left join fetch f.parent "
                                    + "where f.company = :company order by f.code",
                            Fund.class)
                    .setParameter("company", company)
                    .getResultList();

            return assembler.assemble(company, accounts, funds, exportedAt);
        }
    }
}
