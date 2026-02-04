package org.nonprofitbookkeeping.model;

import jakarta.persistence.*;
import java.time.*;
import java.math.*;


@Entity
@Table(name = "fund_transfer",
       indexes = {
           @Index(name = "ix_ft_date", columnList = "transfer_date"),
           @Index(name = "ix_ft_posted", columnList = "posted_txn_id")
       })
public class FundTransfer
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "from_fund_id", nullable = false)
    private Fund fromFund;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "to_fund_id", nullable = false)
    private Fund toFund;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 500)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FundTransferStatus status = FundTransferStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_txn_id")
    private Txn postedTxn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }
    public Fund getFromFund() { return fromFund; }
    public void setFromFund(Fund fromFund) { this.fromFund = fromFund; }
    public Fund getToFund() { return toFund; }
    public void setToFund(Fund toFund) { this.toFund = toFund; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public FundTransferStatus getStatus() { return status; }
    public void setStatus(FundTransferStatus status) { this.status = status; }
    public Txn getPostedTxn() { return postedTxn; }
    public void setPostedTxn(Txn postedTxn) { this.postedTxn = postedTxn; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touchUpdatedAt() { this.updatedAt = Instant.now(); }
}
