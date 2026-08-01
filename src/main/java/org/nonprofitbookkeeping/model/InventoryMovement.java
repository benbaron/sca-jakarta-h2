package org.nonprofitbookkeeping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** H2-backed inventory movement record. */
@Entity
@Table(name = "inventory_movement",
        indexes = {
                @Index(name = "ix_inventory_movement_item", columnList = "inventory_item_id"),
                @Index(name = "ix_inventory_movement_date", columnList = "movement_date"),
                @Index(name = "ix_inventory_movement_txn", columnList = "transaction_id")
        })
public class InventoryMovement
{
    public enum MovementType
    {
        RECEIPT,
        ISSUE,
        ADJUSTMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portable_id", nullable = false, unique = true, updatable = false)
    private UUID portableId = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 40)
    private MovementType movementType;

    @Column(name = "quantity_change", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityChange = BigDecimal.ZERO;

    @Column(name = "resulting_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal resultingQuantity = BigDecimal.ZERO;

    @Column(name = "unit_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitValue = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Txn transaction;

    @Lob
    @Column
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public UUID getPortableId() { return portableId; }
    public InventoryItem getInventoryItem() { return inventoryItem; }
    public void setInventoryItem(InventoryItem inventoryItem) { this.inventoryItem = inventoryItem; }
    public LocalDate getMovementDate() { return movementDate; }
    public void setMovementDate(LocalDate movementDate) { this.movementDate = movementDate; }
    public MovementType getMovementType() { return movementType; }
    public void setMovementType(MovementType movementType) { this.movementType = movementType; }
    public BigDecimal getQuantityChange() { return quantityChange; }
    public void setQuantityChange(BigDecimal quantityChange) { this.quantityChange = quantityChange; }
    public BigDecimal getResultingQuantity() { return resultingQuantity; }
    public void setResultingQuantity(BigDecimal resultingQuantity) { this.resultingQuantity = resultingQuantity; }
    public BigDecimal getUnitValue() { return unitValue; }
    public void setUnitValue(BigDecimal unitValue) { this.unitValue = unitValue; }
    public Txn getTransaction() { return transaction; }
    public void setTransaction(Txn transaction) { this.transaction = transaction; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }

    /** Initializes immutable source metadata before a governed interchange import persists this movement. */
    public void initializeImportMetadata(UUID portableId, Instant createdAt)
    {
        if (id != null)
        {
            throw new IllegalStateException("Inventory-movement import metadata must be initialized before persistence");
        }
        this.portableId = Objects.requireNonNull(portableId, "portableId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
}
