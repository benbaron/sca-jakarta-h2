from pathlib import Path

ROOT = Path('.')


def read(path):
    return (ROOT / path).read_text(encoding='utf-8')


def write(path, content):
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding='utf-8', newline='\n')


def replace_once(path, old, new):
    content = read(path)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f'{path}: expected one occurrence, found {count}: {old[:100]!r}')
    write(path, content.replace(old, new, 1))


# AuditEvent durable portable identity.
replace_once(
    'src/main/java/org/nonprofitbookkeeping/model/AuditEvent.java',
    'import java.time.Instant;\n',
    'import java.time.Instant;\nimport java.util.UUID;\n')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/model/AuditEvent.java',
    '    @ManyToOne(fetch = FetchType.LAZY)\n    @JoinColumn(name = "company_id")\n    private Company company;\n',
    '    @Column(name = "portable_id", nullable = false, unique = true, updatable = false)\n'
    '    private UUID portableId = UUID.randomUUID();\n\n'
    '    @ManyToOne(fetch = FetchType.LAZY)\n'
    '    @JoinColumn(name = "company_id")\n'
    '    private Company company;\n')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/model/AuditEvent.java',
    '    public Long getId() { return id; }\n    public Company getCompany() { return company; }\n',
    '    public Long getId() { return id; }\n'
    '    public UUID getPortableId() { return portableId; }\n'
    '    public Company getCompany() { return company; }\n')

write('src/main/resources/db/migration/V67__audit_event_portable_identity.sql', '''-- P15-S4: give factual audit events durable, database-independent portable identities.
-- Local numeric IDs and polymorphic entity_id text are not stable portable keys.
-- IF NOT EXISTS keeps complete-schema Flyway-history recovery nondestructive.

ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE audit_event SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE audit_event ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE audit_event ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE audit_event ADD CONSTRAINT IF NOT EXISTS uq_audit_event_portable_id UNIQUE (portable_id);
''')

# Portable identity namespace.
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxPortableIdentity.java',
    '    public static String periodCloseEvent(String companyCode, String durableEventKey)\n'
    '    {\n'
    '        return identity("period-close-event", companyCode, durableEventKey);\n'
    '    }\n\n',
    '    public static String periodCloseEvent(String companyCode, String durableEventKey)\n'
    '    {\n'
    '        return identity("period-close-event", companyCode, durableEventKey);\n'
    '    }\n\n'
    '    public static String auditEvent(String companyCode, String durableEventKey)\n'
    '    {\n'
    '        return identity("audit-event", companyCode, durableEventKey);\n'
    '    }\n\n')

