package org.nonprofitbookkeeping.interchange.coa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ChartOfAccountsJsonReviewTest
{
    @TempDir Path directory;

    @Test
    void fixesTheReportedSubtypeConflictWithoutWritingUntilExplicitCommit() throws Exception
    {
        try (Jpa jpa = database())
        {
            Path file = source();
            String original = Files.readString(file);
            var json = json(jpa);
            var preview = json.preview(request(file, CoaImportMode.MERGE_BY_CODE));
            assertTrue(preview.messages().stream().anyMatch(m -> m.code().equals("COA_SUBTYPE_INCOMPATIBLE")));
            var draft = new CoaImportReviewDraft(preview);
            correctSubtype(draft);
            var corrected = json.preview(draft.request(false));
            assertFalse(corrected.hasBlockingErrors(), corrected.messages().toString());
            assertTrue(draft.updated(2));
            assertEquals(0, count(jpa, "Account"));
            assertEquals(0, count(jpa, "Txn"));
            assertEquals(original, json.sourceText(corrected));
            var result = new ChartOfAccountsJsonImportService(jpa, () -> "REVIEW").commit(corrected);
            assertTrue(result.committed(), result.messages().toString());
            assertEquals(3, count(jpa, "Account"));
            assertEquals(0, count(jpa, "Txn"));
            assertEquals(original, Files.readString(file));
            try (var em = jpa.em())
            {
                Account saved = em.createQuery("from Account where code='3000'", Account.class).getSingleResult();
                assertNull(saved.getSubtype());
                assertEquals(NormalBalance.CREDIT, saved.getNormalBalance());
            }
        }
    }

    @Test
    void removesMultipleProposalsAndRevalidatesTheirDependantsWithoutDeletingSavedAccounts() throws Exception
    {
        try (Jpa jpa = database())
        {
            var json = json(jpa);
            var draft = new CoaImportReviewDraft(json.preview(request(source(), CoaImportMode.MERGE_BY_CODE)));
            draft.remove(List.of(0, 2));
            var missingParent = json.preview(draft.request(false));
            assertTrue(missingParent.hasBlockingErrors());
            assertEquals(1, missingParent.items().size());
            var child = draft.account(1);
            draft.apply(1, definition(child, child.code(), null, child.subtype(), BigDecimal.ZERO));
            var fixed = json.preview(draft.request(false));
            assertFalse(fixed.hasBlockingErrors(), fixed.messages().toString());
            assertTrue(new ChartOfAccountsJsonImportService(jpa, () -> "REVIEW").commit(fixed).committed());
            assertEquals(1, count(jpa, "Account"));
            draft.remove(List.of(1));
            assertThrows(IllegalStateException.class, () -> json.preview(draft.request(false)), "The target changed after the successful import");
            var fresh = new CoaImportReviewDraft(json.preview(request(source(), CoaImportMode.MERGE_BY_CODE)));
            fresh.remove(List.of(0, 1, 2));
            assertTrue(json.preview(fresh.request(false)).messages().stream().anyMatch(m -> m.code().equals("COA_REVIEW_EMPTY")));
            assertEquals(1, count(jpa, "Account"), "Deleting proposals never deletes saved accounts");
        }
    }

    @Test
    void mappedEditsKeepOriginalIdentityAndResolveParentsToEditedTargetCodes() throws Exception
    {
        try (Jpa jpa = database())
        {
            var draft = new CoaImportReviewDraft(json(jpa).preview(request(source(), CoaImportMode.MAP_CODES)));
            correctSubtype(draft);
            draft.apply(0, definition(draft.account(0), "A", null, null, BigDecimal.ZERO));
            draft.apply(1, definition(draft.account(1), "B", "1000", AccountSubtype.CASH, BigDecimal.ZERO));
            draft.apply(2, definition(draft.account(2), "C", null, null, BigDecimal.ZERO));
            var reviewed = json(jpa).preview(draft.request(false));
            assertFalse(reviewed.hasBlockingErrors(), reviewed.messages().toString());
            assertEquals("1000", reviewed.items().get(0).account().sourceCode());
            assertEquals("A", reviewed.items().get(0).targetCode());
            assertTrue(new ChartOfAccountsJsonImportService(jpa, () -> "REVIEW").commit(reviewed).committed());
            try (var em = jpa.em())
            {
                Account child = em.createQuery("from Account where code='B'", Account.class).getSingleResult();
                assertEquals("A", child.getParent().getCode());
            }
        }
    }

    @Test
    void rejectsSourceTargetAndCompanyDriftBeforeAnyWrite() throws Exception
    {
        try (Jpa jpa = database())
        {
            Path source = source();
            var json = json(jpa);
            var draft = new CoaImportReviewDraft(json.preview(request(source, CoaImportMode.MERGE_BY_CODE)));
            correctSubtype(draft);
            var reviewed = json.preview(draft.request(false));
            Files.writeString(source, Files.readString(source).replace("Checking", "Replaced checking"));
            assertThrows(IllegalStateException.class, () -> json.preview(draft.request(false)));
            assertThrows(IllegalStateException.class, () -> new ChartOfAccountsJsonImportService(jpa, () -> "REVIEW").commit(reviewed));
            source();
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var chart = em.createQuery("from ChartOfAccounts where name='Target'", ChartOfAccounts.class).getSingleResult();
                chart.setVersion("changed");
                em.getTransaction().commit();
            }
            assertThrows(IllegalStateException.class, () -> json.preview(draft.request(false)));
            assertThrows(IllegalStateException.class, () -> new ChartOfAccountsJsonService(jpa, () -> "DEFAULT").preview(draft.request(false)));
            assertEquals(0, count(jpa, "Account"));
        }
    }

    @Test
    void openingAcknowledgementAndCentPrecisionStillApplyToReviewedDefinitions() throws Exception
    {
        try (Jpa jpa = database())
        {
            var json = json(jpa);
            var draft = new CoaImportReviewDraft(json.preview(request(source(), CoaImportMode.MERGE_BY_CODE)));
            correctSubtype(draft);
            draft.apply(1, definition(draft.account(1), "1010", "1000", AccountSubtype.CASH, new BigDecimal("1.234")));
            assertTrue(json.preview(draft.request(true)).hasBlockingErrors());
            draft.apply(1, definition(draft.account(1), "1010", "1000", AccountSubtype.CASH, new BigDecimal("12.34")));
            var unconfirmed = json.preview(draft.request(false));
            assertFalse(unconfirmed.hasBlockingErrors());
            assertFalse(unconfirmed.confirmationsSatisfied());
            var service = new ChartOfAccountsJsonImportService(jpa, () -> "REVIEW");
            assertThrows(IllegalStateException.class, () -> service.commit(unconfirmed));
            assertTrue(service.commit(json.preview(draft.request(true))).committed());
            assertEquals(0, count(jpa, "Txn"));
        }
    }

    @Test
    void lateFailureRollsBackReviewedAccountsChartAndIdentityReceipts() throws Exception
    {
        try (Jpa jpa = database())
        {
            var json = json(jpa);
            var draft = new CoaImportReviewDraft(json.preview(request(source(), CoaImportMode.CREATE_NEW_CHART)));
            correctSubtype(draft);
            var approved = json.preview(draft.request(false));
            var service = new ChartOfAccountsJsonImportService(jpa, () -> "REVIEW", written ->
            {
                if (written == 2)
                {
                    throw new IllegalStateException("late reviewed-import failure");
                }
            });
            var result = service.commit(approved);
            assertFalse(result.committed());
            assertTrue(result.rolledBack());
            assertEquals(0, count(jpa, "Account"));
            assertEquals(0, count(jpa, "InterchangeIdentity"));
            try (var em = jpa.em())
            {
                assertEquals(0L, em.createQuery("select count(c) from ChartOfAccounts c where name='Reviewed new chart'", Long.class).getSingleResult());
            }
        }
    }

    @Test
    void reviewPayloadIsImmutableAndCannotRewriteSourceIdentityOrUnsupportedFacts() throws Exception
    {
        try (Jpa jpa = database())
        {
            var json = json(jpa);
            var preview = json.preview(request(source(), CoaImportMode.MERGE_BY_CODE));
            var original = preview.originalChart().accounts().get(0);
            var forged = new CoaAccountData("OTHER", original.code(), original.name(), original.type(), original.function(),
                    original.subtype(), original.normalBalance(), original.parentCode(), original.posting(), original.active(),
                    null, null, BigDecimal.ZERO, null, "USD", List.of(), List.of(), Map.of());
            var payload = new CoaReviewEdits(preview.sourceSha256(), preview.scope(), Map.of(0, forged), Set.of());
            var request = new CoaImportRequest(preview.sourceFile(), CoaImportMode.MERGE_BY_CODE, "", "", Map.of(), false, payload);
            assertThrows(UnsupportedOperationException.class, () -> payload.replacements().clear());
            assertThrows(IllegalArgumentException.class, () -> json.preview(request));
            assertEquals(0, count(jpa, "Account"));
        }
    }

    @Test
    void reviewedEditsCannotBypassStructuralHistoryProtection() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("history")))
        {
            var fixture = org.nonprofitbookkeeping.service.FundTransferTestFixture.seed(jpa);
            var json = new ChartOfAccountsJsonService(jpa, () -> "DEFAULT");
            Path source = directory.resolve("historical-chart.json");
            json.exportActiveChart(source);
            var preview = json.preview(request(source, CoaImportMode.MERGE_BY_CODE));
            var draft = new CoaImportReviewDraft(preview);
            int bank = draft.rows().stream().filter(row -> draft.account(row).code().equals("1000")).findFirst().orElseThrow();
            var value = draft.account(bank);
            draft.apply(bank, CoaImportReviewDraft.definition(value, value.code(), value.name(), value.type(),
                    null, value.subtype(), value.normalBalance(), value.parentCode(), value.posting(), value.active(),
                    value.effectiveFrom(), value.effectiveTo(), value.openingBalance(), value.description(), value.currency()));
            var invalid = json.preview(draft.request(false));
            assertTrue(invalid.messages().stream().anyMatch(message -> message.code().equals("COA_HISTORY_STRUCTURAL_CONFLICT")));
            assertThrows(IllegalStateException.class, () -> new ChartOfAccountsJsonImportService(jpa, () -> "DEFAULT").commit(invalid));
            try (var em = jpa.em())
            {
                assertEquals(AccountFunction.BANK, em.find(Account.class, fixture.bank()).getAccountFunction());
            }
            assertEquals(1, count(jpa, "Txn"));
        }
    }

    private Jpa database()
    {
        Jpa jpa = new Jpa(directory.resolve("review"));
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode("REVIEW"); company.setDisplayName("Review fixture"); company.setDefaultCurrency("USD");
            em.persist(company);
            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company); chart.setName("Target"); chart.setVersion("1"); chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart); company.setActiveChartOfAccounts(chart);
            em.getTransaction().commit();
        }
        return jpa;
    }

    private Path source() throws Exception
    {
        return Files.writeString(directory.resolve("chart.json"), """
                {"format":"SCA-COA","version":"1.0","chart":{"name":"Review fixture","chartVersion":"1","status":"DRAFT","currency":"USD"},
                 "accounts":[
                  {"code":"1000","name":"Assets","type":"ASSET","normalBalance":"DEBIT","posting":false},
                  {"code":"1010","name":"Checking","type":"BANK","subtype":"CASH","normalBalance":"DEBIT","parentCode":"1000"},
                  {"code":"3000","name":"Net assets","type":"EQUITY","subtype":"INVENTORY","normalBalance":"CREDIT"}]}
                """);
    }

    private static CoaImportRequest request(Path path, CoaImportMode mode)
    {
        return new CoaImportRequest(path, mode, "Reviewed new chart", "1", Map.of(), false);
    }

    private static ChartOfAccountsJsonService json(Jpa jpa)
    {
        return new ChartOfAccountsJsonService(jpa, () -> "REVIEW");
    }

    private static void correctSubtype(CoaImportReviewDraft draft)
    {
        var value = draft.account(2);
        draft.apply(2, definition(value, value.code(), value.parentCode(), null, value.openingBalance()));
    }

    private static CoaAccountData definition(CoaAccountData value, String code, String parent, AccountSubtype subtype, BigDecimal opening)
    {
        return CoaImportReviewDraft.definition(value, code, value.name(), value.type(), value.function(), subtype,
                value.normalBalance(), parent, value.posting(), value.active(), value.effectiveFrom(), value.effectiveTo(),
                opening, value.description(), value.currency());
    }

    private static long count(Jpa jpa, String entity)
    {
        try (var em = jpa.em())
        {
            return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
        }
    }
}
