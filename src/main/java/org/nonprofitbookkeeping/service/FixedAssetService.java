package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Application service for H2-backed fixed assets and depreciation runs. */
@ApplicationScoped
public class FixedAssetService
{
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final Jpa jpa;
    private final TransactionEntryService transactionEntryService;

    @Inject
    public FixedAssetService(Jpa jpa)
    {
        this(jpa, new TransactionEntryService(jpa));
    }

    public FixedAssetService(Jpa jpa, TransactionEntryService transactionEntryService)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.transactionEntryService = Objects.requireNonNull(transactionEntryService, "transactionEntryService");
    }

    public FixedAssetView create(FixedAssetCommand command)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                FixedAsset asset = new FixedAsset();
                apply(em, asset, command);
                em.persist(asset);
                em.getTransaction().commit();
                return load(asset.getId());
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public FixedAssetView update(long assetId, FixedAssetCommand command)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                FixedAsset asset = require(em, FixedAsset.class, assetId, "Fixed asset");
                apply(em, asset, command);
                asset.touchUpdatedAt();
                em.getTransaction().commit();
                return load(assetId);
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public FixedAssetView load(long assetId)
    {
        try (EntityManager em = jpa.em())
        {
            FixedAsset asset = em.createQuery("""
                    select a from FixedAsset a
                    join fetch a.company
                    join fetch a.assetAccount
                    join fetch a.accumulatedDepreciationAccount
                    join fetch a.depreciationExpenseAccount
                    join fetch a.fund
                    where a.id = :id
                    """, FixedAsset.class)
                    .setParameter("id", assetId)
                    .getSingleResult();
            return toView(em, asset);
        }
    }

    public List<FixedAssetView> listAssets(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("""
                    select a from FixedAsset a
                    join fetch a.company c
                    join fetch a.assetAccount
                    join fetch a.accumulatedDepreciationAccount
                    join fetch a.depreciationExpenseAccount
                    join fetch a.fund
                    where c.code = :companyCode
                    order by a.name, a.id
                    """, FixedAsset.class)
                    .setParameter("companyCode", normalizeCompanyCode(companyCode))
                    .getResultList()
                    .stream()
                    .map(asset -> toView(em, asset))
                    .toList();
        }
    }

    public List<DepreciationRunView> listDepreciationRuns(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("""
                    select r from FixedAssetDepreciationRun r
                    join fetch r.fixedAsset a
                    join fetch a.company c
                    join fetch r.transaction
                    where c.code = :companyCode
                    order by r.runDate desc, r.id desc
                    """, FixedAssetDepreciationRun.class)
                    .setParameter("companyCode", normalizeCompanyCode(companyCode))
                    .getResultList()
                    .stream()
                    .map(this::toRunView)
                    .toList();
        }
    }

    public DepreciationRunView runMonthlyDepreciation(long assetId, LocalDate runDate, String notes)
    {
        if (runDate == null)
        {
            throw new IllegalArgumentException("runDate is required");
        }
        FixedAssetView before = load(assetId);
        if (before.status() != FixedAsset.Status.ACTIVE)
        {
            throw new IllegalStateException("Only active assets can be depreciated");
        }
        BigDecimal amount = before.nextDepreciationAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new IllegalStateException("No remaining depreciable value for asset " + assetId);
        }

        TransactionEntryService companyTransactionEntryService = new TransactionEntryService(
                jpa,
                () -> before.companyCode());
        TransactionView txn = companyTransactionEntryService.enter(new TransactionCommand(
                runDate,
                null,
                "Depreciation: " + before.name(),
                null,
                List.of(
                        new TransactionLineCommand(before.depreciationExpenseAccountId(), before.fundId(), null, null, null, amount, BigDecimal.ZERO, false, notes),
                        new TransactionLineCommand(before.accumulatedDepreciationAccountId(), before.fundId(), null, null, null, BigDecimal.ZERO, amount, false, notes))));

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                FixedAsset asset = require(em, FixedAsset.class, assetId, "Fixed asset");
                FixedAssetDepreciationRun run = new FixedAssetDepreciationRun();
                run.setFixedAsset(asset);
                run.setRunDate(runDate);
                run.setDepreciationAmount(amount);
                Txn depreciationTxn = require(em, Txn.class, txn.id(), "Depreciation transaction");
                new CompanyOwnershipService(jpa).requireOwnedBy(asset.getCompany(), depreciationTxn, "Depreciation transaction");
                run.setTransaction(depreciationTxn);
                run.setNotes(notes);
                em.persist(run);
                em.getTransaction().commit();
                return toRunView(run);
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    private void apply(EntityManager em, FixedAsset asset, FixedAssetCommand command)
    {
        validateCommand(command);
        Company company = em.createQuery("select c from Company c where c.code = :code", Company.class)
                .setParameter("code", normalizeCompanyCode(command.companyCode()))
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + command.companyCode()));
        Account assetAccount = require(em, Account.class, command.assetAccountId(), "Asset account");
        Account accumulatedAccount = require(em, Account.class, command.accumulatedDepreciationAccountId(), "Accumulated depreciation account");
        Account expenseAccount = require(em, Account.class, command.depreciationExpenseAccountId(), "Depreciation expense account");
        Fund fund = require(em, Fund.class, command.fundId(), "Fund");
        CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
        ownership.ensureOwnedBy(em, company, assetAccount, "Asset account");
        ownership.ensureOwnedBy(em, company, accumulatedAccount, "Accumulated depreciation account");
        ownership.ensureOwnedBy(em, company, expenseAccount, "Depreciation expense account");
        ownership.ensureOwnedBy(em, company, fund, "Asset fund");

        validateAssetAccount(assetAccount);
        if (expenseAccount.getAccountType() != AccountType.EXPENSE)
        {
            throw new IllegalArgumentException("Depreciation expense account must be an EXPENSE account");
        }
        if (accumulatedAccount.getAccountType() != AccountType.ASSET)
        {
            throw new IllegalArgumentException("Accumulated depreciation account must be an ASSET account");
        }

        asset.setCompany(company);
        asset.setAssetAccount(assetAccount);
        asset.setAccumulatedDepreciationAccount(accumulatedAccount);
        asset.setDepreciationExpenseAccount(expenseAccount);
        asset.setFund(fund);
        asset.setName(command.name().trim());
        asset.setAcquisitionDate(command.acquisitionDate());
        asset.setAcquisitionCost(scale(command.acquisitionCost()));
        asset.setSalvageValue(scale(command.salvageValue()));
        asset.setUsefulLifeMonths(command.usefulLifeMonths());
        asset.setDepreciationMethod(command.depreciationMethod() == null ? FixedAsset.DepreciationMethod.STRAIGHT_LINE : command.depreciationMethod());
        asset.setOpeningAccumulatedDepreciation(scale(command.openingAccumulatedDepreciation()));
        asset.setStatus(command.status() == null ? FixedAsset.Status.ACTIVE : command.status());
        asset.setNotes(command.notes());
    }

    private static void validateCommand(FixedAssetCommand command)
    {
        if (command == null)
        {
            throw new IllegalArgumentException("command is required");
        }
        if (command.companyCode() == null || command.companyCode().isBlank())
        {
            throw new IllegalArgumentException("companyCode is required");
        }
        if (command.name() == null || command.name().isBlank())
        {
            throw new IllegalArgumentException("Asset name is required");
        }
        if (command.acquisitionDate() == null)
        {
            throw new IllegalArgumentException("Acquisition date is required");
        }
        if (command.acquisitionCost() == null || command.acquisitionCost().compareTo(BigDecimal.ZERO) < 0)
        {
            throw new IllegalArgumentException("Acquisition cost must be nonnegative");
        }
        if (command.salvageValue() == null || command.salvageValue().compareTo(BigDecimal.ZERO) < 0)
        {
            throw new IllegalArgumentException("Salvage value must be nonnegative");
        }
        if (command.salvageValue().compareTo(command.acquisitionCost()) > 0)
        {
            throw new IllegalArgumentException("Salvage value cannot exceed acquisition cost");
        }
        if (command.openingAccumulatedDepreciation() == null || command.openingAccumulatedDepreciation().compareTo(BigDecimal.ZERO) < 0)
        {
            throw new IllegalArgumentException("Opening accumulated depreciation must be nonnegative");
        }
        if (command.usefulLifeMonths() != 36 && command.usefulLifeMonths() != 60 && command.usefulLifeMonths() != 84)
        {
            throw new IllegalArgumentException("Useful life must be 36, 60, or 84 months");
        }
    }

    private static void validateAssetAccount(Account account)
    {
        if (account.getAccountType() != AccountType.ASSET || account.getSubtype() != AccountSubtype.FIXED_ASSET)
        {
            throw new IllegalArgumentException("Asset account must be an ASSET/FIXED_ASSET account");
        }
    }

    private FixedAssetView toView(EntityManager em, FixedAsset asset)
    {
        BigDecimal runTotal = em.createQuery("""
                select coalesce(sum(r.depreciationAmount), 0)
                from FixedAssetDepreciationRun r
                where r.fixedAsset.id = :assetId
                """, BigDecimal.class)
                .setParameter("assetId", asset.getId())
                .getSingleResult();
        BigDecimal accumulated = scale(asset.getOpeningAccumulatedDepreciation()).add(scale(runTotal));
        BigDecimal bookValue = scale(asset.getAcquisitionCost()).subtract(accumulated).max(scale(asset.getSalvageValue()));
        BigDecimal next = nextDepreciation(asset, accumulated);
        return new FixedAssetView(
                asset.getId(),
                asset.getCompany().getCode(),
                asset.getAssetAccount().getId(),
                asset.getAssetAccount().getCode(),
                asset.getAssetAccount().getName(),
                asset.getAccumulatedDepreciationAccount().getId(),
                asset.getAccumulatedDepreciationAccount().getCode(),
                asset.getAccumulatedDepreciationAccount().getName(),
                asset.getDepreciationExpenseAccount().getId(),
                asset.getDepreciationExpenseAccount().getCode(),
                asset.getDepreciationExpenseAccount().getName(),
                asset.getFund().getId(),
                asset.getFund().getCode(),
                asset.getFund().getName(),
                asset.getName(),
                asset.getAcquisitionDate(),
                scale(asset.getAcquisitionCost()),
                scale(asset.getSalvageValue()),
                asset.getUsefulLifeMonths(),
                asset.getDepreciationMethod(),
                scale(asset.getOpeningAccumulatedDepreciation()),
                accumulated,
                bookValue,
                next,
                asset.getStatus(),
                asset.getNotes() == null ? "" : asset.getNotes());
    }

    private DepreciationRunView toRunView(FixedAssetDepreciationRun run)
    {
        return new DepreciationRunView(
                run.getId(),
                run.getFixedAsset().getId(),
                run.getFixedAsset().getName(),
                run.getRunDate(),
                scale(run.getDepreciationAmount()),
                run.getTransaction().getId(),
                run.getNotes() == null ? "" : run.getNotes());
    }

    private static BigDecimal nextDepreciation(FixedAsset asset, BigDecimal accumulated)
    {
        BigDecimal depreciable = scale(asset.getAcquisitionCost()).subtract(scale(asset.getSalvageValue()));
        BigDecimal remaining = depreciable.subtract(accumulated);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0 || asset.getStatus() != FixedAsset.Status.ACTIVE)
        {
            return ZERO;
        }
        BigDecimal monthly = depreciable.divide(BigDecimal.valueOf(asset.getUsefulLifeMonths()), 4, RoundingMode.HALF_UP);
        return monthly.min(remaining).setScale(4, RoundingMode.HALF_UP);
    }

    private static String normalizeCompanyCode(String companyCode)
    {
        return companyCode == null ? "" : companyCode.trim().toUpperCase();
    }

    private static BigDecimal scale(BigDecimal value)
    {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private static <T> T require(EntityManager em, Class<T> type, Long id, String label)
    {
        if (id == null)
        {
            throw new IllegalArgumentException(label + " is required");
        }
        T entity = em.find(type, id);
        if (entity == null)
        {
            throw new IllegalArgumentException(label + " not found: " + id);
        }
        return entity;
    }

    private static void rollback(EntityManager em)
    {
        if (em.getTransaction().isActive())
        {
            em.getTransaction().rollback();
        }
    }
}
