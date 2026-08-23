package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountClassification;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;
import org.nonprofitbookkeeping.model.FixedAssetLifecycleEvent;
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
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
    private final TransactionCorrectionService transactionCorrectionService;
    private final Supplier<String> companyCodeSupplier;
    private final Supplier<UUID> transactionPortableIdSupplier;
    private final Supplier<UUID> runPortableIdSupplier;
    private final Supplier<String> auditActorSupplier;
    private final DepreciationWriteHook depreciationWriteHook;
    private final Supplier<UUID> lifecycleTransactionPortableIdSupplier;
    private final Supplier<UUID> lifecycleEventPortableIdSupplier;
    private final Supplier<UUID> lifecycleReversalPortableIdSupplier;
    private final LifecycleWriteHook lifecycleWriteHook;

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

    @FunctionalInterface
    interface LifecycleWriteHook
    {
        void afterTransactionPersisted(
                EntityManager em,
                FixedAsset asset,
                Txn transaction,
                LifecyclePreview preview);
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
        this(jpa, transactionEntryService,
                new TransactionCorrectionService(jpa, companyCodeSupplier), companyCodeSupplier,
                UUID::randomUUID, UUID::randomUUID, () -> "system",
                (em, asset, transaction, runDate, amount, runPortableId) -> { },
                UUID::randomUUID, UUID::randomUUID, UUID::randomUUID,
                (em, asset, transaction, preview) -> { });
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
        this(jpa, transactionEntryService,
                new TransactionCorrectionService(jpa, companyCodeSupplier), companyCodeSupplier,
                transactionPortableIdSupplier, runPortableIdSupplier, auditActorSupplier,
                depreciationWriteHook, UUID::randomUUID, UUID::randomUUID, UUID::randomUUID,
                (em, asset, transaction, preview) -> { });
    }

    FixedAssetService(
            Jpa jpa,
            TransactionEntryService transactionEntryService,
            TransactionCorrectionService transactionCorrectionService,
            Supplier<String> companyCodeSupplier,
            Supplier<UUID> lifecycleTransactionPortableIdSupplier,
            Supplier<UUID> lifecycleEventPortableIdSupplier,
            Supplier<UUID> lifecycleReversalPortableIdSupplier,
            LifecycleWriteHook lifecycleWriteHook)
    {
        this(jpa, transactionEntryService, transactionCorrectionService, companyCodeSupplier,
                UUID::randomUUID, UUID::randomUUID, () -> "system",
                (em, asset, transaction, runDate, amount, runPortableId) -> { },
                lifecycleTransactionPortableIdSupplier, lifecycleEventPortableIdSupplier,
                lifecycleReversalPortableIdSupplier, lifecycleWriteHook);
    }

    private FixedAssetService(
            Jpa jpa,
            TransactionEntryService transactionEntryService,
            TransactionCorrectionService transactionCorrectionService,
            Supplier<String> companyCodeSupplier,
            Supplier<UUID> transactionPortableIdSupplier,
            Supplier<UUID> runPortableIdSupplier,
            Supplier<String> auditActorSupplier,
            DepreciationWriteHook depreciationWriteHook,
            Supplier<UUID> lifecycleTransactionPortableIdSupplier,
            Supplier<UUID> lifecycleEventPortableIdSupplier,
            Supplier<UUID> lifecycleReversalPortableIdSupplier,
            LifecycleWriteHook lifecycleWriteHook)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.transactionEntryService = Objects.requireNonNull(transactionEntryService, "transactionEntryService");
        this.transactionCorrectionService = Objects.requireNonNull(
                transactionCorrectionService, "transactionCorrectionService");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.transactionPortableIdSupplier = Objects.requireNonNull(
                transactionPortableIdSupplier, "transactionPortableIdSupplier");
        this.runPortableIdSupplier = Objects.requireNonNull(runPortableIdSupplier, "runPortableIdSupplier");
        this.auditActorSupplier = Objects.requireNonNull(auditActorSupplier, "auditActorSupplier");
        this.depreciationWriteHook = Objects.requireNonNull(depreciationWriteHook, "depreciationWriteHook");
        this.lifecycleTransactionPortableIdSupplier = Objects.requireNonNull(
                lifecycleTransactionPortableIdSupplier, "lifecycleTransactionPortableIdSupplier");
        this.lifecycleEventPortableIdSupplier = Objects.requireNonNull(
                lifecycleEventPortableIdSupplier, "lifecycleEventPortableIdSupplier");
        this.lifecycleReversalPortableIdSupplier = Objects.requireNonNull(
                lifecycleReversalPortableIdSupplier, "lifecycleReversalPortableIdSupplier");
        this.lifecycleWriteHook = Objects.requireNonNull(lifecycleWriteHook, "lifecycleWriteHook");
    }

    public FixedAssetView create(FixedAssetCommand command)
    {
        requireInteractiveStatus(command, null);
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
                requireInteractiveStatus(command, asset);
                requireLifecycleSafeUpdate(em, asset, command);
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
                requireNoLaterLifecycleActivity(em, asset, runDate, "Depreciation run date");
                validatePortableIdentityAvailability(em, transactionPortableId, runPortableId);
                PeriodCloseRangeService.requireOpen(
                        em, company.getCode(), runDate, "run monthly depreciation");

                BigDecimal accumulated = accumulatedDepreciation(em, asset);
                BigDecimal impairment = accumulatedImpairment(em, asset);
                BigDecimal amount = nextDepreciation(asset, accumulated, impairment);
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

    /** Builds an immutable, non-mutating preview for a Sale, Retirement, or Impairment. */
    public LifecyclePreview previewLifecycleEvent(long assetId, FixedAssetLifecycleCommand command)
    {
        validateLifecycleCommand(command);
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(
                    em, normalizeCompanyCode(companyCodeSupplier.get()));
            FixedAsset asset = requireLifecycleAsset(em, assetId);
            return buildLifecyclePreview(
                    em,
                    company,
                    asset,
                    command,
                    requirePortableId(lifecycleTransactionPortableIdSupplier.get(),
                            "Fixed-asset lifecycle transaction portable identity"),
                    requirePortableId(lifecycleEventPortableIdSupplier.get(),
                            "Fixed-asset lifecycle event portable identity"));
        }
    }

    /** Commits the frozen lifecycle preview, canonical accounting, status, and audit atomically. */
    public FixedAssetLifecycleEventView recordLifecycleEvent(LifecyclePreview preview, String actor)
    {
        Objects.requireNonNull(preview, "preview");
        String normalizedActor = requireText(actor, "actor");
        String activeCompany = normalizeCompanyCode(companyCodeSupplier.get());
        if (!activeCompany.equals(preview.companyCode()))
        {
            throw new IllegalStateException(
                    "Active company changed after fixed-asset lifecycle preview; reopen the preview");
        }

        try (EntityManager em = jpa.em())
        {
            FixedAssetLifecycleEvent existing = lifecycleEventByPortableId(em, preview.eventPortableId());
            if (existing != null)
            {
                if (!sameLifecycleOperation(existing, preview))
                {
                    throw new IllegalStateException(
                            "Fixed-asset lifecycle portable identity is already used by a different operation");
                }
                return toLifecycleView(existing);
            }

            em.getTransaction().begin();
            try
            {
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, activeCompany);
                FixedAsset asset = em.find(
                        FixedAsset.class, preview.fixedAssetId(), LockModeType.PESSIMISTIC_WRITE);
                if (asset == null)
                {
                    throw new IllegalArgumentException("Fixed asset not found: " + preview.fixedAssetId());
                }
                LifecyclePreview refreshed = buildLifecyclePreview(
                        em,
                        company,
                        asset,
                        preview.command(),
                        preview.transactionPortableId(),
                        preview.eventPortableId());
                if (!preview.equals(refreshed))
                {
                    throw new IllegalStateException(
                            "Fixed asset, carrying amount, accounts, fund, or company changed after preview; reopen the preview");
                }
                validateLifecyclePortableIdentityAvailability(em, preview);

                Txn transaction = transactionEntryService.enter(
                        em,
                        company,
                        preview.transactionCommand(),
                        preview.transactionPortableId(),
                        normalizedActor,
                        "Governed fixed-asset " + preview.eventType().name().toLowerCase());
                lifecycleWriteHook.afterTransactionPersisted(em, asset, transaction, preview);

                asset.setStatus(preview.assetStatusAfter());
                asset.touchUpdatedAt();
                FixedAssetLifecycleEvent event = lifecycleEvent(em, asset, transaction, preview);
                em.persist(event);
                em.flush();
                em.persist(lifecycleAudit(company, normalizedActor, event, preview));
                em.flush();

                FixedAssetLifecycleEventView result = toLifecycleView(event);
                em.getTransaction().commit();
                return result;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    /** Lists immutable lifecycle history, including reversed events, for the selected company. */
    public List<FixedAssetLifecycleEventView> listLifecycleEvents(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("""
                    select e from FixedAssetLifecycleEvent e
                    join fetch e.fixedAsset a
                    join fetch a.company c
                    join fetch e.transaction
                    left join fetch e.reversalTransaction
                    where c.code = :companyCode
                    order by e.eventDate desc, e.id desc
                    """, FixedAssetLifecycleEvent.class)
                    .setParameter("companyCode", normalizeCompanyCode(companyCode))
                    .getResultList()
                    .stream()
                    .map(FixedAssetService::toLifecycleView)
                    .toList();
        }
    }

    /** Builds a non-mutating preview for the domain-owned reversal of one lifecycle event. */
    public LifecycleReversalPreview previewLifecycleReversal(
            long eventId,
            LocalDate reversalDate,
            String reason)
    {
        if (reversalDate == null)
        {
            throw new IllegalArgumentException("reversalDate is required");
        }
        String normalizedReason = requireText(reason, "reason");
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(
                    em, normalizeCompanyCode(companyCodeSupplier.get()));
            FixedAssetLifecycleEvent event = requireLifecycleEvent(em, eventId);
            return buildLifecycleReversalPreview(
                    em,
                    company,
                    event,
                    reversalDate,
                    normalizedReason,
                    requirePortableId(lifecycleReversalPortableIdSupplier.get(),
                            "Fixed-asset lifecycle reversal portable identity"));
        }
    }

    /** Atomically reverses canonical accounting and restores the corresponding asset lifecycle state. */
    public FixedAssetLifecycleEventView reverseLifecycleEvent(
            LifecycleReversalPreview preview,
            String actor)
    {
        Objects.requireNonNull(preview, "preview");
        String normalizedActor = requireText(actor, "actor");
        String activeCompany = normalizeCompanyCode(companyCodeSupplier.get());
        if (!activeCompany.equals(preview.companyCode()))
        {
            throw new IllegalStateException(
                    "Active company changed after fixed-asset reversal preview; reopen the preview");
        }

        try (EntityManager em = jpa.em())
        {
            FixedAssetLifecycleEvent existing = requireLifecycleEvent(em, preview.lifecycleEventId());
            if (existing.getReversalTransaction() != null)
            {
                if (!sameLifecycleReversal(existing, preview, activeCompany))
                {
                    throw new IllegalStateException(
                            "Fixed-asset lifecycle event is already reversed by a different operation");
                }
                return toLifecycleView(existing);
            }

            em.getTransaction().begin();
            try
            {
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, activeCompany);
                FixedAssetLifecycleEvent event = em.find(
                        FixedAssetLifecycleEvent.class,
                        preview.lifecycleEventId(),
                        LockModeType.PESSIMISTIC_WRITE);
                if (event == null)
                {
                    throw new IllegalArgumentException(
                            "Fixed-asset lifecycle event not found: " + preview.lifecycleEventId());
                }
                FixedAsset asset = em.find(
                        FixedAsset.class, event.getFixedAsset().getId(), LockModeType.PESSIMISTIC_WRITE);
                Txn original = em.find(
                        Txn.class, event.getTransaction().getId(), LockModeType.PESSIMISTIC_WRITE);
                event.setFixedAsset(asset);
                event.setTransaction(original);
                LifecycleReversalPreview refreshed = buildLifecycleReversalPreview(
                        em,
                        company,
                        event,
                        preview.reversalDate(),
                        preview.reason(),
                        preview.reversalTransactionPortableId());
                if (!preview.equals(refreshed))
                {
                    throw new IllegalStateException(
                            "Fixed-asset event, transaction, status, or company changed after reversal preview; reopen the preview");
                }

                Txn reversal = transactionCorrectionService.reverse(
                        em,
                        company,
                        original,
                        preview.reversalDate(),
                        normalizedActor,
                        preview.reason(),
                        preview.reversalTransactionPortableId());
                if (event.getEventType() != FixedAssetLifecycleEvent.EventType.IMPAIRMENT)
                {
                    asset.setStatus(event.getAssetStatusBefore());
                }
                asset.touchUpdatedAt();
                event.markReversed(reversal, Instant.now());
                em.flush();
                em.persist(lifecycleReversalAudit(
                        company, normalizedActor, asset, event, preview));
                em.flush();

                FixedAssetLifecycleEventView result = toLifecycleView(event);
                em.getTransaction().commit();
                return result;
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
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

    private LifecyclePreview buildLifecyclePreview(
            EntityManager em,
            Company company,
            FixedAsset asset,
            FixedAssetLifecycleCommand command,
            UUID transactionPortableId,
            UUID eventPortableId)
    {
        validateLifecycleCommand(command);
        CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
        ownership.ensureOwnedBy(em, company, asset, "Fixed asset");
        if (!company.isActive())
        {
            throw new IllegalStateException("Company " + company.getCode() + " is inactive");
        }
        if (asset.getStatus() != FixedAsset.Status.ACTIVE)
        {
            throw new IllegalStateException("Only active fixed assets can receive lifecycle accounting");
        }
        if (command.eventDate().isBefore(asset.getAcquisitionDate()))
        {
            throw new IllegalArgumentException("Lifecycle event date cannot precede acquisition date");
        }
        requireNoActiveFinalDisposition(em, asset);
        requireNoLaterAssetActivity(em, asset, command.eventDate());

        Account assetAccount = asset.getAssetAccount();
        Account accumulatedAccount = asset.getAccumulatedDepreciationAccount();
        Fund assetFund = asset.getFund();
        ownership.ensureOwnedBy(em, company, assetAccount, "Asset account");
        ownership.ensureOwnedBy(em, company, accumulatedAccount, "Accumulated depreciation account");
        ownership.ensureOwnedBy(em, company, assetFund, "Asset fund");
        validateAssetAccount(assetAccount);
        if (accumulatedAccount.getAccountType() != AccountType.ASSET)
        {
            throw new IllegalStateException("Accumulated depreciation account must be an ASSET account");
        }
        if (assetAccount.getId().equals(accumulatedAccount.getId()))
        {
            throw new IllegalStateException(
                    "Asset and accumulated depreciation accounts must be distinct");
        }
        requireUsableAccount(assetAccount, "Asset account", command.eventDate());
        requireUsableAccount(accumulatedAccount, "Accumulated depreciation account", command.eventDate());
        requireUsableFund(assetFund, command.eventDate());
        PeriodCloseRangeService.requireOpen(
                em, company.getCode(), command.eventDate(), "record fixed-asset lifecycle event");

        BigDecimal cost = scale(asset.getAcquisitionCost());
        if (cost.signum() <= 0)
        {
            throw new IllegalStateException(
                    "A zero-cost asset has no nonzero canonical disposal entry; correct its acquisition facts first");
        }
        BigDecimal accumulated = accumulatedDepreciation(em, asset);
        BigDecimal impairmentBefore = accumulatedImpairment(em, asset);
        if (accumulated.add(impairmentBefore).compareTo(cost) > 0)
        {
            throw new IllegalStateException(
                    "Accumulated depreciation and impairment exceed acquisition cost; correct the asset history first");
        }
        BigDecimal carrying = cost.subtract(accumulated).subtract(impairmentBefore).max(ZERO);
        BigDecimal proceeds = scale(command.proceeds());
        BigDecimal impairment = scale(command.impairmentAmount());
        if (command.proceeds().signum() > 0 && proceeds.signum() == 0)
        {
            throw new IllegalArgumentException(
                    "Positive proceeds must round to at least 0.0001 at ledger precision");
        }
        BigDecimal gain;
        BigDecimal loss;
        FixedAsset.Status statusAfter;
        if (command.eventType() == FixedAssetLifecycleEvent.EventType.IMPAIRMENT)
        {
            if (impairment.signum() <= 0)
            {
                throw new IllegalArgumentException("Impairment amount must be positive");
            }
            if (impairment.compareTo(carrying) > 0)
            {
                throw new IllegalArgumentException("Impairment cannot exceed current carrying amount " + carrying);
            }
            gain = ZERO;
            loss = impairment;
            statusAfter = FixedAsset.Status.ACTIVE;
        }
        else
        {
            impairment = ZERO;
            gain = proceeds.subtract(carrying).max(ZERO);
            loss = carrying.subtract(proceeds).max(ZERO);
            statusAfter = FixedAsset.Status.DISPOSED;
        }

        Account proceedsAccount = proceeds.signum() > 0
                ? require(em, Account.class, command.proceedsAccountId(), "Proceeds account") : null;
        Account gainAccount = gain.signum() > 0
                ? require(em, Account.class, command.gainAccountId(), "Gain account") : null;
        Account lossAccount = loss.signum() > 0
                ? require(em, Account.class, command.lossAccountId(), "Loss/impairment account") : null;
        if (proceedsAccount != null)
        {
            ownership.ensureOwnedBy(em, company, proceedsAccount, "Proceeds account");
            requireUsableAccount(proceedsAccount, "Proceeds account", command.eventDate());
            if (proceedsAccount.getAccountType() != AccountType.ASSET)
            {
                throw new IllegalArgumentException("Proceeds account must be an ASSET account");
            }
            if (proceedsAccount.getId().equals(assetAccount.getId())
                    || proceedsAccount.getId().equals(accumulatedAccount.getId()))
            {
                throw new IllegalArgumentException(
                        "Proceeds account must be distinct from the asset and accumulated depreciation accounts");
            }
            requireOutsideFinalizedReconciliation(
                    em, company, proceedsAccount, command.eventDate(), "record fixed-asset proceeds");
        }
        if (gainAccount != null)
        {
            ownership.ensureOwnedBy(em, company, gainAccount, "Gain account");
            requireUsableAccount(gainAccount, "Gain account", command.eventDate());
            if (gainAccount.getAccountType() != AccountType.INCOME)
            {
                throw new IllegalArgumentException("Gain account must be an INCOME account");
            }
        }
        if (lossAccount != null)
        {
            ownership.ensureOwnedBy(em, company, lossAccount, "Loss/impairment account");
            requireUsableAccount(lossAccount, "Loss/impairment account", command.eventDate());
            if (lossAccount.getAccountType() != AccountType.EXPENSE)
            {
                throw new IllegalArgumentException("Loss/impairment account must be an EXPENSE account");
            }
        }

        String lineNote = lifecycleLineNote(command.eventType(), asset.getName());
        List<TransactionLineCommand> lines = new ArrayList<>();
        if (command.eventType() == FixedAssetLifecycleEvent.EventType.IMPAIRMENT)
        {
            lines.add(line(lossAccount, assetFund, impairment, ZERO, lineNote));
            lines.add(line(accumulatedAccount, assetFund, ZERO, impairment, lineNote));
        }
        else
        {
            if (proceeds.signum() > 0)
            {
                lines.add(line(proceedsAccount, assetFund, proceeds, ZERO, lineNote));
            }
            BigDecimal accumulatedContra = accumulated.add(impairmentBefore);
            if (accumulatedContra.signum() > 0)
            {
                lines.add(line(accumulatedAccount, assetFund, accumulatedContra, ZERO, lineNote));
            }
            if (loss.signum() > 0)
            {
                lines.add(line(lossAccount, assetFund, loss, ZERO, lineNote));
            }
            lines.add(line(assetAccount, assetFund, ZERO, cost, lineNote));
            if (gain.signum() > 0)
            {
                lines.add(line(gainAccount, assetFund, ZERO, gain, lineNote));
            }
        }
        TransactionCommand transactionCommand = new TransactionCommand(
                command.eventDate(),
                null,
                lineNote,
                proceedsAccount != null && AccountClassification.isBank(proceedsAccount)
                        ? proceedsAccount.getId() : null,
                lines);
        return new LifecyclePreview(
                company.getCode(),
                asset.getId(),
                asset.getPortableId(),
                asset.getName(),
                command,
                command.eventType(),
                command.eventDate(),
                cost,
                accumulated,
                impairmentBefore,
                carrying,
                proceeds,
                impairment,
                gain,
                loss,
                assetAccount.getId(),
                assetAccount.getCode(),
                assetAccount.getName(),
                accumulatedAccount.getId(),
                accumulatedAccount.getCode(),
                accumulatedAccount.getName(),
                proceedsAccount == null ? null : proceedsAccount.getId(),
                proceedsAccount == null ? "" : proceedsAccount.getCode(),
                proceedsAccount == null ? "" : proceedsAccount.getName(),
                gainAccount == null ? null : gainAccount.getId(),
                gainAccount == null ? "" : gainAccount.getCode(),
                gainAccount == null ? "" : gainAccount.getName(),
                lossAccount == null ? null : lossAccount.getId(),
                lossAccount == null ? "" : lossAccount.getCode(),
                lossAccount == null ? "" : lossAccount.getName(),
                assetFund.getId(),
                assetFund.getCode(),
                assetFund.getName(),
                asset.getStatus(),
                statusAfter,
                transactionCommand,
                transactionPortableId,
                eventPortableId,
                blankToNull(command.notes()));
    }

    private LifecycleReversalPreview buildLifecycleReversalPreview(
            EntityManager em,
            Company company,
            FixedAssetLifecycleEvent event,
            LocalDate reversalDate,
            String reason,
            UUID reversalPortableId)
    {
        CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
        FixedAsset asset = event.getFixedAsset();
        Txn original = event.getTransaction();
        ownership.ensureOwnedBy(em, company, asset, "Fixed asset");
        ownership.ensureOwnedBy(em, company, original, "Fixed-asset lifecycle transaction");
        if (event.isReversed())
        {
            throw new IllegalStateException("Fixed-asset lifecycle event is already reversed");
        }
        if (!"ENTERED".equals(original.getStatus()))
        {
            throw new IllegalStateException("Only an entered fixed-asset lifecycle transaction can be reversed");
        }
        if (event.getEventType() != FixedAssetLifecycleEvent.EventType.IMPAIRMENT
                && asset.getStatus() != event.getAssetStatusAfter())
        {
            throw new IllegalStateException("Fixed-asset status no longer matches the lifecycle event");
        }
        if (event.getEventType() == FixedAssetLifecycleEvent.EventType.IMPAIRMENT)
        {
            Long laterFinal = em.createQuery("""
                    select count(e) from FixedAssetLifecycleEvent e
                    where e.fixedAsset = :asset
                      and e.eventType in :finalTypes
                      and e.reversalTransaction is null
                      and (e.eventDate > :eventDate or (e.eventDate = :eventDate and e.id > :eventId))
                    """, Long.class)
                    .setParameter("asset", asset)
                    .setParameter("finalTypes", List.of(
                            FixedAssetLifecycleEvent.EventType.SALE,
                            FixedAssetLifecycleEvent.EventType.RETIREMENT))
                    .setParameter("eventDate", event.getEventDate())
                    .setParameter("eventId", event.getId())
                    .getSingleResult();
            if (laterFinal > 0)
            {
                throw new IllegalStateException(
                        "Reverse the later Sale or Retirement before reversing this impairment");
            }
        }
        requireTransactionOutsideCompletedReconciliation(em, original.getId());
        PeriodCloseRangeService.requireOpen(
                em, company.getCode(), reversalDate, "reverse fixed-asset lifecycle event");
        return new LifecycleReversalPreview(
                company.getCode(),
                event.getId(),
                event.getPortableId(),
                event.getEventType(),
                event.getEventDate(),
                asset.getId(),
                asset.getName(),
                asset.getStatus(),
                original.getId(),
                original.getPortableId(),
                reversalDate,
                reversalPortableId,
                reason);
    }

    private static FixedAssetLifecycleEvent lifecycleEvent(
            EntityManager em,
            FixedAsset asset,
            Txn transaction,
            LifecyclePreview preview)
    {
        FixedAssetLifecycleEvent event = new FixedAssetLifecycleEvent();
        event.initializePortableIdentity(preview.eventPortableId());
        event.setFixedAsset(asset);
        event.setEventType(preview.eventType());
        event.setEventDate(preview.eventDate());
        event.setAcquisitionCost(preview.acquisitionCost());
        event.setAccumulatedDepreciation(preview.accumulatedDepreciation());
        event.setAccumulatedImpairmentBefore(preview.accumulatedImpairmentBefore());
        event.setCarryingAmountBefore(preview.carryingAmountBefore());
        event.setProceeds(preview.proceeds());
        event.setImpairmentAmount(preview.impairmentAmount());
        event.setGainAmount(preview.gainAmount());
        event.setLossAmount(preview.lossAmount());
        event.setProceedsAccount(preview.proceedsAccountId() == null
                ? null : em.getReference(Account.class, preview.proceedsAccountId()));
        event.setGainAccount(preview.gainAccountId() == null
                ? null : em.getReference(Account.class, preview.gainAccountId()));
        event.setLossAccount(preview.lossAccountId() == null
                ? null : em.getReference(Account.class, preview.lossAccountId()));
        event.setTransaction(transaction);
        event.setAssetStatusBefore(preview.assetStatusBefore());
        event.setAssetStatusAfter(preview.assetStatusAfter());
        event.setNotes(preview.notes());
        return event;
    }

    private static TransactionLineCommand line(
            Account account,
            Fund fund,
            BigDecimal debit,
            BigDecimal credit,
            String notes)
    {
        return new TransactionLineCommand(
                account.getId(), fund.getId(), null, null, null,
                debit, credit, false, notes);
    }

    private static String lifecycleLineNote(
            FixedAssetLifecycleEvent.EventType type,
            String assetName)
    {
        return switch (type)
        {
            case SALE -> "Fixed-asset sale: " + assetName;
            case RETIREMENT -> "Fixed-asset retirement: " + assetName;
            case IMPAIRMENT -> "Fixed-asset impairment: " + assetName;
        };
    }

    private static void validateLifecycleCommand(FixedAssetLifecycleCommand command)
    {
        if (command == null)
        {
            throw new IllegalArgumentException("command is required");
        }
        if (command.eventType() == null)
        {
            throw new IllegalArgumentException("eventType is required");
        }
        if (command.eventDate() == null)
        {
            throw new IllegalArgumentException("eventDate is required");
        }
        if (command.proceeds() == null || command.proceeds().signum() < 0)
        {
            throw new IllegalArgumentException("Proceeds must be nonnegative");
        }
        if (command.impairmentAmount() == null || command.impairmentAmount().signum() < 0)
        {
            throw new IllegalArgumentException("Impairment amount must be nonnegative");
        }
        if (command.eventType() == FixedAssetLifecycleEvent.EventType.RETIREMENT
                && command.proceeds().signum() != 0)
        {
            throw new IllegalArgumentException("Retirement cannot record proceeds; use Sale");
        }
        if (command.eventType() == FixedAssetLifecycleEvent.EventType.IMPAIRMENT
                && command.proceeds().signum() != 0)
        {
            throw new IllegalArgumentException("Impairment cannot record disposal proceeds");
        }
        if (command.eventType() != FixedAssetLifecycleEvent.EventType.IMPAIRMENT
                && command.impairmentAmount().signum() != 0)
        {
            throw new IllegalArgumentException("Only an Impairment event can record impairment amount");
        }
    }

    private static void requireNoActiveFinalDisposition(EntityManager em, FixedAsset asset)
    {
        Long count = em.createQuery("""
                select count(e) from FixedAssetLifecycleEvent e
                where e.fixedAsset = :asset
                  and e.eventType in :types
                  and e.reversalTransaction is null
                """, Long.class)
                .setParameter("asset", asset)
                .setParameter("types", List.of(
                        FixedAssetLifecycleEvent.EventType.SALE,
                        FixedAssetLifecycleEvent.EventType.RETIREMENT))
                .getSingleResult();
        if (count > 0)
        {
            throw new IllegalStateException("Fixed asset already has an unreversed Sale or Retirement");
        }
    }

    private static void requireNoLaterAssetActivity(
            EntityManager em,
            FixedAsset asset,
            LocalDate eventDate)
    {
        Long laterDepreciation = em.createQuery("""
                select count(r) from FixedAssetDepreciationRun r
                where r.fixedAsset = :asset and r.runDate > :eventDate
                """, Long.class)
                .setParameter("asset", asset)
                .setParameter("eventDate", eventDate)
                .getSingleResult();
        long laterLifecycle = laterLifecycleActivityCount(em, asset, eventDate);
        if (laterDepreciation > 0 || laterLifecycle > 0)
        {
            throw new IllegalStateException(
                    "Lifecycle event date precedes later fixed-asset accounting; "
                            + "reverse or correct the later activity first");
        }
    }

    private static void requireNoLaterLifecycleActivity(
            EntityManager em,
            FixedAsset asset,
            LocalDate accountingDate,
            String label)
    {
        long laterLifecycle = laterLifecycleActivityCount(em, asset, accountingDate);
        if (laterLifecycle > 0)
        {
            throw new IllegalStateException(
                    label + " precedes later fixed-asset lifecycle accounting; "
                            + "reverse or correct the later activity first");
        }
    }

    private static long laterLifecycleActivityCount(
            EntityManager em,
            FixedAsset asset,
            LocalDate accountingDate)
    {
        Number count = (Number) em.createNativeQuery("""
                select count(*)
                from fixed_asset_lifecycle_event e
                left join txn reversal on reversal.id = e.reversal_transaction_id
                where e.fixed_asset_id = ?
                  and (e.event_date > ? or reversal.txn_date > ?)
                """)
                .setParameter(1, asset.getId())
                .setParameter(2, accountingDate)
                .setParameter(3, accountingDate)
                .getSingleResult();
        return count.longValue();
    }

    private static void requireUsableFund(Fund fund, LocalDate eventDate)
    {
        if (!fund.isActive())
        {
            throw new IllegalStateException("Asset fund is inactive");
        }
        if (fund.getEffectiveFrom() != null && eventDate.isBefore(fund.getEffectiveFrom()))
        {
            throw new IllegalStateException("Asset fund is not effective on " + eventDate);
        }
        if (fund.getEffectiveTo() != null && eventDate.isAfter(fund.getEffectiveTo()))
        {
            throw new IllegalStateException("Asset fund is not effective on " + eventDate);
        }
    }

    private static void requireOutsideFinalizedReconciliation(
            EntityManager em,
            Company company,
            Account account,
            LocalDate eventDate,
            String operation)
    {
        if (!AccountClassification.isBank(account))
        {
            return;
        }
        Number protectedCount = (Number) em.createNativeQuery("""
                select count(*)
                  from bank_reconciliation_session s
                  join company_bank_account b on b.id = s.bank_account_id
                 where s.company_id = ?
                   and b.account_id = ?
                   and s.status = 'FINALIZED'
                   and ? between s.statement_start_date and s.statement_end_date
                """)
                .setParameter(1, company.getId())
                .setParameter(2, account.getId())
                .setParameter(3, eventDate)
                .getSingleResult();
        if (protectedCount.longValue() > 0)
        {
            throw new IllegalStateException(
                    "Cannot " + operation + " because " + eventDate
                            + " is inside a finalized reconciliation for proceeds account "
                            + account.getCode());
        }
    }

    private static void requireTransactionOutsideCompletedReconciliation(
            EntityManager em,
            long transactionId)
    {
        Number legacy = (Number) em.createNativeQuery("""
                select count(*)
                from txn_reconciliation_protection p
                join reconciliation_run r on r.id = p.reconciliation_run_id
                where p.txn_id = ? and r.status = 'COMPLETED'
                """)
                .setParameter(1, transactionId)
                .getSingleResult();
        Number current = (Number) em.createNativeQuery("""
                select count(*)
                from bank_reconciliation_session s
                join bank_reconciliation_match m on m.session_id = s.id
                join txn_split ts on ts.id = m.txn_split_id
                where ts.txn_id = ? and s.status = 'FINALIZED'
                """)
                .setParameter(1, transactionId)
                .getSingleResult();
        if (legacy.longValue() > 0 || current.longValue() > 0)
        {
            throw new IllegalStateException(
                    "Fixed-asset lifecycle transaction " + transactionId
                            + " is protected by a completed or finalized reconciliation");
        }
    }

    private static void validateLifecyclePortableIdentityAvailability(
            EntityManager em,
            LifecyclePreview preview)
    {
        Long eventCount = em.createQuery(
                        "select count(e) from FixedAssetLifecycleEvent e where e.portableId = :portableId",
                        Long.class)
                .setParameter("portableId", preview.eventPortableId())
                .getSingleResult();
        Long transactionCount = em.createQuery(
                        "select count(t) from Txn t where t.portableId = :portableId", Long.class)
                .setParameter("portableId", preview.transactionPortableId())
                .getSingleResult();
        if (eventCount > 0 || transactionCount > 0)
        {
            throw new IllegalStateException("Fixed-asset lifecycle portable identity is already in use");
        }
    }

    private static FixedAsset requireLifecycleAsset(EntityManager em, long assetId)
    {
        return em.createQuery("""
                select a from FixedAsset a
                join fetch a.company
                join fetch a.assetAccount
                join fetch a.accumulatedDepreciationAccount
                join fetch a.depreciationExpenseAccount
                join fetch a.fund
                where a.id = :id
                """, FixedAsset.class)
                .setParameter("id", assetId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Fixed asset not found: " + assetId));
    }

    private static FixedAssetLifecycleEvent requireLifecycleEvent(EntityManager em, long eventId)
    {
        return em.createQuery("""
                select e from FixedAssetLifecycleEvent e
                join fetch e.fixedAsset a
                join fetch a.company
                join fetch e.transaction
                left join fetch e.reversalTransaction
                where e.id = :id
                """, FixedAssetLifecycleEvent.class)
                .setParameter("id", eventId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Fixed-asset lifecycle event not found: " + eventId));
    }

    private static FixedAssetLifecycleEvent lifecycleEventByPortableId(
            EntityManager em,
            UUID portableId)
    {
        return em.createQuery("""
                select e from FixedAssetLifecycleEvent e
                join fetch e.fixedAsset a
                join fetch a.company
                join fetch e.transaction
                left join fetch e.reversalTransaction
                where e.portableId = :portableId
                """, FixedAssetLifecycleEvent.class)
                .setParameter("portableId", portableId)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    private static boolean sameLifecycleOperation(
            FixedAssetLifecycleEvent event,
            LifecyclePreview preview)
    {
        return preview.companyCode().equals(event.getFixedAsset().getCompany().getCode())
                && preview.fixedAssetId().equals(event.getFixedAsset().getId())
                && preview.eventType() == event.getEventType()
                && preview.eventDate().equals(event.getEventDate())
                && preview.transactionPortableId().equals(event.getTransaction().getPortableId())
                && preview.acquisitionCost().compareTo(scale(event.getAcquisitionCost())) == 0
                && preview.accumulatedDepreciation()
                .compareTo(scale(event.getAccumulatedDepreciation())) == 0
                && preview.accumulatedImpairmentBefore()
                .compareTo(scale(event.getAccumulatedImpairmentBefore())) == 0
                && preview.carryingAmountBefore()
                .compareTo(scale(event.getCarryingAmountBefore())) == 0
                && preview.proceeds().compareTo(scale(event.getProceeds())) == 0
                && preview.impairmentAmount().compareTo(scale(event.getImpairmentAmount())) == 0
                && preview.gainAmount().compareTo(scale(event.getGainAmount())) == 0
                && preview.lossAmount().compareTo(scale(event.getLossAmount())) == 0
                && Objects.equals(preview.proceedsAccountId(), accountId(event.getProceedsAccount()))
                && Objects.equals(preview.gainAccountId(), accountId(event.getGainAccount()))
                && Objects.equals(preview.lossAccountId(), accountId(event.getLossAccount()))
                && preview.assetStatusBefore() == event.getAssetStatusBefore()
                && preview.assetStatusAfter() == event.getAssetStatusAfter()
                && Objects.equals(preview.notes(), blankToNull(event.getNotes()));
    }

    private static boolean sameLifecycleReversal(
            FixedAssetLifecycleEvent event,
            LifecycleReversalPreview preview,
            String activeCompany)
    {
        Txn reversal = event.getReversalTransaction();
        return activeCompany.equals(event.getFixedAsset().getCompany().getCode())
                && preview.companyCode().equals(activeCompany)
                && preview.lifecycleEventPortableId().equals(event.getPortableId())
                && preview.fixedAssetId().equals(event.getFixedAsset().getId())
                && preview.originalTransactionId().equals(event.getTransaction().getId())
                && preview.originalTransactionPortableId().equals(event.getTransaction().getPortableId())
                && preview.reversalTransactionPortableId().equals(reversal.getPortableId())
                && preview.reversalDate().equals(reversal.getTxnDate())
                && Objects.equals(preview.reason(), blankToNull(reversal.getCorrectionNote()));
    }

    private static Long accountId(Account account)
    {
        return account == null ? null : account.getId();
    }

    private static FixedAssetLifecycleEventView toLifecycleView(FixedAssetLifecycleEvent event)
    {
        return new FixedAssetLifecycleEventView(
                event.getId(),
                event.getFixedAsset().getId(),
                event.getFixedAsset().getName(),
                event.getEventType(),
                event.getEventDate(),
                scale(event.getCarryingAmountBefore()),
                scale(event.getProceeds()),
                scale(event.getImpairmentAmount()),
                scale(event.getGainAmount()),
                scale(event.getLossAmount()),
                event.getTransaction().getId(),
                event.getReversalTransaction() == null ? null : event.getReversalTransaction().getId(),
                event.getNotes() == null ? "" : event.getNotes());
    }

    private static AuditEvent lifecycleAudit(
            Company company,
            String actor,
            FixedAssetLifecycleEvent event,
            LifecyclePreview preview)
    {
        AuditEvent audit = new AuditEvent();
        audit.setCompany(company);
        audit.setActor(actor);
        audit.setActionType("FIXED_ASSET_" + preview.eventType().name());
        audit.setEntityType("FixedAssetLifecycleEvent");
        audit.setEntityId(Long.toString(event.getId()));
        audit.setSummary(preview.eventType().name().toLowerCase() + " for fixed asset "
                + preview.assetName());
        audit.setBeforeValue("status=" + preview.assetStatusBefore()
                + ",carryingAmount=" + preview.carryingAmountBefore());
        audit.setAfterValue("status=" + preview.assetStatusAfter()
                + ",transactionId=" + event.getTransaction().getId()
                + ",proceeds=" + preview.proceeds()
                + ",gain=" + preview.gainAmount()
                + ",loss=" + preview.lossAmount());
        audit.setReason(preview.notes());
        return audit;
    }

    private static AuditEvent lifecycleReversalAudit(
            Company company,
            String actor,
            FixedAsset asset,
            FixedAssetLifecycleEvent event,
            LifecycleReversalPreview preview)
    {
        AuditEvent audit = new AuditEvent();
        audit.setCompany(company);
        audit.setActor(actor);
        audit.setActionType("FIXED_ASSET_LIFECYCLE_REVERSED");
        audit.setEntityType("FixedAssetLifecycleEvent");
        audit.setEntityId(Long.toString(event.getId()));
        audit.setSummary("reversed " + event.getEventType().name().toLowerCase()
                + " for fixed asset " + asset.getName());
        audit.setBeforeValue("status=" + preview.assetStatusAtPreview()
                + ",transactionId=" + preview.originalTransactionId());
        audit.setAfterValue("status=" + asset.getStatus()
                + ",reversalTransactionId=" + event.getReversalTransaction().getId());
        audit.setReason(preview.reason());
        return audit;
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

    private static BigDecimal accumulatedImpairment(EntityManager em, FixedAsset asset)
    {
        BigDecimal total = em.createQuery("""
                select coalesce(sum(e.impairmentAmount), 0)
                from FixedAssetLifecycleEvent e
                where e.fixedAsset = :asset
                  and e.eventType = :eventType
                  and e.reversalTransaction is null
                """, BigDecimal.class)
                .setParameter("asset", asset)
                .setParameter("eventType", FixedAssetLifecycleEvent.EventType.IMPAIRMENT)
                .getSingleResult();
        return scale(total);
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
        BigDecimal maximumOpening = command.acquisitionCost().subtract(command.salvageValue());
        if (command.openingAccumulatedDepreciation().compareTo(maximumOpening) > 0)
        {
            throw new IllegalArgumentException(
                    "Opening accumulated depreciation cannot exceed acquisition cost less salvage value");
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

    private static void requireInteractiveStatus(FixedAssetCommand command, FixedAsset existing)
    {
        if (command == null)
        {
            throw new IllegalArgumentException("command is required");
        }
        FixedAsset.Status requested = command.status() == null
                ? FixedAsset.Status.ACTIVE : command.status();
        if (requested == FixedAsset.Status.DISPOSED)
        {
            throw new IllegalArgumentException(
                    "DISPOSED is created only by the governed Sale or Retirement workflow");
        }
        if (existing != null && existing.getStatus() == FixedAsset.Status.DISPOSED)
        {
            throw new IllegalStateException(
                    "A disposed asset is immutable; reverse its lifecycle event before editing it");
        }
    }

    private static void requireLifecycleSafeUpdate(
            EntityManager em,
            FixedAsset asset,
            FixedAssetCommand command)
    {
        Long lifecycleCount = em.createQuery(
                        "select count(e) from FixedAssetLifecycleEvent e where e.fixedAsset = :asset",
                        Long.class)
                .setParameter("asset", asset)
                .getSingleResult();
        if (lifecycleCount == 0)
        {
            return;
        }
        boolean accountingChanged = !Objects.equals(asset.getAssetAccount().getId(), command.assetAccountId())
                || !Objects.equals(asset.getAccumulatedDepreciationAccount().getId(),
                command.accumulatedDepreciationAccountId())
                || !Objects.equals(asset.getDepreciationExpenseAccount().getId(),
                command.depreciationExpenseAccountId())
                || !Objects.equals(asset.getFund().getId(), command.fundId())
                || !Objects.equals(asset.getAcquisitionDate(), command.acquisitionDate())
                || scale(asset.getAcquisitionCost()).compareTo(scale(command.acquisitionCost())) != 0
                || scale(asset.getOpeningAccumulatedDepreciation())
                .compareTo(scale(command.openingAccumulatedDepreciation())) != 0;
        if (accountingChanged)
        {
            throw new IllegalStateException(
                    "Asset accounts, fund, acquisition cost/date, and opening depreciation cannot change after a lifecycle accounting event");
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
        BigDecimal impairment = accumulatedImpairment(em, asset);
        BigDecimal bookValue = scale(asset.getAcquisitionCost())
                .subtract(accumulated)
                .subtract(impairment)
                .max(ZERO);
        BigDecimal next = nextDepreciation(asset, accumulated, impairment);
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
                impairment,
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

    private static BigDecimal nextDepreciation(
            FixedAsset asset,
            BigDecimal accumulated,
            BigDecimal impairment)
    {
        BigDecimal depreciable = scale(asset.getAcquisitionCost()).subtract(scale(asset.getSalvageValue()));
        BigDecimal remaining = depreciable.subtract(accumulated).subtract(scale(impairment));
        if (remaining.compareTo(BigDecimal.ZERO) <= 0 || asset.getStatus() != FixedAsset.Status.ACTIVE)
        {
            return ZERO;
        }
        BigDecimal monthly = depreciable.divide(BigDecimal.valueOf(asset.getUsefulLifeMonths()), 4, RoundingMode.HALF_UP);
        return monthly.min(remaining).setScale(4, RoundingMode.HALF_UP);
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Frozen lifecycle calculation and exact canonical transaction proposal. */
    public record LifecyclePreview(
            String companyCode,
            Long fixedAssetId,
            UUID fixedAssetPortableId,
            String assetName,
            FixedAssetLifecycleCommand command,
            FixedAssetLifecycleEvent.EventType eventType,
            LocalDate eventDate,
            BigDecimal acquisitionCost,
            BigDecimal accumulatedDepreciation,
            BigDecimal accumulatedImpairmentBefore,
            BigDecimal carryingAmountBefore,
            BigDecimal proceeds,
            BigDecimal impairmentAmount,
            BigDecimal gainAmount,
            BigDecimal lossAmount,
            Long assetAccountId,
            String assetAccountCode,
            String assetAccountName,
            Long accumulatedAccountId,
            String accumulatedAccountCode,
            String accumulatedAccountName,
            Long proceedsAccountId,
            String proceedsAccountCode,
            String proceedsAccountName,
            Long gainAccountId,
            String gainAccountCode,
            String gainAccountName,
            Long lossAccountId,
            String lossAccountCode,
            String lossAccountName,
            Long fundId,
            String fundCode,
            String fundName,
            FixedAsset.Status assetStatusBefore,
            FixedAsset.Status assetStatusAfter,
            TransactionCommand transactionCommand,
            UUID transactionPortableId,
            UUID eventPortableId,
            String notes)
    {
    }

    /** Frozen request for reversing a lifecycle event through canonical correction policy. */
    public record LifecycleReversalPreview(
            String companyCode,
            Long lifecycleEventId,
            UUID lifecycleEventPortableId,
            FixedAssetLifecycleEvent.EventType eventType,
            LocalDate eventDate,
            Long fixedAssetId,
            String assetName,
            FixedAsset.Status assetStatusAtPreview,
            Long originalTransactionId,
            UUID originalTransactionPortableId,
            LocalDate reversalDate,
            UUID reversalTransactionPortableId,
            String reason)
    {
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
