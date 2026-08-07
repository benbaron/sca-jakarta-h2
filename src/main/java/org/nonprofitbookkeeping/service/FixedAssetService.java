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
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Application service for H2-backed fixed assets and depreciation runs. */
@ApplicationScoped
public class FixedAssetService
{
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final Jpa jpa;
    private final TransactionEntryService transactionEntryService;
    private final Supplier<String> companyCodeSupplier;
    private final Supplier<UUID> transactionPortableIdSupplier;
    private final Supplier<UUID> runPortableIdSupplier;
    private final Supplier<String> auditActorSupplier;
    private final DepreciationWriteHook depreciationWriteHook;

    @FunctionalInterface
    interface DepreciationWriteHook
    {
        void afterTransactionPersisted(
                EntityManager em,
                FixedAsset asset,
                Txn transaction,
                LocalDate runDate,
                BigDecimal amount,
                UUID runPortableId);
    }

    @Inject
    public FixedAssetService(Jpa jpa)
    {
        this(jpa, new TransactionEntryService(jpa), () -> "DEFAULT");
    }

    public FixedAssetService(Jpa jpa, TransactionEntryService transactionEntryService)
    {
        this(jpa, transactionEntryService, () -> "DEFAULT");
    }

    public FixedAssetService(
            Jpa jpa,
            TransactionEntryService transactionEntryService,
            Supplier<String> companyCodeSupplier)
    {
        this(jpa, transactionEntryService, companyCodeSupplier,
                UUID::randomUUID, UUID::randomUUID, () -> "system",
                (em, asset, transaction, runDate, amount, runPortableId) -> { });
    }

