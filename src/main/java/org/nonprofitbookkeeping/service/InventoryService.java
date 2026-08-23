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
import org.nonprofitbookkeeping.model.Fund;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;
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

/** Application service for H2-backed inventory items and movement history. */
@ApplicationScoped
public class InventoryService
{
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final Jpa jpa;
    private final TransactionEntryService transactionEntryService;
    private final TransactionCorrectionService transactionCorrectionService;
    private final Supplier<String> companyCodeSupplier;
    private final Supplier<UUID> transactionPortableIdSupplier;
    private final Supplier<UUID> movementPortableIdSupplier;
    private final MovementWriteHook movementWriteHook;

    @FunctionalInterface
    interface MovementWriteHook
    {
        void afterTransactionPersisted(
                EntityManager em,
                InventoryItem item,
                Txn transaction,
                MovementPreview preview);
    }

    @Inject
    public InventoryService(Jpa jpa)
    {
        this(jpa, new TransactionEntryService(jpa), new TransactionCorrectionService(jpa), () -> "DEFAULT");
    }

    public InventoryService(
            Jpa jpa,
            TransactionEntryService transactionEntryService,
            Supplier<String> companyCodeSupplier)
    {
        this(jpa, transactionEntryService,
                new TransactionCorrectionService(jpa, companyCodeSupplier), companyCodeSupplier);
    }

    public InventoryService(
            Jpa jpa,
            TransactionEntryService transactionEntryService,
            TransactionCorrectionService transactionCorrectionService,
            Supplier<String> companyCodeSupplier)
    {
        this(jpa, transactionEntryService, transactionCorrectionService, companyCodeSupplier,
                UUID::randomUUID, UUID::randomUUID,
                (em, item, transaction, preview) -> { });
    }

