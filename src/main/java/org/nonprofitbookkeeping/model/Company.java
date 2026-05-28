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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Company/branch profile and current administrative properties. */
@Entity
@Table(name = "company",
       uniqueConstraints = @UniqueConstraint(name = "uq_company_code", columnNames = {"code"}),
       indexes = {
           @Index(name = "ix_company_active", columnList = "is_active"),
           @Index(name = "ix_company_display_name", columnList = "display_name")
       })
public class Company
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "legal_name", length = 250)
    private String legalName;

    @Column(name = "branch_type", length = 80)
    private String branchType;

    @Column(name = "parent_organization", length = 200)
    private String parentOrganization;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "fiscal_year_start_month", nullable = false)
    private int fiscalYearStartMonth = 1;

    @Column(name = "fiscal_year_start_day", nullable = false)
    private int fiscalYearStartDay = 1;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency = "USD";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_chart_of_accounts_id")
    private ChartOfAccounts activeChartOfAccounts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getBranchType() { return branchType; }
    public void setBranchType(String branchType) { this.branchType = branchType; }
    public String getParentOrganization() { return parentOrganization; }
    public void setParentOrganization(String parentOrganization) { this.parentOrganization = parentOrganization; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public int getFiscalYearStartMonth() { return fiscalYearStartMonth; }
    public void setFiscalYearStartMonth(int fiscalYearStartMonth) { this.fiscalYearStartMonth = fiscalYearStartMonth; }
    public int getFiscalYearStartDay() { return fiscalYearStartDay; }
    public void setFiscalYearStartDay(int fiscalYearStartDay) { this.fiscalYearStartDay = fiscalYearStartDay; }
    public String getDefaultCurrency() { return defaultCurrency; }
    public void setDefaultCurrency(String defaultCurrency) { this.defaultCurrency = defaultCurrency; }
    public ChartOfAccounts getActiveChartOfAccounts() { return activeChartOfAccounts; }
    public void setActiveChartOfAccounts(ChartOfAccounts activeChartOfAccounts) { this.activeChartOfAccounts = activeChartOfAccounts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touchUpdatedAt() { this.updatedAt = Instant.now(); }
}
