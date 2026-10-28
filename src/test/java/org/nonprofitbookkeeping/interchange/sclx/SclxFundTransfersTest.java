package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.FundTransferTestFixture.*;

class SclxFundTransfersTest
{
    @TempDir Path directory;
    static final ObjectMapper JSON = new ObjectMapper();
    static final UUID REQUEST = UUID.fromString("de6a757a-3c68-4b3a-b671-c32c787fc145");

    Path source() throws Exception
    {
        Path db = directory.resolve("source"), file = directory.resolve("source.sclx");
        try (Jpa jpa = new Jpa(db))
        {
            var fixture = seed(jpa); var service = new FundTransferService(jpa);
            var original = service.save(fixture.command(REQUEST, "75.1234"));
            var reverse = service.reverse(original.id(), DATE.plusDays(1), "Synthetic reversal reason");
            service.reverse(reverse.id(), DATE.plusDays(2), "Restore allocation");
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                for (var status : List.of(FundTransferStatus.DRAFT, FundTransferStatus.VOID))
                {
                    var fact = new FundTransfer(); fact.setFromFund(em.find(Fund.class, fixture.source())); fact.setToFund(em.find(Fund.class, fixture.destination()));
                    fact.setTransferDate(DATE); fact.setAmount(new BigDecimal("5.0000")); fact.setStatus(status); fact.setMemo(null);
                    em.persist(fact);
                }
                em.find(Fund.class, fixture.source()).setActive(false);
                em.getTransaction().commit();
            }
            var document = new SclxCoreSnapshotQueryService(jpa).query(Instant.EPOCH);
            var bytes = new SclxJsonSerializer().serialize(document);
            assertArrayEquals(bytes, new SclxJsonSerializer().serialize(document));
            Files.write(file, bytes);
        }
        return file;
    }

    @Test
    void completeGraphLegacyFactsAndNativeProtectionSurviveRestartAndExactRetry() throws Exception
    {
        Path file = source(), db = directory.resolve("target");
        var expected = SclxFundTransfers.parse(JSON.readTree(file.toFile()));
        try (Jpa jpa = new Jpa(db))
        {
            commit(jpa, "DEFAULT", file);
            assertEquals(expected, exported(jpa, "DEFAULT"));
            assertEquals(5, count(jpa, "FundTransfer")); assertEquals(4, count(jpa, "Txn")); assertEquals(14, count(jpa, "TxnSplit"));
            assertEquals(3, new FundTransferService(jpa).list().size());
        }
        try (Jpa jpa = new Jpa(db))
        {
            long audit = count(jpa, "AuditEvent"), identities = count(jpa, "InterchangeIdentity");
            commit(jpa, "DEFAULT", file);
            assertEquals(audit, count(jpa, "AuditEvent")); assertEquals(identities, count(jpa, "InterchangeIdentity"));
            assertEquals(expected, exported(jpa, "DEFAULT"));
            long txn, transfer;
            try (var em = jpa.em())
            {
                var current = em.createQuery("from FundTransfer f where f.postedTxn.status='ENTERED'", FundTransfer.class).getSingleResult();
                txn = current.getPostedTxn().getId(); transfer = current.getId();
            }
            assertThrows(IllegalStateException.class, () -> new TransactionCorrectionService(jpa).delete(txn, "tester", "Blocked"));
            new FundTransferService(jpa).reverse(transfer, DATE.plusDays(4), "Restored native reversal");
            assertEquals(4, new FundTransferService(jpa).list().size()); assertEquals(5, count(jpa, "Txn"));
        }
    }

    @Test
    void remappingRetainsSourceRequestProvenanceAndRejectsChangedNativeReplay() throws Exception
    {
        Path file = source();
        try (Jpa jpa = new Jpa(directory.resolve("mapped")))
        {
            seed(jpa); // Occupy native master IDs in another company.
            try (var em = jpa.em())
            {
                em.getTransaction().begin(); var company = new Company(); company.setCode("COPY"); company.setDisplayName("Synthetic copy"); em.persist(company); em.getTransaction().commit();
            }
            commit(jpa, "COPY", file);
            try (var em = jpa.em())
            {
                var saved = em.createQuery("from FundTransfer f where f.requestId=:id", FundTransfer.class).setParameter("id", REQUEST).getSingleResult();
                assertEquals("COPY", saved.getFromFund().getCompany().getCode());
                var asset = em.createQuery("from Account a where a.chart.company.code='COPY' and a.code='1500'", Account.class).getSingleResult();
                var equity = em.createQuery("from Account a where a.chart.company.code='COPY' and a.code='3000'", Account.class).getSingleResult();
                long before = count(jpa, "Txn");
                var replay = new FundTransferCommand(REQUEST, DATE, saved.getFromFund().getId(), saved.getToFund().getId(), asset.getId(), equity.getId(), saved.getAmount(), saved.getMemo());
                assertThrows(IllegalArgumentException.class, () -> new FundTransferService(jpa, () -> "COPY", null).save(replay));
                assertEquals(before, count(jpa, "Txn"));
            }
        }
    }

    @Test
    void changedOrStrippedFactsConflictAndKeepTargetRetainsOperationalAndCanonicalHistory() throws Exception
    {
        Path file = source(); ObjectNode original = (ObjectNode)JSON.readTree(file.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("conflicts")))
        {
            commit(jpa, "DEFAULT", file); var expected = exported(jpa, "DEFAULT");
            for (boolean stripped : List.of(false, true))
            {
                ObjectNode root = original.deepCopy(); var app = (ObjectNode)root.path("extensions").path("scaJakartaH2");
                if (stripped) app.remove(SclxFundTransfers.KEY);
                else for (var row : app.path(SclxFundTransfers.KEY).path("transfers")) ((ObjectNode)row).put("memo", "Changed retained source explanation");
                Path input = directory.resolve("changed-" + stripped + ".sclx"); JSON.writeValue(input.toFile(), root);
                var previews = new SclxImportPreviewService(jpa, () -> "DEFAULT"); var conflict = previews.preview(input);
                assertTrue(conflict.hasBlockingErrors());
                assertTrue(conflict.operation().items().stream().anyMatch(i -> i.entityType().equals("TRANSACTION") && i.identityMatch() == InterchangeIdentityMatch.CONFLICT));
                var keep = conflict.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                        .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
                var resolved = previews.preview(input, List.of(), keep);
                assertFalse(resolved.hasBlockingErrors(), resolved.operation().messages().toString());
                var result = new SclxImportCommitService(jpa, () -> "DEFAULT").commit(input, resolved, "tester", true, true);
                assertTrue(result.committed(), result.messages().toString());
                assertEquals(expected, exported(jpa, "DEFAULT")); assertEquals(4, count(jpa, "Txn"));
            }
        }
    }

    @Test
    void badVersionsShapesRequestsAndCanonicalGraphsBlockBeforeMutation() throws Exception
    {
        Path file = source(); ObjectNode original = (ObjectNode)JSON.readTree(file.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("invalid")))
        {
            for (String kind : List.of("version", "overflow", "duplicate", "uuidalias", "extra", "foreignfund", "missingtxn", "amount", "direction", "date", "request", "partialrequest", "time", "precision", "status", "null", "missinginverse"))
            {
                ObjectNode root = original.deepCopy(); var app = (ObjectNode)root.path("extensions").path("scaJakartaH2");
                var ext = (ObjectNode)app.path(SclxFundTransfers.KEY); var rows = (ArrayNode)ext.path("transfers");
                ObjectNode linked = null;
                for (var row : rows) if (!row.path("transactionId").isNull()) { linked = (ObjectNode)row; break; }
                assertNotNull(linked);
                switch (kind)
                {
                    case "version" -> ext.put("version", 2);
                    case "overflow" -> ext.put("version", 4294967297L);
                    case "duplicate" -> rows.add(linked.deepCopy());
                    case "uuidalias" ->
                    {
                        for (var row : rows) if (row.path("status").asText().equals("DRAFT"))
                        {
                            ObjectNode alias = row.deepCopy(); String id = alias.path("transferId").asText();
                            alias.put("transferId", "fund-transfer:OTHER:" + id.substring(id.lastIndexOf(':') + 1)); rows.add(alias); break;
                        }
                    }
                    case "extra" -> linked.put("unexpected", true);
                    case "foreignfund" -> linked.put("fromFundId", "fund:OTHER:MISSING");
                    case "missingtxn" -> linked.putNull("transactionId");
                    case "amount" -> linked.put("amount", "1.0000");
                    case "direction" -> linked.put("toFundId", linked.path("fromFundId").asText());
                    case "date" -> linked.put("date", "2026-12-31");
                    case "request" -> linked.put("requestId", "not-a-uuid");
                    case "partialrequest" -> { linked.put("requestId", REQUEST.toString()); linked.putNull("requestHash"); }
                    case "time" -> linked.put("createdAt", "yesterday");
                    case "precision" -> linked.put("createdAt", "2026-01-01T00:00:00.123456789Z");
                    case "status" -> linked.put("status", "APPROVED");
                    case "null" -> app.putNull(SclxFundTransfers.KEY);
                    case "missinginverse" ->
                    {
                        for (int i=0; i<rows.size(); i++) if (rows.get(i).path("transactionId").asText().equals(linked.path("transactionId").asText())) { rows.remove(i); break; }
                    }
                }
                Path input = directory.resolve("invalid-" + kind + ".sclx"); JSON.writeValue(input.toFile(), root);
                var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(input);
                assertTrue(preview.hasBlockingErrors(), kind);
                assertThrows(IllegalStateException.class, () -> new SclxImportCommitService(jpa, () -> "DEFAULT").commit(input, preview, "tester", true, true), kind);
                assertEquals(0, count(jpa, "FundTransfer")); assertEquals(0, count(jpa, "Txn")); assertEquals(0, count(jpa, "InterchangeIdentity"));
            }
        }
    }

    @Test
    void requestCollisionViewerAndInjectedLateFailureCannotPartiallyImport() throws Exception
    {
        Path file = source();
        List<Integer> observedWrites = new ArrayList<>();
        try (Jpa probe = new Jpa(directory.resolve("write-probe")))
        {
            var preview = new SclxImportPreviewService(probe, () -> "DEFAULT").preview(file);
            assertTrue(new SclxImportCommitService(probe, () -> "DEFAULT", observedWrites::add).commit(file, preview, "tester", true, true).committed());
        }
        int lateWrite = observedWrites.get(observedWrites.size() - 2); // Last transfer fact, before the outer operation audit.
        try (Jpa jpa = new Jpa(directory.resolve("collision")))
        {
            var f = seed(jpa); new FundTransferService(jpa).save(f.command(REQUEST, "10"));
            var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file);
            assertTrue(preview.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_TRANSFER_REQUEST_COLLISION")));
        }
        try (Jpa jpa = new Jpa(directory.resolve("denied")))
        {
            var viewer = new AuthenticatedUserSession(9001, "viewer", "Viewer", "DEFAULT", Set.of(ReservedSecurityRole.VIEWER), Instant.EPOCH, Instant.EPOCH);
            var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file);
            assertThrows(AuthorizationException.class, () -> new SclxImportCommitService(jpa, () -> "DEFAULT", new AuthorizationGuard(jpa, () -> Optional.of(viewer)))
                    .commit(file, preview, "tester", true, true));
            assertEquals(0, count(jpa, "FundTransfer"));
            var reached = new java.util.concurrent.atomic.AtomicBoolean();
            var failing = new SclxImportCommitService(jpa, () -> "DEFAULT", writes ->
            {
                if (writes >= lateWrite) { reached.set(true); throw new IllegalStateException("Synthetic late transfer failure"); }
            });
            var failed = failing.commit(file, preview, "tester", true, true);
            assertFalse(failed.committed()); assertTrue(reached.get(), failed.messages().toString());
            assertEquals(0, count(jpa, "FundTransfer")); assertEquals(0, count(jpa, "Txn")); assertEquals(0, count(jpa, "AuditEvent")); assertEquals(0, count(jpa, "InterchangeIdentity"));
            commit(jpa, "DEFAULT", file);
        }
    }

    @Test
    void absentLegacyExtensionDoesNotInferTransferFacts() throws Exception
    {
        ObjectNode root = (ObjectNode)JSON.readTree(source().toFile()); ((ObjectNode)root.path("extensions").path("scaJakartaH2")).remove(SclxFundTransfers.KEY);
        assertEquals(root, SclxFundTransfers.identityDocument(root));
        Path file = directory.resolve("legacy.sclx"); JSON.writeValue(file.toFile(), root);
        try (Jpa jpa = new Jpa(directory.resolve("legacy")))
        {
            commit(jpa, "DEFAULT", file); assertEquals(0, count(jpa, "FundTransfer")); assertEquals(4, count(jpa, "Txn"));
        }
    }

    @Test
    void foreignNativeUuidKeptLinkDriftAndMissingRetainedFactsFailClosed() throws Exception
    {
        Path file = source();
        try (Jpa jpa = new Jpa(directory.resolve("legacy-populated")); var em = jpa.em())
        {
            em.getTransaction().begin(); var owner = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
            var from = new Fund(); from.setCompany(owner); from.setCode("LEGACY-A"); from.setName("Legacy A"); from.setFundType(FundType.UNRESTRICTED); em.persist(from);
            var to = new Fund(); to.setCompany(owner); to.setCode("LEGACY-B"); to.setName("Legacy B"); to.setFundType(FundType.DESIGNATED); em.persist(to);
            var fact = new FundTransfer(); fact.setFromFund(from); fact.setToFund(to); fact.setTransferDate(DATE); fact.setAmount(BigDecimal.ONE); em.persist(fact);
            em.getTransaction().commit();
            assertTrue(new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file).targetPopulated(), "Unlinked legacy operational facts must not be treated as an empty target");
        }
        try (Jpa jpa = new Jpa(directory.resolve("native-conflicts")))
        {
            commit(jpa, "DEFAULT", file);
            try (var em = jpa.em())
            {
                em.getTransaction().begin(); var owner = new Company(); owner.setCode("COPY"); owner.setDisplayName("Copy"); em.persist(owner);
                em.createNativeQuery("update fund_transfer set posted_txn_id=(select min(id) from txn) where status='DRAFT'").executeUpdate();
                em.getTransaction().commit();
            }
            var foreign = new SclxImportPreviewService(jpa, () -> "COPY").preview(file);
            assertTrue(foreign.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_NATIVE_PORTABLE_ID_OTHER_COMPANY") && m.message().contains("FUND_TRANSFER")));
            ObjectNode root = (ObjectNode)JSON.readTree(file.toFile());
            for (var row : root.path("extensions").path("scaJakartaH2").path(SclxFundTransfers.KEY).path("transfers"))
                if (row.path("status").asText().equals("DRAFT")) ((ObjectNode)row).put("memo", "Changed legacy source fact");
            Path changed = directory.resolve("kept-link.sclx"); JSON.writeValue(changed.toFile(), root);
            var previews = new SclxImportPreviewService(jpa, () -> "DEFAULT"); var conflict = previews.preview(changed);
            var keep = conflict.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                    .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
            var blocked = previews.preview(changed, List.of(), keep);
            assertTrue(blocked.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_TRANSFER_LINK_CONFLICT")));
            assertThrows(IllegalStateException.class, () -> new SclxImportCommitService(jpa, () -> "DEFAULT").commit(changed, blocked, "tester", true, true));
            assertEquals(4, count(jpa, "Txn")); assertEquals(5, count(jpa, "FundTransfer"));
        }
        try (Jpa jpa = new Jpa(directory.resolve("stale-target")))
        {
            commit(jpa, "DEFAULT", file);
            try (var em = jpa.em())
            {
                em.getTransaction().begin(); em.createNativeQuery("delete from fund_transfer where status='DRAFT'").executeUpdate(); em.getTransaction().commit();
            }
            var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file);
            var rejected = new SclxImportCommitService(jpa, () -> "DEFAULT").commit(file, preview, "tester", true, true);
            assertFalse(rejected.committed()); assertTrue(rejected.rolledBack());
            assertEquals(4, count(jpa, "Txn")); assertEquals(4, count(jpa, "FundTransfer"));
        }
    }

    static List<SclxFundTransfers.Fact> exported(Jpa jpa, String company) throws Exception
    {
        return SclxFundTransfers.parse(JSON.readTree(new SclxJsonSerializer().serialize(new SclxCoreSnapshotQueryService(jpa, () -> company).query(Instant.EPOCH))));
    }

    static void commit(Jpa jpa, String company, Path file)
    {
        var preview = new SclxImportPreviewService(jpa, () -> company).preview(file);
        assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
        var result = new SclxImportCommitService(jpa, () -> company).commit(file, preview, "tester", true, true);
        assertTrue(result.committed(), result.messages().toString());
    }

    static long count(Jpa jpa, String type)
    {
        try (var em = jpa.em()) { return em.createQuery("select count(e) from " + type + " e", Long.class).getSingleResult(); }
    }
}
