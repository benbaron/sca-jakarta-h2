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

/** Immutable fixed-asset sale, retirement, or impairment fact linked to canonical accounting. */
@Entity
@Table(name = "fixed_asset_lifecycle_event",
        indexes = {
                @Index(name = "ix_fixed_asset_lifecycle_asset", columnList = "fixed_asset_id,event_date"),
                @Index(name = "ix_fixed_asset_lifecycle_txn", columnList = "transaction_id"),
                @Index(name = "ix_fixed_asset_lifecycle_reversal", columnList = "reversal_transaction_id")
        })
public class FixedAssetLifecycleEvent
{
    public enum EventType
    {
        SALE,
        RETIREMENT,
        IMPAIRMENT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portable_id", nullable = false, unique = true, updatable = false)
    private UUID portableId = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fixed_asset_id", nullable = false)
    private FixedAsset fixedAsset;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 24)
    private EventType eventType;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "acquisition_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal acquisitionCost = BigDecimal.ZERO;

    @Column(name = "accumulated_depreciation", nullable = false, precision = 19, scale = 4)
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;

    @Column(name = "accumulated_impairment_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal accumulatedImpairmentBefore = BigDecimal.ZERO;

    @Column(name = "carrying_amount_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal carryingAmountBefore = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal proceeds = BigDecimal.ZERO;

    @Column(name = "impairment_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal impairmentAmount = BigDecimal.ZERO;

    @Column(name = "gain_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal gainAmount = BigDecimal.ZERO;

    @Column(name = "loss_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal lossAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proceeds_account_id")
    private Account proceedsAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gain_account_id")
    private Account gainAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loss_account_id")
    private Account lossAccount;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private Txn transaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_transaction_id", unique = true)
    private Txn reversalTransaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_status_before", nullable = false, length = 40)
    private FixedAsset.Status assetStatusBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_status_after", nullable = false, length = 40)
    private FixedAsset.Status assetStatusAfter;

    @Lob
    @Column
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "reversed_at")
    private Instant reversedAt;

    public Long getId() { return id; }
    public UUID getPortableId() { return portableId; }
    public FixedAsset getFixedAsset() { return fixedAsset; }
    public void setFixedAsset(FixedAsset fixedAsset) { this.fixedAsset = fixedAsset; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }
    public BigDecimal getAcquisitionCost() { return acquisitionCost; }
    public void setAcquisitionCost(BigDecimal acquisitionCost) { this.acquisitionCost = acquisitionCost; }
    public BigDecimal getAccumulatedDepreciation() { return accumulatedDepreciation; }
    public void setAccumulatedDepreciation(BigDecimal accumulatedDepreciation) { this.accumulatedDepreciation = accumulatedDepreciation; }
    public BigDecimal getAccumulatedImpairmentBefore() { return accumulatedImpairmentBefore; }
    public void setAccumulatedImpairmentBefore(BigDecimal value) { this.accumulatedImpairmentBefore = value; }
    public BigDecimal getCarryingAmountBefore() { return carryingAmountBefore; }
    public void setCarryingAmountBefore(BigDecimal carryingAmountBefore) { this.carryingAmountBefore = carryingAmountBefore; }
    public BigDecimal getProceeds() { return proceeds; }
    public void setProceeds(BigDecimal proceeds) { this.proceeds = proceeds; }
    public BigDecimal getImpairmentAmount() { return impairmentAmount; }
    public void setImpairmentAmount(BigDecimal impairmentAmount) { this.impairmentAmount = impairmentAmount; }
    public BigDecimal getGainAmount() { return gainAmount; }
    public void setGainAmount(BigDecimal gainAmount) { this.gainAmount = gainAmount; }
    public BigDecimal getLossAmount() { return lossAmount; }
    public void setLossAmount(BigDecimal lossAmount) { this.lossAmount = lossAmount; }
    public Account getProceedsAccount() { return proceedsAccount; }
    public void setProceedsAccount(Account proceedsAccount) { this.proceedsAccount = proceedsAccount; }
    public Account getGainAccount() { return gainAccount; }
    public void setGainAccount(Account gainAccount) { this.gainAccount = gainAccount; }
    public Account getLossAccount() { return lossAccount; }
    public void setLossAccount(Account lossAccount) { this.lossAccount = lossAccount; }
    public Txn getTransaction() { return transaction; }
    public void setTransaction(Txn transaction) { this.transaction = transaction; }
    public Txn getReversalTransaction() { return reversalTransaction; }
    public FixedAsset.Status getAssetStatusBefore() { return assetStatusBefore; }
    public void setAssetStatusBefore(FixedAsset.Status value) { this.assetStatusBefore = value; }
    public FixedAsset.Status getAssetStatusAfter() { return assetStatusAfter; }
    public void setAssetStatusAfter(FixedAsset.Status value) { this.assetStatusAfter = value; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReversedAt() { return reversedAt; }
    public boolean isReversed() { return reversalTransaction != null; }

    /** Initializes the immutable portable identity before persistence. */
    public void initializePortableIdentity(UUID value)
    {
        if (id != null)
        {
            throw new IllegalStateException("Lifecycle-event portable identity must be initialized before persistence");
        }
        portableId = Objects.requireNonNull(value, "portableId");
    }

    /** Links the canonical reversal while preserving the original lifecycle fact. */
    public void markReversed(Txn reversal, Instant occurredAt)
    {
        if (reversalTransaction != null)
        {
            throw new IllegalStateException("Fixed-asset lifecycle event is already reversed");
        }
        reversalTransaction = Objects.requireNonNull(reversal, "reversal");
        reversedAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
