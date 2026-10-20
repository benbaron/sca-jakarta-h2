package org.nonprofitbookkeeping.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Immutable physical-custody fact for one canonical fixed asset or inventory item. */
@Entity
@Table(name = "property_custody_event", indexes = {
        @Index(name = "ix_property_custody_company_date", columnList = "company_id,event_date"),
        @Index(name = "ix_property_custody_fixed_asset", columnList = "fixed_asset_id,event_date"),
        @Index(name = "ix_property_custody_inventory", columnList = "inventory_item_id,event_date")
})
public class PropertyCustodyEvent
{
    public enum EventType { ASSIGNMENT, CONFIRMATION, LOAN, RETURN, TRANSFER, REMOVAL }
    public enum ConfirmationStatus { NOT_REQUIRED, PENDING, CONFIRMED, EXCEPTION }
    public enum AccountingTreatment { CUSTODY_ONLY, FIXED_ASSET_LIFECYCLE, INVENTORY_MOVEMENT, EXPENSED_NO_VALUE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "portable_id", nullable = false, unique = true, updatable = false)
    private UUID portableId = UUID.randomUUID();
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "fixed_asset_id")
    private FixedAsset fixedAsset;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "inventory_item_id")
    private InventoryItem inventoryItem;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 24)
    private EventType eventType;
    @Column(name = "event_date", nullable = false) private LocalDate eventDate;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal quantity;
    @Column(name = "guardian_name", length = 200) private String guardianName;
    @Column(name = "guardian_email", length = 254) private String guardianEmail;
    @Column(name = "guardian_phone", length = 80) private String guardianPhone;
    @Column(length = 200) private String location;
    @Enumerated(EnumType.STRING) @Column(name = "item_condition", nullable = false, length = 40)
    private InventoryItem.Condition condition = InventoryItem.Condition.UNKNOWN;
    @Enumerated(EnumType.STRING) @Column(name = "confirmation_status", nullable = false, length = 24)
    private ConfirmationStatus confirmationStatus = ConfirmationStatus.NOT_REQUIRED;
    @Column(name = "confirmation_due_date") private LocalDate confirmationDueDate;
    @Column(name = "confirmation_date") private LocalDate confirmationDate;
    @Column(name = "expected_return_date") private LocalDate expectedReturnDate;
    @Column(name = "removal_reason", length = 500) private String removalReason;
    @Column(name = "external_authorization_reference", length = 200) private String externalAuthorizationReference;
    @Lob private String notes;
    @Column(nullable = false, length = 200) private String actor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "correction_of_event_id", unique = true)
    private PropertyCustodyEvent correctionOf;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "fixed_asset_lifecycle_event_id")
    private FixedAssetLifecycleEvent fixedAssetLifecycleEvent;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "inventory_movement_id")
    private InventoryMovement inventoryMovement;
    @Enumerated(EnumType.STRING) @Column(name = "accounting_treatment", nullable = false, length = 40)
    private AccountingTreatment accountingTreatment = AccountingTreatment.CUSTODY_ONLY;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public UUID getPortableId() { return portableId; }
    public Company getCompany() { return company; }
    public void setCompany(Company value) { company = value; }
    public FixedAsset getFixedAsset() { return fixedAsset; }
    public void setFixedAsset(FixedAsset value) { fixedAsset = value; }
    public InventoryItem getInventoryItem() { return inventoryItem; }
    public void setInventoryItem(InventoryItem value) { inventoryItem = value; }
    public EventType getEventType() { return eventType; }
    public void setEventType(EventType value) { eventType = value; }
    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate value) { eventDate = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public String getGuardianName() { return guardianName; }
    public void setGuardianName(String value) { guardianName = value; }
    public String getGuardianEmail() { return guardianEmail; }
    public void setGuardianEmail(String value) { guardianEmail = value; }
    public String getGuardianPhone() { return guardianPhone; }
    public void setGuardianPhone(String value) { guardianPhone = value; }
    public String getLocation() { return location; }
    public void setLocation(String value) { location = value; }
    public InventoryItem.Condition getCondition() { return condition; }
    public void setCondition(InventoryItem.Condition value) { condition = value; }
    public ConfirmationStatus getConfirmationStatus() { return confirmationStatus; }
    public void setConfirmationStatus(ConfirmationStatus value) { confirmationStatus = value; }
    public LocalDate getConfirmationDueDate() { return confirmationDueDate; }
    public void setConfirmationDueDate(LocalDate value) { confirmationDueDate = value; }
    public LocalDate getConfirmationDate() { return confirmationDate; }
    public void setConfirmationDate(LocalDate value) { confirmationDate = value; }
    public LocalDate getExpectedReturnDate() { return expectedReturnDate; }
    public void setExpectedReturnDate(LocalDate value) { expectedReturnDate = value; }
    public String getRemovalReason() { return removalReason; }
    public void setRemovalReason(String value) { removalReason = value; }
    public String getExternalAuthorizationReference() { return externalAuthorizationReference; }
    public void setExternalAuthorizationReference(String value) { externalAuthorizationReference = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public String getActor() { return actor; }
    public void setActor(String value) { actor = value; }
    public PropertyCustodyEvent getCorrectionOf() { return correctionOf; }
    public void setCorrectionOf(PropertyCustodyEvent value) { correctionOf = value; }
    public FixedAssetLifecycleEvent getFixedAssetLifecycleEvent() { return fixedAssetLifecycleEvent; }
    public void setFixedAssetLifecycleEvent(FixedAssetLifecycleEvent value) { fixedAssetLifecycleEvent = value; }
    public InventoryMovement getInventoryMovement() { return inventoryMovement; }
    public void setInventoryMovement(InventoryMovement value) { inventoryMovement = value; }
    public AccountingTreatment getAccountingTreatment() { return accountingTreatment; }
    public void setAccountingTreatment(AccountingTreatment value) { accountingTreatment = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void initializePortableIdentity(UUID value) { if (id != null) throw new IllegalStateException("Portable identity is immutable"); portableId = Objects.requireNonNull(value); }
    public void initializeImportMetadata(UUID value, Instant created) { initializePortableIdentity(value); createdAt = Objects.requireNonNull(created); }
}
