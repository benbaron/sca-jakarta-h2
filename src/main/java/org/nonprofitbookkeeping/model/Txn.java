package org.nonprofitbookkeeping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "txn",
       indexes = {
           @Index(name = "ix_txn_date", columnList = "txn_date"),
           @Index(name = "ix_txn_payee", columnList = "payee_id"),
           @Index(name = "ix_txn_status", columnList = "status"),
           @Index(name = "ix_txn_replacement_for", columnList = "replacement_for_txn_id"),
           @Index(name = "ix_txn_company_date", columnList = "company_id, txn_date")
       })
/**
 * Represents an entered accounting transaction.
 */
public class Txn
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "portable_id", nullable = false, unique = true)
    private UUID portableId = UUID.randomUUID();

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payee_id")
    private Counterparty payee;

    @Column(length = 500)
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id")
    private Account bankAccount;

    @Column(nullable = false, length = 20)
    private String status = "ENTERED";

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_txn_id", unique = true)
    private Txn reversalOf;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_for_txn_id")
    private Txn replacementFor;

    @Column(name = "correction_note", length = 1000)
    private String correctionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public UUID getPortableId() { return portableId; }
    public void setPortableId(UUID portableId) { this.portableId = portableId; }
    public LocalDate getTxnDate() { return txnDate; }
    public void setTxnDate(LocalDate txnDate) { this.txnDate = txnDate; }
    public Counterparty getPayee() { return payee; }
    public void setPayee(Counterparty payee) { this.payee = payee; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public Account getBankAccount() { return bankAccount; }
    public void setBankAccount(Account bankAccount) { this.bankAccount = bankAccount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Txn getReversalOf() { return reversalOf; }
    public void setReversalOf(Txn reversalOf) { this.reversalOf = reversalOf; }
    public Txn getReplacementFor() { return replacementFor; }
    public void setReplacementFor(Txn replacementFor) { this.replacementFor = replacementFor; }
    public String getCorrectionNote() { return correctionNote; }
    public void setCorrectionNote(String correctionNote) { this.correctionNote = correctionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touchUpdatedAt() { this.updatedAt = Instant.now(); }
}
