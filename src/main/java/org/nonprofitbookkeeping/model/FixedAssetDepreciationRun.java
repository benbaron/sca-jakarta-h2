package org.nonprofitbookkeeping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** A completed depreciation run tied to the canonical ledger transaction it created. */
@Entity
@Table(name = "fixed_asset_depreciation_run",
        uniqueConstraints = @UniqueConstraint(name = "uq_fixed_asset_dep_run_period", columnNames = {"fixed_asset_id", "run_date"}),
        indexes = {
                @Index(name = "ix_fixed_asset_dep_run_asset", columnList = "fixed_asset_id"),
                @Index(name = "ix_fixed_asset_dep_run_txn", columnList = "transaction_id")
        })
public class FixedAssetDepreciationRun
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portable_id", nullable = false, unique = true, updatable = false)
    private UUID portableId = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fixed_asset_id", nullable = false)
    private FixedAsset fixedAsset;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "depreciation_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal depreciationAmount = BigDecimal.ZERO;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Txn transaction;

    @Lob
    @Column
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public UUID getPortableId() { return portableId; }
    public FixedAsset getFixedAsset() { return fixedAsset; }
    public void setFixedAsset(FixedAsset fixedAsset) { this.fixedAsset = fixedAsset; }
    public LocalDate getRunDate() { return runDate; }
    public void setRunDate(LocalDate runDate) { this.runDate = runDate; }
    public BigDecimal getDepreciationAmount() { return depreciationAmount; }
    public void setDepreciationAmount(BigDecimal depreciationAmount) { this.depreciationAmount = depreciationAmount; }
    public Txn getTransaction() { return transaction; }
    public void setTransaction(Txn transaction) { this.transaction = transaction; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }

    /** Initializes the immutable portable identity before this run is persisted. */
    public void initializePortableIdentity(UUID portableId)
    {
        if (id != null)
        {
            throw new IllegalStateException("Depreciation-run portable identity must be initialized before persistence");
        }
        this.portableId = Objects.requireNonNull(portableId, "portableId");
    }

    /** Initializes immutable source metadata before a governed interchange import persists this run. */
    public void initializeImportMetadata(UUID portableId, Instant createdAt)
    {
        initializePortableIdentity(portableId);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
}
