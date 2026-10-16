package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SclxNmrPolicyRoundTripTest
{
    @TempDir Path directory;
    public static final String COMPANY = "POLICY_TEST";
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);

    public static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(COMPANY);
            company.setDisplayName("Policy test company");
            company.setDefaultCurrency("USD");
            em.persist(company);
            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName("Policy chart");
            chart.setVersion("1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            company.setActiveChartOfAccounts(chart);
            em.getTransaction().commit();
        }
    }

    public static AuthorizationGuard guard(Jpa jpa, ReservedSecurityRole role)
    {
        return guard(jpa, role, "policy-importer");
    }

    private static AuthorizationGuard guard(Jpa jpa, ReservedSecurityRole role, String username)
    {
        Instant now = Instant.now();
        return new AuthorizationGuard(jpa, () -> Optional.of(new AuthenticatedUserSession(
                9001L, username, username, COMPANY, Set.of(role), now, now)));
    }

    public static NmrPolicyTerms terms(LocalDate from, LocalDate through)
    {
        return new NmrPolicyTerms("SOURCE", "STANDARD", from, through, new BigDecimal("15.1250"), "USD",
                "Event date", "Approved rounding text", "Source eligibility", "Source exemptions", "Source recipient",
                "Source reporting", "Source merchant path", "Source reference", "Source authority", FROM.minusDays(1));
    }

    public static NmrPolicyService policies(Jpa jpa)
    {
        return new NmrPolicyService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.MANAGER));
    }

    public static Path export(Jpa jpa, Path path) throws Exception
    {
        var document = new SclxCoreSnapshotQueryService(jpa, () -> COMPANY).query(Instant.EPOCH);
        byte[] bytes = new SclxJsonSerializer().serialize(document);
        assertArrayEquals(bytes, new SclxJsonSerializer().serialize(document));
        Files.write(path, bytes);
        return path;
    }

    private static SclxImportPreview preview(Jpa jpa, Path source)
    {
        return new SclxImportPreviewService(jpa, () -> COMPANY).preview(source);
    }

    private static SclxImportResult commit(Jpa jpa, Path source, SclxImportPreview preview)
    {
        return new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.MANAGER))
                .commit(source, preview, "spoofed actor", true, true);
    }

    public static long count(Jpa jpa, String table)
    {
        try (EntityManager em = jpa.em())
        {
            return ((Number) em.createNativeQuery("select count(*) from " + table).getSingleResult()).longValue();
        }
    }

    @Test
    void endedHistoryRoundTripsAndOriginalSaveRetriesSurviveRestart() throws Exception
    {
        Path source = directory.resolve("policies.sclx");
        UUID original = UUID.randomUUID();
        List<NmrPolicyImport> expected;
        try (Jpa jpa = new Jpa(directory.resolve("source")))
        {
            seed(jpa);
            var sourcePolicies = new NmrPolicyService(jpa, () -> COMPANY,
                    guard(jpa, ReservedSecurityRole.MANAGER, "source-policy-author"));
            var policy = sourcePolicies.save(original, terms(FROM, null));
            sourcePolicies.endVersion(policy.id(), FROM.plusMonths(5).minusDays(1), "Source ended old interval");
            sourcePolicies.save(UUID.randomUUID(), terms(FROM.plusMonths(5), null));
            export(jpa, source);
            var doc = new SclxCoreSnapshotQueryService(jpa, () -> COMPANY).query(Instant.EPOCH);
            assertEquals(2L, SclxExportCounts.from(doc, 0, 0).nmrPolicyVersions());
            assertTrue(SclxExportSection.NMR_POLICIES.includedByCurrentSnapshot());
            expected = SclxNmrPolicies.parseExtensions(doc.extensions()).stream().map(SclxNmrPolicies.Entry::value).toList();
        }
        Path target = directory.resolve("target");
        try (Jpa jpa = new Jpa(target))
        {
            seed(jpa);
            var preview = preview(jpa, source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertEquals(2, preview.operation().items().stream().filter(i -> i.entityType().equals(SclxNmrPolicies.TYPE)).count());
            assertTrue(commit(jpa, source, preview).committed());
        }
        try (Jpa jpa = new Jpa(target))
        {
            var doc = new SclxCoreSnapshotQueryService(jpa, () -> COMPANY).query(Instant.EPOCH);
            assertEquals(expected, SclxNmrPolicies.parseExtensions(doc.extensions()).stream().map(SclxNmrPolicies.Entry::value).toList());
            assertEquals(original, policies(jpa).save(original, terms(FROM, null)).portableId());
            assertEquals(2, policies(jpa).list().size());
            assertTrue(policies(jpa).list().stream().allMatch(p -> p.savedBy().equals("source-policy-author")));
            long audits = count(jpa, "audit_event");
            long identities = count(jpa, "interchange_identity");
            var retry = preview(jpa, source);
            assertTrue(retry.operation().items().stream().allMatch(i -> i.identityMatch() == InterchangeIdentityMatch.IDENTICAL));
            assertTrue(commit(jpa, source, retry).committed());
            assertEquals(audits, count(jpa, "audit_event"));
            assertEquals(identities, count(jpa, "interchange_identity"));
            try (EntityManager em = jpa.em())
            {
                assertEquals("policy-importer", em.createQuery("select a.actor from AuditEvent a where a.actionType = 'SCLX_IMPORTED'", String.class).getSingleResult());
            }
            for (String table : List.of("txn", "txn_split", "fund_transfer", "txn_supplemental_line"))
            {
                assertEquals(0, count(jpa, table));
            }
        }
    }

    @Test
    void previewRejectsMalformedDuplicateAndOverlappingPoliciesAndOldFilesRemainValid() throws Exception
    {
        try (Jpa sourceJpa = new Jpa(directory.resolve("source")); Jpa target = new Jpa(directory.resolve("target")))
        {
            seed(sourceJpa);
            seed(target);
            policies(sourceJpa).save(UUID.randomUUID(), terms(FROM, null));
            Path source = export(sourceJpa, directory.resolve("source.sclx"));
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode original = (ObjectNode) mapper.readTree(source.toFile());
            for (String field : List.of("requestHash", "savedAt", "rate", "currency", "validFrom", "policyVersionId", "savedBy", "authority"))
            {
                ObjectNode changed = original.deepCopy();
                ((ObjectNode) changed.path("extensions").path("scaJakartaH2").path("nmrPolicies").path("versions").get(0)).put(field, Set.of("savedBy", "authority").contains(field) ? "" : "invalid");
                mapper.writeValue(source.toFile(), changed);
                assertTrue(preview(target, source).hasBlockingErrors(), field);
            }
            for (String mode : List.of("duplicate", "overlap", "unknown", "null", "version"))
            {
                ObjectNode changed = original.deepCopy();
                ObjectNode extension = (ObjectNode) changed.path("extensions").path("scaJakartaH2").path("nmrPolicies");
                var versions = (com.fasterxml.jackson.databind.node.ArrayNode) extension.get("versions");
                switch (mode)
                {
                    case "duplicate" -> versions.add(versions.get(0).deepCopy());
                    case "overlap" ->
                    {
                        ObjectNode another = (ObjectNode) versions.get(0).deepCopy();
                        another.put("policyVersionId", "nmr-policy-version:POLICY_TEST:" + UUID.randomUUID());
                        versions.add(another);
                    }
                    case "unknown" -> extension.put("unexpected", true);
                    case "null" -> ((ObjectNode) changed.path("extensions").path("scaJakartaH2")).putNull("nmrPolicies");
                    case "version" -> extension.put("version", 2);
                }
                mapper.writeValue(source.toFile(), changed);
                assertTrue(preview(target, source).hasBlockingErrors(), mode);
            }
            ((ObjectNode) original.path("extensions").path("scaJakartaH2")).remove("nmrPolicies");
            mapper.writeValue(source.toFile(), original);
            var old = preview(target, source);
            assertFalse(old.hasBlockingErrors(), () -> old.operation().messages().toString());
            assertTrue(commit(target, source, old).committed());
            assertEquals(0, count(target, "nmr_policy_version"));
        }
    }

    @Test
    void rolesAndUnguardedCompatibilityCannotImportPoliciesAndInjectedFailureRollsBack() throws Exception
    {
        try (Jpa sourceJpa = new Jpa(directory.resolve("source")); Jpa target = new Jpa(directory.resolve("target")))
        {
            seed(sourceJpa);
            seed(target);
            policies(sourceJpa).save(UUID.randomUUID(), terms(FROM, null));
            Path source = export(sourceJpa, directory.resolve("source.sclx"));
            var preview = preview(target, source);
            for (var role : List.of(ReservedSecurityRole.ACCOUNTANT, ReservedSecurityRole.VIEWER))
            {
                assertThrows(AuthorizationException.class, () -> new SclxImportCommitService(target, () -> COMPANY, guard(target, role))
                        .commit(source, preview, "spoofed", true, true));
            }
            assertThrows(IllegalStateException.class, () -> new SclxImportCommitService(target, () -> COMPANY)
                    .commit(source, preview, "legacy", true, true));
            long audits = count(target, "audit_event");
            for (int failAt : List.of(1, 4))
            {
                java.util.concurrent.atomic.AtomicBoolean reached = new java.util.concurrent.atomic.AtomicBoolean();
                var failing = new SclxImportCommitService(target, () -> COMPANY, guard(target, ReservedSecurityRole.MANAGER),
                        writes ->
                        {
                            if (writes == failAt)
                            {
                                reached.set(true);
                                throw new IllegalStateException("Injected policy/operation audit failure");
                            }
                        });
                assertFalse(failing.commit(source, preview, "spoofed", true, true).committed());
                assertTrue(reached.get(), "The requested rollback boundary must be exercised");
                assertEquals(0, count(target, "nmr_policy_version"));
                assertEquals(0, count(target, "interchange_identity"));
                assertEquals(audits, count(target, "audit_event"));
            }
            assertTrue(commit(target, source, preview).committed());
        }
    }

    @Test
    void nativeReuseConflictsTargetOverlapsAndForeignOwnershipAreGoverned() throws Exception
    {
        try (Jpa sourceJpa = new Jpa(directory.resolve("source")); Jpa target = new Jpa(directory.resolve("target")))
        {
            seed(sourceJpa);
            seed(target);
            UUID id = UUID.randomUUID();
            policies(sourceJpa).save(id, terms(FROM, null));
            Path source = export(sourceJpa, directory.resolve("source.sclx"));
            // Simulate a separately restored native policy, without source identities.
            var value = SclxNmrPolicies.parse(new ObjectMapper().readTree(source.toFile())).get(0).value();
            try (EntityManager em = target.em())
            {
                em.getTransaction().begin();
                Company company = em.createQuery("from Company where code = :code", Company.class).setParameter("code", COMPANY).getSingleResult();
                policies(target).importForInterchange(em, company, value);
                em.getTransaction().commit();
            }
            var nativePreview = preview(target, source);
            var nativeItem = nativePreview.operation().items().stream().filter(i -> i.entityType().equals(SclxNmrPolicies.TYPE)).findFirst().orElseThrow();
            assertNotNull(nativeItem.localEntityId());
            assertFalse(nativePreview.hasBlockingErrors());
            assertTrue(commit(target, source, nativePreview).committed());
            // Target interval changed since its earlier source-specific identity was recorded.
            policies(target).endVersion(policies(target).list().get(0).id(), FROM.plusMonths(2), "Target ended");
            var conflict = preview(target, source);
            assertTrue(conflict.hasBlockingErrors());
            var item = conflict.operation().items().stream().filter(i -> i.entityType().equals(SclxNmrPolicies.TYPE)).findFirst().orElseThrow();
            assertEquals(InterchangeIdentityMatch.CONFLICT, item.identityMatch());
            assertFalse(item.sourceChoiceAllowed());
            var unsafe = new SclxImportPreviewService(target, () -> COMPANY).preview(source, List.of(), List.of(
                    new SclxImportConflictSelection(item.entityType(), item.externalId(), SclxImportConflictChoice.TAKE_SOURCE)));
            assertTrue(unsafe.hasBlockingErrors());
            var kept = new SclxImportPreviewService(target, () -> COMPANY).preview(source, List.of(), List.of(
                    new SclxImportConflictSelection(item.entityType(), item.externalId(), SclxImportConflictChoice.KEEP_TARGET)));
            assertFalse(kept.hasBlockingErrors());
            assertTrue(commit(target, source, kept).committed());
            assertEquals(FROM.plusMonths(2), policies(target).list().get(0).terms().validTo());
            // A new UUID with overlapping dates cannot bypass retained intervals.
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode changed = (ObjectNode) mapper.readTree(source.toFile());
            ((ObjectNode) changed.path("extensions").path("scaJakartaH2").path("nmrPolicies").path("versions").get(0))
                    .put("policyVersionId", "nmr-policy-version:POLICY_TEST:" + UUID.randomUUID());
            mapper.writeValue(source.toFile(), changed);
            assertTrue(preview(target, source).operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_NMR_POLICY_OVERLAP")));
            // The original UUID cannot be restored into a second company in the same DB.
            try (EntityManager em = target.em())
            {
                em.getTransaction().begin();
                Company other = new Company(); other.setCode("OTHER"); other.setDisplayName("Other"); em.persist(other);
                em.getTransaction().commit();
            }
            export(sourceJpa, source);
            var foreign = new SclxImportPreviewService(target, () -> "OTHER").preview(source);
            assertTrue(foreign.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_NATIVE_PORTABLE_ID_OTHER_COMPANY")));
        }
    }

    @Test
    void newlyOverlappingTargetAfterPreviewCannotCommit() throws Exception
    {
        try (Jpa sourceJpa = new Jpa(directory.resolve("source")); Jpa target = new Jpa(directory.resolve("target")))
        {
            seed(sourceJpa); seed(target);
            policies(sourceJpa).save(UUID.randomUUID(), terms(FROM, null));
            Path source = export(sourceJpa, directory.resolve("source.sclx"));
            var approved = preview(target, source);
            policies(target).save(UUID.randomUUID(), terms(FROM, null));
            assertThrows(IllegalStateException.class, () -> commit(target, source, approved));
            assertEquals(1, count(target, "nmr_policy_version"));
            assertEquals(0, count(target, "interchange_identity"));
        }
    }
}
