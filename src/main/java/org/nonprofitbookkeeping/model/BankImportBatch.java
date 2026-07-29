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

    @Column(name = "source_name", nullable = false, length = 260)
    private String sourceName;

    @Column(name = "source_path", length = 1000)
    private String sourcePath;

    @Column(name = "source_hash", length = 128)
    private String sourceHash;

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
    public String getSourcePath() { return sourcePath; }
    public void setSourcePath(String sourcePath) { this.sourcePath = sourcePath; }
    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }
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
}
