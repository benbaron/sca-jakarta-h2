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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Durable reviewed bank statement line imported from an external source. */
@Entity
@Table(name = "bank_statement_line",
       uniqueConstraints = {
           @UniqueConstraint(name = "uq_bank_statement_line_batch_row", columnNames = {"batch_id", "source_row_number"}),
           @UniqueConstraint(name = "uq_bank_statement_line_batch_fingerprint", columnNames = {"batch_id", "deterministic_fingerprint"})
       },
       indexes = {
           @Index(name = "ix_bank_statement_line_batch", columnList = "batch_id"),
           @Index(name = "ix_bank_statement_line_company_date", columnList = "company_id, transaction_date"),
           @Index(name = "ix_bank_statement_line_status", columnList = "status")
       })
public class BankStatementLine
{
    public enum Status { IMPORTED, ACCEPTED, REJECTED, MATCHED, DUPLICATE, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portable_id", nullable = false, unique = true, updatable = false)
    private UUID portableId = UUID.randomUUID();

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private BankImportBatch batch;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id")
    private CompanyBankAccount bankAccount;

    @Column(name = "source_row_number", nullable = false)
    private int sourceRowNumber;

    @Column(name = "source_transaction_id", length = 160)
    private String sourceTransactionId;

    @Column(name = "deterministic_fingerprint", nullable = false, length = 128)
    private String deterministicFingerprint;

    @Column(name = "statement_account_identifier", length = 160)
    private String statementAccountIdentifier;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "posted_date")
    private LocalDate postedDate;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "transaction_type", length = 40)
    private String transactionType;

    @Column(length = 260)
    private String name;

    @Column(length = 1000)
    private String memo;

    @Column(name = "check_number", length = 80)
    private String checkNumber;

    @Column(length = 160)
    private String reference;

    @Column(length = 3)
    private String currency;

    @Column(name = "correction_action", length = 20)
    private String correctionAction;

    @Column(name = "corrected_source_transaction_id", length = 160)
    private String correctedSourceTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.IMPORTED;

    @Column(name = "disposition_note", length = 1000)
    private String dispositionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_txn_id")
    private Txn acceptedTransaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_txn_id")
    private Txn matchedTransaction;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public UUID getPortableId() { return portableId; }
    public BankImportBatch getBatch() { return batch; }
    public void setBatch(BankImportBatch batch) { this.batch = batch; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public CompanyBankAccount getBankAccount() { return bankAccount; }
    public void setBankAccount(CompanyBankAccount bankAccount) { this.bankAccount = bankAccount; }
    public int getSourceRowNumber() { return sourceRowNumber; }
    public void setSourceRowNumber(int sourceRowNumber) { this.sourceRowNumber = sourceRowNumber; }
    public String getSourceTransactionId() { return sourceTransactionId; }
    public void setSourceTransactionId(String sourceTransactionId) { this.sourceTransactionId = sourceTransactionId; }
    public String getDeterministicFingerprint() { return deterministicFingerprint; }
    public void setDeterministicFingerprint(String deterministicFingerprint) { this.deterministicFingerprint = deterministicFingerprint; }
    public String getStatementAccountIdentifier() { return statementAccountIdentifier; }
    public void setStatementAccountIdentifier(String statementAccountIdentifier) { this.statementAccountIdentifier = statementAccountIdentifier; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public LocalDate getPostedDate() { return postedDate; }
    public void setPostedDate(LocalDate postedDate) { this.postedDate = postedDate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getCheckNumber() { return checkNumber; }
    public void setCheckNumber(String checkNumber) { this.checkNumber = checkNumber; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getCorrectionAction() { return correctionAction; }
    public void setCorrectionAction(String correctionAction) { this.correctionAction = correctionAction; }
    public String getCorrectedSourceTransactionId() { return correctedSourceTransactionId; }
    public void setCorrectedSourceTransactionId(String correctedSourceTransactionId) { this.correctedSourceTransactionId = correctedSourceTransactionId; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getDispositionNote() { return dispositionNote; }
    public void setDispositionNote(String dispositionNote) { this.dispositionNote = dispositionNote; }
    public Txn getAcceptedTransaction() { return acceptedTransaction; }
    public void setAcceptedTransaction(Txn acceptedTransaction) { this.acceptedTransaction = acceptedTransaction; }
    public Txn getMatchedTransaction() { return matchedTransaction; }
    public void setMatchedTransaction(Txn matchedTransaction) { this.matchedTransaction = matchedTransaction; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touchUpdatedAt() { this.updatedAt = Instant.now(); }

    /** Initializes immutable source identity before a governed interchange import persists this line. */
    public void initializeImportMetadata(UUID portableId)
    {
        if (id != null)
        {
            throw new IllegalStateException("Bank-statement-line metadata must be initialized before persistence");
        }
        this.portableId = java.util.Objects.requireNonNull(portableId, "portableId");
    }
}
