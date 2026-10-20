package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;

/** Company-scoped physical custody history over the canonical asset and inventory registers. */
public final class PropertyCustodyService
{
    public enum SubjectType { FIXED_ASSET, INVENTORY_ITEM }

    public record Command(SubjectType subjectType, long subjectId,
            PropertyCustodyEvent.EventType eventType, LocalDate eventDate, BigDecimal quantity,
            String guardianName, String guardianEmail, String guardianPhone, String location,
            InventoryItem.Condition condition, PropertyCustodyEvent.ConfirmationStatus confirmationStatus,
            LocalDate confirmationDueDate, LocalDate confirmationDate, LocalDate expectedReturnDate,
            String removalReason, String externalAuthorizationReference, String notes,
            Long fixedAssetLifecycleEventId, Long inventoryMovementId) { }

    public record SubjectView(SubjectType subjectType, long subjectId, String name, String registerStatus,
            BigDecimal quantity, String unit, InventoryItem.FinancialTreatment financialTreatment,
            String guardianName, String location, InventoryItem.Condition condition,
            PropertyCustodyEvent.ConfirmationStatus confirmationStatus, LocalDate confirmationDueDate,
            LocalDate expectedReturnDate, boolean confirmationOverdue, Long currentEventId)
    {
        @Override public String toString() { return name + " (" + subjectType + ")"; }
    }

    public record EventView(long id, UUID portableId, SubjectType subjectType, long subjectId,
            String subjectName, PropertyCustodyEvent.EventType eventType, LocalDate eventDate,
            BigDecimal quantity, String guardianName, String guardianEmail, String guardianPhone,
            String location, InventoryItem.Condition condition,
            PropertyCustodyEvent.ConfirmationStatus confirmationStatus,
            LocalDate confirmationDueDate, LocalDate confirmationDate, LocalDate expectedReturnDate,
            String removalReason, String externalAuthorizationReference, String notes, String actor,
            Long correctionOfEventId, boolean corrected, PropertyCustodyEvent.AccountingTreatment accountingTreatment,
            Long fixedAssetLifecycleEventId, Long inventoryMovementId) { }

    private final Jpa jpa;
    private final Supplier<String> companyCodeSupplier;
    private final AuthorizationGuard authorizationGuard;

    public PropertyCustodyService(Jpa jpa, Supplier<String> companyCodeSupplier)
    {
        this(jpa, companyCodeSupplier, null);
    }

    public PropertyCustodyService(Jpa jpa, Supplier<String> companyCodeSupplier, AuthorizationGuard authorizationGuard)
    {
        this.jpa = Objects.requireNonNull(jpa);
        this.companyCodeSupplier = Objects.requireNonNull(companyCodeSupplier);
        this.authorizationGuard = authorizationGuard;
    }