    InventoryService(
            Jpa jpa,
            TransactionEntryService transactionEntryService,
            TransactionCorrectionService transactionCorrectionService,
            Supplier<String> companyCodeSupplier,
            Supplier<UUID> transactionPortableIdSupplier,
            Supplier<UUID> movementPortableIdSupplier,
            MovementWriteHook movementWriteHook)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
        this.transactionEntryService = Objects.requireNonNull(transactionEntryService, "transactionEntryService");
        this.transactionCorrectionService = Objects.requireNonNull(
                transactionCorrectionService, "transactionCorrectionService");
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier, "companyCodeSupplier");
        this.transactionPortableIdSupplier = Objects.requireNonNull(
                transactionPortableIdSupplier, "transactionPortableIdSupplier");
        this.movementPortableIdSupplier = Objects.requireNonNull(
                movementPortableIdSupplier, "movementPortableIdSupplier");
        this.movementWriteHook = Objects.requireNonNull(movementWriteHook, "movementWriteHook");
    }

    public InventoryItemView create(InventoryItemCommand command)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                validateCommand(command);
                requireActiveCompanyCommand(command);
                if (scale(command.quantity()).signum() > 0 && scale(command.unitValue()).signum() > 0)
                {
                    throw new IllegalArgumentException(
                            "Create valued inventory at zero quantity, then use Receive Quantity to post its value atomically");
                }
                InventoryItem item = new InventoryItem();
                apply(em, item, command);
                em.persist(item);
                if (item.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                {
                    InventoryMovement movement = movement(item,
                            InventoryMovement.MovementType.RECEIPT,
                            item.getQuantity(),
                            item.getQuantity(),
                            item.getAcquisitionDate(),
                            "Initial quantity");
                    em.persist(movement);
                }
                em.getTransaction().commit();
                return load(item.getId());
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public InventoryItemView update(long itemId, InventoryItemCommand command)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                validateCommand(command);
                requireActiveCompanyCommand(command);
                InventoryItem item = require(em, InventoryItem.class, itemId, "Inventory item");
                if (!normalizeCompanyCode(item.getCompany().getCode())
                        .equals(normalizeCompanyCode(command.companyCode())))
                {
                    throw new IllegalStateException("Inventory item belongs to another company");
                }
                BigDecimal existingQuantity = scale(item.getQuantity());
                if (existingQuantity.signum() > 0
                        && (!Objects.equals(item.getInventoryAccount().getId(), command.inventoryAccountId())
                        || !Objects.equals(item.getFund().getId(), command.fundId())
                        || scale(item.getUnitValue()).compareTo(scale(command.unitValue())) != 0))
                {
                    throw new IllegalStateException(
                            "Inventory account, fund, and unit value cannot be changed while quantity is on hand; use governed movements and corrections");
                }
                apply(em, item, command);
                item.setQuantity(existingQuantity);
                item.touchUpdatedAt();
                em.getTransaction().commit();
                return load(itemId);
            }
            catch (RuntimeException ex)
            {
                rollback(em);
                throw ex;
            }
        }
    }

    public InventoryItemView load(long itemId)
    {
        try (EntityManager em = jpa.em())
        {
            InventoryItem item = em.createQuery("""
                    select i from InventoryItem i
                    join fetch i.company
                    join fetch i.inventoryAccount
                    join fetch i.fund
                    where i.id = :id
                    """, InventoryItem.class)
                    .setParameter("id", itemId)
                    .getSingleResult();
            return toView(item);
        }
    }

    public List<InventoryItemView> listItems(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("""
                    select i from InventoryItem i
                    join fetch i.company c
                    join fetch i.inventoryAccount
                    join fetch i.fund
                    where c.code = :companyCode
                    order by i.name, i.id
                    """, InventoryItem.class)
                    .setParameter("companyCode", normalizeCompanyCode(companyCode))
                    .getResultList()
                    .stream()
                    .map(InventoryService::toView)
                    .toList();
        }
    }

    /** Builds a frozen, non-mutating preview for explicit movement confirmation. */
    public MovementPreview previewMovement(long itemId, InventoryMovementCommand command)
    {
        validateMovement(command);
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(
                    em, normalizeCompanyCode(companyCodeSupplier.get()));
            InventoryItem item = require(em, InventoryItem.class, itemId, "Inventory item");
            return buildPreview(
                    em,
                    company,
                    item,
                    command,
                    requirePortableId(transactionPortableIdSupplier.get(),
                            "Inventory transaction portable identity"),
                    requirePortableId(movementPortableIdSupplier.get(),
                            "Inventory movement portable identity"));
        }
    }

    /** Commits the frozen preview, canonical transaction, movement, quantity, and audit atomically. */
    public InventoryMovementView recordMovement(MovementPreview preview, String actor)
    {
        Objects.requireNonNull(preview, "preview");
        String normalizedActor = requireText(actor, "actor");
        String activeCompany = normalizeCompanyCode(companyCodeSupplier.get());
        if (!activeCompany.equals(preview.companyCode()))
        {
            throw new IllegalStateException(
                    "Active company changed after inventory movement preview; reopen the preview");
        }

        try (EntityManager em = jpa.em())
        {
            InventoryMovement existing = movementByPortableId(em, preview.movementPortableId());
            if (existing != null)
            {
                boolean sameTransaction = preview.financial()
                        ? existing.getTransaction() != null
                        && preview.transactionPortableId().equals(existing.getTransaction().getPortableId())
                        : existing.getTransaction() == null;
                if (!preview.companyCode().equals(existing.getInventoryItem().getCompany().getCode())
                        || !preview.inventoryItemId().equals(existing.getInventoryItem().getId())
                        || preview.movementType() != existing.getMovementType()
                        || !preview.movementDate().equals(existing.getMovementDate())
                        || preview.quantityChange().compareTo(scale(existing.getQuantityChange())) != 0
                        || preview.quantityAfter().compareTo(scale(existing.getResultingQuantity())) != 0
                        || !sameTransaction)
                {
                    throw new IllegalStateException(
                            "Inventory movement portable identity is already used by a different operation");
                }
                return toMovementView(existing);
            }

            em.getTransaction().begin();
            try
            {
                CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
                Company company = ownership.requireCompany(em, activeCompany);
                InventoryItem item = em.find(
                        InventoryItem.class, preview.inventoryItemId(), LockModeType.PESSIMISTIC_WRITE);
                if (item == null)
                {
                    throw new IllegalArgumentException("Inventory item not found: " + preview.inventoryItemId());
                }
                MovementPreview refreshed = buildPreview(
                        em,
                        company,
                        item,
                        preview.command(),
                        preview.transactionPortableId(),
                        preview.movementPortableId());
                if (!preview.equals(refreshed))
                {
                    throw new IllegalStateException(
                            "Inventory quantity, value, accounts, fund, or company changed after preview; reopen the preview");
                }
                validatePortableIdentityAvailability(em, preview);

                Txn transaction = null;
                if (preview.financial())
                {
                    transaction = transactionEntryService.enter(
                            em,
                            company,
                            Objects.requireNonNull(preview.transactionCommand(), "transactionCommand"),
                            preview.transactionPortableId(),
                            normalizedActor,
                            "Governed inventory " + preview.movementType().name().toLowerCase());
                }
                movementWriteHook.afterTransactionPersisted(em, item, transaction, preview);

                item.setQuantity(preview.quantityAfter());
                item.touchUpdatedAt();
                InventoryMovement movement = movement(
                        item,
                        preview.movementType(),
                        preview.quantityChange(),
                        preview.quantityAfter(),
                        preview.movementDate(),
                        preview.notes());
                movement.initializePortableIdentity(preview.movementPortableId());
                movement.setTransaction(transaction);
                em.persist(movement);
                em.flush();
                em.persist(inventoryAudit(company, normalizedActor, item, movement, preview));
                em.flush();

                InventoryMovementView result = toMovementView(movement);
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

    /** Compatibility entry point; governed callers should retain and confirm the returned preview. */
    public InventoryMovementView recordMovement(long itemId, InventoryMovementCommand command)
    {
        return recordMovement(previewMovement(itemId, command), "system");
    }

    /** Builds a non-mutating preview for reversing one financial movement and its canonical transaction. */
    public MovementReversalPreview previewMovementReversal(
            long movementId,
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
            InventoryMovement original = requireMovement(em, movementId);
            return buildReversalPreview(
                    em,
                    company,
                    original,
                    reversalDate,
                    normalizedReason,
                    requirePortableId(transactionPortableIdSupplier.get(),
                            "Inventory reversal transaction portable identity"),
                    requirePortableId(movementPortableIdSupplier.get(),
                            "Inventory reversal movement portable identity"));
        }
    }

    /** Atomically reverses the canonical transaction and records the inverse quantity movement. */
    public InventoryMovementView reverseMovement(MovementReversalPreview preview, String actor)
    {
        Objects.requireNonNull(preview, "preview");
        String normalizedActor = requireText(actor, "actor");
        String activeCompany = normalizeCompanyCode(companyCodeSupplier.get());
        if (!activeCompany.equals(preview.companyCode()))
        {
            throw new IllegalStateException(
                    "Active company changed after inventory reversal preview; reopen the preview");
        }
        try (EntityManager em = jpa.em())
        {
            InventoryMovement existing = movementByPortableId(em, preview.reversalMovementPortableId());
            if (existing != null)
            {
                if (!preview.companyCode().equals(existing.getInventoryItem().getCompany().getCode())
                        || !preview.inventoryItemId().equals(existing.getInventoryItem().getId())
                        || existing.getTransaction() == null
                        || !preview.reversalTransactionPortableId().equals(
                        existing.getTransaction().getPortableId())
                        || preview.quantityChange().compareTo(scale(existing.getQuantityChange())) != 0
                        || preview.quantityAfter().compareTo(scale(existing.getResultingQuantity())) != 0)
                {
                    throw new IllegalStateException(
                            "Inventory reversal portable identity is already used by a different operation");
                }
                return toMovementView(existing);
            }

            em.getTransaction().begin();
            try
            {
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, activeCompany);
                InventoryMovement original = requireMovement(em, preview.originalMovementId());
                InventoryItem item = em.find(
                        InventoryItem.class, original.getInventoryItem().getId(), LockModeType.PESSIMISTIC_WRITE);
                Txn originalTransaction = em.find(
                        Txn.class, original.getTransaction().getId(), LockModeType.PESSIMISTIC_WRITE);
                original.setInventoryItem(item);
                original.setTransaction(originalTransaction);
                MovementReversalPreview refreshed = buildReversalPreview(
                        em,
                        company,
                        original,
                        preview.reversalDate(),
                        preview.reason(),
                        preview.reversalTransactionPortableId(),
                        preview.reversalMovementPortableId());
                if (!preview.equals(refreshed))
                {
                    throw new IllegalStateException(
                            "Inventory movement, quantity, transaction, or company changed after reversal preview; reopen the preview");
                }
                validateReversalPortableIdentityAvailability(em, preview);

                Txn reversal = transactionCorrectionService.reverse(
                        em,
                        company,
                        originalTransaction,
                        preview.reversalDate(),
                        normalizedActor,
                        preview.reason(),
                        preview.reversalTransactionPortableId());
                item.setQuantity(preview.quantityAfter());
                item.touchUpdatedAt();

                InventoryMovement correction = new InventoryMovement();
                correction.initializePortableIdentity(preview.reversalMovementPortableId());
                correction.setInventoryItem(item);
                correction.setMovementDate(preview.reversalDate());
                correction.setMovementType(InventoryMovement.MovementType.ADJUSTMENT);
                correction.setQuantityChange(preview.quantityChange());
                correction.setResultingQuantity(preview.quantityAfter());
                correction.setUnitValue(preview.unitValue());
                correction.setTransaction(reversal);
                correction.setNotes("Reversal of movement " + original.getId() + ": " + preview.reason());
                em.persist(correction);
                em.flush();
                em.persist(reversalAudit(company, normalizedActor, item, correction, preview));
                em.flush();

                InventoryMovementView result = toMovementView(correction);
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

    public List<InventoryMovementView> listMovements(String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("""
                    select m from InventoryMovement m
                    join fetch m.inventoryItem i
                    join fetch i.company c
                    left join fetch m.transaction
                    where c.code = :companyCode
                    order by m.movementDate desc, m.id desc
                    """, InventoryMovement.class)
                    .setParameter("companyCode", normalizeCompanyCode(companyCode))
                    .getResultList()
                    .stream()
                    .map(InventoryService::toMovementView)
                    .toList();
        }
    }

    /** Creates an inventory item inside an interchange caller's existing transaction. */
    public InventoryItem createForImport(
            EntityManager em,
            Company company,
            InventoryItemCommand command,
            UUID portableId,
            Instant createdAt,
            Instant updatedAt)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Inventory-item import requires an active caller-owned transaction");
        }
        if (command == null || command.companyCode() == null
                || !company.getCode().equalsIgnoreCase(command.companyCode().trim()))
        {
            throw new IllegalArgumentException("Inventory-item import company does not match the command");
        }
        InventoryItem item = new InventoryItem();
        apply(em, item, command);
        item.initializeImportMetadata(portableId, createdAt, updatedAt);
        em.persist(item);
        return item;
    }

    /** Records source movement history without creating another accounting transaction. */
    public InventoryMovement recordMovementForImport(
            EntityManager em,
            Company company,
            InventoryItem item,
            LocalDate movementDate,
            InventoryMovement.MovementType movementType,
            BigDecimal quantityChange,
            BigDecimal resultingQuantity,
            BigDecimal unitValue,
            Txn transaction,
            String notes,
            UUID portableId,
            Instant createdAt)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(movementType, "movementType");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Inventory-movement import requires an active caller-owned transaction");
        }
        if (movementDate == null)
        {
            throw new IllegalArgumentException("movementDate is required");
        }
        if (quantityChange == null || quantityChange.compareTo(BigDecimal.ZERO) == 0)
        {
            throw new IllegalArgumentException("Inventory movement quantity change must be nonzero");
        }
        if (resultingQuantity == null || resultingQuantity.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new IllegalArgumentException("Inventory movement resulting quantity must be nonnegative");
        }
        if (unitValue == null || unitValue.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new IllegalArgumentException("Inventory movement unit value must be nonnegative");
        }
        CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
        ownership.ensureOwnedBy(em, company, item, "Inventory item");
        if (transaction != null)
        {
            ownership.ensureOwnedBy(em, company, transaction, "Inventory movement transaction");
        }
        InventoryMovement movement = new InventoryMovement();
        movement.setInventoryItem(item);
        movement.setMovementDate(movementDate);
        movement.setMovementType(movementType);
        movement.setQuantityChange(scale(quantityChange));
        movement.setResultingQuantity(scale(resultingQuantity));
        movement.setUnitValue(scale(unitValue));
        movement.setTransaction(transaction);
        movement.setNotes(notes);
        movement.initializeImportMetadata(portableId, createdAt);
        em.persist(movement);
        return movement;
    }

    private MovementReversalPreview buildReversalPreview(
            EntityManager em,
            Company company,
            InventoryMovement original,
            LocalDate reversalDate,
            String reason,
            UUID reversalTransactionPortableId,
            UUID reversalMovementPortableId)
    {
        CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
        InventoryItem item = original.getInventoryItem();
        Txn originalTransaction = original.getTransaction();
        ownership.ensureOwnedBy(em, company, item, "Inventory item");
        if (originalTransaction == null)
        {
            throw new IllegalStateException(
                    "Only a financial inventory movement with a canonical transaction can be reversed here");
        }
        ownership.ensureOwnedBy(em, company, originalTransaction, "Inventory movement transaction");
        if (!"ENTERED".equals(originalTransaction.getStatus()))
        {
            throw new IllegalStateException("Only an entered inventory movement transaction can be reversed");
        }
        Long reversalCount = em.createQuery("""
                select count(m) from InventoryMovement m
                where m.inventoryItem = :item
                  and m.transaction.reversalOf = :transaction
                """, Long.class)
                .setParameter("item", item)
                .setParameter("transaction", originalTransaction)
                .getSingleResult();
        if (reversalCount > 0)
        {
            throw new IllegalStateException("Inventory movement " + original.getId() + " is already reversed");
        }
        requireTransactionOutsideCompletedReconciliation(em, originalTransaction.getId());
        PeriodCloseRangeService.requireOpen(
                em, company.getCode(), reversalDate, "reverse inventory movement");

        BigDecimal current = scale(item.getQuantity());
        BigDecimal change = scale(original.getQuantityChange()).negate();
        BigDecimal resulting = current.add(change).setScale(4, RoundingMode.HALF_UP);
        if (resulting.signum() < 0)
        {
            throw new IllegalStateException(
                    "Reversing inventory movement " + original.getId() + " would make quantity negative");
        }
        BigDecimal unitValue = scale(original.getUnitValue());
        BigDecimal extendedValue = change.abs().multiply(unitValue).setScale(4, RoundingMode.HALF_UP);
        return new MovementReversalPreview(
                company.getCode(),
                original.getId(),
                original.getPortableId(),
                originalTransaction.getId(),
                originalTransaction.getPortableId(),
                item.getId(),
                item.getName(),
                original.getMovementType(),
                original.getMovementDate(),
                reversalDate,
                current,
                change,
                resulting,
                unitValue,
                extendedValue,
                reversalTransactionPortableId,
                reversalMovementPortableId,
                reason);
    }

    private static InventoryMovement requireMovement(EntityManager em, long movementId)
    {
        return em.createQuery("""
                select m from InventoryMovement m
                join fetch m.inventoryItem i
                join fetch i.company
                join fetch i.inventoryAccount
                join fetch i.fund
                left join fetch m.transaction
                where m.id = :id
                """, InventoryMovement.class)
                .setParameter("id", movementId)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Inventory movement not found: " + movementId));
    }

    private static void requireTransactionOutsideCompletedReconciliation(EntityManager em, long transactionId)
    {
        Number legacy = (Number) em.createNativeQuery("""
                select count(*)
                from txn_reconciliation_protection p
                join reconciliation_run r on r.id = p.reconciliation_run_id
                where p.txn_id = ? and r.status = 'COMPLETED'
                """)
                .setParameter(1, transactionId)
                .getSingleResult();
        Number nativeReconciliation = (Number) em.createNativeQuery("""
                select count(*)
                from bank_reconciliation_session s
                join bank_reconciliation_match m on m.session_id = s.id
                join txn_split ts on ts.id = m.txn_split_id
                where ts.txn_id = ? and s.status = 'FINALIZED'
                """)
                .setParameter(1, transactionId)
                .getSingleResult();
        if (legacy.longValue() > 0 || nativeReconciliation.longValue() > 0)
        {
            throw new IllegalStateException(
                    "Inventory movement transaction " + transactionId
                            + " is protected by a completed or finalized reconciliation");
        }
    }

    private static void validateReversalPortableIdentityAvailability(
            EntityManager em,
            MovementReversalPreview preview)
    {
        Long movementCount = em.createQuery(
                        "select count(m) from InventoryMovement m where m.portableId = :portableId", Long.class)
                .setParameter("portableId", preview.reversalMovementPortableId())
                .getSingleResult();
        Long transactionCount = em.createQuery(
                        "select count(t) from Txn t where t.portableId = :portableId", Long.class)
                .setParameter("portableId", preview.reversalTransactionPortableId())
                .getSingleResult();
        if (movementCount > 0 || transactionCount > 0)
        {
            throw new IllegalStateException("Inventory reversal portable identity is already in use");
        }
    }

    private static AuditEvent reversalAudit(
            Company company,
            String actor,
            InventoryItem item,
            InventoryMovement correction,
            MovementReversalPreview preview)
    {
        AuditEvent event = new AuditEvent();
        event.setCompany(company);
        event.setActor(actor);
        event.setActionType("INVENTORY_MOVEMENT_REVERSED");
        event.setEntityType("InventoryMovement");
        event.setEntityId(Long.toString(correction.getId()));
        event.setSummary("Reversed inventory movement " + preview.originalMovementId()
                + " for " + item.getName());
        event.setBeforeValue("quantity=" + preview.quantityBefore()
                + ",originalMovementId=" + preview.originalMovementId());
        event.setAfterValue("quantity=" + preview.quantityAfter()
                + ",reversalTransactionId=" + correction.getTransaction().getId());
        event.setReason(preview.reason());
        return event;
    }

    private MovementPreview buildPreview(
            EntityManager em,
            Company company,
            InventoryItem item,
            InventoryMovementCommand command,
            UUID transactionPortableId,
            UUID movementPortableId)
    {
        validateMovement(command);
        CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
        ownership.ensureOwnedBy(em, company, item, "Inventory item");
        if (!company.isActive())
        {
            throw new IllegalStateException("Company " + company.getCode() + " is inactive");
        }
        if (item.getStatus() != InventoryItem.Status.ACTIVE)
        {
            throw new IllegalStateException("Only active inventory items can receive movement records");
        }

        Account inventoryAccount = item.getInventoryAccount();
        Fund inventoryFund = item.getFund();
        ownership.ensureOwnedBy(em, company, inventoryAccount, "Inventory account");
        ownership.ensureOwnedBy(em, company, inventoryFund, "Inventory fund");
        validateInventoryAccount(inventoryAccount);
        requireUsableAccount(inventoryAccount, "Inventory account", command.movementDate());
        requireUsableFund(inventoryFund, command.movementDate());

        BigDecimal current = scale(item.getQuantity());
        BigDecimal requested = scale(command.quantity());
        BigDecimal change = switch (command.movementType())
        {
            case RECEIPT -> requested;
            case ISSUE -> requested.negate();
            case ADJUSTMENT -> requested.subtract(current);
        };
        if (change.signum() == 0)
        {
            throw new IllegalArgumentException("Inventory adjustment must change the current quantity");
        }
        BigDecimal resulting = current.add(change).setScale(4, RoundingMode.HALF_UP);
        if (resulting.signum() < 0)
        {
            throw new IllegalArgumentException("Inventory movement cannot make quantity negative");
        }

        BigDecimal unitValue = scale(item.getUnitValue());
        BigDecimal extendedValue = change.abs().multiply(unitValue).setScale(4, RoundingMode.HALF_UP);
        boolean financial = unitValue.signum() > 0;
        Account offsetAccount = null;
        TransactionCommand transactionCommand = null;
        if (financial)
        {
            if (extendedValue.signum() == 0)
            {
                throw new IllegalArgumentException(
                        "Inventory movement value rounds to zero at ledger precision; adjust quantity or unit value");
            }
            offsetAccount = require(em, Account.class, command.offsetAccountId(), "Offset account");
            ownership.ensureOwnedBy(em, company, offsetAccount, "Offset account");
            if (Objects.equals(offsetAccount.getId(), inventoryAccount.getId()))
            {
                throw new IllegalArgumentException("Offset account must differ from the inventory account");
            }
            requireUsableAccount(offsetAccount, "Offset account", command.movementDate());
            requireOutsideFinalizedReconciliation(
                    em, company, offsetAccount, command.movementDate(), "record inventory movement");
            transactionCommand = movementTransaction(
                    item, command, change, extendedValue, inventoryAccount, offsetAccount, inventoryFund);
        }
        else
        {
            if (!command.nonfinancialConfirmed())
            {
                throw new IllegalArgumentException(
                        "Zero-value inventory requires explicit nonfinancial movement confirmation");
            }
            if (command.offsetAccountId() != null)
            {
                throw new IllegalArgumentException("A nonfinancial movement must not select an offset account");
            }
        }

        PeriodCloseRangeService.requireOpen(
                em, company.getCode(), command.movementDate(), "record inventory movement");
        return new MovementPreview(
                company.getCode(),
                item.getId(),
                item.getPortableId(),
                item.getName(),
                command,
                command.movementType(),
                command.movementDate(),
                current,
                change,
                resulting,
                unitValue,
                extendedValue,
                financial,
                inventoryAccount.getId(),
                inventoryAccount.getCode(),
                inventoryAccount.getName(),
                offsetAccount == null ? null : offsetAccount.getId(),
                offsetAccount == null ? "" : offsetAccount.getCode(),
                offsetAccount == null ? "" : offsetAccount.getName(),
                inventoryFund.getId(),
                inventoryFund.getCode(),
                inventoryFund.getName(),
                transactionCommand,
                transactionPortableId,
                movementPortableId,
                blankToNull(command.notes()));
    }

    private static TransactionCommand movementTransaction(
            InventoryItem item,
            InventoryMovementCommand command,
            BigDecimal change,
            BigDecimal amount,
            Account inventoryAccount,
            Account offsetAccount,
            Fund fund)
    {
        boolean increase = change.signum() > 0;
        String lineNote = "Inventory " + command.movementType().name().toLowerCase()
                + ": " + item.getName();
        TransactionLineCommand inventoryLine = new TransactionLineCommand(
                inventoryAccount.getId(), fund.getId(), null, null, null,
                increase ? amount : ZERO,
                increase ? ZERO : amount,
                false, lineNote);
        TransactionLineCommand offsetLine = new TransactionLineCommand(
                offsetAccount.getId(), fund.getId(), null, null, null,
                increase ? ZERO : amount,
                increase ? amount : ZERO,
                false, lineNote);
        return new TransactionCommand(
                command.movementDate(),
                null,
                lineNote,
                AccountClassification.isBank(offsetAccount) ? offsetAccount.getId() : null,
                List.of(inventoryLine, offsetLine));
    }

    private static void requireUsableAccount(Account account, String label, LocalDate movementDate)
    {
        if (!account.isActive())
        {
            throw new IllegalStateException(label + " is inactive");
        }
        if (!account.isPosting())
        {
            throw new IllegalStateException(label + " is not a posting account");
        }
        if (account.getEffectiveFrom() != null && movementDate.isBefore(account.getEffectiveFrom()))
        {
            throw new IllegalStateException(label + " is not effective on " + movementDate);
        }
        if (account.getEffectiveTo() != null && movementDate.isAfter(account.getEffectiveTo()))
        {
            throw new IllegalStateException(label + " is not effective on " + movementDate);
        }
    }

    private static void requireUsableFund(Fund fund, LocalDate movementDate)
    {
        if (!fund.isActive())
        {
            throw new IllegalStateException("Inventory fund is inactive");
        }
        if (fund.getEffectiveFrom() != null && movementDate.isBefore(fund.getEffectiveFrom()))
        {
            throw new IllegalStateException("Inventory fund is not effective on " + movementDate);
        }
        if (fund.getEffectiveTo() != null && movementDate.isAfter(fund.getEffectiveTo()))
        {
            throw new IllegalStateException("Inventory fund is not effective on " + movementDate);
        }
    }

    private static void requireOutsideFinalizedReconciliation(
            EntityManager em,
            Company company,
            Account account,
            LocalDate movementDate,
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
                .setParameter(3, movementDate)
                .getSingleResult();
        if (protectedCount.longValue() > 0)
        {
            throw new IllegalStateException(
                    "Cannot " + operation + " because " + movementDate
                            + " is inside a finalized reconciliation for offset account " + account.getCode());
        }
    }

    private static void validatePortableIdentityAvailability(EntityManager em, MovementPreview preview)
    {
        Long movementCount = em.createQuery(
                        "select count(m) from InventoryMovement m where m.portableId = :portableId", Long.class)
                .setParameter("portableId", preview.movementPortableId())
                .getSingleResult();
        if (movementCount > 0)
        {
            throw new IllegalStateException(
                    "Inventory movement portable identity is already in use: " + preview.movementPortableId());
        }
        if (preview.financial())
        {
            Long transactionCount = em.createQuery(
                            "select count(t) from Txn t where t.portableId = :portableId", Long.class)
                    .setParameter("portableId", preview.transactionPortableId())
                    .getSingleResult();
            if (transactionCount > 0)
            {
                throw new IllegalStateException(
                        "Inventory transaction portable identity is already in use: "
                                + preview.transactionPortableId());
            }
        }
    }

    private static InventoryMovement movementByPortableId(EntityManager em, UUID portableId)
    {
        return em.createQuery("""
                select m from InventoryMovement m
                join fetch m.inventoryItem i
                join fetch i.company
                left join fetch m.transaction
                where m.portableId = :portableId
                """, InventoryMovement.class)
                .setParameter("portableId", portableId)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    private static AuditEvent inventoryAudit(
            Company company,
            String actor,
            InventoryItem item,
            InventoryMovement movement,
            MovementPreview preview)
    {
        AuditEvent event = new AuditEvent();
        event.setCompany(company);
        event.setActor(actor);
        event.setActionType("INVENTORY_MOVEMENT_RECORDED");
        event.setEntityType("InventoryMovement");
        event.setEntityId(Long.toString(movement.getId()));
        event.setSummary("Recorded " + preview.movementType().name().toLowerCase()
                + " for inventory item " + item.getName());
        event.setBeforeValue("quantity=" + preview.quantityBefore()
                + ",unitValue=" + preview.unitValue());
        event.setAfterValue("quantity=" + preview.quantityAfter()
                + ",value=" + preview.extendedValue()
                + ",transactionId=" + (movement.getTransaction() == null
                ? "nonfinancial" : movement.getTransaction().getId()));
        event.setReason(preview.notes());
        return event;
    }

    private void apply(EntityManager em, InventoryItem item, InventoryItemCommand command)
    {
        validateCommand(command);
        Company company = em.createQuery("select c from Company c where c.code = :code", Company.class)
                .setParameter("code", normalizeCompanyCode(command.companyCode()))
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + command.companyCode()));
        Account account = require(em, Account.class, command.inventoryAccountId(), "Inventory account");
        Fund fund = require(em, Fund.class, command.fundId(), "Fund");
        CompanyOwnershipService ownership = new CompanyOwnershipService(jpa);
        ownership.ensureOwnedBy(em, company, account, "Inventory account");
        ownership.ensureOwnedBy(em, company, fund, "Inventory fund");
        validateInventoryAccount(account);

        item.setCompany(company);
        item.setInventoryAccount(account);
        item.setFund(fund);
        item.setName(command.name().trim());
        item.setItemType(command.itemType().trim());
        item.setQuantity(scale(command.quantity()));
        item.setUnit(command.unit().trim());
        item.setUnitValue(scale(command.unitValue()));
        item.setAcquisitionDate(command.acquisitionDate());
        item.setCustodian(blankToNull(command.custodian()));
        item.setStorageLocation(blankToNull(command.storageLocation()));
        item.setCondition(command.condition() == null ? InventoryItem.Condition.UNKNOWN : command.condition());
        item.setStatus(command.status() == null ? InventoryItem.Status.ACTIVE : command.status());
        item.setNotes(command.notes());
    }

    private static InventoryMovement movement(InventoryItem item,
                                              InventoryMovement.MovementType type,
                                              BigDecimal quantityChange,
                                              BigDecimal resultingQuantity,
                                              LocalDate movementDate,
                                              String notes)
    {
        InventoryMovement movement = new InventoryMovement();
        movement.setInventoryItem(item);
        movement.setMovementDate(movementDate);
        movement.setMovementType(type);
        movement.setQuantityChange(scale(quantityChange));
        movement.setResultingQuantity(scale(resultingQuantity));
        movement.setUnitValue(scale(item.getUnitValue()));
        movement.setNotes(notes);
        return movement;
    }

    private static void validateCommand(InventoryItemCommand command)
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
            throw new IllegalArgumentException("Item name is required");
        }
        if (command.itemType() == null || command.itemType().isBlank())
        {
            throw new IllegalArgumentException("Item type is required");
        }
        if (command.quantity() == null || command.quantity().compareTo(BigDecimal.ZERO) < 0)
        {
            throw new IllegalArgumentException("Quantity must be nonnegative");
        }
        if (command.unit() == null || command.unit().isBlank())
        {
            throw new IllegalArgumentException("Unit is required");
        }
        if (command.unitValue() == null || command.unitValue().compareTo(BigDecimal.ZERO) < 0)
        {
            throw new IllegalArgumentException("Value must be nonnegative");
        }
        if (command.acquisitionDate() == null)
        {
            throw new IllegalArgumentException("Acquisition date is required");
        }
    }

    private void requireActiveCompanyCommand(InventoryItemCommand command)
    {
        String activeCompany = normalizeCompanyCode(companyCodeSupplier.get());
        if (!activeCompany.equals(normalizeCompanyCode(command.companyCode())))
        {
            throw new IllegalStateException(
                    "Inventory command company " + normalizeCompanyCode(command.companyCode())
                            + " does not match active company " + activeCompany);
        }
    }

    private static void validateMovement(InventoryMovementCommand command)
    {
        if (command == null)
        {
            throw new IllegalArgumentException("movement command is required");
        }
        if (command.movementType() == null)
        {
            throw new IllegalArgumentException("Movement type is required");
        }
        if (command.quantity() == null)
        {
            throw new IllegalArgumentException("Movement quantity is required");
        }
        if (command.movementType() == InventoryMovement.MovementType.ADJUSTMENT
                && command.quantity().compareTo(BigDecimal.ZERO) < 0)
        {
            throw new IllegalArgumentException("Adjusted inventory quantity must be nonnegative");
        }
        if (command.movementType() != InventoryMovement.MovementType.ADJUSTMENT
                && command.quantity().compareTo(BigDecimal.ZERO) <= 0)
        {
            throw new IllegalArgumentException("Movement quantity must be greater than zero");
        }
        if (command.movementDate() == null)
        {
            throw new IllegalArgumentException("Movement date is required");
        }
    }

    private static void validateInventoryAccount(Account account)
    {
        if (account.getAccountType() != AccountType.ASSET || account.getSubtype() != AccountSubtype.INVENTORY)
        {
            throw new IllegalArgumentException("Inventory account must be an ASSET/INVENTORY account");
        }
    }

    private static InventoryItemView toView(InventoryItem item)
    {
        BigDecimal quantity = scale(item.getQuantity());
        BigDecimal unitValue = scale(item.getUnitValue());
        return new InventoryItemView(
                item.getId(),
                item.getCompany().getCode(),
                item.getInventoryAccount().getId(),
                item.getInventoryAccount().getCode(),
                item.getInventoryAccount().getName(),
                item.getFund().getId(),
                item.getFund().getCode(),
                item.getFund().getName(),
                item.getName(),
                item.getItemType(),
                quantity,
                item.getUnit(),
                unitValue,
                quantity.multiply(unitValue).setScale(4, RoundingMode.HALF_UP),
                item.getAcquisitionDate(),
                item.getCustodian() == null ? "" : item.getCustodian(),
                item.getStorageLocation() == null ? "" : item.getStorageLocation(),
                item.getCondition(),
                item.getStatus(),
                item.getNotes() == null ? "" : item.getNotes());
    }

    private static InventoryMovementView toMovementView(InventoryMovement movement)
    {
        return new InventoryMovementView(
                movement.getId(),
                movement.getInventoryItem().getId(),
                movement.getInventoryItem().getName(),
                movement.getMovementDate(),
                movement.getMovementType(),
                scale(movement.getQuantityChange()),
                scale(movement.getResultingQuantity()),
                scale(movement.getUnitValue()),
                movement.getTransaction() == null ? null : movement.getTransaction().getId(),
                movement.getNotes() == null ? "" : movement.getNotes());
    }

    private static String normalizeCompanyCode(String companyCode)
    {
        return companyCode == null ? "" : companyCode.trim().toUpperCase();
    }

    private static String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static BigDecimal scale(BigDecimal value)
    {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }

    private static UUID requirePortableId(UUID value, String label)
    {
        if (value == null)
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value;
    }

    private static String requireText(String value, String label)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
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

    /** Frozen service-owned preview retained through explicit user confirmation. */
    public record MovementPreview(
            String companyCode,
            Long inventoryItemId,
            UUID inventoryItemPortableId,
            String inventoryItemName,
            InventoryMovementCommand command,
            InventoryMovement.MovementType movementType,
            LocalDate movementDate,
            BigDecimal quantityBefore,
            BigDecimal quantityChange,
            BigDecimal quantityAfter,
            BigDecimal unitValue,
            BigDecimal extendedValue,
            boolean financial,
            Long inventoryAccountId,
            String inventoryAccountCode,
            String inventoryAccountName,
            Long offsetAccountId,
            String offsetAccountCode,
            String offsetAccountName,
            Long fundId,
            String fundCode,
            String fundName,
            TransactionCommand transactionCommand,
            UUID transactionPortableId,
            UUID movementPortableId,
            String notes)
    {
    }

    /** Frozen preview for an immutable financial-movement correction. */
    public record MovementReversalPreview(
            String companyCode,
            Long originalMovementId,
            UUID originalMovementPortableId,
            Long originalTransactionId,
            UUID originalTransactionPortableId,
            Long inventoryItemId,
            String inventoryItemName,
            InventoryMovement.MovementType originalMovementType,
            LocalDate originalMovementDate,
            LocalDate reversalDate,
            BigDecimal quantityBefore,
            BigDecimal quantityChange,
            BigDecimal quantityAfter,
            BigDecimal unitValue,
            BigDecimal extendedValue,
            UUID reversalTransactionPortableId,
            UUID reversalMovementPortableId,
            String reason)
    {
    }
}
