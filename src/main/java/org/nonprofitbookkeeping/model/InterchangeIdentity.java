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

/** Durable external identity used for idempotent interchange and traceability. */
@Entity
@Table(name = "interchange_identity",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_interchange_identity_external",
               columnNames = {"company_id", "format_code", "source_system", "entity_type", "external_id"}),
       indexes = {
           @Index(name = "ix_interchange_identity_company_type", columnList = "company_id, entity_type"),
           @Index(name = "ix_interchange_identity_local", columnList = "company_id, entity_type, local_entity_id")
       })
public class InterchangeIdentity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "format_code", nullable = false, length = 40)
    private String formatCode;

    @Column(name = "source_system", nullable = false, length = 160)
    private String sourceSystem;

    @Column(name = "entity_type", nullable = false, length = 80)
    private String entityType;

    @Column(name = "external_id", nullable = false, length = 160)
    private String externalId;

    @Column(name = "normalized_content_hash", nullable = false, length = 64)
    private String normalizedContentHash;

    @Column(name = "local_entity_id", length = 120)
    private String localEntityId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getFormatCode() { return formatCode; }
    public void setFormatCode(String formatCode) { this.formatCode = formatCode; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getNormalizedContentHash() { return normalizedContentHash; }
    public void setNormalizedContentHash(String normalizedContentHash) { this.normalizedContentHash = normalizedContentHash; }
    public String getLocalEntityId() { return localEntityId; }
    public void setLocalEntityId(String localEntityId) { this.localEntityId = localEntityId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void touchUpdatedAt() { this.updatedAt = Instant.now(); }
}
