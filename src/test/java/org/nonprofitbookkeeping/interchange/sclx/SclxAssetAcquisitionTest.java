package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.AssetAcquisitionTestFixture.*;

class SclxAssetAcquisitionTest
{
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();

    private Path source() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("source")))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa); var service = f.service(jpa);
            var purchased = service.recordAcquisition(service.previewAcquisition(new AssetAcquisitionCommand(null, f.command("Purchased", "1000.1234"),
                    AssetAcquisitionSource.NEW_PURCHASE, f.base().bank(), null), UUID.randomUUID()));
            long transaction = new TransactionEntryService(jpa).enter(new TransactionCommand(DATE, null, "Shared invoice", null,
                    List.of(FundTransferTestFixture.line(f.asset(), f.base().source(), "1000", "0"),
                            FundTransferTestFixture.line(f.base().bank(), f.base().source(), "0", "1000")))).id();
            long split;
            try (EntityManager em = jpa.em())
            {
                split = em.createQuery("select s.id from TxnSplit s where s.txn.id = :txn and s.account.id = :asset", Long.class)
                        .setParameter("txn", transaction).setParameter("asset", f.asset()).getSingleResult();
            }
            for (String cost : List.of("600", "400"))
            {
                service.recordAcquisition(service.previewAcquisition(new AssetAcquisitionCommand(null, f.command("Invoice " + cost, cost),
                        AssetAcquisitionSource.EXISTING_ENTRY, null, split), UUID.randomUUID()));
            }
            service.recordAcquisition(service.previewAcquisition(new AssetAcquisitionCommand(null, f.command("Opening", "800"),
                    AssetAcquisitionSource.REGISTER_ONLY, null, null), UUID.randomUUID()));
            service.create(f.command("Legacy", "500"));
            service.runMonthlyDepreciation(purchased.id(), DATE.plusMonths(1), "Portable completed depreciation");
            var document = new SclxCoreSnapshotQueryService(jpa, () -> COMPANY).query(Instant.EPOCH);
            byte[] bytes = new SclxJsonSerializer().serialize(document);
            assertArrayEquals(bytes, new SclxJsonSerializer().serialize(document));
            Path file = directory.resolve("source.sclx"); Files.write(file, bytes); return file;
        }
    }

    private static void target(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin(); Company company = em.createQuery("from Company c where c.code = 'DEFAULT'", Company.class).getSingleResult();
            ChartOfAccounts chart = new ChartOfAccounts(); chart.setCompany(company); chart.setName("Target"); chart.setVersion("1"); chart.setStatus(ChartStatus.ACTIVE); em.persist(chart); company.setActiveChartOfAccounts(chart);
            em.getTransaction().commit();
        }
    }

    @Test
    void roundTripRestartRetryRetainsSharedClaimsLegacyAndReceiptProvenance() throws Exception
    {
        Path file = source(); Path database = directory.resolve("target");
        assertEquals(4, JSON.readTree(file.toFile()).path("extensions").path("scaJakartaH2").path("assetAcquisitions").path("assets").size());
        try (Jpa jpa = new Jpa(database))
        {
            target(jpa); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(file);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            var result = new SclxImportCommitService(jpa, () -> COMPANY).commit(file, preview, "tester", true, true);
            assertTrue(result.committed(), result.toString());
        }
        try (Jpa jpa = new Jpa(database))
        {
            var rows = new FixedAssetService(jpa).listAssets(COMPANY);
            assertEquals(5, rows.size());
            for (var row : rows)
            {
                if (row.name().startsWith("Invoice"))
                {
                    assertEquals("1000.0000", row.acquisition().claimed().toPlainString());
                    assertEquals("0.0000", row.acquisition().remaining().toPlainString());
                }
                if (row.name().equals("Legacy")) { assertNull(row.acquisition().source()); }
                if (row.name().equals("Opening")) { assertEquals(AssetAcquisitionSource.REGISTER_ONLY, row.acquisition().source()); }
            }
            long audits = count(jpa, "select count(a) from AuditEvent a");
            var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(file);
            assertTrue(preview.operation().items().stream().allMatch(i -> i.identityMatch() == InterchangeIdentityMatch.IDENTICAL));
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(file, preview, "tester", true, true).committed());
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            assertEquals(4, count(jpa, "select count(a) from AuditEvent a where a.actionType = 'FIXED_ASSET_ACQUISITION_ACCEPTED'"));
            assertEquals(4, count(jpa, "select count(t) from Txn t"));
            assertEquals(1, count(jpa, "select count(r) from FixedAssetDepreciationRun r where r.fixedAsset.acquisitionSplit is not null"));
            var reexport = JSON.readTree(new SclxJsonSerializer().serialize(new SclxCoreSnapshotQueryService(jpa, () -> COMPANY).query(Instant.EPOCH)));
            assertEquals(SclxAssetAcquisitions.parse(JSON.readTree(file.toFile())), SclxAssetAcquisitions.parse(reexport));
        }
    }

    @Test
    void changedOrMissingFactsConflictOnWholeAssetAndKeepTargetRetainsLinks() throws Exception
    {
        Path file = source(); ObjectNode original = (ObjectNode)JSON.readTree(file.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("retained")))
        {
            target(jpa); var service = new SclxImportPreviewService(jpa, () -> COMPANY);
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(file, service.preview(file), "tester", true, true).committed());
            for (boolean absent : List.of(false, true))
            {
                ObjectNode root = original.deepCopy(); ObjectNode app = (ObjectNode)root.path("extensions").path("scaJakartaH2");
                if (absent) { app.remove("assetAcquisitions"); }
                else
                {
                    for (var fact : app.path("assetAcquisitions").path("assets"))
                    {
                        if ("NEW_PURCHASE".equals(fact.path("source").asText())) { ((ObjectNode)fact).put("source", "EXISTING_ENTRY"); }
                    }
                }
                Path changed = directory.resolve("changed-" + absent + ".sclx"); JSON.writeValue(changed.toFile(), root);
                var preview = service.preview(changed);
                assertTrue(preview.operation().items().stream().anyMatch(i -> i.entityType().equals("FIXED_ASSET") && i.identityMatch() == InterchangeIdentityMatch.CONFLICT));
                var keep = preview.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                        .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
                var resolved = service.preview(changed, List.of(), keep); assertFalse(resolved.hasBlockingErrors(), () -> resolved.operation().messages().toString());
                assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(changed, resolved, "tester", true, true).committed());
                assertEquals(3, count(jpa, "select count(a) from FixedAsset a where a.acquisitionSplit is not null"));
                assertEquals(1, count(jpa, "select count(a) from FixedAsset a where a.acquisitionSource = org.nonprofitbookkeeping.model.AssetAcquisitionSource.NEW_PURCHASE"));
            }
        }
    }

    @Test
    void legacyAbsenceDoesNotGuessProvenance() throws Exception
    {
        ObjectNode root = (ObjectNode)JSON.readTree(source().toFile()); ((ObjectNode)root.path("extensions").path("scaJakartaH2")).remove("assetAcquisitions");
        Path file = directory.resolve("legacy.sclx"); JSON.writeValue(file.toFile(), root);
        try (Jpa jpa = new Jpa(directory.resolve("legacy")))
        {
            target(jpa); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(file);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(file, preview, "tester", true, true).committed());
            assertEquals(5, count(jpa, "select count(a) from FixedAsset a"));
            assertEquals(0, count(jpa, "select count(a) from FixedAsset a where a.acquisitionSource is not null"));
        }
    }

    @Test
    void strictMetadataRejectsBadShapesReferencesAndOverclaimsAndLateFailureRollsBack() throws Exception
    {
        Path file = source(); ObjectNode original = (ObjectNode)JSON.readTree(file.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("invalid")))
        {
            target(jpa); var service = new SclxImportPreviewService(jpa, () -> COMPANY);
            for (String mutation : List.of("version", "source", "falsepurchase", "null", "foreign", "duplicate", "shape", "missingline", "overclaim", "date"))
            {
                ObjectNode root = original.deepCopy(); ObjectNode extension = (ObjectNode)root.path("extensions").path("scaJakartaH2").path("assetAcquisitions");
                ArrayNode facts = (ArrayNode)extension.path("assets");
                ObjectNode first = null;
                for (var fact : facts)
                {
                    if (fact.has("lineId"))
                    {
                        first = (ObjectNode)fact;
                        break;
                    }
                }
                assertNotNull(first);
                switch (mutation)
                {
                    case "version" -> extension.put("version", 4294967297L);
                    case "source" -> first.put("source", "UNKNOWN");
                    case "falsepurchase" -> {
                        for (var fact : facts)
                        {
                            if ("EXISTING_ENTRY".equals(fact.path("source").asText()))
                            {
                                ((ObjectNode)fact).put("source", "NEW_PURCHASE");
                                break;
                            }
                        }
                    }
                    case "null" -> first.putNull("source");
                    case "foreign" -> first.put("assetId", "foreign-asset");
                    case "duplicate" -> facts.add(first.deepCopy());
                    case "shape" -> first.put("extra", true);
                    case "missingline" -> first.put("lineId", "missing");
                    case "overclaim", "date" -> {
                        for (var asset : root.path("extensions").path("scaJakartaH2").path("fixedAssets").path("assets"))
                        {
                            if (asset.path("assetId").asText().equals(first.path("assetId").asText()))
                            {
                                ((ObjectNode)asset).put(mutation.equals("date") ? "acquisitionDate" : "acquisitionCost",
                                        mutation.equals("date") ? "2026-05-01" : "999999.0000");
                            }
                        }
                    }
                }
                Path changed = directory.resolve("invalid-" + mutation + ".sclx"); JSON.writeValue(changed.toFile(), root);
                assertTrue(service.preview(changed).hasBlockingErrors(), mutation);
            }
            var preview = service.preview(file); long audits = count(jpa, "select count(a) from AuditEvent a");
            var reached = new java.util.concurrent.atomic.AtomicBoolean();
            // Throw after all assets, acquisition links and the completed depreciation run, before identity receipts commit.
            var commit = new SclxImportCommitService(jpa, () -> COMPANY, null, writes -> {
                if (writes == 19) { reached.set(true); throw new IllegalStateException("Synthetic late import failure"); }
            }).commit(file, preview, "tester", true, true);
            assertFalse(commit.committed(), commit.toString()); assertTrue(reached.get());
            assertEquals(0, count(jpa, "select count(a) from FixedAsset a"));
            assertEquals(0, count(jpa, "select count(t) from Txn t"));
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            assertEquals(0, count(jpa, "select count(i) from InterchangeIdentity i"));
        }
    }

    @Test
    void retainedTargetClaimsRejectAnOtherwiseValidNewSourceClaimAtomically() throws Exception
    {
        Path file = source(); ObjectNode root = (ObjectNode)JSON.readTree(file.toFile());
        ObjectNode app = (ObjectNode)root.path("extensions").path("scaJakartaH2");
        ArrayNode facts = (ArrayNode)app.path("assetAcquisitions").path("assets");
        String sharedLine = null; String assetId = null;
        for (var fact : facts)
        {
            if ("EXISTING_ENTRY".equals(fact.path("source").asText()))
            {
                sharedLine = fact.path("lineId").asText(); assetId = fact.path("assetId").asText();
                ((ObjectNode)fact).put("source", "REGISTER_ONLY"); ((ObjectNode)fact).remove("lineId");
            }
        }
        ArrayNode assets = (ArrayNode)app.path("fixedAssets").path("assets"); ObjectNode additional = null;
        for (var asset : assets)
        {
            if (asset.path("assetId").asText().equals(assetId)) { additional = asset.deepCopy(); }
        }
        assertNotNull(additional);
        String newId = SclxPortableIdentity.fixedAsset(COMPANY, UUID.randomUUID().toString());
        additional.put("assetId", newId); additional.put("name", "Extra source claim"); additional.put("acquisitionCost", "1.0000");
        assets.add(additional); facts.addObject().put("assetId", newId).put("source", "EXISTING_ENTRY").put("lineId", sharedLine);
        Path changed = directory.resolve("retained-capacity.sclx"); JSON.writeValue(changed.toFile(), root);
        try (Jpa jpa = new Jpa(directory.resolve("capacity")))
        {
            target(jpa); var previewService = new SclxImportPreviewService(jpa, () -> COMPANY);
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(file, previewService.preview(file), "tester", true, true).committed());
            var preview = previewService.preview(changed);
            var keep = preview.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                    .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
            var resolved = previewService.preview(changed, List.of(), keep);
            assertFalse(resolved.hasBlockingErrors(), () -> resolved.operation().messages().toString());
            long audits = count(jpa, "select count(a) from AuditEvent a");
            var result = new SclxImportCommitService(jpa, () -> COMPANY).commit(changed, resolved, "tester", true, true);
            assertFalse(result.committed());
            assertEquals(5, count(jpa, "select count(a) from FixedAsset a"));
            assertEquals(3, count(jpa, "select count(a) from FixedAsset a where a.acquisitionSplit is not null"));
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
        }
    }

    private static long count(Jpa jpa, String query)
    {
        try (EntityManager em = jpa.em()) { return em.createQuery(query, Long.class).getSingleResult(); }
    }
}