write('src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxAuditHistoryExtension.java', '''package org.nonprofitbookkeeping.interchange.sclx;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Governed selected-company factual audit history for SCLX 1.3. */
public final class SclxAuditHistoryExtension
{
    public static final String KEY = "auditHistory";

    private SclxAuditHistoryExtension()
    {
    }

    public static Map<String, Object> value(List<Map<String, Object>> events)
    {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("version", 1);
        value.put("events", List.copyOf(events));
        return Map.copyOf(value);
    }

    public static Map<String, Object> eventEntry(
            String auditEventId,
            Instant occurredAt,
            String actor,
            String actionType,
            String entityType,
            String entityId,
            String summary,
            String beforeValue,
            String afterValue,
            String reason)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("auditEventId", auditEventId);
        entry.put("occurredAt", occurredAt);
        entry.put("actor", actor);
        entry.put("actionType", actionType);
        entry.put("entityType", entityType);
        putOptional(entry, "entityId", entityId);
        entry.put("summary", summary);
        putOptional(entry, "beforeValue", beforeValue);
        putOptional(entry, "afterValue", afterValue);
        putOptional(entry, "reason", reason);
        return Map.copyOf(entry);
    }

    private static void putOptional(Map<String, Object> entry, String key, Object value)
    {
        if (value != null)
        {
            entry.put(key, value);
        }
    }

    public static Data data(SclxExportDocument.Extensions extensions)
    {
        Object raw = extensions.scaJakartaH2().get(KEY);
        if (raw == null)
        {
            return new Data(List.of());
        }
        if (!(raw instanceof Map<?, ?> root))
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.auditHistory must be an object");
        }
        if (!root.keySet().equals(Set.of("version", "events")))
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.auditHistory has unsupported fields");
        }
        if (SclxExtensionValueReader.integer(root, "version", "extensions.scaJakartaH2.auditHistory") != 1)
        {
            throw new IllegalArgumentException("extensions.scaJakartaH2.auditHistory.version must be 1");
        }

        List<EventEntry> events = new ArrayList<>();
        List<Map<?, ?>> eventObjects = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "events", "extensions.scaJakartaH2.auditHistory"),
                "extensions.scaJakartaH2.auditHistory.events",
                Set.of("auditEventId", "occurredAt", "actor", "actionType", "entityType", "entityId",
                        "summary", "beforeValue", "afterValue", "reason"));
        for (int index = 0; index < eventObjects.size(); index++)
        {
            Map<?, ?> value = eventObjects.get(index);
            String path = "extensions.scaJakartaH2.auditHistory.events[" + index + ']';
            events.add(new EventEntry(
                    SclxExtensionValueReader.text(value, "auditEventId", path),
                    SclxExtensionValueReader.instant(value, "occurredAt", path, false),
                    SclxExtensionValueReader.text(value, "actor", path),
                    SclxExtensionValueReader.text(value, "actionType", path),
                    SclxExtensionValueReader.text(value, "entityType", path),
                    SclxExtensionValueReader.optionalText(value, "entityId", path),
                    SclxExtensionValueReader.text(value, "summary", path),
                    SclxExtensionValueReader.optionalText(value, "beforeValue", path),
                    SclxExtensionValueReader.optionalText(value, "afterValue", path),
                    SclxExtensionValueReader.optionalText(value, "reason", path)));
        }
        return new Data(events);
    }

    public static void requireUniqueIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        for (EventEntry event : data.events())
        {
            if (!ids.add(event.auditEventId()))
            {
                throw new IllegalArgumentException("duplicate audit-event identity: " + event.auditEventId());
            }
        }
    }

    public record Data(List<EventEntry> events)
    {
        public Data
        {
            events = List.copyOf(Objects.requireNonNull(events, "events"));
        }
    }

    public record EventEntry(
            String auditEventId,
            Instant occurredAt,
            String actor,
            String actionType,
            String entityType,
            String entityId,
            String summary,
            String beforeValue,
            String afterValue,
            String reason)
    {
    }
}
''')

write('src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxAuditHistorySnapshotAssembler.java', '''package org.nonprofitbookkeeping.interchange.sclx;

import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Maps authoritative selected-company factual audit events into SCLX. */
final class SclxAuditHistorySnapshotAssembler
{
    Map<String, Object> assemble(String companyCode, Company company, List<AuditEvent> events)
    {
        Objects.requireNonNull(companyCode, "companyCode");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(events, "events");
        if (!companyCode.equals(company.getCode()))
        {
            throw new IllegalArgumentException("audit-history company code does not match the selected company");
        }

        List<Map<String, Object>> exportedEvents = events.stream()
                .peek(event -> requireOwnership(event, company))
                .sorted(Comparator.comparing(AuditEvent::getOccurredAt)
                        .thenComparing(event -> event.getPortableId().toString()))
                .map(event -> SclxAuditHistoryExtension.eventEntry(
                        SclxPortableIdentity.auditEvent(
                                companyCode,
                                Objects.requireNonNull(event.getPortableId(),
                                        "audit event portableId").toString()),
                        Objects.requireNonNull(event.getOccurredAt(), "audit event occurredAt"),
                        requireText(event.getActor(), "audit event actor"),
                        requireText(event.getActionType(), "audit event actionType"),
                        requireText(event.getEntityType(), "audit event entityType"),
                        event.getEntityId(),
                        requireText(event.getSummary(), "audit event summary"),
                        event.getBeforeValue(),
                        event.getAfterValue(),
                        event.getReason()))
                .toList();
        return SclxAuditHistoryExtension.value(exportedEvents);
    }

    private static void requireOwnership(AuditEvent event, Company company)
    {
        Objects.requireNonNull(event, "audit event");
        if (event.getCompany() != company)
        {
            throw new IllegalArgumentException("audit event is outside the selected company");
        }
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
''')

# Core snapshot assembly: retain old signature as a compatibility overload and add audit events.
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxCoreSnapshotAssembler.java',
    'import org.nonprofitbookkeeping.model.Account;\n',
    'import org.nonprofitbookkeeping.model.Account;\nimport org.nonprofitbookkeeping.model.AuditEvent;\n')
