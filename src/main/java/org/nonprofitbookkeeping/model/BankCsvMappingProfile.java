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

import java.time.Instant;
import java.util.UUID;

/** Durable company-owned mapping rules for one source bank CSV shape. */
@Entity
@Table(name = "bank_csv_mapping_profile",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_bank_csv_profile_portable", columnNames = "portable_id"),
           @UniqueConstraint(name = "uq_bank_csv_profile_name_version",
                   columnNames = {"company_id", "bank_account_id", "profile_name", "profile_version"})
       },
       indexes = @Index(name = "ix_bank_csv_profile_company_account_active",
               columnList = "company_id,bank_account_id,is_active"))
public class BankCsvMappingProfile
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portable_id", nullable = false, unique = true, updatable = false)
    private UUID portableId = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private CompanyBankAccount bankAccount;

    @Column(name = "profile_name", nullable = false, length = 160)
    private String profileName;

    @Column(name = "profile_version", nullable = false, length = 20)
    private String profileVersion;

    @Column(nullable = false, length = 10)
    private String delimiter;

    @Column(name = "source_encoding", nullable = false, length = 20)
    private String sourceEncoding;

    @Column(name = "amount_mode", nullable = false, length = 30)
    private String amountMode;

    @Column(name = "fixed_currency", length = 3)
    private String fixedCurrency;

    @Column(name = "fixed_account_id", length = 160)
    private String fixedAccountId;

    @Lob
    @Column(name = "mapping_json", nullable = false)
    private String mappingJson;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public UUID getPortableId() { return portableId; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public CompanyBankAccount getBankAccount() { return bankAccount; }
    public void setBankAccount(CompanyBankAccount bankAccount) { this.bankAccount = bankAccount; }
    public String getProfileName() { return profileName; }
    public void setProfileName(String profileName) { this.profileName = profileName; }
    public String getProfileVersion() { return profileVersion; }
    public void setProfileVersion(String profileVersion) { this.profileVersion = profileVersion; }
    public String getDelimiter() { return delimiter; }
    public void setDelimiter(String delimiter) { this.delimiter = delimiter; }
    public String getSourceEncoding() { return sourceEncoding; }
    public void setSourceEncoding(String sourceEncoding) { this.sourceEncoding = sourceEncoding; }
    public String getAmountMode() { return amountMode; }
    public void setAmountMode(String amountMode) { this.amountMode = amountMode; }
    public String getFixedCurrency() { return fixedCurrency; }
    public void setFixedCurrency(String fixedCurrency) { this.fixedCurrency = fixedCurrency; }
    public String getFixedAccountId() { return fixedAccountId; }
    public void setFixedAccountId(String fixedAccountId) { this.fixedAccountId = fixedAccountId; }
    public String getMappingJson() { return mappingJson; }
    public void setMappingJson(String mappingJson) { this.mappingJson = mappingJson; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touchUpdatedAt() { updatedAt = Instant.now(); }
}
