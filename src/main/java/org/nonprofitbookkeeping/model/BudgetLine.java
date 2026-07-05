package org.nonprofitbookkeeping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

/**
 * Planned amount for one budget category, optional fund, and optional fiscal period.
 */
@Entity
@Table(name = "budget_line",
       uniqueConstraints = @UniqueConstraint(name = "uq_budget_line_scope", columnNames = {"budget_plan_id", "budget_category_id", "fund_id", "period_month"}),
       indexes = {
           @Index(name = "ix_budget_line_plan", columnList = "budget_plan_id"),
           @Index(name = "ix_budget_line_category", columnList = "budget_category_id"),
           @Index(name = "ix_budget_line_fund", columnList = "fund_id"),
           @Index(name = "ix_budget_line_period", columnList = "period_month")
       })
public class BudgetLine
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_plan_id", nullable = false)
    private BudgetPlan budgetPlan;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_category_id", nullable = false)
    private BudgetCategory budgetCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id")
    private Fund fund;

    @Column(name = "period_month", length = 7)
    private String periodMonth;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Lob
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public BudgetPlan getBudgetPlan() { return budgetPlan; }
    public void setBudgetPlan(BudgetPlan budgetPlan) { this.budgetPlan = budgetPlan; }
    public BudgetCategory getBudgetCategory() { return budgetCategory; }
    public void setBudgetCategory(BudgetCategory budgetCategory) { this.budgetCategory = budgetCategory; }
    public Fund getFund() { return fund; }
    public void setFund(Fund fund) { this.fund = fund; }
    public YearMonth getPeriodMonth() { return periodMonth == null ? null : YearMonth.parse(periodMonth); }
    public void setPeriodMonth(YearMonth periodMonth) { this.periodMonth = periodMonth == null ? null : periodMonth.toString(); }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touchUpdatedAt() { this.updatedAt = Instant.now(); }
}
