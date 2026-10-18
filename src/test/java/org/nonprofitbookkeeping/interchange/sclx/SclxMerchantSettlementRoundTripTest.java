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
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;

class SclxMerchantSettlementRoundTripTest
{
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();

    private Path source() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("source")))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var history = f.history(jpa);
            new TransactionCorrectionService(jpa, () -> COMPANY).reverse(history.get(2), DATE.plusDays(3), "tester", "Correction", false);
            var document = new SclxCoreSnapshotQueryService(jpa, () -> COMPANY).query(Instant.EPOCH);
            byte[] bytes = new SclxJsonSerializer().serialize(document); assertArrayEquals(bytes, new SclxJsonSerializer().serialize(document));
            Path path = directory.resolve("source.sclx"); Files.write(path, bytes); return path;
        }
    }

    private static void target(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin(); Company company = new Company(); company.setCode(COMPANY); company.setDisplayName("Target"); company.setDefaultCurrency("USD"); em.persist(company);
            ChartOfAccounts chart = new ChartOfAccounts(); chart.setCompany(company); chart.setName("Target"); chart.setVersion("1"); chart.setStatus(ChartStatus.ACTIVE); em.persist(chart); company.setActiveChartOfAccounts(chart);
            AppUser user = new AppUser(); user.setUsername("operator"); user.setDisplayName("Operator"); em.persist(user); em.getTransaction().commit();
        }
    }

    private static AuthorizationGuard guard(Jpa jpa, ReservedSecurityRole role)
    {
        long user;
        try (EntityManager em = jpa.em()) { user = em.createQuery("select u.id from AppUser u", Long.class).getSingleResult(); }
        Instant now = Instant.now();
        return new AuthorizationGuard(jpa, () -> Optional.of(new AuthenticatedUserSession(user, "operator", "Operator", COMPANY, Set.of(role), now, now)));
    }

    @Test
    void accountantRoundTripRestartAndExactRetryRetainLinksObservationsAndDatedCorrectionsWithoutPolicies() throws Exception
    {
        Path file = source(); Path database = directory.resolve("target");
        try (Jpa jpa = new Jpa(database))
        {
            target(jpa); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(file);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(preview.operation().messages().stream().anyMatch(m -> "SCLX_MERCHANT_SETTLEMENT_FACTS".equals(m.code())));
            assertThrows(AuthorizationException.class, () -> new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.VIEWER)).commit(file, preview, "spoofed", true, true));
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.ACCOUNTANT)).commit(file, preview, "spoofed", true, true).committed());
        }
        try (Jpa jpa = new Jpa(database))
        {
            var query = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            var before = query.queryMerchantReceivables(DATE, DATE.plusDays(1)); assertTrue(before.ready());
            assertEquals("30.0000", before.summaries().get(0).closing().toPlainString());
            assertEquals("processor/withheld/001", before.summaries().get(0).latestObservation().evidence());
            assertEquals("0.0000", query.queryMerchantReceivables(DATE, DATE.plusDays(2)).summaries().get(0).closing().toPlainString());
            var inverse = query.queryMerchantReceivables(DATE.plusDays(3), DATE.plusDays(3)); assertTrue(inverse.ready());
            assertEquals("-30.0000", inverse.summaries().get(0).nmr().toPlainString());
            assertEquals("30.0000", inverse.summaries().get(0).latestObservation().amount().toPlainString());
            long audits = count(jpa, "select count(a) from AuditEvent a"); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(file);
            assertTrue(preview.operation().items().stream().allMatch(i -> i.identityMatch() == InterchangeIdentityMatch.IDENTICAL));
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.ACCOUNTANT)).commit(file, preview, "tester", true, true).committed());
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a")); assertEquals(4, count(jpa, "select count(t) from Txn t"));
            assertEquals(0, count(jpa, "select count(p) from NmrPolicyVersion p"));
            try (EntityManager em = jpa.em()) { assertEquals(3L, em.createNativeQuery("select count(*) from txn_supplemental_merchant_component", Long.class).getSingleResult()); }
        }
    }

    @Test
    void changedObservationOrMissingLegacyFactsConflictAtCompleteTransactionAndDetailAndKeepTargetRetainsHistory() throws Exception
    {
        Path file = source(); ObjectNode original = (ObjectNode)JSON.readTree(file.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("retained")))
        {
            target(jpa); var service = new SclxImportPreviewService(jpa, () -> COMPANY);
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(file, service.preview(file), "tester", true, true).committed());
            for (boolean missing : List.of(false, true))
            {
                ObjectNode root = original.deepCopy();
                for (var detail : root.path("extensions").path("scaJakartaH2").path("supplementalDetails"))
                {
                    if (missing) { ((ObjectNode)detail).remove("merchantSettlement"); }
                    else if (detail.has("merchantSettlement") && !detail.path("merchantSettlement").path("withheldUnconfirmed").isNull())
                    {
                        ((ObjectNode)detail.get("merchantSettlement")).put("evidenceReference", "changed/evidence");
                    }
                }
                Path changed = directory.resolve("changed-" + missing + ".sclx"); JSON.writeValue(changed.toFile(), root);
                var preview = service.preview(changed);
                assertTrue(preview.operation().items().stream().anyMatch(i -> i.entityType().equals("TRANSACTION") && i.identityMatch() == InterchangeIdentityMatch.CONFLICT));
                assertTrue(preview.operation().items().stream().anyMatch(i -> i.entityType().equals("SUPPLEMENTAL_DETAIL") && i.identityMatch() == InterchangeIdentityMatch.CONFLICT));
                var keep = preview.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                        .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
                var resolved = service.preview(changed, List.of(), keep);
                assertFalse(resolved.hasBlockingErrors(), () -> resolved.operation().messages().toString());
                assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(changed, resolved, "tester", true, true).committed());
                assertEquals("processor/withheld/001", new SupplementalOpenItemQueryService(jpa, () -> COMPANY)
                        .queryMerchantReceivables(DATE, DATE.plusDays(1)).summaries().get(0).latestObservation().evidence());
            }
        }
    }

    @Test
    void legacyFilesRemainImportableWithMerchantClassificationUnknown() throws Exception
    {
        Path file = source(); ObjectNode root = (ObjectNode)JSON.readTree(file.toFile());
        for (var detail : root.path("extensions").path("scaJakartaH2").path("supplementalDetails"))
        {
            ((ObjectNode)detail).remove("merchantSettlement");
        }
        Path legacy = directory.resolve("legacy.sclx"); JSON.writeValue(legacy.toFile(), root);
        try (Jpa jpa = new Jpa(directory.resolve("legacy-target")))
        {
            target(jpa); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(legacy);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(legacy, preview, "tester", true, true).committed());
            var result = new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryMerchantReceivables(DATE, DATE.plusDays(3));
            assertFalse(result.ready()); assertEquals("30.0000", result.summaries().get(0).closing().toPlainString());
            assertNull(result.summaries().get(0).latestObservation());
        }
    }

    @Test
    void malformedFactsComponentsAndInjectedFailureBlockOrRollBackWithoutResidue() throws Exception
    {
        Path file = source(); ObjectNode original = (ObjectNode)JSON.readTree(file.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("invalid")))
        {
            target(jpa); var service = new SclxImportPreviewService(jpa, () -> COMPANY);
            for (String mutation : List.of("version", "precision", "negative", "evidence", "missing", "role", "foreign", "duplicate", "kind"))
            {
                ObjectNode root = original.deepCopy();
                ObjectNode facts = (ObjectNode)root.path("extensions").path("scaJakartaH2").path("supplementalDetails").findValues("merchantSettlement").stream()
                        .filter(v -> v.path("withheldUnconfirmed").asText().equals("30.0000")).findFirst().orElseThrow();
                switch (mutation)
                {
                    case "version" -> facts.put("version", 4294967297L);
                    case "precision" -> facts.put("withheldUnconfirmed", "1.00001");
                    case "negative" -> facts.put("withheldUnconfirmed", "-1");
                    case "evidence" -> facts.putNull("evidenceReference");
                    case "missing" -> facts.remove("components");
                    case "role" -> facts.put("role", "GROSS_RECEIPT");
                    case "foreign" -> ((ObjectNode)facts.path("components").get(0)).put("lineId", root.path("transactions").get(0).path("lines").get(0).path("lineId").asText());
                    case "duplicate" -> ((com.fasterxml.jackson.databind.node.ArrayNode)facts.path("components")).add(facts.path("components").get(0).deepCopy());
                    case "kind" -> ((ObjectNode)facts.path("components").get(0)).put("kind", "UNKNOWN");
                }
                Path changed = directory.resolve(mutation + ".sclx"); JSON.writeValue(changed.toFile(), root);
                assertTrue(service.preview(changed).hasBlockingErrors(), mutation);
            }
            var preview = service.preview(file);
            var reached = new java.util.concurrent.atomic.AtomicBoolean();
            long audits = count(jpa, "select count(a) from AuditEvent a");
            assertFalse(new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.ACCOUNTANT), writes -> {
                if (writes == 11) { reached.set(true); throw new IllegalStateException("Injected"); }
            }).commit(file, preview, "tester", true, true).committed());
            assertTrue(reached.get());
            assertEquals(0, count(jpa, "select count(t) from Txn t")); assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            assertEquals(0, count(jpa, "select count(i) from InterchangeIdentity i"));
            try (EntityManager em = jpa.em()) { assertEquals(0L, em.createNativeQuery("select count(*) from txn_supplemental_merchant_component", Long.class).getSingleResult()); }
        }
    }

    private static long count(Jpa jpa, String hql)
    {
        try (EntityManager em = jpa.em()) { return em.createQuery(hql, Long.class).getSingleResult(); }
    }
}
