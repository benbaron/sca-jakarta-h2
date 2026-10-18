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
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;

class SclxNmrSettlementRoundTripTest
{
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();

    private Path source() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("source")))
        {
            var fixture = NmrSettlementTestFixture.seed(jpa); var history = fixture.history(jpa, true, true);
            new TransactionCorrectionService(jpa, () -> COMPANY).reverse(history.merchantTransaction(), DATE.plusDays(3), "tester", "Dated correction", false);
            var document = new SclxCoreSnapshotQueryService(jpa, () -> COMPANY).query(Instant.EPOCH);
            byte[] bytes = new SclxJsonSerializer().serialize(document);
            assertArrayEquals(bytes, new SclxJsonSerializer().serialize(document));
            Path path = directory.resolve("source.sclx"); Files.write(path, bytes); return path;
        }
    }

    private static void target(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin(); Company company = new Company(); company.setCode(COMPANY); company.setDisplayName("Target"); company.setDefaultCurrency("USD"); em.persist(company);
            ChartOfAccounts chart = new ChartOfAccounts(); chart.setCompany(company); chart.setName("Target"); chart.setVersion("1"); chart.setStatus(ChartStatus.ACTIVE); em.persist(chart);
            company.setActiveChartOfAccounts(chart); AppUser user = new AppUser(); user.setUsername("operator"); user.setDisplayName("Operator"); em.persist(user);
            em.getTransaction().commit();
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
    void accountantImportsExplicitMovementEvidenceAndMerchantWithPeriodCutoffsRestartAndExactRetry() throws Exception
    {
        Path source = source(); Path database = directory.resolve("target");
        try (Jpa jpa = new Jpa(database))
        {
            target(jpa); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertThrows(AuthorizationException.class, () -> new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.VIEWER)).commit(source, preview, "spoofed", true, true));
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.ACCOUNTANT)).commit(source, preview, "spoofed", true, true).committed());
        }
        try (Jpa jpa = new Jpa(database))
        {
            var query = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            assertTrue(query.queryNmrPeriod(DATE, DATE.plusDays(3)).ready());
            assertEquals("0.0000", query.queryNmrPeriod(DATE, DATE.plusDays(2)).summaries().stream().map(NmrPeriodQueryService.Summary::closing).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).toPlainString());
            var correction = query.queryNmrPeriod(DATE.plusDays(3), DATE.plusDays(3)).movements().get(0);
            assertEquals(NmrMovementKind.MERCHANT_DISCHARGE, correction.kind()); assertTrue(correction.correction());
            assertEquals("processor/settlement/001", correction.evidence()); assertEquals("Synthetic processor", correction.merchantName());
            long audits = count(jpa, "select count(a) from AuditEvent a");
            var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(source);
            assertTrue(preview.operation().items().stream().allMatch(i -> i.identityMatch() == InterchangeIdentityMatch.IDENTICAL));
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.ACCOUNTANT)).commit(source, preview, "spoofed", true, true).committed());
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a")); assertEquals(5, count(jpa, "select count(t) from Txn t"));
            assertEquals(0, count(jpa, "select count(p) from NmrPolicyVersion p"));
        }
    }

    @Test
    void changedEvidenceAndLegacyVersionRequireExplicitKeepTargetWithoutRelabelingHistory() throws Exception
    {
        Path source = source(); ObjectNode original = (ObjectNode)JSON.readTree(source.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("retained")))
        {
            target(jpa); var service = new SclxImportPreviewService(jpa, () -> COMPANY);
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(source, service.preview(source), "tester", true, true).committed());
            for (boolean legacy : List.of(false, true))
            {
                ObjectNode root = original.deepCopy();
                for (var detail : root.path("extensions").path("scaJakartaH2").path("supplementalDetails"))
                {
                    if (!detail.has("nmr")) continue;
                    ObjectNode facts = (ObjectNode)detail.get("nmr");
                    if (legacy) { facts.put("version", 1); facts.remove(List.of("movement", "evidenceReference")); }
                    else if (facts.path("movement").asText().equals("MERCHANT_DISCHARGE")) facts.put("evidenceReference", "changed/reference");
                }
                JSON.writeValue(source.toFile(), root); var conflict = service.preview(source);
                assertTrue(conflict.hasBlockingErrors());
                assertTrue(conflict.operation().items().stream().anyMatch(i -> i.entityType().equals("TRANSACTION") && i.identityMatch() == InterchangeIdentityMatch.CONFLICT));
                var keep = conflict.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                        .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
                var preview = service.preview(source, List.of(), keep); assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
                assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(source, preview, "tester", true, true).committed());
                assertTrue(new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryNmrPeriod(DATE, DATE.plusDays(3)).ready());
            }
        }
        try (Jpa jpa = new Jpa(directory.resolve("legacy")))
        {
            target(jpa); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(source);
            assertTrue(new SclxImportCommitService(jpa, () -> COMPANY).commit(source, preview, "tester", true, true).committed());
            var query = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            assertTrue(query.queryNmr(DATE.plusDays(3)).ready()); assertFalse(query.queryNmrPeriod(DATE, DATE.plusDays(3)).ready());
            assertEquals(0, count(jpa, "select count(l) from TxnSupplementalLine l where l.nmrMovement is not null"));
        }
    }

    @Test
    void malformedMovementOrMissingDischargeEvidenceBlocksAndLateFailureRollsBackAllFacts() throws Exception
    {
        Path source = source(); ObjectNode original = (ObjectNode)JSON.readTree(source.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("failure")))
        {
            target(jpa); var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(source); var reached = new java.util.concurrent.atomic.AtomicBoolean();
            long audits = count(jpa, "select count(a) from AuditEvent a");
            var failing = new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.ACCOUNTANT), writes -> {
                if (writes == 11) { reached.set(true); throw new IllegalStateException("Injected failure after canonical settlement facts"); }
            });
            assertFalse(failing.commit(source, preview, "tester", true, true).committed()); assertTrue(reached.get());
            assertEquals(0, count(jpa, "select count(t) from Txn t")); assertEquals(0, count(jpa, "select count(l) from TxnSupplementalLine l"));
            assertEquals(0, count(jpa, "select count(i) from InterchangeIdentity i")); assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            for (String invalid : List.of("wrong-kind", "wrong-effect", "missing-evidence", "long-evidence", "version", "inverse-merchant"))
            {
                ObjectNode root = original.deepCopy();
                for (var detail : root.path("extensions").path("scaJakartaH2").path("supplementalDetails"))
                {
                    if (!detail.path("nmr").path("movement").asText().equals("MERCHANT_DISCHARGE")) continue;
                    ObjectNode facts = (ObjectNode)detail.get("nmr");
                    switch (invalid)
                    {
                        case "wrong-kind" -> facts.put("movement", "UNKNOWN");
                        case "wrong-effect" -> facts.put("movement", "COLLECTION");
                        case "missing-evidence" -> facts.putNull("evidenceReference");
                        case "long-evidence" -> facts.put("evidenceReference", "x".repeat(501));
                        case "version" -> facts.put("version", 4294967298L);
                    }
                }
                if (invalid.equals("inverse-merchant"))
                {
                    java.util.Set<String> inverseLines = new java.util.HashSet<>();
                    root.path("transactions").forEach(t -> {
                        if (t.path("correctionType").asText().equals("REVERSAL")) t.path("lines").forEach(l -> inverseLines.add(l.path("lineId").asText()));
                    });
                    var links = (com.fasterxml.jackson.databind.node.ArrayNode)root.path("extensions").path("scaJakartaH2")
                            .path("counterparties").path("transactionLineMerchants");
                    for (int index = links.size() - 1; index >= 0; index--)
                    {
                        if (inverseLines.contains(links.get(index).path("lineId").asText())) links.remove(index);
                    }
                }
                JSON.writeValue(source.toFile(), root);
                assertTrue(new SclxImportPreviewService(jpa, () -> COMPANY).preview(source).hasBlockingErrors(), invalid);
                assertEquals(0, count(jpa, "select count(t) from Txn t"));
            }
        }
    }

    private static long count(Jpa jpa, String query)
    {
        try (EntityManager em = jpa.em()) { return em.createQuery(query, Long.class).getSingleResult(); }
    }
}
