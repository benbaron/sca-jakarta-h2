package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.Activity;
import org.nonprofitbookkeeping.model.BudgetLine;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Counterparty;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;
import org.nonprofitbookkeeping.model.Merchant;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.model.TxnSupplementalLine;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.CompanyOwnershipService;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Loads the selected company's bounded entity graph and assembles an immutable SCLX snapshot. */
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
            List<Activity> activities = em.createQuery(
                            "select a from Activity a "
                                    + "where a.company = :company order by a.code",
                            Activity.class)
                    .setParameter("company", company)
                    .getResultList();
            List<Counterparty> counterparties = em.createQuery(
                            "select c from Counterparty c "
                                    + "where c.company = :company order by c.portableId",
                            Counterparty.class)
                    .setParameter("company", company)
                    .getResultList();
            List<Merchant> merchants = em.createQuery(
                            "select m from Merchant m "
                                    + "where m.company = :company order by m.portableId",
                            Merchant.class)
                    .setParameter("company", company)
                    .getResultList();
            List<BudgetPlan> budgetPlans = em.createQuery(
                            "select p from BudgetPlan p "
                                    + "where p.company = :company "
                                    + "order by p.fiscalYear, p.versionCode",
                            BudgetPlan.class)
                    .setParameter("company", company)
                    .getResultList();
            List<BudgetLine> budgetLines = em.createQuery(
                            "select l from BudgetLine l "
                                    + "join fetch l.budgetPlan p "
                                    + "join fetch l.budgetCategory "
                                    + "left join fetch l.fund "
                                    + "where p.company = :company",
                            BudgetLine.class)
                    .setParameter("company", company)
                    .getResultList();
            List<Txn> transactions = em.createQuery(
                            "select t from Txn t "
                                    + "left join fetch t.payee "
                                    + "left join fetch t.reversalOf "
                                    + "left join fetch t.replacementFor "
                                    + "where t.company = :company",
                            Txn.class)
                    .setParameter("company", company)
                    .getResultList();
            List<TxnSplit> transactionLines = em.createQuery(
                            "select s from TxnSplit s "
                                    + "join fetch s.txn t "
                                    + "join fetch s.account a "
                                    + "join fetch a.chart "
                                    + "join fetch s.fund "
                                    + "left join fetch s.budgetCategory "
                                    + "left join fetch s.activity "
                                    + "left join fetch s.merchant "
                                    + "left join fetch s.matchedBankStatementLine "
                                    + "where t.company = :company",
                            TxnSplit.class)
                    .setParameter("company", company)
                    .getResultList();
            List<TxnSupplementalLine> supplementalDetails = em.createQuery(
                            "select d from TxnSupplementalLine d "
                                    + "join fetch d.txn t "
                                    + "where t.company = :company "
                                    + "order by t.portableId, d.lineOrder",
                            TxnSupplementalLine.class)
                    .setParameter("company", company)
                    .getResultList();
            SclxBankingSnapshot banking = new SclxBankingSnapshotQuery().query(em, company);
            List<FixedAsset> fixedAssets = em.createQuery(
                            "select a from FixedAsset a "
                                    + "join fetch a.assetAccount aa join fetch aa.chart "
                                    + "join fetch a.accumulatedDepreciationAccount ada join fetch ada.chart "
                                    + "join fetch a.depreciationExpenseAccount dea join fetch dea.chart "
                                    + "join fetch a.fund "
                                    + "where a.company = :company order by a.portableId",
                            FixedAsset.class)
                    .setParameter("company", company)
                    .getResultList();
            List<FixedAssetDepreciationRun> depreciationRuns = em.createQuery(
                            "select r from FixedAssetDepreciationRun r "
                                    + "join fetch r.fixedAsset a "
                                    + "join fetch r.transaction t "
                                    + "where a.company = :company order by r.portableId",
                            FixedAssetDepreciationRun.class)
                    .setParameter("company", company)
                    .getResultList();

            return assembler.assemble(
                    company,
                    accounts,
                    funds,
                    activities,
                    counterparties,
                    merchants,
                    budgetPlans,
                    budgetLines,
                    transactions,
                    transactionLines,
                    supplementalDetails,
                    banking,
                    fixedAssets,
                    depreciationRuns,
                    exportedAt);
        }
    }
}
