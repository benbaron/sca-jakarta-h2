package org.nonprofitbookkeeping.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.Account;
import org.nonprofitbookkeeping.model.AccountSubtype;
import org.nonprofitbookkeeping.model.AccountType;
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

/** Application service for H2-backed inventory items and movement history. */
@ApplicationScoped
public class InventoryService
{
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final Jpa jpa;

    @Inject
    public InventoryService(Jpa jpa)
    {
        this.jpa = Objects.requireNonNull(jpa, "jpa");
    }

    public InventoryItemView create(InventoryItemCommand command)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
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
                InventoryItem item = require(em, InventoryItem.class, itemId, "Inventory item");
                BigDecimal existingQuantity = scale(item.getQuantity());
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

    public InventoryMovementView recordMovement(long itemId, InventoryMovementCommand command)
    {
        validateMovement(command);
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                InventoryItem item = require(em, InventoryItem.class, itemId, "Inventory item");
                if (item.getStatus() != InventoryItem.Status.ACTIVE)
                {
                    throw new IllegalStateException("Only active inventory items can receive movement records");
                }
                BigDecimal current = scale(item.getQuantity());
                BigDecimal requested = scale(command.quantity());
                BigDecimal change = switch (command.movementType())
                {
                    case RECEIPT -> requested;
                    case ISSUE -> requested.negate();
                    case ADJUSTMENT -> requested.subtract(current);
                };
                BigDecimal resulting = current.add(change).setScale(4, RoundingMode.HALF_UP);
                if (resulting.compareTo(BigDecimal.ZERO) < 0)
                {
                    throw new IllegalArgumentException("Inventory movement cannot make quantity negative");
                }
                item.setQuantity(resulting);
                item.touchUpdatedAt();
                InventoryMovement movement = movement(
                        item,
                        command.movementType(),
                        change,
                        resulting,
                        command.movementDate(),
                        command.notes());
                em.persist(movement);
                em.getTransaction().commit();
                return toMovementView(movement);
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
        if (command.quantity() == null || command.quantity().compareTo(BigDecimal.ZERO) <= 0)
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
