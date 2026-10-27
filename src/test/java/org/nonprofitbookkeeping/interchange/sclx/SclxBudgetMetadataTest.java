package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SclxBudgetMetadataTest
{
    @TempDir Path directory;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDate START = LocalDate.of(2026, 7, 1), END = LocalDate.of(2027, 6, 30);

    private Path source(boolean calendar) throws Exception
    {
        Path database = directory.resolve("source-" + calendar);
        Path file = directory.resolve("source-" + calendar + ".sclx");
        try (Jpa jpa = new Jpa(database))
        {
            var f = FundTransferTestFixture.seed(jpa);
            long categoryId, eventId;
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var owner = new CompanyOwnershipService(jpa).requireCompany(em, "DEFAULT");
                var category = new BudgetCategory(); category.setCompany(owner); category.setCode("PROGRAM"); category.setName("Program"); em.persist(category);
                var event = new Activity(); event.setCompany(owner); event.setCode("EVENT"); event.setName("Retained event"); em.persist(event);
                em.flush(); categoryId = category.getId(); eventId = event.getId(); em.getTransaction().commit();
            }
            var service = new BudgetPlanService(jpa);
            for (String version : List.of("OLD", "ACTIVE", "DRAFT", "NEVER-ACTIVE"))
            {
                var plan = service.createDraft(new BudgetPlanCommand("Synthetic " + version, 2026, version,
                        calendar ? START.withDayOfYear(1) : START, calendar ? LocalDate.of(2026, 12, 31) : END, "Header " + version));
                service.replaceDraftLines(plan.id(), List.of(
                        new BudgetLineCommand(categoryId, f.source(), eventId, YearMonth.of(calendar ? 2026 : 2027, 2), new BigDecimal("123.4567"), "Line " + version),
                        new BudgetLineCommand(categoryId, f.destination(), null, null, new BigDecimal("40"), "General scope")));
                if (version.equals("OLD") || version.equals("ACTIVE")) service.activate(plan.id());
                if (version.equals("NEVER-ACTIVE")) service.archive(plan.id());
            }
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                em.find(Fund.class, f.source()).setActive(false);
                em.find(Activity.class, eventId).setActive(false);
                for (var plan : em.createQuery("from BudgetPlan", BudgetPlan.class).getResultList())
                {
                    plan.setNotes(plan.getVersionCode().equals("DRAFT") ? null : "  Unicode café\n" + plan.getVersionCode() + "  ");
                    plan.restoreImportTimestamps(Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2026-09-01T01:02:03.123456Z"));
                }
                for (var line : em.createQuery("from BudgetLine", BudgetLine.class).getResultList())
                {
                    line.setNotes(line.getActivity() == null ? null : "  Line café\nexact spacing  ");
                    line.restoreImportTimestamps(Instant.parse("2021-01-01T00:00:00Z"), Instant.parse("2026-09-02T01:02:03.123456Z"));
                }
                em.getTransaction().commit();
            }
            var doc = new SclxCoreSnapshotQueryService(jpa).query(Instant.EPOCH);
            byte[] bytes = new SclxJsonSerializer().serialize(doc);
            assertArrayEquals(bytes, new SclxJsonSerializer().serialize(doc));
            Files.write(file, bytes);
        }
        return file;
    }

    @Test
    void exactFiscalHistoryNotesInactiveScopesAndNativeStatesSurviveFileImportRestartAndRetry() throws Exception
    {
        Path file = source(false), target = directory.resolve("target");
        var expected = SclxBudgetMetadata.parse(JSON.readTree(file.toFile()));
        try (Jpa jpa = new Jpa(target))
        {
            var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            assertTrue(preview.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_BUDGET_METADATA")));
            var imported = new SclxImportCommitService(jpa, () -> "DEFAULT").commit(file, preview, "tester", true, true);
            assertTrue(imported.committed(), imported.toString());
            assertEquals(expected, reexport(jpa));
            try (var em = jpa.em())
            {
                assertEquals(2L, em.createQuery("select count(p) from BudgetPlan p where p.status=:status", Long.class).setParameter("status", BudgetPlan.Status.ARCHIVED).getSingleResult());
                var never = em.createQuery("from BudgetPlan p where p.versionCode='NEVER-ACTIVE'", BudgetPlan.class).getSingleResult();
                assertNull(never.getActivatedAt()); assertNotNull(never.getArchivedAt());
                assertFalse(em.createQuery("from Activity", Activity.class).getSingleResult().isActive());
                assertTrue(em.createQuery("from BudgetLine l where l.periodMonth='2027-02'", BudgetLine.class).getResultList().size() > 0);
            }
        }
        try (Jpa jpa = new Jpa(target))
        {
            long audits = count(jpa, "AuditEvent"), identities = count(jpa, "InterchangeIdentity");
            var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(jpa, () -> "DEFAULT").commit(file, preview, "tester", true, true).committed());
            assertEquals(expected, reexport(jpa)); assertEquals(audits, count(jpa, "AuditEvent")); assertEquals(identities, count(jpa, "InterchangeIdentity"));
        }
    }

    @Test
    void crossCompanyRemappingRetainsExactMetadataWithoutClaimingForeignOwnership() throws Exception
    {
        Path file = source(false);
        try (Jpa jpa = new Jpa(directory.resolve("company")))
        {
            try (var em = jpa.em())
            {
                em.getTransaction().begin(); var owner = new Company(); owner.setCode("COPY"); owner.setDisplayName("Copy"); em.persist(owner); em.getTransaction().commit();
            }
            var preview = new SclxImportPreviewService(jpa, () -> "COPY").preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            var imported = new SclxImportCommitService(jpa, () -> "COPY").commit(file, preview, "tester", true, true);
            assertTrue(imported.committed(), imported.toString());
            try (var em = jpa.em())
            {
                assertEquals(4L, em.createQuery("select count(p) from BudgetPlan p where p.company.code='COPY'", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(p) from BudgetPlan p where p.company.code='DEFAULT'", Long.class).getSingleResult());
            }
            var exported = new SclxCoreSnapshotQueryService(jpa, () -> "COPY").query(Instant.EPOCH);
            var copy = SclxBudgetMetadata.parse(JSON.readTree(new SclxJsonSerializer().serialize(exported)));
            var original = SclxBudgetMetadata.parse(JSON.readTree(file.toFile()));
            assertEquals(new HashSet<>(original.plans().values()), new HashSet<>(copy.plans().values()));
            assertEquals(new HashSet<>(original.lines().values()), new HashSet<>(copy.lines().values()));
        }
    }

    @Test
    void changedAndStrippedFactsConflictAndKeepTargetPreservesWholeHistory() throws Exception
    {
        Path file = source(true);
        ObjectNode original = (ObjectNode) JSON.readTree(file.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("conflicts")))
        {
            var previews = new SclxImportPreviewService(jpa, () -> "DEFAULT");
            var imported = new SclxImportCommitService(jpa, () -> "DEFAULT").commit(file, previews.preview(file), "tester", true, true);
            assertTrue(imported.committed(), imported.toString());
            var expected = reexport(jpa);
            for (String kind : List.of("header", "line", "stripped"))
            {
                ObjectNode changed = original.deepCopy();
                var app = (ObjectNode) changed.path("extensions").path("scaJakartaH2");
                if (kind.equals("stripped")) app.remove(SclxBudgetMetadata.KEY);
                else ((ObjectNode) app.path(SclxBudgetMetadata.KEY).path(kind.equals("header") ? "plans" : "lines").get(0)).put("notes", "Changed source note");
                Path input = directory.resolve(kind + ".sclx"); JSON.writeValue(input.toFile(), changed);
                var conflict = previews.preview(input);
                assertTrue(conflict.hasBlockingErrors());
                assertTrue(conflict.operation().items().stream().anyMatch(i -> i.entityType().equals("BUDGET") && i.identityMatch() == InterchangeIdentityMatch.CONFLICT));
                assertTrue(conflict.operation().items().stream().filter(i -> i.entityType().equals("BUDGET") && i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                        .noneMatch(SclxImportEntityPreview::sourceChoiceAllowed));
                var keep = conflict.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                        .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
                var resolved = previews.preview(input, List.of(), keep);
                assertFalse(resolved.hasBlockingErrors(), resolved.operation().messages().toString());
                var retained = new SclxImportCommitService(jpa, () -> "DEFAULT").commit(input, resolved, "tester", true, true);
                assertTrue(retained.committed(), kind + ": " + retained.messages());
                assertEquals(expected, reexport(jpa));
            }
            ObjectNode extra = original.deepCopy();
            var newLine = ((ArrayNode)extra.path("budgets").get(0).path("lines")).addObject();
            newLine.put("lineId", "new-budget-line"); newLine.put("categoryCode", "PROGRAM");
            newLine.put("periodMonth", "2026-03"); newLine.put("amount", "1.0000");
            var newHistory = ((ArrayNode)extra.path("extensions").path("scaJakartaH2").path(SclxBudgetMetadata.KEY).path("lines")).addObject();
            newHistory.put("lineId", "new-budget-line"); newHistory.putNull("notes");
            newHistory.put("createdAt", "2020-01-01T00:00:00Z"); newHistory.put("updatedAt", "2020-01-01T00:00:00Z");
            Path added = directory.resolve("added-line.sclx"); JSON.writeValue(added.toFile(), extra);
            var conflict = previews.preview(added);
            var keep = conflict.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                    .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
            var blocked = previews.preview(added, List.of(), keep);
            assertTrue(blocked.hasBlockingErrors());
            assertTrue(blocked.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_KEPT_BUDGET_NEW_LINE")));
            assertThrows(IllegalStateException.class, () -> new SclxImportCommitService(jpa, () -> "DEFAULT").commit(added, blocked, "tester", true, true));
            assertEquals(expected, reexport(jpa));
        }
    }

    @Test
    void malformedVersionsShapesReferencesDatesAndScopesBlockBeforeMutation() throws Exception
    {
        Path file = source(false); ObjectNode original = (ObjectNode) JSON.readTree(file.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("invalid")))
        {
            for (String kind : List.of("version", "overflow", "extra", "null", "missing", "duplicate", "foreign", "badtime", "precision", "reversed", "active", "activation", "archiveMissing", "draftArchived", "month", "line", "partial", "notes", "multibyte"))
            {
                ObjectNode root = original.deepCopy();
                ObjectNode app = (ObjectNode) root.path("extensions").path("scaJakartaH2");
                ObjectNode ext = (ObjectNode) app.path(SclxBudgetMetadata.KEY);
                ObjectNode header = (ObjectNode) ext.path("plans").get(0);
                switch (kind)
                {
                    case "version" -> ext.put("version", 2);
                    case "overflow" -> ext.put("version", 4294967297L);
                    case "extra" -> header.put("unexpected", true);
                    case "null" -> app.putNull(SclxBudgetMetadata.KEY);
                    case "missing" -> header.remove("createdAt");
                    case "duplicate" -> ((ArrayNode)ext.path("plans")).add(header.deepCopy());
                    case "foreign" -> header.put("budgetId", "budget:FOREIGN:2026:ACTIVE");
                    case "badtime" -> header.put("updatedAt", "tomorrow");
                    case "precision" -> header.put("updatedAt", "2026-01-01T00:00:00.123456789Z");
                    case "reversed" -> header.put("periodEnd", "2025-01-01");
                    case "active" -> header.put("status", "DRAFT");
                    case "activation" -> header.putNull("activatedAt");
                    case "archiveMissing" ->
                    {
                        ((ObjectNode)root.path("budgets").get(0)).put("active", false);
                        header.put("status", "ARCHIVED"); header.putNull("archivedAt");
                    }
                    case "draftArchived" ->
                    {
                        ((ObjectNode)root.path("budgets").get(0)).put("active", false);
                        header.put("status", "DRAFT"); header.put("archivedAt", "2026-01-01T00:00:00Z");
                    }
                    case "month" -> ((ObjectNode)root.path("budgets").get(0).path("lines").get(0)).put("periodMonth", "2028-01");
                    case "line" -> ((ObjectNode)ext.path("lines").get(0)).put("lineId", "foreign-line");
                    case "partial" -> ((ArrayNode)ext.path("lines")).remove(0);
                    case "notes" -> header.put("notes", "x".repeat(1_048_577));
                    case "multibyte" -> header.put("notes", "€".repeat(400_000));
                }
                Path input = directory.resolve("invalid-" + kind + ".sclx"); JSON.writeValue(input.toFile(), root);
                var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(input);
                assertTrue(preview.hasBlockingErrors(), kind);
                assertThrows(IllegalStateException.class, () -> new SclxImportCommitService(jpa, () -> "DEFAULT").commit(input, preview, "tester", true, true), kind);
                assertEquals(0, count(jpa, "BudgetPlan")); assertEquals(0, count(jpa, "BudgetLine")); assertEquals(0, count(jpa, "InterchangeIdentity"));
            }
        }
    }

    @Test
    void legacyAbsenceRetainsReducedContractAndLateFailureRollsBackEnrichedGraph() throws Exception
    {
        Path file = source(true); ObjectNode root = (ObjectNode)JSON.readTree(file.toFile());
        ((ObjectNode)root.path("extensions").path("scaJakartaH2")).remove(SclxBudgetMetadata.KEY);
        assertEquals(root, SclxBudgetMetadata.identityDocument(root), "Legacy identity projection must remain byte-shape compatible");
        Path legacy = directory.resolve("legacy.sclx"); JSON.writeValue(legacy.toFile(), root);
        try (Jpa jpa = new Jpa(directory.resolve("legacy")))
        {
            var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(legacy);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(jpa, () -> "DEFAULT").commit(legacy, preview, "tester", true, true).committed());
            try (var em = jpa.em())
            {
                var old = em.createQuery("from BudgetPlan p where p.versionCode='OLD'", BudgetPlan.class).getSingleResult();
                assertEquals(BudgetPlan.Status.DRAFT, old.getStatus()); assertEquals(LocalDate.of(2026, 1, 1), old.getPeriodStart());
                assertEquals("", old.getNotes());
            }
        }
        try (Jpa jpa = new Jpa(directory.resolve("rollback")))
        {
            var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file);
            var reached = new java.util.concurrent.atomic.AtomicBoolean();
            var failing = new SclxImportCommitService(jpa, () -> "DEFAULT", writes ->
            {
                if (count(jpa, "BudgetLine") > 0) fail("Uncommitted import must not be externally visible");
                if (writes >= 20)
                {
                    reached.set(true);
                    throw new IllegalStateException("Synthetic late failure");
                }
            });
            assertFalse(failing.commit(file, preview, "tester", true, true).committed());
            assertTrue(reached.get(), "The injected late failure must actually be reached");
            assertEquals(0, count(jpa, "BudgetPlan")); assertEquals(0, count(jpa, "BudgetLine"));
            assertEquals(0, count(jpa, "AuditEvent")); assertEquals(0, count(jpa, "InterchangeIdentity"));
            assertTrue(new SclxImportCommitService(jpa, () -> "DEFAULT").commit(file,
                    new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file), "tester", true, true).committed());
        }
    }

    private static SclxBudgetMetadata.Data reexport(Jpa jpa) throws Exception
    {
        return SclxBudgetMetadata.parse(JSON.readTree(new SclxJsonSerializer().serialize(new SclxCoreSnapshotQueryService(jpa).query(Instant.EPOCH))));
    }

    @Test
    void viewerCannotImportEnrichedBudgetHistory() throws Exception
    {
        Path file = source(false);
        try (Jpa jpa = new Jpa(directory.resolve("denied")))
        {
            var session = new AuthenticatedUserSession(9001, "viewer", "Viewer", "DEFAULT", Set.of(ReservedSecurityRole.VIEWER), Instant.EPOCH, Instant.EPOCH);
            var guard = new AuthorizationGuard(jpa, () -> Optional.of(session));
            var preview = new SclxImportPreviewService(jpa, () -> "DEFAULT").preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            assertThrows(AuthorizationException.class, () -> new SclxImportCommitService(jpa, () -> "DEFAULT", guard).commit(file, preview, "tester", true, true));
            assertEquals(0, count(jpa, "BudgetPlan")); assertEquals(0, count(jpa, "BudgetLine")); assertEquals(0, count(jpa, "InterchangeIdentity"));
        }
    }

    private static long count(Jpa jpa, String type)
    {
        try (var em = jpa.em())
        {
            return em.createQuery("select count(e) from " + type + " e", Long.class).getSingleResult();
        }
    }
}
