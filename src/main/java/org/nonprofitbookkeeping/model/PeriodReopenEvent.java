package org.nonprofitbookkeeping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Audit record for reopening a previously closed accounting period. */
@Entity
@Table(name = "period_reopen_event")
public class PeriodReopenEvent
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "accounting_period_id", nullable = false)
    private AccountingPeriod accountingPeriod;

    @Column(name = "reopened_at", nullable = false)
    private Instant reopenedAt = Instant.now();

    @Column(name = "reopened_by", nullable = false, length = 200)
    private String reopenedBy;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "reopen_scope", nullable = false, length = 40)
    private ReopenScope reopenScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "prior_status", nullable = false, length = 20)
    private AccountingPeriodStatus priorStatus;

    public Long getId() { return id; }
    public AccountingPeriod getAccountingPeriod() { return accountingPeriod; }
    public void setAccountingPeriod(AccountingPeriod accountingPeriod) { this.accountingPeriod = accountingPeriod; }
    public Instant getReopenedAt() { return reopenedAt; }
    public String getReopenedBy() { return reopenedBy; }
    public void setReopenedBy(String reopenedBy) { this.reopenedBy = reopenedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public ReopenScope getReopenScope() { return reopenScope; }
    public void setReopenScope(ReopenScope reopenScope) { this.reopenScope = reopenScope; }
    public AccountingPeriodStatus getPriorStatus() { return priorStatus; }
    public void setPriorStatus(AccountingPeriodStatus priorStatus) { this.priorStatus = priorStatus; }
}
