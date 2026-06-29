package org.nonprofitbookkeeping.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/** Material-change audit event stored in the active organization database. */
@Entity
@Table(name = "audit_event")
public class AuditEvent
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    @Column(nullable = false, length = 200)
    private String actor;

    @Column(name = "action_type", nullable = false, length = 80)
    private String actionType;

    @Column(name = "entity_type", nullable = false, length = 120)
    private String entityType;

    @Column(name = "entity_id", length = 120)
    private String entityId;

    @Column(nullable = false, length = 500)
    private String summary;

    @Lob
    @Column(name = "before_value")
    private String beforeValue;

    @Lob
    @Column(name = "after_value")
    private String afterValue;

    @Column(length = 1000)
    private String reason;

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getBeforeValue() { return beforeValue; }
    public void setBeforeValue(String beforeValue) { this.beforeValue = beforeValue; }
    public String getAfterValue() { return afterValue; }
    public void setAfterValue(String afterValue) { this.afterValue = afterValue; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