old_signature = '''    public SclxExportDocument assemble(
            Company company,
            List<Account> accounts,
            List<Fund> funds,
            List<Activity> activities,
            List<Counterparty> counterparties,
            List<Merchant> merchants,
            List<BudgetPlan> budgetPlans,
            List<BudgetLine> budgetLines,
            List<Txn> transactions,
            List<TxnSplit> transactionLines,
            List<TxnSupplementalLine> supplementalDetails,
            SclxBankingSnapshot banking,
            List<FixedAsset> fixedAssets,
            List<FixedAssetDepreciationRun> depreciationRuns,
            List<InventoryItem> inventoryItems,
            List<InventoryMovement> inventoryMovements,
            List<PeriodCloseRangeView> periodCloseRanges,
            List<PeriodCloseEventView> periodCloseEvents,
            Instant exportedAt)
    {
'''
new_signature = '''    public SclxExportDocument assemble(
            Company company,
            List<Account> accounts,
            List<Fund> funds,
            List<Activity> activities,
            List<Counterparty> counterparties,
            List<Merchant> merchants,
            List<BudgetPlan> budgetPlans,
            List<BudgetLine> budgetLines,
            List<Txn> transactions,
            List<TxnSplit> transactionLines,
            List<TxnSupplementalLine> supplementalDetails,
            SclxBankingSnapshot banking,
            List<FixedAsset> fixedAssets,
            List<FixedAssetDepreciationRun> depreciationRuns,
            List<InventoryItem> inventoryItems,
            List<InventoryMovement> inventoryMovements,
            List<PeriodCloseRangeView> periodCloseRanges,
            List<PeriodCloseEventView> periodCloseEvents,
            Instant exportedAt)
    {
        return assemble(
                company, accounts, funds, activities, counterparties, merchants,
                budgetPlans, budgetLines, transactions, transactionLines, supplementalDetails, banking,
                fixedAssets, depreciationRuns, inventoryItems, inventoryMovements,
                periodCloseRanges, periodCloseEvents, List.of(), exportedAt);
    }

    public SclxExportDocument assemble(
            Company company,
            List<Account> accounts,
            List<Fund> funds,
            List<Activity> activities,
            List<Counterparty> counterparties,
            List<Merchant> merchants,
            List<BudgetPlan> budgetPlans,
            List<BudgetLine> budgetLines,
            List<Txn> transactions,
            List<TxnSplit> transactionLines,
            List<TxnSupplementalLine> supplementalDetails,
            SclxBankingSnapshot banking,
            List<FixedAsset> fixedAssets,
            List<FixedAssetDepreciationRun> depreciationRuns,
            List<InventoryItem> inventoryItems,
            List<InventoryMovement> inventoryMovements,
            List<PeriodCloseRangeView> periodCloseRanges,
            List<PeriodCloseEventView> periodCloseEvents,
            List<AuditEvent> auditEvents,
            Instant exportedAt)
    {
'''
replace_once('src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxCoreSnapshotAssembler.java', old_signature, new_signature)
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxCoreSnapshotAssembler.java',
    '        Objects.requireNonNull(periodCloseEvents, "periodCloseEvents");\n'
    '        Objects.requireNonNull(exportedAt, "exportedAt");\n',
    '        Objects.requireNonNull(periodCloseEvents, "periodCloseEvents");\n'
    '        Objects.requireNonNull(auditEvents, "auditEvents");\n'
    '        Objects.requireNonNull(exportedAt, "exportedAt");\n')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxCoreSnapshotAssembler.java',
    '        extensionValues.put(SclxPeriodCloseExtension.KEY,\n'
    '                new SclxPeriodCloseSnapshotAssembler().assemble(companyCode, periodCloseRanges, periodCloseEvents));\n',
    '        extensionValues.put(SclxPeriodCloseExtension.KEY,\n'
    '                new SclxPeriodCloseSnapshotAssembler().assemble(companyCode, periodCloseRanges, periodCloseEvents));\n'
    '        extensionValues.put(SclxAuditHistoryExtension.KEY,\n'
    '                new SclxAuditHistorySnapshotAssembler().assemble(companyCode, company, auditEvents));\n')

# Selected-company query.
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxCoreSnapshotQueryService.java',
    'import org.nonprofitbookkeeping.model.Account;\n',
    'import org.nonprofitbookkeeping.model.Account;\nimport org.nonprofitbookkeeping.model.AuditEvent;\n')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxCoreSnapshotQueryService.java',
    '            List<PeriodCloseEventView> periodCloseEvents = periodCloseService.listEvents(company.getCode());\n\n'
    '            return assembler.assemble(\n',
    '            List<PeriodCloseEventView> periodCloseEvents = periodCloseService.listEvents(company.getCode());\n'
    '            List<AuditEvent> auditEvents = em.createQuery(\n'
    '                            "select e from AuditEvent e "\n'
    '                                    + "where e.company = :company order by e.occurredAt, e.portableId",\n'
    '                            AuditEvent.class)\n'
    '                    .setParameter("company", company)\n'
    '                    .getResultList();\n\n'
    '            return assembler.assemble(\n')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxCoreSnapshotQueryService.java',
    '                    periodCloseRanges,\n'
    '                    periodCloseEvents,\n'
    '                    exportedAt);\n',
    '                    periodCloseRanges,\n'
    '                    periodCloseEvents,\n'
    '                    auditEvents,\n'
    '                    exportedAt);\n')

