package org.nonprofitbookkeeping.model;

import jakarta.persistence.*;
import java.time.*;


@Entity
@Table(name = "account_report_section",
       uniqueConstraints = @UniqueConstraint(name = "uq_account_report", columnNames = {"account_id", "report_section_id"}))
/**
 * Represents the AccountReportSection component in the nonprofit bookkeeping application.
 */
public class AccountReportSection
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "report_section_id", nullable = false)
    private ReportSection reportSection;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "sign_policy", nullable = false, length = 20)
    private SignPolicy signPolicy = SignPolicy.NORMAL;

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public ReportSection getReportSection() { return reportSection; }
    public void setReportSection(ReportSection reportSection) { this.reportSection = reportSection; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public SignPolicy getSignPolicy() { return signPolicy; }
    public void setSignPolicy(SignPolicy signPolicy) { this.signPolicy = signPolicy; }
}
