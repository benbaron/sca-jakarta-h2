package org.nonprofitbookkeeping.model;

import jakarta.persistence.*;
import java.time.*;
import java.math.*;


@Entity
@Table(name = "txn_split",
       indexes = {
           @Index(name = "ix_split_txn", columnList = "txn_id"),
           @Index(name = "ix_split_account", columnList = "account_id"),
           @Index(name = "ix_split_fund", columnList = "fund_id"),
           @Index(name = "ix_split_activity", columnList = "activity_id")
       })
public class TxnSplit
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "txn_id", nullable = false)
    private Txn txn;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;

    @Column(name = "nmr_flag", nullable = false)
    private boolean nmr = false;

    @Column(length = 500)
    private String notes;

    /**
     * Signed amount. Interpretation is driven by Account.normalBalance.
     * For presentation as DR/CR:
     *   - If account normal is DEBIT:  debit=max(amount,0), credit=max(-amount,0)
     *   - If account normal is CREDIT: credit=max(amount,0), debit=max(-amount,0)
     */
    @Column(name = "amount_signed", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountSigned;

    public Long getId() { return id; }
    public Txn getTxn() { return txn; }
    public void setTxn(Txn txn) { this.txn = txn; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public Fund getFund() { return fund; }
    public void setFund(Fund fund) { this.fund = fund; }
    public Activity getActivity() { return activity; }
    public void setActivity(Activity activity) { this.activity = activity; }
    public Merchant getMerchant() { return merchant; }
    public void setMerchant(Merchant merchant) { this.merchant = merchant; }
    public boolean isNmr() { return nmr; }
    public void setNmr(boolean nmr) { this.nmr = nmr; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public BigDecimal getAmountSigned() { return amountSigned; }
    public void setAmountSigned(BigDecimal amountSigned) { this.amountSigned = amountSigned; }
}