# Strict validation and section classification.
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportDocumentValidator.java',
    '        validatePeriodClose(SclxPeriodCloseExtension.data(document.extensions()));\n',
    '        validatePeriodClose(SclxPeriodCloseExtension.data(document.extensions()));\n'
    '        validateAuditHistory(SclxAuditHistoryExtension.data(document.extensions()));\n')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportDocumentValidator.java',
    '    private static void validateBankConfiguration(\n',
    '    private static void validateAuditHistory(SclxAuditHistoryExtension.Data data)\n'
    '    {\n'
    '        SclxAuditHistoryExtension.requireUniqueIds(data);\n'
    '    }\n\n'
    '    private static void validateBankConfiguration(\n')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportSection.java',
    '    AUDIT_HISTORY(Support.EXTENSION, "extensions.scaJakartaH2.auditHistory", "Company-owned factual audit events"),\n',
    '    AUDIT_HISTORY(Support.EXTENSION, true, "extensions.scaJakartaH2.auditHistory", "Company-owned factual audit events"),\n')

# Exact counts with a compatibility constructor for all existing callers.
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportCounts.java',
    '        long periodCloseRanges,\n'
    '        long periodCloseEvents,\n'
    '        long warnings,\n',
    '        long periodCloseRanges,\n'
    '        long periodCloseEvents,\n'
    '        long auditEvents,\n'
    '        long warnings,\n')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportCounts.java',
    '                || periodCloseRanges < 0L || periodCloseEvents < 0L\n'
    '                || warnings < 0L || exclusions < 0L || totalEntities < 0L)\n',
    '                || periodCloseRanges < 0L || periodCloseEvents < 0L || auditEvents < 0L\n'
    '                || warnings < 0L || exclusions < 0L || totalEntities < 0L)\n')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportCounts.java',
    '    /** Backward-compatible constructor used before inventory export. */\n',
    '''    /** Backward-compatible constructor used before factual audit-history export. */
    public SclxExportCounts(
            long organizations, long accounts, long funds, long activities, long counterparties, long merchants,
            long budgets, long budgetLines, long transactions, long transactionLines, long supplementalDetails,
            long banks, long bankAccounts, long importBatches, long statementLines, long importIssues,
            long reconciliationSessions, long reconciliationMatches, long fixedAssets, long depreciationRuns,
            long inventoryItems, long inventoryMovements, long periodCloseRanges, long periodCloseEvents,
            long warnings, long exclusions, long totalEntities)
    {
        this(organizations, accounts, funds, activities, counterparties, merchants, budgets, budgetLines,
                transactions, transactionLines, supplementalDetails, banks, bankAccounts, importBatches,
                statementLines, importIssues, reconciliationSessions, reconciliationMatches, fixedAssets,
                depreciationRuns, inventoryItems, inventoryMovements, periodCloseRanges, periodCloseEvents,
                0L, warnings, exclusions, totalEntities);
    }

    /** Backward-compatible constructor used before inventory export. */
''')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportCounts.java',
    '        SclxPeriodCloseExtension.Data periodCloseData = SclxPeriodCloseExtension.data(document.extensions());\n\n',
    '        SclxPeriodCloseExtension.Data periodCloseData = SclxPeriodCloseExtension.data(document.extensions());\n'
    '        SclxAuditHistoryExtension.Data auditHistoryData = SclxAuditHistoryExtension.data(document.extensions());\n\n')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportCounts.java',
    '        long periodCloseRangeCount = periodCloseData.ranges().size();\n'
    '        long periodCloseEventCount = periodCloseData.events().size();\n',
    '        long periodCloseRangeCount = periodCloseData.ranges().size();\n'
    '        long periodCloseEventCount = periodCloseData.events().size();\n'
    '        long auditEventCount = auditHistoryData.events().size();\n')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportCounts.java',
    '                + periodCloseRangeCount + periodCloseEventCount;\n',
    '                + periodCloseRangeCount + periodCloseEventCount + auditEventCount;\n')
