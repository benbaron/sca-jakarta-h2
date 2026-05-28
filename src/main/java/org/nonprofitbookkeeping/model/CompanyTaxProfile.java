package org.nonprofitbookkeeping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/** Tax and filing identifiers/settings for a company/branch. */
@Entity
@Table(name = "company_tax_profile",
       indexes = @Index(name = "ix_company_tax_profile_company", columnList = "company_id"))
public class CompanyTaxProfile
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(length = 40)
    private String ein;

    @Column(name = "tax_jurisdiction", length = 120)
    private String taxJurisdiction;

    @Column(name = "filing_name", length = 250)
    private String filingName;

    @Column(name = "filing_address", length = 1000)
    private String filingAddress;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getEin() { return ein; }
    public void setEin(String ein) { this.ein = ein; }
    public String getTaxJurisdiction() { return taxJurisdiction; }
    public void setTaxJurisdiction(String taxJurisdiction) { this.taxJurisdiction = taxJurisdiction; }
    public String getFilingName() { return filingName; }
    public void setFilingName(String filingName) { this.filingName = filingName; }
    public String getFilingAddress() { return filingAddress; }
    public void setFilingAddress(String filingAddress) { this.filingAddress = filingAddress; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touchUpdatedAt() { this.updatedAt = Instant.now(); }
}
