package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;

import java.net.URLDecoder;
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
            case "COUNTERPARTY" -> counterpartyFingerprint(
                    text(value, "displayName"), text(value, "kind"), nullableText(value, "email"),
                    nullableText(value, "phone"), nullableText(value, "notes"),
                    value.path("active").asBoolean());
            case "MERCHANT" -> merchantFingerprint(
                    text(value, "name"), nullableText(value, "notes"),
                    value.path("active").asBoolean());
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
        values.put("BANK", direct("bank", "e.portable_id", ""));
        values.put("BANK_ACCOUNT", direct("company_bank_account", "e.portable_id", ""));
        values.put("BANK_IMPORT_BATCH", direct("bank_import_batch", "e.portable_id", ""));
        values.put("BANK_STATEMENT_LINE", direct("bank_statement_line", "e.portable_id", ""));
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
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
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