    FixedAssetService(
            Jpa jpa,
            TransactionEntryService transactionEntryService,
            Supplier<String> companyCodeSupplier,
            Supplier<UUID> transactionPortableIdSupplier,
            Supplier<UUID> runPortableIdSupplier,
            Supplier<String> auditActorSupplier,
            DepreciationWriteHook depreciationWriteHook)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.transactionEntryService = Objects.requireNonNull(transactionEntryService, "transactionEntryService");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.transactionPortableIdSupplier = Objects.requireNonNull(
                transactionPortableIdSupplier, "transactionPortableIdSupplier");
        this.runPortableIdSupplier = Objects.requireNonNull(runPortableIdSupplier, "runPortableIdSupplier");
        this.auditActorSupplier = Objects.requireNonNull(auditActorSupplier, "auditActorSupplier");
        this.depreciationWriteHook = Objects.requireNonNull(depreciationWriteHook, "depreciationWriteHook");
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
        UUID transactionPortableId = requirePortableId(
                transactionPortableIdSupplier.get(), "Depreciation transaction portable identity");
        UUID runPortableId = requirePortableId(
                runPortableIdSupplier.get(), "Depreciation run portable identity");
        String companyCode = normalizeCompanyCode(companyCodeSupplier.get());
        if (companyCode.isBlank())
        {
            throw new IllegalArgumentException("Active company code is required");
        }

        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, companyCode);
                if (!company.isActive())
                {
                    throw new IllegalStateException("Company " + company.getCode() + " is inactive");
                }

                FixedAsset asset = require(em, FixedAsset.class, assetId, "Fixed asset");
                ownership.requireOwnedBy(company, asset.getCompany(), "Fixed asset");
                validateDepreciationEligibility(ownership, company, asset, runDate);
                requireNoPriorRun(em, asset, runDate);
                validatePortableIdentityAvailability(em, transactionPortableId, runPortableId);
                PeriodCloseRangeService.requireOpen(
                        em, company.getCode(), runDate, "run monthly depreciation");

                BigDecimal accumulated = accumulatedDepreciation(em, asset);
                BigDecimal amount = nextDepreciation(asset, accumulated);
                if (amount.compareTo(BigDecimal.ZERO) <= 0)
                {
                    throw new IllegalStateException("No remaining depreciable value for asset " + assetId);
                }

                TransactionCommand command = new TransactionCommand(
                        runDate,
                        null,
                        "Depreciation: " + asset.getName(),
                        null,
                        List.of(
                                new TransactionLineCommand(
                                        asset.getDepreciationExpenseAccount().getId(),
                                        asset.getFund().getId(),
                                        null,
                                        null,
                                        null,
                                        amount,
                                        BigDecimal.ZERO,
                                        false,
                                        notes),
                                new TransactionLineCommand(
                                        asset.getAccumulatedDepreciationAccount().getId(),
                                        asset.getFund().getId(),
                                        null,
                                        null,
                                        null,
                                        BigDecimal.ZERO,
                                        amount,
                                        false,
                                        notes)));

                Txn transaction = transactionEntryService.enter(
                        em,
                        company,
                        command,
                        transactionPortableId,
                        auditActorSupplier.get(),
                        "Monthly fixed-asset depreciation");
                depreciationWriteHook.afterTransactionPersisted(
                        em, asset, transaction, runDate, amount, runPortableId);

                FixedAssetDepreciationRun run = new FixedAssetDepreciationRun();
                run.initializePortableIdentity(runPortableId);
                run.setFixedAsset(asset);
                run.setRunDate(runDate);
                run.setDepreciationAmount(amount);
                run.setTransaction(transaction);
                run.setNotes(notes);
                em.persist(run);
                em.flush();

                DepreciationRunView result = toRunView(run);
                em.getTransaction().commit();
                return result;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw actionableDepreciationFailure(assetId, runDate, ex);
            }
        }
    }

    /** Creates a fixed asset inside an interchange caller's existing transaction. */
    public FixedAsset createForImport(
            EntityManager em,
            Company company,
            FixedAssetCommand command,
            UUID portableId,
            Instant createdAt,
            Instant updatedAt)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Fixed-asset import requires an active caller-owned transaction");
        }
        if (command == null || command.companyCode() == null
                || !company.getCode().equalsIgnoreCase(command.companyCode().trim()))
        {
            throw new IllegalArgumentException("Fixed-asset import company does not match the command");
        }
        FixedAsset asset = new FixedAsset();
        apply(em, asset, command);
        asset.initializeImportMetadata(portableId, createdAt, updatedAt);
        em.persist(asset);
        return asset;
    }

    /** Records a completed depreciation run inside an interchange caller's existing transaction. */
    public FixedAssetDepreciationRun recordCompletedRunForImport(
            EntityManager em,
            Company company,
            FixedAsset asset,
            LocalDate runDate,
            BigDecimal amount,
            Txn transaction,
            String notes,
            UUID portableId,
            Instant createdAt)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(transaction, "transaction");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Depreciation-run import requires an active caller-owned transaction");
        }
        if (runDate == null)
        {
            throw new IllegalArgumentException("runDate is required");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new IllegalArgumentException("Depreciation amount must be positive");
        }
        CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
        ownership.ensureOwnedBy(em, company, asset, "Fixed asset");
        ownership.ensureOwnedBy(em, company, transaction, "Depreciation transaction");
        boolean duplicatePeriod = !em.createQuery("""
                select r.id from FixedAssetDepreciationRun r
                where r.fixedAsset = :asset and r.runDate = :runDate
                """, Long.class)
                .setParameter("asset", asset)
                .setParameter("runDate", runDate)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
        if (duplicatePeriod)
        {
            throw new IllegalStateException("A completed depreciation run already exists for " + runDate);
        }
        FixedAssetDepreciationRun run = new FixedAssetDepreciationRun();
        run.setFixedAsset(asset);
        run.setRunDate(runDate);
        run.setDepreciationAmount(scale(amount));
        run.setTransaction(transaction);
        run.setNotes(notes);
        run.initializeImportMetadata(portableId, createdAt);
        em.persist(run);
        return run;
    }

    private static void validateDepreciationEligibility(
            CompanyOwnershipService ownership,
            Company company,
            FixedAsset asset,
            LocalDate runDate)
    {
        if (asset.getStatus() != FixedAsset.Status.ACTIVE)
        {
            throw new IllegalStateException("Only active assets can be depreciated");
        }
        if (asset.getDepreciationMethod() != FixedAsset.DepreciationMethod.STRAIGHT_LINE)
        {
            throw new IllegalStateException("Unsupported depreciation method for asset " + asset.getId());
        }
        if (runDate.isBefore(asset.getAcquisitionDate()))
        {
            throw new IllegalArgumentException(
                    "Depreciation run date cannot be before the asset acquisition date "
                            + asset.getAcquisitionDate());
        }

        ownership.requireOwnedBy(company, asset.getAssetAccount(), "Asset account");
        ownership.requireOwnedBy(company, asset.getAccumulatedDepreciationAccount(),
                "Accumulated depreciation account");
        ownership.requireOwnedBy(company, asset.getDepreciationExpenseAccount(),
                "Depreciation expense account");
        ownership.requireOwnedBy(company, asset.getFund(), "Asset fund");

        requireUsableAccount(asset.getAssetAccount(), "Asset account", runDate);
        validateAssetAccount(asset.getAssetAccount());
        requireUsableAccount(
                asset.getAccumulatedDepreciationAccount(), "Accumulated depreciation account", runDate);
        if (asset.getAccumulatedDepreciationAccount().getAccountType() != AccountType.ASSET)
        {
            throw new IllegalStateException("Accumulated depreciation account must be an ASSET account");
        }
        requireUsableAccount(
                asset.getDepreciationExpenseAccount(), "Depreciation expense account", runDate);
        if (asset.getDepreciationExpenseAccount().getAccountType() != AccountType.EXPENSE)
        {
            throw new IllegalStateException("Depreciation expense account must be an EXPENSE account");
        }
        Fund fund = asset.getFund();
        if (!fund.isActive())
        {
            throw new IllegalStateException("Asset fund is inactive");
        }
        if (fund.getEffectiveFrom() != null && runDate.isBefore(fund.getEffectiveFrom()))
        {
            throw new IllegalStateException("Asset fund is not effective on " + runDate);
        }
        if (fund.getEffectiveTo() != null && runDate.isAfter(fund.getEffectiveTo()))
        {
            throw new IllegalStateException("Asset fund is not effective on " + runDate);
        }
    }

    private static void requireUsableAccount(Account account, String label, LocalDate runDate)
    {
        if (!account.isActive())
        {
            throw new IllegalStateException(label + " is inactive");
        }
        if (!account.isPosting())
        {
            throw new IllegalStateException(label + " is not a posting account");
        }
        if (account.getEffectiveFrom() != null && runDate.isBefore(account.getEffectiveFrom()))
        {
            throw new IllegalStateException(label + " is not effective on " + runDate);
        }
        if (account.getEffectiveTo() != null && runDate.isAfter(account.getEffectiveTo()))
        {
            throw new IllegalStateException(label + " is not effective on " + runDate);
        }
    }

    private static void requireNoPriorRun(EntityManager em, FixedAsset asset, LocalDate runDate)
    {
        List<Object[]> existing = em.createQuery("""
                select r.id, r.transaction.id
                from FixedAssetDepreciationRun r
                where r.fixedAsset = :asset and r.runDate = :runDate
                """, Object[].class)
                .setParameter("asset", asset)
                .setParameter("runDate", runDate)
                .setMaxResults(1)
                .getResultList();
        if (existing.isEmpty())
        {
            return;
        }

        long transactionId = ((Number) existing.get(0)[1]).longValue();
        boolean completedProtection = countNative(em, """
                select count(*)
                from txn_reconciliation_protection p
                join reconciliation_run r on r.id = p.reconciliation_run_id
                where p.txn_id = ? and r.status = 'COMPLETED'
                """, transactionId) > 0;
        boolean finalizedProtection = countNative(em, """
                select count(*)
                from bank_reconciliation_session s
                join bank_reconciliation_match m on m.session_id = s.id
                join txn_split ts on ts.id = m.txn_split_id
                where ts.txn_id = ? and s.status = 'FINALIZED'
                """, transactionId) > 0;
        if (completedProtection || finalizedProtection)
        {
            throw new IllegalStateException(
                    "A completed depreciation run already exists for " + runDate
                            + " and its transaction is protected by a completed or finalized reconciliation");
        }
        throw new IllegalStateException("A completed depreciation run already exists for " + runDate);
    }

    private static long countNative(EntityManager em, String sql, long value)
    {
        return ((Number) em.createNativeQuery(sql)
                .setParameter(1, value)
                .getSingleResult()).longValue();
    }

    private static void validatePortableIdentityAvailability(
            EntityManager em,
            UUID transactionPortableId,
            UUID runPortableId)
    {
        Long transactionCount = em.createQuery(
                        "select count(t) from Txn t where t.portableId = :portableId", Long.class)
                .setParameter("portableId", transactionPortableId)
                .getSingleResult();
        if (transactionCount > 0)
        {
            throw new IllegalStateException(
                    "Depreciation transaction portable identity is already in use: " + transactionPortableId);
        }
        Long runCount = em.createQuery(
                        "select count(r) from FixedAssetDepreciationRun r where r.portableId = :portableId",
                        Long.class)
                .setParameter("portableId", runPortableId)
                .getSingleResult();
        if (runCount > 0)
        {
            throw new IllegalStateException(
                    "Depreciation run portable identity is already in use: " + runPortableId);
        }
    }

    private static BigDecimal accumulatedDepreciation(EntityManager em, FixedAsset asset)
    {
        BigDecimal runTotal = em.createQuery("""
                select coalesce(sum(r.depreciationAmount), 0)
                from FixedAssetDepreciationRun r
                where r.fixedAsset = :asset
                """, BigDecimal.class)
                .setParameter("asset", asset)
                .getSingleResult();
        return scale(asset.getOpeningAccumulatedDepreciation()).add(scale(runTotal));
    }

    private static UUID requirePortableId(UUID value, String label)
    {
        return Objects.requireNonNull(value, label + " is required");
    }

    private static RuntimeException actionableDepreciationFailure(
            long assetId,
            LocalDate runDate,
            RuntimeException failure)
    {
        if (failure instanceof IllegalArgumentException
                || failure instanceof IllegalStateException
                || failure instanceof PostingException)
        {
            return failure;
        }
        String detail = deepestMessage(failure);
        return new IllegalStateException(
                "Monthly depreciation for asset " + assetId + " on " + runDate
                        + " was not saved; all accounting changes were rolled back"
                        + (detail.isBlank() ? "." : ": " + detail),
                failure);
    }

    private static String deepestMessage(Throwable failure)
    {
        Throwable current = failure;
        String message = "";
        while (current != null)
        {
            if (current.getMessage() != null && !current.getMessage().isBlank())
            {
                message = current.getMessage().trim();
            }
            current = current.getCause();
        }
        return message;
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
