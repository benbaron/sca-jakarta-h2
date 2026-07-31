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

/** H2-backed fixed asset register record. */
@Entity
@Table(name = "fixed_asset",
        indexes = {
                @Index(name = "ix_fixed_asset_company", columnList = "company_id"),
                @Index(name = "ix_fixed_asset_account", columnList = "asset_account_id"),
                @Index(name = "ix_fixed_asset_status", columnList = "status")
        })
public class FixedAsset
{
    public enum DepreciationMethod
    {
        STRAIGHT_LINE
    }

    public enum Status
    {
        ACTIVE,
        DISPOSED,
        INACTIVE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portable_id", nullable = false, unique = true, updatable = false)
    private UUID portableId = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_account_id", nullable = false)
    private Account assetAccount;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "accumulated_depreciation_account_id", nullable = false)
    private Account accumulatedDepreciationAccount;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "depreciation_expense_account_id", nullable = false)
    private Account depreciationExpenseAccount;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "acquisition_date", nullable = false)
    private LocalDate acquisitionDate;

    @Column(name = "acquisition_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal acquisitionCost = BigDecimal.ZERO;

    @Column(name = "salvage_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal salvageValue = BigDecimal.ZERO;

    @Column(name = "useful_life_months", nullable = false)
    private int usefulLifeMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "depreciation_method", nullable = false, length = 40)
    private DepreciationMethod depreciationMethod = DepreciationMethod.STRAIGHT_LINE;

    @Column(name = "opening_accumulated_depreciation", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingAccumulatedDepreciation = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Status status = Status.ACTIVE;

    @Lob
    @Column
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public UUID getPortableId() { return portableId; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Account getAssetAccount() { return assetAccount; }
    public void setAssetAccount(Account assetAccount) { this.assetAccount = assetAccount; }
    public Account getAccumulatedDepreciationAccount() { return accumulatedDepreciationAccount; }
    public void setAccumulatedDepreciationAccount(Account accumulatedDepreciationAccount) { this.accumulatedDepreciationAccount = accumulatedDepreciationAccount; }
    public Account getDepreciationExpenseAccount() { return depreciationExpenseAccount; }
    public void setDepreciationExpenseAccount(Account depreciationExpenseAccount) { this.depreciationExpenseAccount = depreciationExpenseAccount; }
    public Fund getFund() { return fund; }
    public void setFund(Fund fund) { this.fund = fund; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDate getAcquisitionDate() { return acquisitionDate; }
    public void setAcquisitionDate(LocalDate acquisitionDate) { this.acquisitionDate = acquisitionDate; }
    public BigDecimal getAcquisitionCost() { return acquisitionCost; }
    public void setAcquisitionCost(BigDecimal acquisitionCost) { this.acquisitionCost = acquisitionCost; }
    public BigDecimal getSalvageValue() { return salvageValue; }
    public void setSalvageValue(BigDecimal salvageValue) { this.salvageValue = salvageValue; }
    public int getUsefulLifeMonths() { return usefulLifeMonths; }
    public void setUsefulLifeMonths(int usefulLifeMonths) { this.usefulLifeMonths = usefulLifeMonths; }
    public DepreciationMethod getDepreciationMethod() { return depreciationMethod; }
    public void setDepreciationMethod(DepreciationMethod depreciationMethod) { this.depreciationMethod = depreciationMethod; }
    public BigDecimal getOpeningAccumulatedDepreciation() { return openingAccumulatedDepreciation; }
    public void setOpeningAccumulatedDepreciation(BigDecimal openingAccumulatedDepreciation) { this.openingAccumulatedDepreciation = openingAccumulatedDepreciation; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touchUpdatedAt() { this.updatedAt = Instant.now(); }

    /** Initializes immutable source metadata before a governed interchange import persists this asset. */
    public void initializeImportMetadata(UUID portableId, Instant createdAt, Instant updatedAt)
    {
        if (id != null)
        {
            throw new IllegalStateException("Fixed asset import metadata must be initialized before persistence");
        }
        this.portableId = Objects.requireNonNull(portableId, "portableId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
