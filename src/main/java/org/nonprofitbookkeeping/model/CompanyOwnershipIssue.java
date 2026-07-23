package org.nonprofitbookkeeping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Unresolved migration or cross-company ownership diagnostic. */
@Entity
@Table(name = "company_ownership_issue",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_company_ownership_issue",
               columnNames = {"entity_type", "entity_id", "issue_code"}),
       indexes = @Index(name = "ix_company_ownership_issue_open", columnList = "resolved_at, entity_type"))
public class CompanyOwnershipIssue
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 120)
    private String entityId;

    @Column(name = "issue_code", nullable = false, length = 80)
    private String issueCode;

    @Column(name = "candidate_company_count", nullable = false)
    private int candidateCompanyCount;

    @Column(nullable = false, length = 1000)
    private String details;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getIssueCode() { return issueCode; }
    public void setIssueCode(String issueCode) { this.issueCode = issueCode; }
    public int getCandidateCompanyCount() { return candidateCompanyCount; }
    public void setCandidateCompanyCount(int candidateCompanyCount) { this.candidateCompanyCount = candidateCompanyCount; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public Instant getDetectedAt() { return detectedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void resolve() { this.resolvedAt = Instant.now(); }
}
