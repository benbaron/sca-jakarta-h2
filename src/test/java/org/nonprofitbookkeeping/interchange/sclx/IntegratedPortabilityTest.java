package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.persistence.*;
import org.nonprofitbookkeeping.service.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.interchange.sclx.IntegratedPortabilityFixture.*;
import static org.nonprofitbookkeeping.service.DatabaseSnapshotTestSupport.rows;

/** Combined graph acceptance; focused feature tests continue to own exhaustive per-feature variants. */
class IntegratedPortabilityTest
{
    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS) static Path directory;
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    static Path source, file;
    static IntegratedPortabilityFixture fixture;
    static ObjectNode expected;
    static Map<LocalDate, Map<String, JsonNode>> reports;
    static final List<LocalDate> CUTOFFS = List.of(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 5),
            LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 4), LocalDate.of(2026, 6, 2),
            LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 15));

    @BeforeAll
    static void source() throws Exception
    {
        source = directory.resolve("source"); file = directory.resolve("combined.sclx");
        try (Jpa jpa = new Jpa(source))
        {
            fixture = IntegratedPortabilityFixture.populate(jpa, source);
            var document = new SclxCoreSnapshotQueryService(jpa, () -> COMPANY).query(Instant.EPOCH);
            byte[] bytes = new SclxJsonSerializer().serialize(document);
            assertArrayEquals(bytes, new SclxJsonSerializer().serialize(document)); Files.write(file, bytes);
            expected = (ObjectNode) JSON.readTree(bytes);
            var counts = SclxExportCounts.from(document, 0, 0);
            assertAll(() -> assertEquals(1, counts.organizations()), () -> assertEquals(3, counts.funds()),
                    () -> assertEquals(2, counts.budgetCategories()), () -> assertEquals(4, counts.budgets()),
                    () -> assertTrue(counts.transactions() >= 30, counts.toString()), () -> assertTrue(counts.supplementalDetails() >= 12, counts.toString()),
                    () -> assertEquals(2, counts.fundTransfers()), () -> assertEquals(1, counts.nmrPolicyVersions()),
                    () -> assertEquals(1, counts.fixedAssets()), () -> assertEquals(1, counts.depreciationRuns()),
                    () -> assertEquals(2, counts.inventoryItems()), () -> assertEquals(5, counts.inventoryMovements()),
                    () -> assertEquals(1, counts.importBatches()), () -> assertEquals(1, counts.statementLines()),
                    () -> assertEquals(3, counts.reconciliationSessions()), () -> assertEquals(1, counts.reconciliationMatches()),
                    () -> assertEquals(2, counts.periodCloseRanges()), () -> assertEquals(3, counts.periodCloseEvents()));
            for (String excluded : List.of("RECOVERY-ONLY-ALIAS", "recovery-only-divider", "RECOVERY_ONLY_GLOBAL"))
                assertFalse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).contains(excluded), excluded);
            assertFalse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).contains(COPY), "Namespace reserved for target normalization");
            assertFalse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).contains("NMR_DETAIL"), "Other-company ledger is excluded");
            for (String key : List.of("budgetCategories", "budgetMetadata", "fundTransfers", "paymentReferences", "nmrPolicies", "expenseFunctions",
                    "assetAcquisitions", "propertyCustody", "periodClose", "auditHistory", "inventory"))
                assertTrue(expected.path("extensions").path("scaJakartaH2").has(key), key);
            reports = projections(jpa, COMPANY);
            var items = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            assertItem(items, SupplementalOpenItemQueryService.Kind.RECEIVABLE, fixture.merchantItem, LocalDate.of(2026, 6, 2), "30");
            assertItem(items, SupplementalOpenItemQueryService.Kind.RECEIVABLE, fixture.merchantItem, LocalDate.of(2026, 6, 3), "0");
            assertItem(items, SupplementalOpenItemQueryService.Kind.OTHER_LIABILITY, fixture.nmrItem, LocalDate.of(2026, 6, 3), "70");
            assertItem(items, SupplementalOpenItemQueryService.Kind.OTHER_LIABILITY, fixture.nmrItem, LocalDate.of(2026, 6, 30), "0");
        }
    }

    @Test
    void completeGraphAndCanonicalCutoffsSurviveCompanyAndNativeIdRemappingRestartAndRetry() throws Exception
    {
        Path target = directory.resolve("remapped");
        try (Jpa jpa = new Jpa(target))
        {
            // Occupy native account/fund/transaction IDs in another company before importing into COPY.
            FundTransferTestFixture.seed(jpa);
            var unrelated = snapshot(jpa, COMPANY);
            company(jpa, COPY);
            commit(jpa, COPY, file);
            ObjectNode actual = snapshot(jpa, COPY);
            assertGraph(expected, actual);
            assertEquals(unrelated, snapshot(jpa, COMPANY), "Import must leave the occupied other-company ledger unchanged");
            assertReports(jpa, COPY);
            try (var em = jpa.em())
            {
                var sourceBankId = fixture.base.bank();
                var targetBankId = em.createQuery("select a.id from Account a where a.chart.company.code=:code and a.code='1000'", Long.class).setParameter("code", COPY).getSingleResult();
                assertNotEquals(sourceBankId, targetBankId, "The fixture must exercise real native ID remapping");
                assertEquals(0L, ((Number) em.createNativeQuery("select count(*) from budget_category_alias").getSingleResult()).longValue());
                assertEquals(0L, ((Number) em.createNativeQuery("select count(*) from company_ui_state").getSingleResult()).longValue());
            }
            var periods = new PeriodCloseRangeService(jpa);
            var before = rows(target);
            assertThrows(IllegalStateException.class, () -> periods.closeRange(COPY, LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31),
                    "CUSTOM", "tester", "Old source evidence is not current authorization", fixture.reviewId));
            assertEquals(before, rows(target));
            protectedRange(jpa, COPY);
        }
        try (Jpa jpa = new Jpa(target))
        {
            var before = rows(target); commit(jpa, COPY, file);
            assertEquals(before, rows(target), "Restart/reimport must not add business facts, identities or audit history");
            assertGraph(expected, snapshot(jpa, COPY)); assertReports(jpa, COPY);
        }
    }

    @Test
    void populatedTargetConflictsNeedExplicitKeepAndUnsupportedVersionsWriteNothing() throws Exception
    {
        Path target = directory.resolve("conflicts");
        try (Jpa jpa = new Jpa(target))
        {
            commit(jpa, COMPANY, file);
            ObjectNode changed = expected.deepCopy();
            for (var category : changed.path("extensions").path("scaJakartaH2").path("budgetCategories").path("categories"))
                if (category.path("code").asText().equals("PROGRAM")) ((ObjectNode) category).put("name", "Conflicting source name");
            Path changedFile = directory.resolve("changed.sclx"); JSON.writeValue(changedFile.toFile(), changed);
            var service = new SclxImportPreviewService(jpa, () -> COMPANY);
            var preview = service.preview(changedFile); assertTrue(preview.hasBlockingErrors());
            var before = rows(target);
            assertThrows(IllegalStateException.class, () -> importer(jpa, COMPANY).commit(changedFile, preview, "tester", true, true));
            assertEquals(before, rows(target));
            var choices = preview.operation().items().stream().filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                    .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET)).toList();
            assertFalse(choices.isEmpty());
            var resolved = service.preview(changedFile, List.of(), choices);
            assertFalse(resolved.hasBlockingErrors(), resolved.operation().messages().toString());
            assertTrue(importer(jpa, COMPANY).commit(changedFile, resolved, "tester", true, true).committed());
            assertGraph(expected, snapshot(jpa, COMPANY)); assertReports(jpa, COMPANY);
            before = rows(target);
            for (String key : List.of("budgetCategories", "budgetMetadata", "fundTransfers", "nmrPolicies", "propertyCustody", "periodClose"))
            {
                var bad = expected.deepCopy(); ((ObjectNode) bad.path("extensions").path("scaJakartaH2").path(key)).put("version", 999);
                Path badFile = directory.resolve(key + "-unsupported.sclx"); JSON.writeValue(badFile.toFile(), bad);
                var rejected = service.preview(badFile); assertTrue(rejected.hasBlockingErrors(), key);
                if (key.equals("propertyCustody") || key.equals("periodClose"))
                    assertTrue(rejected.operation().messages().stream().anyMatch(m -> m.path().equals("$.extensions.scaJakartaH2." + key) && m.blocking()), key);
                assertThrows(IllegalStateException.class, () -> importer(jpa, COMPANY).commit(badFile, rejected, "tester", true, true));
                assertEquals(before, rows(target), key + " must reject without writes");
            }
            for (String badKind : List.of("custody-quantity", "review-payload"))
            {
                var bad = expected.deepCopy();
                if (badKind.equals("custody-quantity"))
                    ((ObjectNode) bad.path("extensions").path("scaJakartaH2").path("propertyCustody").path("events").get(0)).put("quantity", "not-a-decimal");
                else
                {
                    boolean changedReview = false;
                    for (var audit : bad.path("extensions").path("scaJakartaH2").path("auditHistory").path("events"))
                        if (audit.path("actionType").asText().equals("CLOSE_REVIEW_RECORDED"))
                        {
                            var payload = (ObjectNode) JSON.readTree(audit.path("afterValue").asText()); payload.put("version", 999);
                            ((ObjectNode) audit).put("afterValue", JSON.writeValueAsString(payload)); changedReview = true; break;
                        }
                    assertTrue(changedReview);
                }
                Path invalid = directory.resolve(badKind + ".sclx"); JSON.writeValue(invalid.toFile(), bad);
                var rejected = service.preview(invalid); assertTrue(rejected.hasBlockingErrors(), badKind);
                String code = badKind.equals("custody-quantity") ? "SCLX_PROPERTY_CUSTODY_INVALID" : "SCLX_AUDIT_HISTORY_INVALID";
                assertTrue(rejected.operation().messages().stream().anyMatch(m -> m.code().equals(code) && m.blocking()), badKind);
                assertThrows(IllegalStateException.class, () -> importer(jpa, COMPANY).commit(invalid, rejected, "tester", true, true));
                assertEquals(before, rows(target), badKind);
            }
        }
    }

    @Test
    void authorizationDenialAndInjectedLateFailureCannotLeavePartialCombinedGraph() throws Exception
    {
        var writes = new ArrayList<Integer>();
        try (Jpa probe = new Jpa(directory.resolve("write-probe")))
        {
            var preview = new SclxImportPreviewService(probe, () -> COMPANY).preview(file);
            assertTrue(new SclxImportCommitService(probe, () -> COMPANY, guard(probe, COMPANY, ReservedSecurityRole.MANAGER), writes::add)
                    .commit(file, preview, "tester", true, true).committed());
        }
        int late = writes.get(writes.size() - 2); assertTrue(late > 30, "Exercise a genuinely populated late transaction");
        Path target = directory.resolve("atomic");
        try (Jpa jpa = new Jpa(target))
        {
            new SecurityBootstrapService(jpa).initializeIfUnambiguous();
            long viewerId = new UserAdminService(jpa).listUsers().stream().filter(u -> u.getUsername().equals("VIEWER")).findFirst().orElseThrow().getId();
            var viewer = new AuthorizationGuard(jpa, () -> Optional.of(new AuthenticatedUserSession(viewerId, "VIEWER", "Viewer", COMPANY,
                    Set.of(ReservedSecurityRole.VIEWER), Instant.EPOCH, Instant.EPOCH)));
            var preview = new SclxImportPreviewService(jpa, () -> COMPANY).preview(file);
            var before = rows(target);
            assertThrows(AuthorizationException.class, () -> new SclxImportCommitService(jpa, () -> COMPANY, viewer)
                    .commit(file, preview, "tester", true, true));
            var afterDenial = rows(target);
            before.forEach((table, values) -> { if (!table.equals("security_event")) assertEquals(values, afterDenial.get(table), table); });
            assertEquals(before.get("security_event").size() + 1, afterDenial.get("security_event").size(), "Denial itself must be audited");
            var reached = new AtomicBoolean();
            var result = new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, COMPANY, ReservedSecurityRole.MANAGER), n ->
            {
                if (n >= late) { reached.set(true); throw new IllegalStateException("Synthetic integrated late failure"); }
            }).commit(file, preview, "tester", true, true);
            assertTrue(reached.get()); assertFalse(result.committed()); assertEquals(afterDenial, rows(target), "All partial writes must roll back");
            commit(jpa, COMPANY, file); assertGraph(expected, snapshot(jpa, COMPANY)); assertReports(jpa, COMPANY);
        }
    }

    @Test
    void fullPopulatedDatabaseRecoveryRetainsEveryRowTriggerAndNativeRetry() throws Exception
    {
        Path restored = directory.resolve("restored");
        Map<String, List<String>> before;
        try (Jpa jpa = new Jpa(source))
        {
            before = rows(source);
            for (String table : List.of("budget_category_alias", "company_ui_state", "company_ui_preference", "app_user_credential", "user_company_role", "security_event")) assertFalse(before.get(table).isEmpty(), table);
            var transfer = new DatabaseTransferService(() -> source, ignored -> fail("Recovery must not switch active database"));
            var backup = transfer.backUpDatabase(directory.resolve("complete.zip"));
            var recovery = transfer.restoreDatabaseCopy(backup.backupFile(), restored);
            assertTrue(recovery.validated());
            assertEquals(backup.counts(), recovery.counts(), "Native restore must retain the current populated source");
            assertEquals(before, rows(source));
        }
        try (Jpa jpa = new Jpa(restored))
        {
            var actual = rows(restored);
            assertEquals(before.keySet(), actual.keySet());
            before.forEach((table, values) -> assertEquals(values.size(), actual.get(table).size(), table + " recovered row count"));
            before.forEach((table, values) -> assertEquals(values, actual.get(table), table + " exact recovered values"));
            assertReports(jpa, COMPANY); assertGraph(expected, snapshot(jpa, COMPANY));
            assertEquals(fixture.transfer, new FundTransferService(jpa).save(fixture.base.command(fixture.transferRequest, "75")));
            assertEquals(fixture.closedRange, new PeriodCloseRangeService(jpa).closeRange(COMPANY, CLOSED_START, CLOSED_END,
                    "CUSTOM", "tester", "Retained closed period", fixture.closedRange.id()));
            assertEquals(before, rows(restored), "Native command retries must be exactly write-free");
            protectedRange(jpa, COMPANY);
            long highest;
            try (var em = jpa.em()) { highest = em.createQuery("select max(t.id) from Txn t", Long.class).getSingleResult(); }
            var entered = new TransactionEntryService(jpa).enter(new TransactionCommand(LocalDate.of(2027, 1, 16), null, "Recovered allocator write", null,
                    List.of(FundTransferTestFixture.line(fixture.base.bank(), fixture.base.source(), "1", "0"),
                            FundTransferTestFixture.line(fixture.base.income(), fixture.base.source(), "0", "1"))));
            assertTrue(entered.id() > highest, "Restored identity allocation must not collide with retained rows");
            assertEquals(before.get("txn").size() + 1, rows(restored).get("txn").size());
            assertEquals(before, rows(source), "A future write to the restored copy cannot mutate source");
        }
    }

    private static void assertItem(SupplementalOpenItemQueryService service, SupplementalOpenItemQueryService.Kind kind, UUID id, LocalDate cutoff, String amount)
    {
        var row = service.query(kind, cutoff).rows().stream().filter(r -> id.equals(r.itemId())).findFirst().orElseThrow();
        assertEquals(0, new java.math.BigDecimal(amount).compareTo(row.openBalance()), cutoff + " " + id);
    }

    private static void protectedRange(Jpa jpa, String company)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            assertThrows(ClosedPeriodRangeException.class, () -> PeriodCloseRangeService.requireOpen(em, company, CLOSED_START, "Restored protected write"));
            em.getTransaction().rollback();
        }
    }

    private static SclxImportCommitService importer(Jpa jpa, String company)
    {
        return new SclxImportCommitService(jpa, () -> company, guard(jpa, company, ReservedSecurityRole.MANAGER));
    }

    private static void commit(Jpa jpa, String company, Path input)
    {
        var preview = new SclxImportPreviewService(jpa, () -> company).preview(input);
        assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
        var result = importer(jpa, company).commit(input, preview, "tester", true, true);
        assertTrue(result.committed(), result.toString());
    }

    private static ObjectNode snapshot(Jpa jpa, String company) throws Exception
    {
        return (ObjectNode) JSON.readTree(new SclxJsonSerializer().serialize(new SclxCoreSnapshotQueryService(jpa, () -> company).query(Instant.EPOCH)));
    }

    private static void company(Jpa jpa, String code)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin(); var company = new Company(); company.setCode(code); company.setDisplayName(expected.path("organization").path("name").asText()); em.persist(company); em.getTransaction().commit();
        }
    }

    private static void assertGraph(ObjectNode source, ObjectNode target) throws Exception
    {
        // Only this reserved synthetic namespace changes. Opaque source audit payloads contain DEFAULT and remain untouched.
        var actual = (ObjectNode) JSON.readTree(JSON.writeValueAsString(target).replace(COPY, COMPANY));
        var original = source.deepCopy();
        var sourceAudits = original.path("extensions").path("scaJakartaH2").path("auditHistory").path("events");
        var ids = new HashSet<String>(); sourceAudits.forEach(e -> ids.add(e.path("auditEventId").asText()));
        var retained = JSON.createArrayNode();
        actual.path("extensions").path("scaJakartaH2").path("auditHistory").path("events").forEach(e -> { if (ids.contains(e.path("auditEventId").asText())) retained.add(e); });
        ((ObjectNode) actual.path("extensions").path("scaJakartaH2").path("auditHistory")).set("events", retained);
        assertEquals(ids.size(), retained.size(), "All original source audits must survive");
        var differences = new ArrayList<String>();
        differences(sorted(original), sorted(actual), "$", differences);
        assertTrue(differences.isEmpty(), String.join("\n", differences));
    }

    private static void differences(JsonNode expected, JsonNode actual, String path, List<String> differences)
    {
        if (expected.getNodeType() != actual.getNodeType())
        {
            differences.add(path + ": expected " + expected + ", actual " + actual); return;
        }
        if (expected.isObject())
        {
            if (expected.size() != actual.size()) differences.add(path + " field count: " + expected.size() + " / " + actual.size());
            expected.fields().forEachRemaining(e -> differences(e.getValue(), actual.path(e.getKey()), path + "." + e.getKey(), differences));
        }
        else if (expected.isArray())
        {
            if (expected.size() != actual.size()) differences.add(path + " row count: " + expected.size() + " / " + actual.size());
            for (int i = 0; i < Math.min(expected.size(), actual.size()); i++) differences(expected.get(i), actual.get(i), path + "[" + i + "]", differences);
        }
        else if (!expected.equals(actual)) differences.add(path + ": expected " + expected + ", actual " + actual);
    }

    /** Graph collections are sets of ID-backed facts; explicit line/order fields are retained. */
    private static JsonNode sorted(JsonNode node)
    {
        if (node.isObject())
        {
            var object = JSON.createObjectNode(); var keys = new TreeSet<String>(); node.fieldNames().forEachRemaining(keys::add);
            keys.forEach(k -> object.set(k, sorted(node.get(k)))); return object;
        }
        if (node.isArray())
        {
            var values = new ArrayList<JsonNode>(); node.forEach(v -> values.add(sorted(v))); values.sort(Comparator.comparing(JsonNode::toString));
            var array = JSON.createArrayNode(); values.forEach(array::add); return array;
        }
        return node;
    }

    private static void assertReports(Jpa jpa, String company)
    {
        var actual = projections(jpa, company);
        var differences = new ArrayList<String>();
        for (var cutoff : CUTOFFS)
            reports.get(cutoff).forEach((name, facts) -> differences(sorted(facts), sorted(actual.get(cutoff).get(name)), cutoff + "/" + name, differences));
        assertTrue(differences.isEmpty(), differences.size() + " report differences:\n" + String.join("\n", differences.stream().limit(40).toList()));
    }

    private static Map<LocalDate, Map<String, JsonNode>> projections(Jpa jpa, String company)
    {
        var result = new LinkedHashMap<LocalDate, Map<String, JsonNode>>();
        var financial = new FinancialReportService(jpa, () -> company);
        var items = new SupplementalOpenItemQueryService(jpa, () -> company);
        var property = new org.nonprofitbookkeeping.report.AssetInventoryReportQueryService(jpa, () -> company);
        for (var cutoff : CUTOFFS)
        {
            LocalDate start = LocalDate.of(2026, 1, 1);
            var values = new TreeMap<String, JsonNode>();
            var trial = financial.trialBalance(cutoff, null); assertTrue(trial.isBalanced());
            var balance = financial.balanceSheet(cutoff, null); assertTrue(balance.isBalanced(), cutoff + ": " + balance);
            values.put("trial", JSON.valueToTree(trial)); values.put("balance", JSON.valueToTree(balance));
            values.put("income", JSON.valueToTree(financial.incomeStatement(start, cutoff, null)));
            values.put("ledger", reportFacts(JSON.valueToTree(financial.generalLedgerDetail(start, cutoff, null, 10000))));
            values.put("functions", reportFacts(JSON.valueToTree(financial.functionalExpenses(start, cutoff, null, null))));
            for (var kind : SupplementalOpenItemQueryService.Kind.values()) values.put(kind.name(), reportFacts(JSON.valueToTree(items.query(kind, cutoff))));
            values.put("nmr", reportFacts(JSON.valueToTree(items.queryNmrPeriod(start, cutoff))));
            values.put("merchant", reportFacts(JSON.valueToTree(items.queryMerchantReceivables(start, cutoff))));
            var assetRequest = new org.nonprofitbookkeeping.report.AssetInventoryReportQueryService.FixedAssetReportRequest(start, cutoff, null, null, null, null, org.nonprofitbookkeeping.report.ReportRequest.MAX_ROW_LIMIT);
            var stockRequest = new org.nonprofitbookkeeping.report.AssetInventoryReportQueryService.InventoryReportRequest(start, cutoff, null, null, null, null, org.nonprofitbookkeeping.report.ReportRequest.MAX_ROW_LIMIT);
            values.put("assets", reportFacts(JSON.valueToTree(property.fixedAssetRegister(assetRequest))));
            values.put("depreciation", reportFacts(JSON.valueToTree(property.fixedAssetDepreciation(assetRequest))));
            values.put("inventory", reportFacts(JSON.valueToTree(property.inventoryValuation(stockRequest))));
            values.put("movements", reportFacts(JSON.valueToTree(property.inventoryMovementHistory(stockRequest))));
            values.put("custody", reportFacts(JSON.valueToTree(property.propertyCustodyAsOf(cutoff, org.nonprofitbookkeeping.report.ReportRequest.MAX_ROW_LIMIT))));
            values.put("checks", reportFacts(JSON.valueToTree(new CheckExceptionService(jpa, () -> company, null).report(null, cutoff, cutoff))));
            values.put("budget", sorted(JSON.valueToTree(new BudgetPlanService(jpa, () -> company).activeVariance(cutoff).stream()
                    .map(v -> Map.of("code", v.budgetCategoryCode(), "name", v.budgetCategoryName(), "fund", v.fundCode().orElse(""),
                            "event", v.activityCode().orElse(""), "budget", v.budget(), "actual", v.actual(), "variance", v.variance())).toList())));
            result.put(cutoff, values);
        }
        return result;
    }

    /** Remove only native query references; portable item UUIDs, codes, money and diagnostic facts remain. */
    private static JsonNode reportFacts(JsonNode node)
    {
        if (node.isObject())
        {
            var object = ((ObjectNode) node).deepCopy();
            if (object.path("exceptions").asText().startsWith("Cancelled pair:"))
            {
                // Validate this derived label against its own canonical relationship fields before removing native IDs.
                assertEquals("Cancelled pair: original #" + object.path("transactionId").asLong()
                        + " / reversal #" + object.path("reversalId").asLong() + " / replacement #" + object.path("replacementId").asLong(),
                        object.path("exceptions").asText());
                object.put("exceptions", "Cancelled pair: original / reversal / replacement");
            }
            object.remove(List.of("transactionId", "sourceTransactionId", "openingTransactionId", "splitId", "txnId", "accountId", "fundId", "activityId", "merchantId", "recipientId", "assetId", "assetAccountId", "accumulatedAccountId", "latestMovementId", "movementId", "eventId", "subjectId", "correctionOfEventId", "reversalId", "replacementId", "replacesId", "sessionId", "statementLineId"));
            if (object.path("itemId").isNumber()) object.remove("itemId");
            object.fields().forEachRemaining(e -> object.set(e.getKey(), reportFacts(e.getValue()))); return object;
        }
        if (node.isArray())
        {
            var array = JSON.createArrayNode(); node.forEach(v -> array.add(reportFacts(v))); return sorted(array);
        }
        return node;
    }
}
