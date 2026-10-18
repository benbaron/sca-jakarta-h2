package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;

class SclxNmrItemRoundTripTest
{
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();

    private Path source() throws Exception
    {
        Path path = directory.resolve("source.sclx");
        try (Jpa jpa = new Jpa(directory.resolve("source")))
        {
            var r = seed(jpa); var entry = r.entry(jpa); UUID item = UUID.randomUUID();
            entry.enter(r.receipt(item, r.recipient(), r.event()));
            var payment = entry.enter(r.payment(item, "5", r.recipient(), r.event()));
            new TransactionCorrectionService(jpa, () -> COMPANY).reverse(payment.id(), DATE.plusDays(2), "tester", "reverse payment", false);
            var document = new SclxCoreSnapshotQueryService(jpa, () -> COMPANY).query(Instant.EPOCH);
            var bytes = new SclxJsonSerializer().serialize(document);
            assertArrayEquals(bytes, new SclxJsonSerializer().serialize(document));
            Files.write(path, bytes); return path;
        }
    }

    private static void target(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company(); company.setCode(COMPANY); company.setDisplayName("Target"); company.setDefaultCurrency("USD"); em.persist(company);
            ChartOfAccounts chart = new ChartOfAccounts(); chart.setCompany(company); chart.setName("Target"); chart.setVersion("1"); chart.setStatus(ChartStatus.ACTIVE); em.persist(chart);
            company.setActiveChartOfAccounts(chart); em.getTransaction().commit();
        }
    }

    @Test
    void explicitRecipientEventItemCutoffsSurviveInterchangeRestartAndRetry() throws Exception
    {
        Path source = source(); Path database = directory.resolve("target");
        try (Jpa jpa = new Jpa(database))
        {
            target(jpa); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(preview.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_NMR_ITEM_DETAILS")));
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(source, preview, "tester", true, true).committed());
        }
        try (Jpa jpa = new Jpa(database))
        {
            var query = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            assertTrue(query.queryNmr(DATE.plusDays(2)).ready(), () -> query.queryNmr(DATE.plusDays(2)).toString());
            assertEquals("15.0000", query.queryNmr(DATE.plusDays(1)).items().get(0).openBalance().toPlainString());
            assertEquals("20.0000", query.queryNmr(DATE.plusDays(2)).items().get(0).openBalance().toPlainString());
            assertEquals("Synthetic recipient", query.queryNmr(DATE.plusDays(2)).items().get(0).nmr().recipientName());
            var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(source);
            assertTrue(preview.operation().items().stream().allMatch(i -> i.identityMatch() == InterchangeIdentityMatch.IDENTICAL));
            long before = count(jpa, "select count(a) from AuditEvent a");
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(source, preview, "tester", true, true).committed());
            assertEquals(before, count(jpa, "select count(a) from AuditEvent a"));
            assertEquals(3, count(jpa, "select count(t) from Txn t"));
            assertEquals(0, count(jpa, "select count(p) from NmrPolicyVersion p"));
        }
    }

    @Test
    void missingDesignationOnRetainedHistoryConflictsAndKeepTargetPreservesIt() throws Exception
    {
        Path source = source(); ObjectNode root = (ObjectNode) JSON.readTree(source.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("conflict")))
        {
            target(jpa); var service = new SclxImportPreviewService(jpa, () -> COMPANY);
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(source, service.preview(source), "tester", true, true).committed());
            root.path("extensions").path("scaJakartaH2").path("supplementalDetails").forEach(d -> ((ObjectNode)d).remove("nmr"));
            JSON.writeValue(source.toFile(), root);
            var conflict = service.preview(source);
            assertTrue(conflict.hasBlockingErrors());
            assertTrue(conflict.operation().items().stream().anyMatch(i -> i.entityType().equals("TRANSACTION") && i.identityMatch() == InterchangeIdentityMatch.CONFLICT));
            assertTrue(conflict.operation().items().stream().anyMatch(i -> i.entityType().equals("SUPPLEMENTAL_DETAIL") && i.identityMatch() == InterchangeIdentityMatch.CONFLICT));
            var keep = conflict.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                    .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
            var preview = service.preview(source, List.of(), keep);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(source, preview, "tester", true, true).committed());
            assertTrue(new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryNmr(DATE.plusDays(2)).ready());
        }
        try (Jpa jpa = new Jpa(directory.resolve("old-file")))
        {
            target(jpa); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(source);
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(source, preview, "tester", true, true).committed());
            var projection = new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryNmr(DATE.plusDays(2));
            assertTrue(projection.items().isEmpty()); assertFalse(projection.ready());
        }
    }

    @Test
    void invalidOrUnresolvedDesignationBlocksPreviewAndLateFailureRollsBackMetadata() throws Exception
    {
        Path source = source(); ObjectNode root = (ObjectNode) JSON.readTree(source.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("rollback")))
        {
            target(jpa); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(source);
            var reached = new java.util.concurrent.atomic.AtomicBoolean();
            var failing = new SclxImportCommitService(jpa, () -> COMPANY, null, writes -> {
                if (writes == 8) { reached.set(true); throw new IllegalStateException("Injected canonical write failure"); }
            });
            assertFalse(failing.commit(source, preview, "tester", true, true).committed()); assertTrue(reached.get());
            assertEquals(0, count(jpa, "select count(t) from Txn t"));
            assertEquals(0, count(jpa, "select count(l) from TxnSupplementalLine l"));
            assertEquals(0, count(jpa, "select count(i) from InterchangeIdentity i"));
            ObjectNode changedInverse = root.deepCopy();
            boolean altered = false;
            for (var transaction : changedInverse.path("transactions"))
            {
                if (transaction.path("correctionOfTransactionId").isTextual())
                {
                    ((ObjectNode)transaction.path("lines").get(0)).putNull("activityId");
                    altered = true;
                }
            }
            assertTrue(altered, "The regression must alter a real canonical inverse");
            JSON.writeValue(source.toFile(), changedInverse);
            var invalidInverse = new SclxImportPreviewService(jpa, () -> COMPANY).preview(source);
            assertTrue(invalidInverse.hasBlockingErrors(), "An inverse cannot silently move item event attribution");
            assertEquals(0, count(jpa, "select count(t) from Txn t"));
            for (String invalid : List.of("null", "{}", "{\"version\":4294967297,\"recipientId\":\"missing\"}", "{\"version\":1,\"recipientId\":\"missing\"}"))
            {
                ((ObjectNode)root.path("extensions").path("scaJakartaH2").path("supplementalDetails").get(0)).set("nmr", JSON.readTree(invalid));
                JSON.writeValue(source.toFile(), root);
                assertTrue(new SclxImportPreviewService(jpa, () -> COMPANY).preview(source).hasBlockingErrors());
                assertEquals(0, count(jpa, "select count(t) from Txn t"));
            }
        }
    }

    private static long count(Jpa jpa, String query)
    {
        try (EntityManager em = jpa.em()) { return em.createQuery(query, Long.class).getSingleResult(); }
    }
}
