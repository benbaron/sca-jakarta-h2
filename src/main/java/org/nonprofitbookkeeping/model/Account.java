package org.nonprofitbookkeeping.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;


@Entity
@Table(name = "account",
       uniqueConstraints = @UniqueConstraint(name = "uq_account_code", columnNames = {"chart_id", "code"}),
       indexes = {
           @Index(name = "ix_account_parent", columnList = "parent_id"),
           @Index(name = "ix_account_active", columnList = "is_active")
       })
/**
 * Represents the Account component in the nonprofit bookkeeping application.
 */
public class Account
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "chart_id", nullable = false)
    private ChartOfAccounts chart;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "subtype", length = 40)
    private AccountSubtype subtype;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 10)
    private NormalBalance normalBalance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Account parent;

    @Column(name = "is_posting", nullable = false)
    private boolean posting = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Lob
    private String description;

    public Long getId() { return id; }
    public ChartOfAccounts getChart() { return chart; }
    public void setChart(ChartOfAccounts chart) { this.chart = chart; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) { this.accountType = accountType; }

    public NormalBalance getNormalBalance() { return normalBalance; }
    public void setNormalBalance(NormalBalance normalBalance) { this.normalBalance = normalBalance; }

    public Account getParent() { return parent; }
    public void setParent(Account parent) { this.parent = parent; }

    public boolean isPosting() { return posting; }
    public void setPosting(boolean posting) { this.posting = posting; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public AccountSubtype getSubtype() { return subtype; }
    public void setSubtype(AccountSubtype subtype) { this.subtype = subtype; }

    public BigDecimal getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(BigDecimal openingBalance) { this.openingBalance = openingBalance; }

}