replace_once(
    'src/main/java/org/nonprofitbookkeeping/interchange/sclx/SclxExportCounts.java',
    '                inventoryItemCount, inventoryMovementCount, periodCloseRangeCount, periodCloseEventCount,\n'
    '                warningCount, exclusionCount, entityCount);\n',
    '                inventoryItemCount, inventoryMovementCount, periodCloseRangeCount, periodCloseEventCount,\n'
    '                auditEventCount, warningCount, exclusionCount, entityCount);\n')

replace_once(
    'src/main/java/org/nonprofitbookkeeping/ui/SclxExportCoordinator.java',
    '                + "\\n  Period-close events: " + counts.periodCloseEvents()\n'
    '                + "\\n  Total entities: " + counts.totalEntities()\n',
    '                + "\\n  Period-close events: " + counts.periodCloseEvents()\n'
    '                + "\\n  Audit events: " + counts.auditEvents()\n'
    '                + "\\n  Total entities: " + counts.totalEntities()\n')

# Focused migration coverage.
write('src/test/java/org/nonprofitbookkeeping/persistence/AuditEventPortableIdentityMigrationTest.java', '''package org.nonprofitbookkeeping.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AuditEventPortableIdentityMigrationTest
{
    @Test
    public void backfillsDefaultsAndRejectsDuplicatePortableIdentities() throws Exception
    {
        String url = jdbcUrl("audit-event-portable-identity");
        migrateTo(url, "66");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            insertCompany(statement);
            statement.executeUpdate(auditInsertSql(17_001L, "Initial audit fact", null));
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            UUID first = portableId(statement, 17_001L);
            assertNotNull(first);
            assertEquals("Initial audit fact", scalarString(statement,
                    "SELECT summary FROM audit_event WHERE id = 17001"));

            statement.executeUpdate(auditInsertSql(17_002L, "Later audit fact", null));
            assertNotEquals(first, portableId(statement, 17_002L));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                    auditInsertSql(17_003L, "Duplicate identity", first)));
        }
    }

    @Test
    public void toleratesExistingColumnAndConstraintDuringRecovery() throws Exception
    {
        String url = jdbcUrl("audit-event-portable-identity-recovery");
        migrateTo(url, "66");
        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            insertCompany(statement);
            statement.executeUpdate(auditInsertSql(17_001L, "Recovery audit fact", null));
            statement.executeUpdate("ALTER TABLE audit_event ADD COLUMN portable_id UUID");
            statement.executeUpdate("UPDATE audit_event SET portable_id = RANDOM_UUID()");
            statement.executeUpdate("ALTER TABLE audit_event ADD CONSTRAINT "
                    + "uq_audit_event_portable_id UNIQUE (portable_id)");
        }

        migrate(url);

        try (Connection connection = connect(url); Statement statement = connection.createStatement())
        {
            assertNotNull(portableId(statement, 17_001L));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM information_schema.table_constraints "
                            + "WHERE lower(table_name) = 'audit_event' "
                            + "AND lower(constraint_name) = 'uq_audit_event_portable_id'"));
            assertEquals(1L, scalarLong(statement,
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '67' AND success = TRUE"));
        }
    }

    private static void insertCompany(Statement statement) throws SQLException
    {
        statement.executeUpdate("INSERT INTO chart_of_accounts "
                + "(id, name, version, status) VALUES (17001, 'Audit Chart', '1', 'ACTIVE')");
        statement.executeUpdate("INSERT INTO company "
                + "(id, code, display_name, active_chart_of_accounts_id) VALUES "
                + "(17001, 'AUDPORT', 'Audit Portable Company', 17001)");
        statement.executeUpdate("UPDATE chart_of_accounts SET company_id = 17001 WHERE id = 17001");
    }

    private static String auditInsertSql(long id, String summary, UUID portableId)
    {
        String portableColumn = portableId == null ? "" : ", portable_id";
        String portableValue = portableId == null ? "" : ", UUID '" + portableId + "'";
        return "INSERT INTO audit_event "
                + "(id, company_id, occurred_at, actor, action_type, entity_type, entity_id, summary, "
                + "before_value, after_value, reason" + portableColumn + ") VALUES ("
                + id + ", 17001, TIMESTAMP '2026-01-01 00:00:00', 'treasurer', 'UPDATED', "
                + "'Transaction', 'txn-1', '" + summary.replace("'", "''")
                + "', 'before', 'after', 'correction'" + portableValue + ")";
    }

    private static UUID portableId(Statement statement, long id) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(
                "SELECT portable_id FROM audit_event WHERE id = " + id))
        {
            rows.next();
            return rows.getObject(1, UUID.class);
        }
    }

    private static long scalarLong(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static String scalarString(Statement statement, String sql) throws SQLException
    {
        try (ResultSet rows = statement.executeQuery(sql))
        {
            rows.next();
            return rows.getString(1);
        }
    }

    private static void migrateTo(String url, String target)
    {
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target(target).load().migrate();
    }

    private static void migrate(String url)
    {
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .load().migrate();
    }

    private static Connection connect(String url) throws SQLException
    {
        return DriverManager.getConnection(url, "sa", "");
    }

    private static String jdbcUrl(String name)
    {
        return "jdbc:h2:mem:" + name + '-' + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"
                + ";INIT=CREATE SCHEMA IF NOT EXISTS PUBLIC\\\\;SET SCHEMA PUBLIC";
    }
}
''')

