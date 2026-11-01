package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.*;
import org.nonprofitbookkeeping.service.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.interchange.sclx.NativeRecoveryFixture.*;
import static org.nonprofitbookkeeping.service.DatabaseSnapshotTestSupport.rows;

/** Native recovery must preserve current committed state, not merely open a structurally valid older image. */
class NativeDatabaseRecoveryTest
{
    @TempDir Path directory;

    @Test
    void repeatedOnlineBackupsRetainEveryCurrentRowAndTriggerAcrossValidationCloseAndRestart() throws Exception
    {
        Path source = directory.resolve("source");
        try (Jpa jpa = new Jpa(source))
        {
            var fixture = NativeRecoveryFixture.populate(jpa, source);
            var transfer = new DatabaseTransferService(() -> source, ignored -> fail("Restore must not switch the active database"));
            for (int generation = 0; generation < 3; generation++)
            {
                if (generation > 0)
                {
                    new TransactionEntryService(jpa).enter(receipt(fixture, LocalDate.of(2027, 1, 16 + generation), "New committed source generation " + generation));
                }
                var expected = rows(source);
                for (String table : List.of("budget_category_alias", "company_ui_state", "company_ui_preference", "app_user_credential", "user_company_role", "security_event"))
                    assertFalse(expected.get(table).isEmpty(), table);
                Path restored = directory.resolve("restored-" + generation);
                var backup = transfer.backUpDatabase(directory.resolve("generation-" + generation + ".zip"));
                assertEquals(2, backup.counts().companies());
                assertEquals(33 + generation, backup.counts().transactions());
                var recovery = transfer.restoreDatabaseCopy(backup.backupFile(), restored);
                assertTrue(recovery.validated());
                assertEquals(backup.counts(), recovery.counts(), "Restoration must retain the current committed image");
                assertRows(expected, rows(source), "Source after backup/restore");
                try (Jpa copy = new Jpa(restored))
                {
                    assertRows(expected, rows(restored), "Validated copy generation " + generation);
                }
                try (Jpa copy = new Jpa(restored))
                {
                    assertRows(expected, rows(restored), "Reopened copy generation " + generation);
                    assertEquals(fixture.transfer, new FundTransferService(copy).save(fixture.base.command(fixture.transferRequest, "75")));
                    assertEquals(fixture.closedRange, new PeriodCloseRangeService(copy).closeRange(COMPANY, CLOSED_START, CLOSED_END,
                            "CUSTOM", "tester", "Retained closed period", fixture.closedRange.id()));
                    assertRows(expected, rows(restored), "Native exact retries");
                    var entries = new TransactionEntryService(copy);
                    assertThrows(ClosedPeriodRangeException.class, () -> entries.enter(receipt(fixture, CLOSED_START, "Protected restored period")));
                    assertRows(expected, rows(restored), "Rejected closed-period write");
                    long highest;
                    try (var em = copy.em()) { highest = em.createQuery("select max(t.id) from Txn t", Long.class).getSingleResult(); }
                    var entered = entries.enter(receipt(fixture, LocalDate.of(2027, 2, 1), "Restored identity allocation"));
                    assertTrue(entered.id() > highest, "Restored generated IDs must not collide with retained history");
                    assertEquals(expected.get("txn").size() + 1, rows(restored).get("txn").size());
                }
                assertRows(expected, rows(source), "Writes to restored copy cannot mutate source");
            }
        }
    }