    public List<SubjectView> listSubjects()
    {
        String code = companyCode();
        LocalDate today = LocalDate.now();
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, code);
            List<SubjectView> result = new ArrayList<>();
            for (FixedAsset asset : em.createQuery("from FixedAsset a where a.company = :company order by a.name, a.id", FixedAsset.class)
                    .setParameter("company", company).getResultList())
            {
                PropertyCustodyEvent current = current(em, SubjectType.FIXED_ASSET, asset.getId());
                result.add(subject(asset, current, today));
            }
            for (InventoryItem item : em.createQuery("from InventoryItem i where i.company = :company order by i.name, i.id", InventoryItem.class)
                    .setParameter("company", company).getResultList())
            {
                PropertyCustodyEvent current = current(em, SubjectType.INVENTORY_ITEM, item.getId());
                result.add(subject(item, current, today));
            }
            return List.copyOf(result);
        }
    }

    public List<EventView> history(SubjectType type, long subjectId)
    {
        try (EntityManager em = jpa.em())
        {
            Company company = new CompanyOwnershipService(jpa).requireCompany(em, companyCode());
            String predicate = type == SubjectType.FIXED_ASSET ? "e.fixedAsset.id" : "e.inventoryItem.id";
            return em.createQuery("select e from PropertyCustodyEvent e where e.company = :company and "
                            + predicate + " = :id order by e.eventDate desc, e.id desc", PropertyCustodyEvent.class)
                    .setParameter("company", company).setParameter("id", subjectId).getResultList().stream()
                    .map(e -> toView(em, e)).toList();
        }
    }

    public EventView save(Command command, String actor)
    {
        return write(command, null, actor);
    }

    public EventView correctLatest(long eventId, Command replacement, String actor)
    {
        return write(replacement, eventId, actor);
    }

    private EventView write(Command command, Long correctionId, String actor)
    {
        validate(command);
        String code = companyCode();
        String normalizedActor = text(ServiceAuthorization.actor(authorizationGuard,
                ApplicationPermission.BOOKKEEPING_WRITE, code, "save property custody event", actor), "actor");
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            try
            {
                Company company = new CompanyOwnershipService(jpa).requireCompany(em, code);
                if (!company.isActive()) throw new IllegalStateException("Company " + code + " is inactive");
                FixedAsset asset = null;
                InventoryItem item = null;
                if (command.subjectType() == SubjectType.FIXED_ASSET)
                {
                    asset = em.find(FixedAsset.class, command.subjectId(), LockModeType.PESSIMISTIC_WRITE);
                    if (asset == null || !asset.getCompany().getId().equals(company.getId())) throw new IllegalArgumentException("Fixed asset not found in active company");
                }
                else
                {
                    item = em.find(InventoryItem.class, command.subjectId(), LockModeType.PESSIMISTIC_WRITE);
                    if (item == null || !item.getCompany().getId().equals(company.getId())) throw new IllegalArgumentException("Inventory item not found in active company");
                }
                PropertyCustodyEvent correction = null;
                if (correctionId != null)
                {
                    correction = em.find(PropertyCustodyEvent.class, correctionId, LockModeType.PESSIMISTIC_WRITE);
                    if (correction == null || !correction.getCompany().getId().equals(company.getId())) throw new IllegalArgumentException("Custody event not found in active company");
                    if (!sameSubject(correction, command)) throw new IllegalArgumentException("A correction must retain its original subject");
                    if (current(em, command.subjectType(), command.subjectId()) != correction)
                        throw new IllegalStateException("Only the latest effective custody event can be corrected");
                    if (command.eventDate().isBefore(correction.getEventDate()))
                        throw new IllegalArgumentException("A replacement custody event cannot predate the event it corrects");
                }

                PropertyCustodyEvent event = new PropertyCustodyEvent();
                event.setCompany(company); event.setFixedAsset(asset); event.setInventoryItem(item);
                event.setEventType(command.eventType()); event.setEventDate(command.eventDate());
                event.setQuantity(scale(command.quantity())); event.setGuardianName(blank(command.guardianName()));
                event.setGuardianEmail(blank(command.guardianEmail())); event.setGuardianPhone(blank(command.guardianPhone()));
                event.setLocation(blank(command.location()));
                event.setCondition(command.condition() == null ? InventoryItem.Condition.UNKNOWN : command.condition());
                event.setConfirmationStatus(command.confirmationStatus() == null
                        ? PropertyCustodyEvent.ConfirmationStatus.NOT_REQUIRED : command.confirmationStatus());
                event.setConfirmationDueDate(command.confirmationDueDate()); event.setConfirmationDate(command.confirmationDate());
                event.setExpectedReturnDate(command.expectedReturnDate()); event.setRemovalReason(blank(command.removalReason()));
                event.setExternalAuthorizationReference(blank(command.externalAuthorizationReference()));
                event.setNotes(blank(command.notes())); event.setActor(normalizedActor); event.setCorrectionOf(correction);
                linkAccountingSource(em, event, command, asset, item);
                validateQuantity(command, asset, item, event);
                em.persist(event);
                em.flush();
                if (item != null && current(em, SubjectType.INVENTORY_ITEM, item.getId()) == event)
                {
                    item.setCustodian(event.getGuardianName()); item.setStorageLocation(event.getLocation());
                    item.setCondition(event.getCondition()); item.touchUpdatedAt();
                }
                em.persist(audit(company, normalizedActor, event, correction));
                em.getTransaction().commit();
                return findView(event.getId());
            }
            catch (RuntimeException ex)
            {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                throw ex;
            }
        }
    }

    private void linkAccountingSource(EntityManager em, PropertyCustodyEvent event, Command command,
            FixedAsset asset, InventoryItem item)
    {
        if (command.eventType() != PropertyCustodyEvent.EventType.REMOVAL)
        {
            if (command.fixedAssetLifecycleEventId() != null || command.inventoryMovementId() != null)
                throw new IllegalArgumentException("Accounting sources are allowed only for removal custody events");
            event.setAccountingTreatment(PropertyCustodyEvent.AccountingTreatment.CUSTODY_ONLY);
            return;
        }
        if (command.removalReason() == null || command.removalReason().isBlank())
            throw new IllegalArgumentException("Removal reason is required");
        if (asset != null)
        {
            if (command.fixedAssetLifecycleEventId() == null || command.inventoryMovementId() != null)
                throw new IllegalArgumentException("Fixed-asset removal requires its Sale or Retirement lifecycle event");
            FixedAssetLifecycleEvent source = em.find(FixedAssetLifecycleEvent.class, command.fixedAssetLifecycleEventId());
            if (source == null || !source.getFixedAsset().getId().equals(asset.getId()) || source.isReversed()
                    || source.getEventDate().isAfter(command.eventDate())
                    || (source.getEventType() != FixedAssetLifecycleEvent.EventType.SALE
                    && source.getEventType() != FixedAssetLifecycleEvent.EventType.RETIREMENT))
                throw new IllegalArgumentException("Removal source must be an effective Sale or Retirement for this asset");
            event.setFixedAssetLifecycleEvent(source);
            event.setAccountingTreatment(PropertyCustodyEvent.AccountingTreatment.FIXED_ASSET_LIFECYCLE);
        }
        else
        {
            if (command.inventoryMovementId() == null || command.fixedAssetLifecycleEventId() != null)
                throw new IllegalArgumentException("Inventory removal requires its reducing movement");
            InventoryMovement source = em.find(InventoryMovement.class, command.inventoryMovementId());
            if (source == null || !source.getInventoryItem().getId().equals(item.getId())
                    || source.getQuantityChange().signum() >= 0
                    || (source.getMovementType() != InventoryMovement.MovementType.ISSUE
                    && source.getMovementType() != InventoryMovement.MovementType.ADJUSTMENT)
                    || source.getMovementDate().isAfter(command.eventDate()))
                throw new IllegalArgumentException("Removal source must reduce this inventory item");
            if (source.getTransaction() != null)
            {
                Long reversals = em.createQuery("select count(m) from InventoryMovement m where m.transaction.reversalOf = :txn", Long.class)
                        .setParameter("txn", source.getTransaction()).getSingleResult();
                if (reversals > 0) throw new IllegalArgumentException("Removal source movement has been reversed");
            }
            event.setInventoryMovement(source);
            event.setAccountingTreatment(item.getFinancialTreatment() == InventoryItem.FinancialTreatment.EXPENSED_SUPPLY
                    ? PropertyCustodyEvent.AccountingTreatment.EXPENSED_NO_VALUE
                    : PropertyCustodyEvent.AccountingTreatment.INVENTORY_MOVEMENT);
        }
    }

    private static void validateQuantity(Command command, FixedAsset asset, InventoryItem item, PropertyCustodyEvent event)
    {
        BigDecimal quantity = scale(command.quantity());
        if (asset != null && quantity.compareTo(BigDecimal.ONE.setScale(4)) != 0)
            throw new IllegalArgumentException("Fixed-asset custody quantity must be 1");
        if (item != null)
        {
            BigDecimal expected = command.eventType() == PropertyCustodyEvent.EventType.REMOVAL
                    ? event.getInventoryMovement().getQuantityChange().abs() : scale(item.getQuantity());
            if (quantity.compareTo(expected) != 0)
                throw new IllegalArgumentException("Custody quantity must cover the complete inventory subject quantity " + expected);
        }
    }

    private EventView findView(long id)
    {
        try (EntityManager em = jpa.em()) { return toView(em, em.find(PropertyCustodyEvent.class, id)); }
    }

    private static PropertyCustodyEvent current(EntityManager em, SubjectType type, long subjectId)
    {
        String predicate = type == SubjectType.FIXED_ASSET ? "e.fixedAsset.id" : "e.inventoryItem.id";
        return em.createQuery("select e from PropertyCustodyEvent e where " + predicate + " = :id and not exists "
                        + "(select c.id from PropertyCustodyEvent c where c.correctionOf = e) order by e.eventDate desc, e.id desc",
                        PropertyCustodyEvent.class)
                .setParameter("id", subjectId).setMaxResults(1).getResultStream().findFirst().orElse(null);
    }

    private static SubjectView subject(FixedAsset a, PropertyCustodyEvent e, LocalDate today)
    {
        return new SubjectView(SubjectType.FIXED_ASSET, a.getId(), a.getName(), a.getStatus().name(),
                BigDecimal.ONE.setScale(4), "asset", null, val(e, PropertyCustodyEvent::getGuardianName, ""),
                val(e, PropertyCustodyEvent::getLocation, ""), val(e, PropertyCustodyEvent::getCondition, InventoryItem.Condition.UNKNOWN),
                val(e, PropertyCustodyEvent::getConfirmationStatus, PropertyCustodyEvent.ConfirmationStatus.NOT_REQUIRED),
                e == null ? null : e.getConfirmationDueDate(), e == null ? null : e.getExpectedReturnDate(), overdue(e, today), e == null ? null : e.getId());
    }

    private static SubjectView subject(InventoryItem i, PropertyCustodyEvent e, LocalDate today)
    {
        return new SubjectView(SubjectType.INVENTORY_ITEM, i.getId(), i.getName(), i.getStatus().name(), scale(i.getQuantity()),
                i.getUnit(), i.getFinancialTreatment(), e == null ? Objects.toString(i.getCustodian(), "") : Objects.toString(e.getGuardianName(), ""),
                e == null ? Objects.toString(i.getStorageLocation(), "") : Objects.toString(e.getLocation(), ""),
                e == null ? i.getCondition() : e.getCondition(), val(e, PropertyCustodyEvent::getConfirmationStatus, PropertyCustodyEvent.ConfirmationStatus.NOT_REQUIRED),
                e == null ? null : e.getConfirmationDueDate(), e == null ? null : e.getExpectedReturnDate(), overdue(e, today), e == null ? null : e.getId());
    }

    private static EventView toView(EntityManager em, PropertyCustodyEvent e)
    {
        boolean fixed = e.getFixedAsset() != null;
        Long corrections = em.createQuery("select count(c) from PropertyCustodyEvent c where c.correctionOf = :event", Long.class)
                .setParameter("event", e).getSingleResult();
        return new EventView(e.getId(), e.getPortableId(), fixed ? SubjectType.FIXED_ASSET : SubjectType.INVENTORY_ITEM,
                fixed ? e.getFixedAsset().getId() : e.getInventoryItem().getId(), fixed ? e.getFixedAsset().getName() : e.getInventoryItem().getName(),
                e.getEventType(), e.getEventDate(), e.getQuantity(), Objects.toString(e.getGuardianName(), ""),
                Objects.toString(e.getGuardianEmail(), ""), Objects.toString(e.getGuardianPhone(), ""), Objects.toString(e.getLocation(), ""),
                e.getCondition(), e.getConfirmationStatus(), e.getConfirmationDueDate(), e.getConfirmationDate(), e.getExpectedReturnDate(),
                Objects.toString(e.getRemovalReason(), ""), Objects.toString(e.getExternalAuthorizationReference(), ""), Objects.toString(e.getNotes(), ""),
                e.getActor(), e.getCorrectionOf() == null ? null : e.getCorrectionOf().getId(), corrections > 0, e.getAccountingTreatment(),
                e.getFixedAssetLifecycleEvent() == null ? null : e.getFixedAssetLifecycleEvent().getId(),
                e.getInventoryMovement() == null ? null : e.getInventoryMovement().getId());
    }

    private static AuditEvent audit(Company company, String actor, PropertyCustodyEvent event, PropertyCustodyEvent correction)
    {
        AuditEvent audit = new AuditEvent(); audit.setCompany(company); audit.setActor(actor);
        audit.setActionType(correction == null ? "PROPERTY_CUSTODY_RECORDED" : "PROPERTY_CUSTODY_CORRECTED");
        audit.setEntityType("PropertyCustodyEvent"); audit.setEntityId(Long.toString(event.getId()));
        audit.setSummary(event.getEventType() + " custody event recorded");
        audit.setBeforeValue(correction == null ? null : "eventId=" + correction.getId());
        audit.setAfterValue("eventId=" + event.getId() + ",accounting=" + event.getAccountingTreatment());
        audit.setReason(correction == null ? event.getNotes() : "Replacement custody fact"); return audit;
    }

    private String companyCode() { return text(companyCodeSupplier.get(), "companyCode").toUpperCase(Locale.ROOT); }
    private static boolean sameSubject(PropertyCustodyEvent e, Command c) { return c.subjectType() == SubjectType.FIXED_ASSET ? e.getFixedAsset() != null && e.getFixedAsset().getId() == c.subjectId() : e.getInventoryItem() != null && e.getInventoryItem().getId() == c.subjectId(); }
    private static void validate(Command c) { Objects.requireNonNull(c, "command"); Objects.requireNonNull(c.subjectType(), "subjectType"); Objects.requireNonNull(c.eventType(), "eventType"); Objects.requireNonNull(c.eventDate(), "eventDate"); if (c.quantity() == null || c.quantity().signum() < 0) throw new IllegalArgumentException("quantity must be nonnegative"); if ((c.confirmationStatus() == PropertyCustodyEvent.ConfirmationStatus.CONFIRMED || c.confirmationStatus() == PropertyCustodyEvent.ConfirmationStatus.EXCEPTION) && c.confirmationDate() == null) throw new IllegalArgumentException("Confirmation date is required for confirmed or exception custody"); if (c.eventType() == PropertyCustodyEvent.EventType.LOAN && c.expectedReturnDate() == null) throw new IllegalArgumentException("Expected return date is required for a loan"); }
    private static BigDecimal scale(BigDecimal value) { return value.setScale(4, RoundingMode.HALF_UP); }
    private static String text(String value, String label) { if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required"); return value.trim(); }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static boolean overdue(PropertyCustodyEvent e, LocalDate today) { return e != null && e.getConfirmationDueDate() != null && e.getConfirmationDueDate().isBefore(today) && e.getConfirmationStatus() != PropertyCustodyEvent.ConfirmationStatus.CONFIRMED; }
    private static <T> T val(PropertyCustodyEvent e, java.util.function.Function<PropertyCustodyEvent,T> f, T fallback) { return e == null || f.apply(e) == null ? fallback : f.apply(e); }
}