# Focused governed extension, ownership, validation, and count coverage.
write('src/test/java/org/nonprofitbookkeeping/interchange/sclx/SclxAuditHistoryExportIntegrationTest.java', '''package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxAuditHistoryExportIntegrationTest
{
    @Test
    void mapsValidatesAndCountsCompanyOwnedAuditFacts()
    {
        Company company = company("TEST");
        AuditEvent event = event(company);

        Map<String, Object> value = new SclxAuditHistorySnapshotAssembler()
                .assemble("TEST", company, List.of(event));
        SclxExportDocument document = document(value);

        new SclxExportDocumentValidator().validate(document);
        SclxAuditHistoryExtension.EventEntry exported =
                SclxAuditHistoryExtension.data(document.extensions()).events().get(0);
        assertEquals(SclxPortableIdentity.auditEvent("TEST", event.getPortableId().toString()),
                exported.auditEventId());
        assertEquals("treasurer", exported.actor());
        assertEquals("UPDATED", exported.actionType());
        assertEquals("Transaction", exported.entityType());
        assertEquals("txn-1", exported.entityId());
        assertEquals("Corrected transaction memo", exported.summary());
        assertEquals("old memo", exported.beforeValue());
        assertEquals("new memo", exported.afterValue());
        assertEquals("clerical correction", exported.reason());

        SclxExportCounts counts = SclxExportCounts.from(document, 0L, 0L);
        assertEquals(1L, counts.auditEvents());
        assertEquals(2L, counts.totalEntities());
        assertTrue(SclxExportSection.AUDIT_HISTORY.includedByCurrentSnapshot());
        assertFalse(SclxExportSection.AUDIT_HISTORY.deferred());
    }

    @Test
    void rejectsCrossCompanyAndDuplicateAuditFacts()
    {
        Company selected = company("TEST");
        AuditEvent foreign = event(company("OTHER"));
        assertThrows(IllegalArgumentException.class,
                () -> new SclxAuditHistorySnapshotAssembler().assemble("TEST", selected, List.of(foreign)));

        Map<String, Object> entry = SclxAuditHistoryExtension.eventEntry(
                SclxPortableIdentity.auditEvent("TEST", "11111111-1111-1111-1111-111111111111"),
                Instant.EPOCH, "actor", "UPDATED", "Transaction", "txn-1", "summary",
                null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> new SclxExportDocumentValidator().validate(
                        document(SclxAuditHistoryExtension.value(List.of(entry, entry)))));
    }

    private static Company company(String code)
    {
        Company company = new Company();
        company.setCode(code);
        company.setDisplayName(code + " Company");
        return company;
    }

    private static AuditEvent event(Company company)
    {
        AuditEvent event = new AuditEvent();
        event.setCompany(company);
        event.setActor("treasurer");
        event.setActionType("UPDATED");
        event.setEntityType("Transaction");
        event.setEntityId("txn-1");
        event.setSummary("Corrected transaction memo");
        event.setBeforeValue("old memo");
        event.setAfterValue("new memo");
        event.setReason("clerical correction");
        return event;
    }

    private static SclxExportDocument document(Map<String, Object> auditHistory)
    {
        return new SclxExportDocument(
                "SCLX", "1.3", Instant.EPOCH,
                new SclxExportDocument.Organization(
                        SclxPortableIdentity.organization("TEST"), "TEST", "Test", "USD",
                        LocalDate.of(2026, 1, 1)),
                List.of(), List.of(), List.of(), List.of(),
                new SclxExportDocument.Extensions(1, Map.of(
                        SclxAuditHistoryExtension.KEY, auditHistory)));
    }
}
''')

