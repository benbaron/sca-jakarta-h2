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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

/** Durable metadata for one reviewed bank import batch. */
@Entity
@Table(name = "bank_import_batch",
       indexes = {
           @Index(name = "ix_bank_import_batch_company", columnList = "company_id"),
           @Index(name = "ix_bank_import_batch_bank_account", columnList = "bank_account_id"),
           @Index(name = "ix_bank_import_batch_status", columnList = "status")
       })
public class BankImportBatch
{
    private static final int MAX_SOURCE_NAME_LENGTH = 260;

    public enum SourceFormat { OFX, QFX, QIF, CSV, SCLX, OTHER }
    public enum Status { IMPORTED, PARTIALLY_ACCEPTED, ACCEPTED, REJECTED, FAILED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portable_id", nullable = false, unique = true, updatable = false)
    private UUID portableId = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id")
    private CompanyBankAccount bankAccount;

    @Column(name = "source_name", nullable = false, length = MAX_SOURCE_NAME_LENGTH)
    private String sourceName;

    @Column(name = "source_external_id", length = 200)
    private String sourceExternalId;

    @Column(name = "source_path", length = 1000)
    private String sourcePath;

    @Column(name = "source_hash", length = 128)
    private String sourceHash;

    @Column(name = "source_variant", length = 30)
    private String sourceVariant;

    @Column(name = "source_version", length = 20)
    private String sourceVersion;

    @Column(name = "source_encoding", length = 20)
    private String sourceEncoding;

    @Column(name = "source_institution_id", length = 120)
    private String sourceInstitutionId;

    @Column(name = "source_bank_id", length = 80)
    private String sourceBankId;

    @Column(name = "source_account_id", length = 160)
    private String sourceAccountId;

    @Column(name = "source_account_type", length = 80)
    private String sourceAccountType;

    @Column(length = 3)
    private String currency;

    @Column(name = "statement_start_date")
    private LocalDate statementStartDate;

    @Column(name = "statement_end_date")
    private LocalDate statementEndDate;

    @Column(name = "ledger_balance", precision = 19, scale = 4)
    private BigDecimal ledgerBalance;

    @Column(name = "available_balance", precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(name = "account_match_status", length = 30)
    private String accountMatchStatus;

    @Column(name = "account_identity_confirmed", nullable = false)
    private boolean accountIdentityConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_format", nullable = false, length = 20)
    private SourceFormat sourceFormat = SourceFormat.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.IMPORTED;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "imported_by_user_id")
    private AppUser importedByUser;

    @Column(name = "total_line_count", nullable = false)
    private int totalLineCount;

    @Column(name = "accepted_line_count", nullable = false)
    private int acceptedLineCount;

    @Column(name = "rejected_line_count", nullable = false)
    private int rejectedLineCount;

    @Column(name = "issue_count", nullable = false)
    private int issueCount;

    @Column(length = 1000)
    private String notes;

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
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getSourceExternalId() { return sourceExternalId; }
    public void setSourceExternalId(String sourceExternalId) { this.sourceExternalId = sourceExternalId; }
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }
    public String getSourceVariant() { return sourceVariant; }
    public void setSourceVariant(String sourceVariant) { this.sourceVariant = sourceVariant; }
    public String getSourceVersion() { return sourceVersion; }
    public void setSourceVersion(String sourceVersion) { this.sourceVersion = sourceVersion; }
    public String getSourceEncoding() { return sourceEncoding; }
    public void setSourceEncoding(String sourceEncoding) { this.sourceEncoding = sourceEncoding; }
    public String getSourceInstitutionId() { return sourceInstitutionId; }
    public void setSourceInstitutionId(String sourceInstitutionId) { this.sourceInstitutionId = sourceInstitutionId; }
    public String getSourceBankId() { return sourceBankId; }
    public void setSourceBankId(String sourceBankId) { this.sourceBankId = sourceBankId; }
    public String getSourceAccountId() { return sourceAccountId; }
    public void setSourceAccountId(String sourceAccountId) { this.sourceAccountId = sourceAccountId; }
    public String getSourceAccountType() { return sourceAccountType; }
    public void setSourceAccountType(String sourceAccountType) { this.sourceAccountType = sourceAccountType; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public LocalDate getStatementStartDate() { return statementStartDate; }
    public void setStatementStartDate(LocalDate statementStartDate) { this.statementStartDate = statementStartDate; }
    public LocalDate getStatementEndDate() { return statementEndDate; }
    public void setStatementEndDate(LocalDate statementEndDate) { this.statementEndDate = statementEndDate; }
    public BigDecimal getLedgerBalance() { return ledgerBalance; }
    public void setLedgerBalance(BigDecimal ledgerBalance) { this.ledgerBalance = ledgerBalance; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public void setAvailableBalance(BigDecimal availableBalance) { this.availableBalance = availableBalance; }
    public String getAccountMatchStatus() { return accountMatchStatus; }
    public void setAccountMatchStatus(String accountMatchStatus) { this.accountMatchStatus = accountMatchStatus; }
    public boolean isAccountIdentityConfirmed() { return accountIdentityConfirmed; }
    public void setAccountIdentityConfirmed(boolean accountIdentityConfirmed) { this.accountIdentityConfirmed = accountIdentityConfirmed; }
    public SourceFormat getSourceFormat() { return sourceFormat; }
    public void setSourceFormat(SourceFormat sourceFormat) { this.sourceFormat = sourceFormat; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getImportedAt() { return importedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public AppUser getImportedByUser() { return importedByUser; }
    public void setImportedByUser(AppUser importedByUser) { this.importedByUser = importedByUser; }
    public int getTotalLineCount() { return totalLineCount; }
    public void setTotalLineCount(int totalLineCount) { this.totalLineCount = totalLineCount; }
    public int getAcceptedLineCount() { return acceptedLineCount; }
    public void setAcceptedLineCount(int acceptedLineCount) { this.acceptedLineCount = acceptedLineCount; }
    public int getRejectedLineCount() { return rejectedLineCount; }
    public void setRejectedLineCount(int rejectedLineCount) { this.rejectedLineCount = rejectedLineCount; }
    public int getIssueCount() { return issueCount; }
    public void setIssueCount(int issueCount) { this.issueCount = issueCount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touchUpdatedAt() { this.updatedAt = Instant.now(); }

    /** Initializes immutable source identity and factual import time before persistence. */
    public void initializeImportMetadata(UUID portableId, Instant importedAt)
    {
        if (id != null)
        {
            throw new IllegalStateException("Bank-import-batch metadata must be initialized before persistence");
        }
        this.portableId = java.util.Objects.requireNonNull(portableId, "portableId");
        this.importedAt = java.util.Objects.requireNonNull(importedAt, "importedAt");
        this.createdAt = importedAt;
        this.updatedAt = importedAt;
    }
}
