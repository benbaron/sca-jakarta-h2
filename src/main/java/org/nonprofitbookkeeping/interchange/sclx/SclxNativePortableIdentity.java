package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;

import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves intrinsic portable UUIDs independently from source-specific
 * {@code interchange_identity} rows.
 */
final class SclxNativePortableIdentity
{
    private static final Map<String, TableSpec> TABLES = tables();

    private SclxNativePortableIdentity()
    {
    }

    static UUID portableUuid(String externalId)
    {
        int colon = externalId.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < externalId.length())
        {
            String decoded = URLDecoder.decode(externalId.substring(colon + 1), StandardCharsets.UTF_8);
            try
            {
                return UUID.fromString(decoded);
            }
            catch (IllegalArgumentException ignored)
            {
                // Older/donor identities receive a deterministic local durable UUID below.
            }
        }
        return UUID.nameUUIDFromBytes(("SCLX:" + externalId).getBytes(StandardCharsets.UTF_8));
    }

    static SclxImportTargetSnapshot.NativePortableKey key(String entityType, String externalId)
    {
        String normalizedType = entityType.toUpperCase(Locale.ROOT);
        return TABLES.containsKey(normalizedType)
                ? new SclxImportTargetSnapshot.NativePortableKey(
                        normalizedType, portableUuid(externalId))
                : null;
    }

    static Map<SclxImportTargetSnapshot.NativePortableKey,
            SclxImportTargetSnapshot.NativePortableFact> read(
            EntityManager em,
            Set<SclxImportTargetSnapshot.NativePortableKey> requested)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(requested, "requested");
        Map<String, List<UUID>> byType = new HashMap<>();
        requested.forEach(key -> byType.computeIfAbsent(key.entityType(), ignored -> new ArrayList<>())
                .add(key.portableId()));
        Map<SclxImportTargetSnapshot.NativePortableKey,
                SclxImportTargetSnapshot.NativePortableFact> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<UUID>> entry : byType.entrySet())
        {
            TableSpec spec = TABLES.get(entry.getKey());
            if (spec == null || entry.getValue().isEmpty())
            {
                continue;
            }
            if (SclxNmrPolicies.TYPE.equals(entry.getKey()))
            {
                em.createQuery("from NmrPolicyVersion p join fetch p.company where p.portableId in :ids",
                                org.nonprofitbookkeeping.model.NmrPolicyVersion.class)
                        .setParameter("ids", entry.getValue()).getResultList().forEach(p -> result.put(
                                new SclxImportTargetSnapshot.NativePortableKey(SclxNmrPolicies.TYPE, p.getPortableId()),
                                new SclxImportTargetSnapshot.NativePortableFact(p.getId().toString(), p.getCompany().getCode(),
                                        org.nonprofitbookkeeping.service.NmrPolicyService.importFingerprint(
                                                org.nonprofitbookkeeping.service.NmrPolicyService.importValue(p)))));
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Object[]> rows = em.createNativeQuery(spec.selectSql()
                            + " where " + spec.portableExpression() + " in (:portableIds)")
                    .setParameter("portableIds", entry.getValue())
                    .getResultList();
            for (Object[] row : rows)
            {
                UUID portableId = uuid(row[1]);
                SclxImportTargetSnapshot.NativePortableKey key =
                        new SclxImportTargetSnapshot.NativePortableKey(entry.getKey(), portableId);
                String localId = String.valueOf(row[0]);
                String companyCode = row[2] == null ? null : String.valueOf(row[2]);
                String fingerprint = fingerprint(entry.getKey(), row);
                SclxImportTargetSnapshot.NativePortableFact previous = result.put(key,
                        new SclxImportTargetSnapshot.NativePortableFact(
                                localId, companyCode, fingerprint));
                if (previous != null)
                {
                    throw new IllegalStateException("Duplicate native portable identity: "
                            + entry.getKey() + " " + portableId + ".");
                }
            }
        }
        return Map.copyOf(result);
    }

    static String incomingFingerprint(String entityType, JsonNode value)
    {
        return switch (entityType)
        {
            case "NMR_POLICY_VERSION" -> org.nonprofitbookkeeping.service.NmrPolicyService.importFingerprint(
                    SclxNmrPolicies.parseEntry(value).value());
            case "COUNTERPARTY" -> counterpartyFingerprint(
                    text(value, "displayName"), text(value, "kind"), nullableText(value, "email"),
                    nullableText(value, "phone"), nullableText(value, "notes"),
                    value.path("active").asBoolean());
            case "MERCHANT" -> merchantFingerprint(
                    text(value, "name"), nullableText(value, "notes"),
                    value.path("active").asBoolean());
            case "PROPERTY_CUSTODY_EVENT" -> custodyFingerprint(
                    text(value, "eventType"), text(value, "eventDate"), decimal(value, "quantity"),
                    nullableText(value, "guardianName"), nullableText(value, "guardianEmail"),
                    nullableText(value, "guardianPhone"), nullableText(value, "location"),
                    text(value, "condition"), text(value, "confirmationStatus"),
                    nullableText(value, "confirmationDueDate"), nullableText(value, "confirmationDate"),
                    nullableText(value, "expectedReturnDate"), nullableText(value, "removalReason"),
                    nullableText(value, "authorizationReference"), nullableText(value, "notes"),
                    text(value, "actor"), text(value, "accountingTreatment"));
            default -> null;
        };
    }

    static void requireUnchanged(
            EntityManager em,
            String targetCompanyCode,
            SclxImportEntityPreview preview)
    {
        if (preview.nativePortableId() == null)
        {
            return;
        }
        SclxImportTargetSnapshot.NativePortableKey key =
                new SclxImportTargetSnapshot.NativePortableKey(
                        preview.entityType(), UUID.fromString(preview.nativePortableId()));
        SclxImportTargetSnapshot.NativePortableFact fact = read(em, Set.of(key)).get(key);
        if (fact == null
                || !fact.localEntityId().equals(preview.localEntityId())
                || fact.companyCode() == null
                || !fact.companyCode().equalsIgnoreCase(targetCompanyCode))
        {
            throw new IllegalStateException("The native portable identity changed after preview: "
                    + preview.entityType() + " " + preview.externalId() + ".");
        }
    }

    private static String fingerprint(String entityType, Object[] row)
    {
        return switch (entityType)
        {
            case "COUNTERPARTY" -> counterpartyFingerprint(
                    string(row[3]), string(row[4]), nullableString(row[5]),
                    nullableString(row[6]), nullableString(row[7]), booleanValue(row[8]));
            case "MERCHANT" -> merchantFingerprint(
                    string(row[3]), nullableString(row[4]), booleanValue(row[5]));
            case "PROPERTY_CUSTODY_EVENT" -> custodyFingerprint(
                    string(row[3]), string(row[4]), decimal(row[5]), nullableString(row[6]),
                    nullableString(row[7]), nullableString(row[8]), nullableString(row[9]), string(row[10]),
                    string(row[11]), nullableString(row[12]), nullableString(row[13]), nullableString(row[14]),
                    nullableString(row[15]), nullableString(row[16]), nullableString(row[17]), string(row[18]),
                    string(row[19]));
            default -> null;
        };
    }

    private static String counterpartyFingerprint(
            String displayName,
            String kind,
            String email,
            String phone,
            String notes,
            boolean active)
    {
        return hash(displayName, kind, email, phone, notes, Boolean.toString(active));
    }

    private static String merchantFingerprint(String name, String notes, boolean active)
    {
        return hash(name, notes, Boolean.toString(active));
    }

    private static String custodyFingerprint(String... values)
    {
        return hash(values);
    }

    private static String hash(String... values)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values)
            {
                byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (value == null ? 0 : 1));
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static Map<String, TableSpec> tables()
    {
        Map<String, TableSpec> values = new LinkedHashMap<>();
        values.put("COUNTERPARTY", direct("counterparty", "e.portable_id",
                ", e.display_name, e.kind, e.email, e.phone, cast(e.notes as varchar), e.is_active"));
        values.put("MERCHANT", direct("merchant", "e.portable_id",
                ", e.name, cast(e.notes as varchar), e.is_active"));
        values.put("TRANSACTION", direct("txn", "e.portable_id", ""));
        values.put("FIXED_ASSET", direct("fixed_asset", "e.portable_id", ""));
        values.put("INVENTORY_ITEM", direct("inventory_item", "e.portable_id", ""));
        values.put("PROPERTY_CUSTODY_EVENT", direct("property_custody_event", "e.portable_id",
                ", e.event_type, e.event_date, e.quantity, e.guardian_name, e.guardian_email, e.guardian_phone, e.location, e.item_condition, e.confirmation_status, e.confirmation_due_date, e.confirmation_date, e.expected_return_date, e.removal_reason, e.external_authorization_reference, cast(e.notes as varchar), e.actor, e.accounting_treatment, e.created_at"));
        values.put("BANK", direct("bank", "e.portable_id", ""));
        values.put("BANK_ACCOUNT", direct("company_bank_account", "e.portable_id", ""));
        values.put("BANK_IMPORT_BATCH", direct("bank_import_batch", "e.portable_id", ""));
        values.put("BANK_STATEMENT_LINE", direct("bank_statement_line", "e.portable_id", ""));
        values.put("NMR_POLICY_VERSION", direct("nmr_policy_version", "e.portable_id", ""));
        values.put("AUDIT_EVENT", direct("audit_event", "e.portable_id", ""));
        values.put("DEPRECIATION_RUN", indirect(
                "fixed_asset_depreciation_run e join fixed_asset p on p.id = e.fixed_asset_id",
                "e.portable_id", "p.company_id"));
        values.put("INVENTORY_MOVEMENT", indirect(
                "inventory_movement e join inventory_item p on p.id = e.inventory_item_id",
                "e.portable_id", "p.company_id"));
        values.put("BANK_IMPORT_ISSUE", indirect(
                "import_issue e join bank_import_batch p on p.id = e.batch_id",
                "e.portable_id", "p.company_id"));
        values.put("RECONCILIATION_SESSION", direct(
                "bank_reconciliation_session", "e.portable_id", ""));
        values.put("RECONCILIATION_MATCH", indirect(
                "bank_reconciliation_match e join bank_reconciliation_session p on p.id = e.session_id",
                "e.portable_id", "p.company_id"));
        values.put("PERIOD_CLOSE_RANGE", direct("period_close_range", "e.id", ""));
        values.put(SclxFundTransfers.TYPE, indirect(
                "fund_transfer e join fund f on f.id=e.from_fund_id join fund g on g.id=e.to_fund_id left join txn t on t.id=e.posted_txn_id",
                "e.portable_id", "case when f.company_id=g.company_id and (t.id is null or t.company_id=f.company_id) then f.company_id else null end"));
        values.put("PERIOD_CLOSE_EVENT", direct("period_close_event", "e.id", ""));
        return Map.copyOf(values);
    }

    private static TableSpec direct(String table, String portableExpression, String extraColumns)
    {
        return new TableSpec(
                "select cast(e.id as varchar), " + portableExpression + ", c.code"
                        + extraColumns + " from " + table
                        + " e left join company c on c.id = e.company_id",
                portableExpression);
    }

    private static TableSpec indirect(String from, String portableExpression, String companyExpression)
    {
        return new TableSpec(
                "select cast(e.id as varchar), " + portableExpression + ", c.code from "
                        + from + " left join company c on c.id = " + companyExpression,
                portableExpression);
    }

    private static UUID uuid(Object value)
    {
        if (value instanceof UUID uuid)
        {
            return uuid;
        }
        if (value instanceof byte[] bytes && bytes.length == 16)
        {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        return UUID.fromString(String.valueOf(value));
    }

    private static String text(JsonNode value, String field)
    {
        JsonNode node = value.get(field);
        return node == null || !node.isTextual() ? "" : node.textValue();
    }

    private static String nullableText(JsonNode value, String field)
    {
        JsonNode node = value.get(field);
        return node == null || node.isNull() || !node.isTextual() ? null : node.textValue();
    }

    private static String decimal(JsonNode value, String field)
    {
        JsonNode node = value.get(field);
        return node == null || !node.isNumber() ? "" : node.decimalValue().stripTrailingZeros().toPlainString();
    }

    private static String decimal(Object value)
    {
        return value instanceof java.math.BigDecimal number
                ? number.stripTrailingZeros().toPlainString() : Objects.toString(value, "");
    }

    private static String string(Object value)
    {
        return Objects.toString(value, "");
    }

    private static String nullableString(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean booleanValue(Object value)
    {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private record TableSpec(String selectSql, String portableExpression) { }
}