# Governing SCLX contract.
replace_once(
    'doc/data-exchange/sclx.md',
    '## 9. Deterministic SCLX 1.3 output\n',
    '''### 8.8 Inventory extension

`extensions.scaJakartaH2.inventory` version 1 contains the selected company inventory items and factual movement history. Items and movements use intrinsic UUID portable identities, retain their authoritative account, fund, quantity, value, status, condition, transaction-provenance, timestamp, and notes fields, and are ordered by portable identity. Cross-company records and unresolved account, fund, item, or canonical transaction references are blocking export errors.

### 8.9 Period-close extension

`extensions.scaJakartaH2.periodClose` version 1 contains authoritative calculated or custom close ranges and their factual close/reopen events. Ranges and events use their intrinsic UUID identities namespaced by company. Range status, close and reopen actors/timestamps/reasons, event type, and event-to-range references are preserved. Legacy accounting-period and close-run compatibility records are not substituted for this authority.

### 8.10 Factual audit-history extension

`extensions.scaJakartaH2.auditHistory` version 1 contains every `AuditEvent` whose `company_id` is the selected company. It contains one `events` array. Each entry contains `auditEventId`, `occurredAt`, `actor`, `actionType`, `entityType`, optional `entityId`, `summary`, optional `beforeValue`, optional `afterValue`, and optional `reason`.

`auditEventId` is `audit-event:<company-code>:<portable-uuid>` using the intrinsic UUID added to `audit_event`; it never uses `audit_event.id`, a mutable summary, or the polymorphic `entityId`. Events are ordered by `occurredAt` and then portable UUID. The polymorphic entity type and identifier are preserved as factual subject text and are not rewritten into a reference to an unrelated SCLX object. This prevents a legacy local ID from being presented as a portable foreign key.

Application-global audit rows, unresolved historical rows with no company owner, legacy `ApprovalAuditRecord` workflow records, users, roles, authentication facts, and UI state are not included. Duplicate identities, blank required fields, unsupported extension fields, and any event owned by another company are blocking export errors. Audit events contribute to exact entity counts, and `AUDIT_HISTORY` is no longer reported as deferred.

## 9. Deterministic SCLX 1.3 output
''')
replace_once(
    'doc/data-exchange/sclx.md',
    '`SclxExportResult` reports the final destination, format/version, fixed export timestamp, portable\norganization identity, byte count, SHA-256, core and activity entity counts, deferred-extension warnings, and the\n',
    '`SclxExportResult` reports the final destination, format/version, fixed export timestamp, portable\norganization identity, byte count, SHA-256, exact governed entity counts, deferred-extension warnings, and the\n')

# Persistence authority and interface inventory reconciliation.
replace_once(
    'doc/persistence-authority-inventory.md',
    'Status: P00 inventory of current main, updated through P15-S1 company ownership, migration diagnostics, and external interchange identity.',
    'Status: P00 inventory of current main, updated through P15-S4 selected-company audit-history identity and SCLX export.')
replace_once(
    'doc/persistence-authority-inventory.md',
    '| Audit/approval | `AuditEvent` is factual JPA audit history; `ApprovalAuditRecord` remains a legacy approval-oriented repository/panel | yes for both stored record types | legacy approval terminology conflicts with product decision outside Period Close | P12 should rename/scope the remaining approval audit surface |',
    '| Audit/approval | `AuditEvent` is company-owned factual JPA audit history with an intrinsic portable UUID; `ApprovalAuditRecord` remains a legacy approval-oriented repository/panel | yes for both stored record types | the two record families are distinct and legacy approval records are not selected-company SCLX authority | export only company-owned `AuditEvent`; retain the legacy surface as compatibility until deliberately replaced |')
replace_once(
    'doc/persistence-authority-inventory.md',
    '## Fund master-data authority\n',
    '''## Factual audit-history authority

- `AuditEvent` is the selected-company authority for material-change audit facts. V61 supplies explicit nullable company ownership for recoverable historical data, and V67 supplies a non-null intrinsic UUID portable identity without rewriting local IDs or polymorphic subject text.
- New JPA and SQL-created business audit events receive a UUID through entity initialization or the H2 default. Existing events are backfilled nondestructively, and duplicate portable identities are rejected.
- SCLX exports only events whose `company_id` is the selected company. Application-global or unresolved historical rows remain outside active-company export rather than being guessed.
- `ApprovalAuditRecord` is a separate legacy workflow-oriented compatibility record and is not substituted for `AuditEvent` in SCLX.

## Fund master-data authority
''')
replace_once(
    'doc/interface-operation-matrix.md',
    'Status: P00 inventory of current main, updated through P15-S3 Chart of Accounts JSON implementation and verification.',
    'Status: P00 inventory of current main, updated through P15-S4 selected-company SCLX factual audit-history export.')

