package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.AssetInventoryReportQueryService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.COMPANY;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.DATE;

class OperationalAccountingPacketTest
{
    @TempDir Path directory;

    @Test
    void merchantNmrSchedulesEarlierBankItemsRecordedSessionsAndBudgetsShareOneScopeWithoutWrites() throws Exception
    {
        Path db = directory.resolve("merchant");
        try (Jpa jpa = new Jpa(db))
        {
            var f = MerchantSettlementTestFixture.seed(jpa);
            var base = f.nmr().base();
            var entry = base.entry(jpa);
            long earlier = entry.enter(new TransactionCommand(DATE.minusDays(1), null, "Earlier outstanding", null,
                    List.of(base.line(base.cash(), "7.1234", "0", base.event()), base.line(base.income(), "0", "7.1234", base.event())))).id();
            entry.enter(f.receipt());
            entry.enter(f.payout());
            long bankId = configureBank(jpa, COMPANY, base.cash(), DATE.minusDays(10));
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var bankSplit = em.createQuery("from TxnSplit s where s.txn.id = :id and s.account.id = :account", TxnSplit.class)
                        .setParameter("id", earlier).setParameter("account", base.cash()).getSingleResult();
                bankSplit.setBankCleared(true);
                bankSplit.setBankClearedOn(DATE.plusDays(10));
                em.getTransaction().commit();
            }
            var workspace = new BankReconciliationWorkspaceService(jpa);
            var session = workspace.start(new BankReconciliationWorkspaceService.StartCommand(COMPANY, bankId, DATE.plusDays(1),
                    new BigDecimal("957.1234"), BankReconciliationWorkspaceService.ClearedStatePolicy.WARN_ONLY, "Synthetic statement"));
            assertThrows(IllegalArgumentException.class, () -> workspace.readForPacket(session.sessionId(), "DEFAULT"));
            workspace.addManualLine(new BankReconciliationWorkspaceService.ManualStatementLineCommand(session.sessionId(),
                    DATE.plusDays(1), new BigDecimal("950"), "Processor payout", "PROC-001"));
            var budget = budget(jpa, COMPANY, base.fund(), base.event());
            var canonical = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            var nmr = canonical.queryNmrPeriod(DATE, DATE.plusDays(1));
            var merchant = canonical.queryMerchantReceivables(DATE, DATE.plusDays(1));
            long transactions = count(jpa, "Txn"), audits = count(jpa, "AuditEvent");
            String stored = storedSession(jpa, session.sessionId());
            var result = new CoreAccountingPacketService(() -> db, () -> COMPANY, null)
                    .export(DATE, DATE.plusDays(1), directory.resolve("merchant.zip"));
            try (var zip = new ZipFile(result.destination().toFile()))
            {
                assertTrue(result.exceptionCount() > 0, "Unfinished session/Unclassified fee must remain visible.");
                assertTrue(read(zip, "Outstanding Bank Items.csv").contains("Earlier outstanding"));
                assertFalse(read(zip, "Bank Activity.csv").contains("Earlier outstanding"));
                var earlierRow = rows(zip, "Outstanding Bank Items.csv").stream().filter(r -> r.get("transactionId").equals(Long.toString(earlier))).findFirst().orElseThrow();
                assertEquals("false", earlierRow.get("clearedThroughCutoff"));
                assertEquals(DATE.plusDays(10).toString(), earlierRow.get("clearedOn"));
                var bank = rows(zip, "Bank Configuration.csv").get(0);
                money("7.1234", bank.get("bookBeginning")); money("957.1234", bank.get("bookEnding"));
                assertTrue(read(zip, "Reconciliation Sessions.csv").contains(Long.toString(session.sessionId())));
                assertTrue(read(zip, "Reconciliation Differences.csv").contains("UNMATCHED"));
                assertTrue(read(zip, "Reconciled Statement Lines.csv").contains("PROC-001"));
                assertFalse(rows(zip, "Reconciled Ledger Lines.csv").isEmpty());
                assertTrue(rows(zip, "General Ledger.csv").stream().anyMatch(r -> r.get("merchantId").equals(Long.toString(f.nmr().merchant()))
                        && r.get("eventId").equals(Long.toString(base.event())) && r.get("fundId").equals(Long.toString(base.fund()))));
                for (var kind : SupplementalOpenItemQueryService.Kind.values())
                {
                    assertNotNull(zip.getEntry("Schedule " + kind.name() + ".csv"));
                    assertTrue(read(zip, "Schedule " + kind.name() + ".csv").contains("openBalance"));
                }
                var summary = rows(zip, "Merchant Summaries.csv").get(0);
                money(merchant.summaries().get(0).closing().toPlainString(), summary.get("closing"));
                money("30", summary.get("latestObservation.amount"));
                money("950", summary.get("payout")); money("20", summary.get("fee"));
                var nmrSummary = rows(zip, "NMR Summaries.csv").get(0);
                money(nmr.summaries().get(0).closing().toPlainString(), nmrSummary.get("closing"));
                assertTrue(read(zip, "NMR Movements.csv").contains("COLLECTION"));
                assertTrue(read(zip, "Schedule Movements.csv").contains(f.receivableItem().toString()));
                assertTrue(rows(zip, "Schedule Period Balances.csv").stream().allMatch(r -> new BigDecimal(r.get("difference")).signum() == 0));
                assertTrue(read(zip, "Budget Versions.csv").contains(budget.versionCode()));
                var comparison = rows(zip, "Budget vs Actual.csv");
                assertTrue(comparison.stream().allMatch(r -> r.get("planId").equals(budget.id().toString())));
                assertEquals(LocalDate.of(2026, 1, 1).toString(), comparison.get(0).get("start"));
                var expected = new BudgetPlanService(jpa, () -> COMPANY).activeVariance(DATE.plusDays(1));
                BigDecimal expectedActual = expected.stream().map(BudgetVarianceView::actual).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal actual = comparison.stream().map(r -> new BigDecimal(r.get("actual"))).reduce(BigDecimal.ZERO, BigDecimal::add);
                assertEquals(0, expectedActual.compareTo(actual));
                var manifest = new ObjectMapper().readTree(zip.getInputStream(zip.getEntry("manifest.json")));
                assertEquals("accounting-2", manifest.get("packetVersion").asText());
                assertTrue(manifest.get("status").asText().contains("INCOMPLETE"));
                assertEquals(0, manifest.get("omittedSections").size());
                assertTrue(read(zip, "Exceptions and Notes.csv").contains("Unclassified"));
                try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(zip.getInputStream(zip.getEntry("Accounting Packet.xlsx"))))
                {
                    var sheet = workbook.getSheet("Merchant Summaries");
                    int closingColumn = -1;
                    for (var cell : sheet.getRow(0))
                        if (cell.getStringCellValue().equals("closing")) closingColumn = cell.getColumnIndex();
                    assertTrue(closingColumn >= 0);
                    money(summary.get("closing"), sheet.getRow(1).getCell(closingColumn).getStringCellValue());
                    assertNotNull(workbook.getSheet("Reconciled Statement Lines"));
                    assertNotNull(workbook.getSheet("Schedule Period Balances"));
                }
            }
            assertEquals(transactions, count(jpa, "Txn")); assertEquals(audits, count(jpa, "AuditEvent"));
            assertEquals(stored, storedSession(jpa, session.sessionId()), "Export must not recalculate or save a live reconciliation.");
            entry.enter(f.discharge());
            var discharged = new CoreAccountingPacketService(() -> db, () -> COMPANY, null)
                    .export(DATE, DATE.plusDays(2), directory.resolve("discharged.zip"));
            try (var zip = new ZipFile(discharged.destination().toFile()))
            {
                money("0", rows(zip, "Merchant Summaries.csv").get(0).get("closing"));
                money("70", rows(zip, "NMR Summaries.csv").get(0).get("closing"));
                assertTrue(read(zip, "NMR Movements.csv").contains("MERCHANT_DISCHARGE"));
            }
        }
    }

    @Test
    void assetsValuationCustodyCutoffAndDatedTransferReversalsAreRetained() throws Exception
    {
        Path db = directory.resolve("property");
        var cutoff = LocalDate.of(2026, 6, 30);
        try (Jpa jpa = new Jpa(db))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa);
            var assetService = f.service(jpa);
            var asset = assetService.recordAcquisition(assetService.previewAcquisition(new AssetAcquisitionCommand(null,
                    f.command("Packet equipment", "50.1234"), AssetAcquisitionSource.NEW_PURCHASE, f.base().bank(), null), UUID.randomUUID()));
            assetService.runMonthlyDepreciation(asset.id(), LocalDate.of(2026, 4, 30), "Synthetic depreciation");
            long inventoryAccount;
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                var account = new Account(); account.setChart(em.find(Account.class, f.asset()).getChart());
                account.setCode("1700"); account.setName("Inventory"); account.setAccountType(AccountType.ASSET); account.setSubtype(AccountSubtype.INVENTORY);
                account.setNormalBalance(NormalBalance.DEBIT); account.setActive(true); account.setPosting(true); em.persist(account);
                em.getTransaction().commit(); inventoryAccount = account.getId();
            }
            var inventory = new InventoryService(jpa);
            var item = inventory.create(new InventoryItemCommand("DEFAULT", inventoryAccount, f.base().source(), "Packet supplies", "Supply",
                    BigDecimal.ZERO, "each", BigDecimal.ZERO, InventoryItem.FinancialTreatment.CAPITALIZED_INVENTORY,
                    LocalDate.of(2026, 4, 1), "Alex", "Hall", InventoryItem.Condition.GOOD, InventoryItem.Status.ACTIVE, "Synthetic"));
            inventory.recordMovement(item.id(), new InventoryMovementCommand(InventoryMovement.MovementType.RECEIPT,
                    new BigDecimal("2"), LocalDate.of(2026, 4, 2), f.base().bank(), false, "Packet stock", null, null, true, new BigDecimal("5.1234")));
            var custody = new PropertyCustodyService(jpa, () -> "DEFAULT");
            var original = custody.save(custody(asset.id(), LocalDate.of(2026, 5, 1), "Alex"), "tester");
            custody.correctLatest(original.id(), custody(asset.id(), LocalDate.of(2026, 8, 1), "Morgan"), "tester");
            var transfers = new FundTransferService(jpa);
            var transfer = transfers.save(f.base().command(UUID.randomUUID(), "20"));
            transfers.reverse(transfer.id(), LocalDate.of(2026, 5, 15), "Synthetic reversal");
            long transactions = count(jpa, "Txn"), audits = count(jpa, "AuditEvent");
            var packet = new CoreAccountingPacketService(() -> db, () -> "DEFAULT", null)
                    .export(LocalDate.of(2026, 4, 1), cutoff, directory.resolve("property.zip"));
            try (var zip = new ZipFile(packet.destination().toFile()))
            {
                assertTrue(read(zip, "Fixed Assets.csv").contains("Packet equipment"));
                assertTrue(read(zip, "Asset Acquisition Links.csv").contains("NEW_PURCHASE"));
                assertTrue(read(zip, "Depreciation.csv").contains("Completed depreciation"));
                var value = rows(zip, "Inventory and Supplies.csv").get(0);
                money("2", value.get("quantity")); money("10.2468", value.get("totalValue"));
                assertTrue(read(zip, "Inventory Movements.csv").contains("RECEIPT"));
                var property = rows(zip, "Property Custody.csv");
                assertEquals(1, property.size()); assertEquals("Alex", property.get(0).get("guardian"));
                assertEquals("false", property.get(0).get("corrected")); assertEquals("false", property.get(0).get("overdue"));
                var transferRows = rows(zip, "Fund Transfers.csv");
                assertEquals(1, transferRows.size(), "Original February transfer is outside range but its May reversal belongs here.");
                assertEquals("DATED REVERSAL", transferRows.get(0).get("record")); money("20", transferRows.get(0).get("amount"));
                assertEquals("DESIGNATED", transferRows.get(0).get("fromFund"));
                assertEquals("GENERAL", transferRows.get(0).get("toFund"));
                assertTrue(read(zip, "General Ledger.csv").contains("reversalOf"));
                assertTrue(read(zip, "Exceptions and Notes.csv").contains("No active plan"));
            }
            assertEquals(transactions, count(jpa, "Txn")); assertEquals(audits, count(jpa, "AuditEvent"));
            var reports = new AssetInventoryReportQueryService(jpa, () -> "DEFAULT");
            assertFalse(reports.propertyCustodyAsOf(cutoff, 100).get(0).corrected());
            assertTrue(reports.propertyCustody(LocalDate.of(2026, 1, 1), cutoff, 100).get(0).corrected(),
                    "Interactive current-state correction behavior is preserved.");
        }
    }

    @Test
    void operationalCapacityFailureDoesNotPublishTruncatedPacket() throws Exception
    {
        Path db = directory.resolve("capacity");
        try (Jpa jpa = new Jpa(db))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa);
            var asset = f.service(jpa).create(f.command("Custody subject", "10"));
            try (var em = jpa.em())
            {
                em.getTransaction().begin();
                for (int i = 0; i < PacketOperationalSections.DOMAIN_SENTINEL + 1; i++)
                {
                    var event = new PropertyCustodyEvent(); event.setCompany(em.find(FixedAsset.class, asset.id()).getCompany());
                    event.setFixedAsset(em.getReference(FixedAsset.class, asset.id())); event.setEventDate(DATE);
                    event.setEventType(PropertyCustodyEvent.EventType.ASSIGNMENT); event.setQuantity(BigDecimal.ONE); event.setActor("synthetic-capacity");
                    em.persist(event);
                }
                em.getTransaction().commit();
            }
            Path destination = directory.resolve("too-many.zip");
            var error = assertThrows(IllegalArgumentException.class, () -> new CoreAccountingPacketService(() -> db, () -> "DEFAULT", null)
                    .export(DATE, DATE, destination));
            assertTrue(error.getMessage().contains("capacity"), error::getMessage);
            assertFalse(Files.exists(destination));
            assertEquals(5001, count(jpa, "PropertyCustodyEvent"));
        }
    }

    private static PropertyCustodyService.Command custody(long asset, LocalDate date, String guardian)
    {
        return new PropertyCustodyService.Command(PropertyCustodyService.SubjectType.FIXED_ASSET, asset,
                PropertyCustodyEvent.EventType.ASSIGNMENT, date, BigDecimal.ONE, guardian, null, null, "Hall",
                InventoryItem.Condition.GOOD, PropertyCustodyEvent.ConfirmationStatus.PENDING,
                LocalDate.of(2026, 7, 15), null, null, null, "AUTH/synthetic", "Packet custody", null, null);
    }

    private static BudgetPlanView budget(Jpa jpa, String company, long fund, long event)
    {
        long category;
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            var value = new BudgetCategory(); value.setCompany(new CompanyOwnershipService(jpa).requireCompany(em, company));
            value.setCode("PACKET"); value.setName("Packet budget"); em.persist(value); em.getTransaction().commit(); category = value.getId();
        }
        var service = new BudgetPlanService(jpa, () -> company);
        var draft = service.createDraft(new BudgetPlanCommand("Packet FY26", 2026, "PACKET-26", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "Synthetic"));
        service.replaceDraftLines(draft.id(), List.of(new BudgetLineCommand(category, fund, event, null, new BigDecimal("2500.1234"), "Annual unphased")));
        return service.activate(draft.id());
    }

    private static long configureBank(Jpa jpa, String company, long account, LocalDate opening)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            var institution = new Bank(); institution.setCompany(new CompanyOwnershipService(jpa).requireCompany(em, company));
            institution.setName("Synthetic packet bank"); em.persist(institution);
            var bank = new CompanyBankAccount(); bank.setCompany(new CompanyOwnershipService(jpa).requireCompany(em, company));
            bank.setBank(institution);
            bank.setAccount(em.find(Account.class, account)); bank.setName("Packet operating"); bank.setOpeningDate(opening); bank.setOpeningBalance(BigDecimal.ZERO);
            em.persist(bank); em.getTransaction().commit(); return bank.getId();
        }
    }

    private static String storedSession(Jpa jpa, long id)
    {
        try (var em = jpa.em())
        {
            return java.util.Arrays.deepToString((Object[]) em.createNativeQuery("select * from bank_reconciliation_session where id = :id").setParameter("id", id).getSingleResult());
        }
    }

    private static long count(Jpa jpa, String entity)
    {
        try (var em = jpa.em()) { return em.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult(); }
    }

    private static String read(ZipFile zip, String name) throws Exception
    {
        try (var input = zip.getInputStream(zip.getEntry(name))) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }

    private static List<Map<String, String>> rows(ZipFile zip, String name) throws Exception
    {
        var csv = PoiXlsxFinancialReportAdapter.parseCsv(read(zip, name));
        List<Map<String, String>> rows = new ArrayList<>();
        for (var values : csv.subList(1, csv.size()))
        {
            var row = new LinkedHashMap<String, String>();
            for (int i = 0; i < csv.get(0).size(); i++) row.put(csv.get(0).get(i), values.get(i));
            rows.add(row);
        }
        return rows;
    }

    private static void money(String expected, String actual) { assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(actual))); }
}