    @Test
    void genuineOldEngineBackupRetainsEveryOriginalColumnRowAndTriggerThroughUpgradeAndRestart() throws Exception
    {
        var json = new ObjectMapper();
        JsonNode manifest;
        try (var stream = getClass().getResourceAsStream("/fixtures/recovery/h2-2.2.224-v92.json"))
        {
            assertNotNull(stream); manifest = json.readTree(stream);
        }
        assertEquals("2.2.224", manifest.path("sourceH2").asText());
        assertEquals("10.17.0", manifest.path("sourceFlyway").asText());
        Path archive = directory.resolve("legacy.zip");
        try (var stream = getClass().getResourceAsStream("/fixtures/recovery/h2-2.2.224-v92.zip"))
        {
            assertNotNull(stream); Files.copy(stream, archive);
        }
        assertEquals(manifest.path("backupSha256").asText(), sha256(Files.readAllBytes(archive)), "Validate the frozen old-engine artifact");
        Path active = directory.resolve("active"), restored = directory.resolve("legacy-copy");
        try (Jpa jpa = new Jpa(active))
        {
            var before = rows(active);
            var transfer = new DatabaseTransferService(() -> active, ignored -> fail("Restore must not switch active database"));
            var recovery = transfer.restoreDatabaseCopy(archive, restored);
            assertTrue(recovery.validated());
            assertEquals(2, recovery.counts().companies()); assertEquals(33, recovery.counts().transactions()); assertEquals(73, recovery.counts().transactionSplits());
            assertRows(before, rows(active), "Legacy upgrade cannot alter the active database");
        }
        Map<String, List<String>> upgraded;
        try (Jpa jpa = new Jpa(restored))
        {
            assertLegacy(manifest, restored);
            upgraded = rows(restored);
        }
        try (Jpa jpa = new Jpa(restored))
        {
            assertLegacy(manifest, restored);
            assertRows(upgraded, rows(restored), "Restart after old-engine upgrade");
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                assertThrows(ClosedPeriodRangeException.class, () -> PeriodCloseRangeService.requireOpen(em, COMPANY, CLOSED_START, "Legacy restored protection"));
                em.getTransaction().rollback();
            }
            long bank, fund, income, highest;
            try (var em = jpa.em())
            {
                bank = account(em, "1000"); income = account(em, "4000");
                fund = em.createQuery("select f.id from Fund f where f.company.code='DEFAULT' and f.code='GENERAL'", Long.class).getSingleResult();
                highest = em.createQuery("select max(t.id) from Txn t", Long.class).getSingleResult();
            }
            var entered = new TransactionEntryService(jpa).enter(new TransactionCommand(LocalDate.of(2027, 2, 1), null, "Legacy upgraded allocation", null,
                    List.of(FundTransferTestFixture.line(bank, fund, "1", "0"), FundTransferTestFixture.line(income, fund, "0", "1"))));
            assertTrue(entered.id() > highest);
            assertEquals(upgraded.get("txn").size() + 1, rows(restored).get("txn").size());
        }
        assertEquals(manifest.path("backupSha256").asText(), sha256(Files.readAllBytes(archive)), "Migration must never rewrite the original backup");
    }

    @Test
    void olderHotImageRetainsAllOriginalFactsAndUsableIndexesAfterCopyFinalization() throws Exception
    {
        Path archive = directory.resolve("affected.zip"), active = directory.resolve("active"), target = directory.resolve("affected-copy");
        JsonNode manifest;
        try (var stream = getClass().getResourceAsStream("/fixtures/recovery/h2-2.2.224-v92.json"))
        {
            assertNotNull(stream); manifest = new ObjectMapper().readTree(stream);
        }
        try (var stream = getClass().getResourceAsStream("/fixtures/recovery/h2-2.2.224-hot-affected.zip"))
        {
            assertNotNull(stream); Files.copy(stream, archive);
        }
        String digest = sha256(Files.readAllBytes(archive));
        assertEquals(manifest.path("affectedHotBackupSha256").asText(), digest);
        try (Jpa jpa = new Jpa(active))
        {
            var before = rows(active);
            var service = new DatabaseTransferService(() -> active, ignored -> fail("Finalization cannot switch the active database"));
            var recovery = service.restoreDatabaseCopy(archive, target);
            assertTrue(recovery.validated());
            assertEquals(2, recovery.counts().companies()); assertEquals(33, recovery.counts().transactions());
            assertLegacy(manifest, target);
            try (Jpa copy = new Jpa(target); var em = copy.em())
            {
                assertTrue(account(em, "1000") > 0); assertTrue(account(em, "4000") > 0);
            }
            try (Jpa copy = new Jpa(target); var em = copy.em())
            {
                assertLegacy(manifest, target);
                assertTrue(account(em, "1000") > 0); assertTrue(account(em, "4000") > 0);
                em.getTransaction().begin();
                assertThrows(ClosedPeriodRangeException.class, () -> PeriodCloseRangeService.requireOpen(em, COMPANY, CLOSED_START, "Finalized old-image protection"));
                em.getTransaction().rollback();
            }
            assertRows(before, rows(active), "Old-image finalization cannot mutate the active database");
            try (var paths = Files.list(directory))
            {
                assertTrue(paths.noneMatch(p -> p.getFileName().toString().startsWith(".npbk-restore-")), "Finalization removes its isolated workspace");
            }
        }
        assertEquals(digest, sha256(Files.readAllBytes(archive)), "Finalization never rewrites the backup");
    }

    private static long account(jakarta.persistence.EntityManager em, String code)
    {
        return em.createQuery("select a.id from Account a where a.chart.company.code='DEFAULT' and a.code=:code", Long.class).setParameter("code", code).getSingleResult();
    }

    private static TransactionCommand receipt(NativeRecoveryFixture fixture, LocalDate date, String memo)
    {
        return new TransactionCommand(date, null, memo, null, List.of(
                FundTransferTestFixture.line(fixture.base.bank(), fixture.base.source(), "1", "0"),
                FundTransferTestFixture.line(fixture.base.income(), fixture.base.source(), "0", "1")));
    }

    private static void assertRows(Map<String, List<String>> expected, Map<String, List<String>> actual, String phase)
    {
        assertEquals(expected.keySet(), actual.keySet(), phase + " tables/triggers");
        expected.forEach((table, values) ->
        {
            assertEquals(values.size(), actual.get(table).size(), phase + "/" + table + " row count");
            for (int i = 0; i < values.size(); i++) assertEquals(values.get(i), actual.get(table).get(i), phase + "/" + table + " row " + i);
        });
    }

    /** Compare every original field of the frozen legacy image, not selected balances or entity counts. */
    private static void assertLegacy(JsonNode manifest, Path database) throws Exception
    {
        try (var connection = DriverManager.getConnection(DatabaseMigrationService.jdbcUrlFor(database), "sa", ""))
        {
            var tables = new TreeSet<String>();
            try (var rs = connection.getMetaData().getTables(null, "public", null, new String[]{"BASE TABLE"}))
            {
                while (rs.next()) tables.add(rs.getString("TABLE_NAME"));
            }
            var expectedTables = new TreeSet<String>(); manifest.path("tables").fieldNames().forEachRemaining(expectedTables::add);
            assertTrue(expectedTables.size() > 50); assertEquals(expectedTables, tables);
            for (String table : tables)
            {
                var expected = manifest.path("tables").path(table);
                var columns = new ArrayList<String>(); var encoded = new ArrayList<String>();
                try (var statement = connection.createStatement(); var rs = statement.executeQuery("select * from \"" + table.replace("\"", "\"\"") + "\""))
                {
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) columns.add(rs.getMetaData().getColumnName(i));
                    assertEquals(jsonStrings(expected.path("columns")), columns, table + " original columns");
                    while (rs.next())
                    {
                        var row = new StringBuilder();
                        for (int i = 1; i <= columns.size(); i++)
                        {
                            String value = rs.getString(i); row.append(value == null ? "N;" : value.length() + ":" + value + ";");
                        }
                        encoded.add(row.toString());
                    }
                }
                Collections.sort(encoded);
                assertEquals(expected.path("rows").asInt(), encoded.size(), table + " legacy row count");
                assertEquals(expected.path("sha256").asText(), sha256(String.join("\n", encoded).getBytes(StandardCharsets.UTF_8)), table + " every original field");
            }
        }
        assertEquals(jsonStrings(manifest.path("triggers")), rows(database).get("<trigger inventory>"), "Legacy guard definitions");
    }

    private static List<String> jsonStrings(JsonNode node)
    {
        var values = new ArrayList<String>(); node.forEach(v -> values.add(v.asText())); return values;
    }

    private static String sha256(byte[] bytes) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