# Begin plan reconciliation. PR/head are finalized after the implementation commit and draft PR exist.
replace_once(
    'doc/PLAN.md',
    '''---
plan_version: 95
active_phase: P15
active_slice: P15-S4
active_status: VERIFYING
active_branch: codex/P15-S4-C3-inventory-sclx-export
active_pull_request: 223
active_head: "8a7eec251d4bba14c3d7cc0e167e2e2bebfdfe47"
next_action: "Review and merge PR #223 after owner authorization, then start a fresh P15-S4 branch for period-close and factual audit-history export."
---''',
    '''---
plan_version: 96
active_phase: P15
active_slice: P15-S4
active_status: IN_PROGRESS
active_branch: codex/P15-S4-C6-audit-history-sclx-export
active_pull_request: null
active_head: "PENDING_IMPLEMENTATION_COMMIT"
next_action: "Complete factual AuditEvent portable identity and selected-company SCLX export on P15-S4-C6, open a draft PR, and run full Maven PR Tests."
---''')
replace_once(
    'doc/PLAN.md',
    '**Status:** IN_PROGRESS at P15-S4; P15-S0 through P15-S3 DONE',
    '**Status:** IN_PROGRESS at P15-S4; P15-S0 through P15-S3 DONE; inventory and period-close export merged through PR #225')
replace_once(
    'doc/PLAN.md',
    'Status: VERIFYING on branch `codex/P15-S4-C3-inventory-sclx-export` in draft PR #223.\n\nCurrent tested implementation head: `8a7eec251d4bba14c3d7cc0e167e2e2bebfdfe47`',
    'Status: IN_PROGRESS on branch `codex/P15-S4-C6-audit-history-sclx-export`; draft PR pending.\n\nCurrent implementation head: pending first P15-S4-C6 implementation commit.')
replace_once(
    'doc/PLAN.md',
    '- PR #223: corrective selected-company inventory item and movement export implementation, including deterministic identities/order, strict ownership/reference validation, exact counts, completion-summary integration, and focused tests.\n',
    '- PR #223: corrective selected-company inventory item and movement export implementation, including deterministic identities/order, strict ownership/reference validation, exact counts, completion-summary integration, and focused tests.\n'
    '- PR #224: governed period-close extension foundation, range/event snapshot mapping, and portable identity contract; merged before production query/count/summary integration was complete.\n'
    '- PR #225: corrective period-close production integration, strict validation, exact range/event counts, completion-summary integration, focused tests, and successful Maven PR Tests; merged at `6959f57daf840b9f93edb0bd9ed9a8d188685170`.\n'
    '- P15-S4-C6: add durable AuditEvent portable identity and selected-company `extensions.scaJakartaH2.auditHistory` export, validation, counts, tests, and governing-document reconciliation.\n')
replace_once(
    'doc/PLAN.md',
    '## P15-S5 — SCLX preview, mapping, and atomic import\n',
    '''### P15-S4-C6 — Selected-company factual audit-history export

Status: IN_PROGRESS on `codex/P15-S4-C6-audit-history-sclx-export`; draft PR pending.

Scope:

- Add a recovery-safe V67 UUID portable identity for `AuditEvent` without serializing or deriving identity from local numeric IDs or polymorphic `entityId` text.
- Export every factual `AuditEvent` owned by the selected company under governed `extensions.scaJakartaH2.auditHistory` version 1.
- Preserve actor, action/entity types, optional subject identifier, summary, before/after values, reason, and timestamp with deterministic ordering.
- Strictly validate shape and duplicate identity, include exact audit-event counts and completion-summary output, and remove only the audit-history deferred warning.
- Keep application-global/unresolved audit rows, legacy `ApprovalAuditRecord`, users/authentication, UI state, and other-company records excluded.
- Add migration recovery/default/uniqueness tests and focused extension/ownership/count tests.

Current validation status:

- Implementation publication and Maven PR Tests pending.

Next exact action:

- Publish the implementation commit, open a draft PR, run full Maven PR Tests, correct any failures, and update this handoff with the exact head and run.

## P15-S5 — SCLX preview, mapping, and atomic import
''')

print('P15-S4-C6 patch applied')
