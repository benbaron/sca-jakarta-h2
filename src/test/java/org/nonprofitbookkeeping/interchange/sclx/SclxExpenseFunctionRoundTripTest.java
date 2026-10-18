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
import static org.nonprofitbookkeeping.service.FunctionalExpenseTestFixture.*;

class SclxExpenseFunctionRoundTripTest
{
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();

    private Path source() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("source")))
        {
            var f = FunctionalExpenseTestFixture.seed(jpa);
            long id = f.entry(jpa).enter(f.mixed(), UUID.randomUUID()).id();
            new TransactionCorrectionService(jpa).reverse(id, DATE.plusDays(1), "tester", "Correction", false);
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
            AppUser user = new AppUser(); user.setUsername("operator"); user.setDisplayName("Operator"); em.persist(user); em.getTransaction().commit();
        }
    }

    private static AuthorizationGuard guard(Jpa jpa, ReservedSecurityRole role)
    {
        long user;
        try (EntityManager em = jpa.em()) { user = em.createQuery("select u.id from AppUser u where u.username = 'operator'", Long.class).getSingleResult(); }
        Instant now = Instant.now(); return new AuthorizationGuard(jpa, () -> Optional.of(new AuthenticatedUserSession(user, "operator", "Operator", COMPANY, Set.of(role), now, now)));
    }

    @Test
    void accountantRoundTripRestartRetryAndCorrectionsPreservePurposesAndNativeReceiptProvenance() throws Exception
    {
        Path file = source(); Path database = directory.resolve("target");
        assertEquals(6, JSON.readTree(file.toFile()).path("extensions").path("scaJakartaH2").path("expenseFunctions").path("lines").size());
        try (Jpa jpa = new Jpa(database))
        {
            target(jpa); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(file);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(preview.operation().messages().stream().anyMatch(m -> "SCLX_EXPENSE_FUNCTIONS".equals(m.code())));
            assertThrows(AuthorizationException.class, () -> new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.VIEWER)).commit(file, preview, "spoofed", true, true));
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.ACCOUNTANT)).commit(file, preview, "spoofed", true, true).committed());
        }
        try (Jpa jpa = new Jpa(database))
        {
            var query = new FunctionalExpenseQueryService(jpa, () -> COMPANY);
            assertEquals("1000.0000", query.query(DATE, DATE, null, null).gl().toPlainString());
            assertEquals("0.0000", query.query(DATE, DATE.plusDays(1), null, null).gl().toPlainString());
            assertTrue(query.query(DATE, DATE.plusDays(1), null, null).ready());
            assertEquals(6, count(jpa, "select count(s) from TxnSplit s where s.expenseFunction is not null"));
            long audits = count(jpa, "select count(a) from AuditEvent a");
            var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(file);
            assertTrue(preview.operation().items().stream().allMatch(i -> i.identityMatch() == InterchangeIdentityMatch.IDENTICAL));
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.ACCOUNTANT)).commit(file, preview, "tester", true, true).committed());
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            try (EntityManager em = jpa.em())
            {
                var receipts = em.createQuery("from AuditEvent a where a.reason like 'Journal create request v2 SHA-256:%'", AuditEvent.class).getResultList(); assertEquals(1, receipts.size());
                assertEquals(1L, em.createQuery("select count(t) from Txn t where t.portableId = :id", Long.class).setParameter("id", receipts.get(0).getPortableId()).getSingleResult());
            }
        }
    }

    @Test
    void changedOrMissingPurposesRequireCompleteTransactionKeepTargetAndNeverRewriteAcceptedHistory() throws Exception
    {
        Path file = source(); ObjectNode original = (ObjectNode)JSON.readTree(file.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("retained")))
        {
            target(jpa); var service = new SclxImportPreviewService(jpa, () -> COMPANY);
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(file, service.preview(file), "tester", true, true).committed());
            for (boolean absent : List.of(false, true))
            {
                ObjectNode root = original.deepCopy(); ObjectNode app = (ObjectNode)root.path("extensions").path("scaJakartaH2");
                if (absent) { app.remove("expenseFunctions"); }
                else
                {
                    for (var fact : app.path("expenseFunctions").path("lines"))
                    {
                        if ("AR".equals(fact.path("code").asText())) { ((ObjectNode)fact).put("code", "OA"); }
                    }
                }
                Path changed = directory.resolve("changed-" + absent + ".sclx"); JSON.writeValue(changed.toFile(), root);
                var preview = service.preview(changed);
                assertTrue(preview.operation().items().stream().anyMatch(i -> i.entityType().equals("TRANSACTION") && i.identityMatch() == InterchangeIdentityMatch.CONFLICT));
                var keep = preview.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                        .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
                var resolved = service.preview(changed, List.of(), keep); assertFalse(resolved.hasBlockingErrors(), () -> resolved.operation().messages().toString());
                assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(changed, resolved, "tester", true, true).committed());
                assertEquals(6, count(jpa, "select count(s) from TxnSplit s where s.expenseFunction is not null"));
                assertEquals("700.0000", new FunctionalExpenseQueryService(jpa, () -> COMPANY).query(DATE, DATE, null, null).totals().get(ExpenseFunction.AR).toPlainString());
            }
        }
    }

    @Test
    void olderAbsenceImportsAsUnknownWithoutInferredDefaults() throws Exception
    {
        ObjectNode root = (ObjectNode)JSON.readTree(source().toFile()); ((ObjectNode)root.path("extensions").path("scaJakartaH2")).remove("expenseFunctions");
        Path file = directory.resolve("legacy.sclx"); JSON.writeValue(file.toFile(), root);
        try (Jpa jpa = new Jpa(directory.resolve("legacy")))
        {
            target(jpa); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(file); assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(file, preview, "tester", true, true).committed());
            var result = new FunctionalExpenseQueryService(jpa, () -> COMPANY).query(DATE, DATE.plusDays(1), null, null);
            assertFalse(result.ready()); assertEquals("0.0000", result.gl().toPlainString()); assertEquals("2000.0000", result.unknownGross().toPlainString());
            assertEquals(0, count(jpa, "select count(s) from TxnSplit s where s.expenseFunction is not null"));
        }
    }

    @Test
    void malformedCodesReferencesVersionsAndInversePurposesBlockWhileLateFailureRollsBackTheGraph() throws Exception
    {
        ObjectNode original = (ObjectNode)JSON.readTree(source().toFile());
        try (Jpa jpa = new Jpa(directory.resolve("invalid")))
        {
            target(jpa); var service = new SclxImportPreviewService(jpa, () -> COMPANY);
            for (String mutation : List.of("version", "code", "null", "foreign", "duplicate", "shape", "nonexpense", "inverse"))
            {
                ObjectNode root = original.deepCopy(); ObjectNode extension = (ObjectNode)root.path("extensions").path("scaJakartaH2").path("expenseFunctions");
                ArrayNode lines = (ArrayNode)extension.path("lines"); ObjectNode first = (ObjectNode)lines.get(0);
                switch (mutation)
                {
                    case "version" -> extension.put("version", 4294967297L);
                    case "code" -> first.put("code", "UNKNOWN");
                    case "null" -> first.putNull("code");
                    case "foreign" -> first.put("lineId", "not-a-line");
                    case "duplicate" -> lines.add(first.deepCopy());
                    case "shape" -> first.put("extra", true);
                    case "nonexpense" -> first.put("lineId", root.path("transactions").get(0).path("lines").get(0).path("lineId").asText());
                    case "inverse" -> first.put("code", "AR".equals(first.path("code").asText()) ? "OA" : "AR");
                }
                Path changed = directory.resolve(mutation + ".sclx"); JSON.writeValue(changed.toFile(), root); assertTrue(service.preview(changed).hasBlockingErrors(), mutation);
            }
            Path file = directory.resolve("source.sclx"); var preview = service.preview(file); long audits = count(jpa, "select count(a) from AuditEvent a"); var reached = new java.util.concurrent.atomic.AtomicBoolean();
            var commit = new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.ACCOUNTANT), writes -> {
                if (writes == 13) { reached.set(true); throw new IllegalStateException("Synthetic late import failure"); }
            }).commit(file, preview, "tester", true, true);
            assertFalse(commit.committed()); assertTrue(reached.get());
            assertEquals(0, count(jpa, "select count(t) from Txn t")); assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            assertEquals(0, count(jpa, "select count(i) from InterchangeIdentity i"));
        }
    }

    private static long count(Jpa jpa, String query)
    {
        try (EntityManager em = jpa.em()) { return em.createQuery(query, Long.class).getSingleResult(); }
    }
}
