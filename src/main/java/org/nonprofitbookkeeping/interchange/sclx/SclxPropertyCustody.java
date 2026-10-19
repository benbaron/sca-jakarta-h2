package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import org.nonprofitbookkeeping.model.PropertyCustodyEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Strict optional version-1 custody event extension. */
final class SclxPropertyCustody
{
    static final String KEY = "propertyCustody";

    record Value(String eventId, String fixedAssetId, String inventoryItemId, String correctionOfEventId,
            String inventoryMovementId, PropertyCustodyEvent.EventType eventType, LocalDate eventDate,
            BigDecimal quantity, String guardianName, String guardianEmail, String guardianPhone,
            String location, String condition, String confirmationStatus, LocalDate confirmationDueDate,
            LocalDate confirmationDate, LocalDate expectedReturnDate, String removalReason,
            String authorizationReference, String notes, String actor, String accountingTreatment,
            Instant createdAt) { }

    static Map<String,Object> export(String companyCode, List<PropertyCustodyEvent> events)
    {
        List<Map<String,Object>> rows = new ArrayList<>();
        for (PropertyCustodyEvent e : events)
        {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("eventId", SclxPortableIdentity.propertyCustodyEvent(companyCode, e.getPortableId().toString()));
            put(row, "fixedAssetId", e.getFixedAsset() == null ? null : SclxPortableIdentity.fixedAsset(companyCode, e.getFixedAsset().getPortableId().toString()));
            put(row, "inventoryItemId", e.getInventoryItem() == null ? null : SclxPortableIdentity.inventoryItem(companyCode, e.getInventoryItem().getPortableId().toString()));
            put(row, "correctionOfEventId", e.getCorrectionOf() == null ? null : SclxPortableIdentity.propertyCustodyEvent(companyCode, e.getCorrectionOf().getPortableId().toString()));
            put(row, "inventoryMovementId", e.getInventoryMovement() == null ? null : SclxPortableIdentity.inventoryMovement(companyCode, e.getInventoryMovement().getPortableId().toString()));
            row.put("eventType", e.getEventType().name()); row.put("eventDate", e.getEventDate()); row.put("quantity", e.getQuantity());
            put(row, "guardianName", e.getGuardianName()); put(row, "guardianEmail", e.getGuardianEmail()); put(row, "guardianPhone", e.getGuardianPhone()); put(row, "location", e.getLocation());
            row.put("condition", e.getCondition().name()); row.put("confirmationStatus", e.getConfirmationStatus().name());
            put(row, "confirmationDueDate", e.getConfirmationDueDate()); put(row, "confirmationDate", e.getConfirmationDate()); put(row, "expectedReturnDate", e.getExpectedReturnDate());
            put(row, "removalReason", e.getRemovalReason()); put(row, "authorizationReference", e.getExternalAuthorizationReference()); put(row, "notes", e.getNotes());
            row.put("actor", e.getActor()); row.put("accountingTreatment", e.getAccountingTreatment().name()); row.put("createdAt", e.getCreatedAt()); rows.add(Map.copyOf(row));
        }
        return Map.of("version", 1, "events", List.copyOf(rows));
    }

    static int count(SclxExportDocument.Extensions extensions)
    {
        Object raw = extensions.scaJakartaH2().get(KEY);
        if (raw == null) return 0;
        if (!(raw instanceof Map<?,?> map) || !(map.get("events") instanceof List<?> events))
            throw new IllegalArgumentException("extensions.scaJakartaH2.propertyCustody must contain events");
        return events.size();
    }

    static List<Value> parse(JsonNode root)
    {
        JsonNode ext = root.path("extensions").path("scaJakartaH2").path(KEY);
        if (ext.isMissingNode() || ext.isNull()) return List.of();
        if (!ext.isObject() || ext.path("version").asInt(-1) != 1 || !ext.path("events").isArray())
            throw new IllegalStateException("$.extensions.scaJakartaH2.propertyCustody must be a version-1 object with events.");
        ext.fieldNames().forEachRemaining(name -> { if (!Set.of("version", "events").contains(name)) throw new IllegalStateException("Unsupported property custody extension field: " + name); });
        Set<String> allowed = Set.of("eventId","fixedAssetId","inventoryItemId","correctionOfEventId","inventoryMovementId","eventType","eventDate","quantity","guardianName","guardianEmail","guardianPhone","location","condition","confirmationStatus","confirmationDueDate","confirmationDate","expectedReturnDate","removalReason","authorizationReference","notes","actor","accountingTreatment","createdAt");
        List<Value> result = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (int i=0; i<ext.path("events").size(); i++)
        {
            JsonNode n = ext.path("events").get(i); if (!n.isObject()) throw new IllegalStateException("propertyCustody.events["+i+"] must be an object");
            n.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw new IllegalStateException("Unsupported property custody field: " + name); });
            String id = required(n,"eventId"); if (!ids.add(id)) throw new IllegalStateException("Duplicate property custody identity: " + id);
            String fixed = optional(n,"fixedAssetId"), inventory = optional(n,"inventoryItemId");
            if ((fixed == null) == (inventory == null)) throw new IllegalStateException("Each property custody event requires exactly one subject");
            result.add(new Value(id, fixed, inventory, optional(n,"correctionOfEventId"), optional(n,"inventoryMovementId"),
                    PropertyCustodyEvent.EventType.valueOf(required(n,"eventType")), LocalDate.parse(required(n,"eventDate")), n.path("quantity").decimalValue(),
                    optional(n,"guardianName"), optional(n,"guardianEmail"), optional(n,"guardianPhone"), optional(n,"location"), required(n,"condition"), required(n,"confirmationStatus"),
                    date(n,"confirmationDueDate"), date(n,"confirmationDate"), date(n,"expectedReturnDate"), optional(n,"removalReason"), optional(n,"authorizationReference"), optional(n,"notes"),
                    required(n,"actor"), required(n,"accountingTreatment"), Instant.parse(required(n,"createdAt"))));
        }
        Set<String> known = new HashSet<>(ids);
        Set<String> corrected = new HashSet<>(); Map<String,String> parents = new HashMap<>();
        for (Value v : result) if (v.correctionOfEventId()!=null) { if (!known.contains(v.correctionOfEventId())) throw new IllegalStateException("Custody correction reference is outside the extension"); if (!corrected.add(v.correctionOfEventId())) throw new IllegalStateException("A custody event can have only one direct correction"); parents.put(v.eventId(), v.correctionOfEventId()); }
        for (String id : ids) { Set<String> seen = new HashSet<>(); String next=id; while ((next=parents.get(next))!=null) if(!seen.add(next)) throw new IllegalStateException("Custody correction chain contains a cycle"); }
        return List.copyOf(result);
    }

    private static void put(Map<String,Object> map,String key,Object value){ if(value!=null) map.put(key,value); }
    private static String required(JsonNode n,String key){ String v=optional(n,key); if(v==null) throw new IllegalStateException("Property custody field is required: "+key); return v; }
    private static String optional(JsonNode n,String key){ JsonNode v=n.get(key); return v==null||v.isNull()?null:v.asText(); }
    private static LocalDate date(JsonNode n,String key){ String v=optional(n,key); return v==null?null:LocalDate.parse(v); }
}
