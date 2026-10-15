package org.nonprofitbookkeeping.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Retained company policy evidence; no ledger or financial running balance. */
@Entity
@Table(name = "nmr_policy_version")
public class NmrPolicyVersion
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "portable_id", nullable = false, unique = true, updatable = false)
    private UUID portableId;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, updatable = false)
    private Company company;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;
    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt = Instant.now();
    @Column(name = "saved_by", nullable = false, length = 200, updatable = false)
    private String savedBy;
    @Column(name = "policy_code", nullable = false, length = 60, updatable = false)
    private String policyCode;
    @Column(name = "category", nullable = false, length = 80, updatable = false)
    private String category;
    @Column(name = "valid_from", nullable = false, updatable = false)
    private LocalDate validFrom;
    @Column(name = "valid_to", nullable = true)
    private LocalDate validTo;
    @Column(name = "rate", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal rate;
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;
    @Column(name = "date_basis", nullable = false, length = 200, updatable = false)
    private String dateBasis;
    @Column(name = "rounding_rule", nullable = false, length = 200, updatable = false)
    private String roundingRule;
    @Column(name = "eligibility", nullable = false, length = 1000, updatable = false)
    private String eligibility;
    @Column(name = "exemptions", nullable = false, length = 1000, updatable = false)
    private String exemptions;
    @Column(name = "recipient", nullable = false, length = 300, updatable = false)
    private String recipient;
    @Column(name = "reporting", nullable = false, length = 500, updatable = false)
    private String reporting;
    @Column(name = "merchant_path", nullable = false, length = 1000, updatable = false)
    private String merchantPath;
    @Column(name = "source_reference", nullable = false, length = 500, updatable = false)
    private String sourceReference;
    @Column(name = "authority", nullable = false, length = 200, updatable = false)
    private String authority;
    @Column(name = "source_date", nullable = false, updatable = false)
    private LocalDate sourceDate;

    public Long getId()
    {
        return id;
    }

    public UUID getPortableId()
    {
        return portableId;
    }

    public void setPortableId(UUID value)
    {
        portableId = value;
    }

    public Company getCompany()
    {
        return company;
    }

    public void setCompany(Company value)
    {
        company = value;
    }

    public String getRequestHash()
    {
        return requestHash;
    }

    public void setRequestHash(String value)
    {
        requestHash = value;
    }

    public Instant getSavedAt()
    {
        return savedAt;
    }

    public String getSavedBy()
    {
        return savedBy;
    }

    public void setSavedBy(String value)
    {
        savedBy = value;
    }

    public String getPolicyCode()
    {
        return policyCode;
    }

    public void setPolicyCode(String value)
    {
        policyCode = value;
    }

    public String getCategory()
    {
        return category;
    }

    public void setCategory(String value)
    {
        category = value;
    }

    public LocalDate getValidFrom()
    {
        return validFrom;
    }

    public void setValidFrom(LocalDate value)
    {
        validFrom = value;
    }

    public LocalDate getValidTo()
    {
        return validTo;
    }

    public void setValidTo(LocalDate value)
    {
        validTo = value;
    }

    public BigDecimal getRate()
    {
        return rate;
    }

    public void setRate(BigDecimal value)
    {
        rate = value;
    }

    public String getCurrency()
    {
        return currency;
    }

    public void setCurrency(String value)
    {
        currency = value;
    }

    public String getDateBasis()
    {
        return dateBasis;
    }

    public void setDateBasis(String value)
    {
        dateBasis = value;
    }

    public String getRoundingRule()
    {
        return roundingRule;
    }

    public void setRoundingRule(String value)
    {
        roundingRule = value;
    }

    public String getEligibility()
    {
        return eligibility;
    }

    public void setEligibility(String value)
    {
        eligibility = value;
    }

    public String getExemptions()
    {
        return exemptions;
    }

    public void setExemptions(String value)
    {
        exemptions = value;
    }

    public String getRecipient()
    {
        return recipient;
    }

    public void setRecipient(String value)
    {
        recipient = value;
    }

    public String getReporting()
    {
        return reporting;
    }

    public void setReporting(String value)
    {
        reporting = value;
    }

    public String getMerchantPath()
    {
        return merchantPath;
    }

    public void setMerchantPath(String value)
    {
        merchantPath = value;
    }

    public String getSourceReference()
    {
        return sourceReference;
    }

    public void setSourceReference(String value)
    {
        sourceReference = value;
    }

    public String getAuthority()
    {
        return authority;
    }

    public void setAuthority(String value)
    {
        authority = value;
    }

    public LocalDate getSourceDate()
    {
        return sourceDate;
    }

    public void setSourceDate(LocalDate value)
    {
        sourceDate = value;
    }
}
