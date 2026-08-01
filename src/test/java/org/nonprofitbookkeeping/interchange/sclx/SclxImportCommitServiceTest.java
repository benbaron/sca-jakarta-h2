package org.nonprofitbookkeeping.interchange.sclx;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.ChartOfAccounts;
import org.nonprofitbookkeeping.model.ChartStatus;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.model.FixedAssetDepreciationRun;
import org.nonprofitbookkeeping.model.InventoryItem;
import org.nonprofitbookkeeping.model.InventoryMovement;
import org.nonprofitbookkeeping.model.BudgetCategory;
import org.nonprofitbookkeeping.model.BudgetLine;
import org.nonprofitbookkeeping.model.BudgetPlan;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.model.TxnSplit;
import org.nonprofitbookkeeping.model.TxnSupplementalLine;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxImportCommitServiceTest
{
    private static final String TARGET = "SCLX_TARGET";
    private static final UUID TRANSACTION_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID FIXED_ASSET_UUID = UUID.fromString("22222222-3333-4444-5555-666666666666");
    private static final UUID DEPRECIATION_RUN_UUID = UUID.fromString("33333333-4444-5555-6666-777777777777");
    private static final UUID INVENTORY_ITEM_UUID = UUID.fromString("44444444-5555-6666-7777-888888888888");
    private static final UUID INVENTORY_MOVEMENT_UUID = UUID.fromString("55555555-6666-7777-8888-999999999999");
    private static final UUID BANK_UUID = UUID.fromString("66666666-7777-8888-9999-aaaaaaaaaaaa");
    private static final UUID BANK_ACCOUNT_UUID = UUID.fromString("77777777-8888-9999-aaaa-bbbbbbbbbbbb");
    private static final UUID IMPORT_BATCH_UUID = UUID.fromString("88888888-9999-aaaa-bbbb-cccccccccccc");
    private static final UUID STATEMENT_LINE_UUID = UUID.fromString("99999999-aaaa-bbbb-cccc-dddddddddddd");
    private static final UUID IMPORT_ISSUE_UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-ffffffffffff");
    private static final UUID RECONCILIATION_SESSION_UUID = UUID.fromString("bbbbbbbb-cccc-dddd-eeee-ffffffffffff");
    private static final UUID RECONCILIATION_MATCH_UUID = UUID.fromString("cccccccc-dddd-eeee-ffff-000000000000");

    @Test
    void importsBankingAndReconciliationFactsAtomically(@TempDir Path tempDir) throws Exception
    {
        Path source = writeBankingSource(tempDir.resolve("banking.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("banking")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreviewService previews = new SclxImportPreviewService(jpa, () -> TARGET);
            SclxImportPreview preview = previews.preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());

            SclxImportResult result = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, preview, "tester");

            assertTrue(result.committed(), () -> result.messages().toString());
            assertEquals(27L, result.counts().created());
            try (EntityManager em = jpa.em())
            {
                Bank bank = em.createQuery("from Bank b", Bank.class).getSingleResult();
                assertEquals(BANK_UUID, bank.getPortableId());
                assertEquals("Portable Credit Union", bank.getName());
                CompanyBankAccount bankAccount = em.createQuery(
                        "from CompanyBankAccount a", CompanyBankAccount.class).getSingleResult();
                assertEquals(BANK_ACCOUNT_UUID, bankAccount.getPortableId());
                assertEquals("1000", bankAccount.getAccount().getCode());
                BankImportBatch batch = em.createQuery(
                        "from BankImportBatch b", BankImportBatch.class).getSingleResult();
                assertEquals(IMPORT_BATCH_UUID, batch.getPortableId());
                assertEquals(Instant.parse("2026-07-20T10:00:00Z"), batch.getImportedAt());
                BankStatementLine statementLine = em.createQuery(
                        "from BankStatementLine l", BankStatementLine.class).getSingleResult();
                assertEquals(STATEMENT_LINE_UUID, statementLine.getPortableId());
                assertEquals(BankStatementLine.Status.MATCHED, statementLine.getStatus());
                ImportIssue issue = em.createQuery("from ImportIssue i", ImportIssue.class).getSingleResult();
                assertEquals(IMPORT_ISSUE_UUID, issue.getPortableId());
                TxnSplit bankLine = em.createQuery(
                        "from TxnSplit s where s.account.code = '1000'", TxnSplit.class).getSingleResult();
                assertTrue(bankLine.isBankCleared());
                assertEquals(statementLine.getId(), bankLine.getMatchedBankStatementLine().getId());
                assertEquals(1L, nativeCount(em, "bank_reconciliation_session"));
                assertEquals(1L, nativeCount(em, "bank_reconciliation_match"));
                assertEquals(27L, count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }

            SclxImportPreview secondPreview = previews.preview(source);
            assertEquals(27L, secondPreview.operation().counts().identical());
            SclxImportResult second = new SclxImportCommitService(jpa, () -> TARGET)
                    .commit(source, secondPreview, "tester");
            assertTrue(second.committed());
            assertEquals(0L, second.counts().created());
        }
    }

    @Test
    void failureAfterReconciliationWriteRollsBackBankingGraph(@TempDir Path tempDir) throws Exception
    {
        Path source = writeBankingSource(tempDir.resolve("banking-rollback.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("banking-rollback")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            SclxImportCommitService service = new SclxImportCommitService(
                    jpa, () -> TARGET, writes -> {
                        if (writes == 23)
                        {
                            throw new IllegalStateException("injected after reconciliation write");
                        }
                    });

            SclxImportResult result = service.commit(source, preview, "tester");

            assertTrue(result.rolledBack());
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, count(em, "select count(b) from Bank b"));
                assertEquals(0L, count(em, "select count(a) from CompanyBankAccount a"));
                assertEquals(0L, count(em, "select count(b) from BankImportBatch b"));
                assertEquals(0L, count(em, "select count(l) from BankStatementLine l"));
                assertEquals(0L, count(em, "select count(i) from ImportIssue i"));
                assertEquals(0L, nativeCount(em, "bank_reconciliation_session"));
                assertEquals(0L, nativeCount(em, "bank_reconciliation_match"));
                assertEquals(0L, count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }
        }
    }

    @Test
    void rejectsUnresolvedReconciliationTransactionLineBeforeMutation(@TempDir Path tempDir) throws Exception
    {
        Path source = writeBankingSource(tempDir.resolve("banking-unresolved-line.sclx"));
        String transaction = SclxPortableIdentity.transaction("SOURCE", TRANSACTION_UUID.toString());
        String bankLine = SclxPortableIdentity.transactionLine(transaction, 2);
        String marker = "\"lineId\": \"" + bankLine + "\"";
        String json = Files.readString(source);
        int markerIndex = json.lastIndexOf(marker);
        assertTrue(markerIndex >= 0, "reconciliation transaction-line fixture marker must exist");
        Files.writeString(source, json.substring(0, markerIndex)
                + "\"lineId\": \"sclx:transaction-line:missing\""
                + json.substring(markerIndex + marker.length()));

        try (Jpa jpa = new Jpa(tempDir.resolve("banking-unresolved-line")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);

            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new SclxImportCommitService(jpa, () -> TARGET)
                            .commit(source, preview, "tester"));

            assertTrue(failure.getMessage().contains("reconciliation.matches[].lineId does not resolve"));
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, count(em, "select count(b) from Bank b"));
                assertEquals(0L, count(em,
                        "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }
        }
    }

    @Test
    void commitsCoreGraphAtomicallyAndReimportIsIdempotent(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("core-import");
        Path source = writeSource(tempDir.resolve("core.sclx"));
        try (Jpa jpa = new Jpa(database))
        {
            seedEmptyTarget(jpa);
            SclxImportPreviewService previews = new SclxImportPreviewService(jpa, () -> TARGET);
            SclxImportCommitService service = new SclxImportCommitService(jpa, () -> TARGET);

            SclxImportPreview firstPreview = previews.preview(source);
            assertFalse(firstPreview.hasBlockingErrors(), () -> firstPreview.operation().messages().toString());
            SclxImportResult first = service.commit(source, firstPreview, "tester");

            assertTrue(first.committed());
            assertFalse(first.rolledBack());
            assertEquals(20L, first.counts().created());
            try (EntityManager em = jpa.em())
            {
                Company company = company(em);
                assertEquals("Portable Source Company", company.getDisplayName());
                assertEquals("CAD", company.getDefaultCurrency());
                assertEquals(4, company.getFiscalYearStartMonth());
                assertEquals(1, company.getFiscalYearStartDay());
                assertEquals("Portable Chart", company.getActiveChartOfAccounts().getName());
                assertEquals(5L, count(em, "select count(a) from Account a"));
                assertEquals(1L, count(em, "select count(f) from Fund f"));
                assertEquals(1L, count(em, "select count(c) from BudgetCategory c"));
                assertEquals(1L, count(em, "select count(p) from BudgetPlan p"));
                assertEquals(1L, count(em, "select count(l) from BudgetLine l"));
                assertEquals(1L, count(em, "select count(t) from Txn t"));
                assertEquals(2L, count(em, "select count(s) from TxnSplit s"));
                assertEquals(1L, count(em, "select count(a) from Activity a"));
                assertEquals(1L, count(em, "select count(c) from Counterparty c"));
                assertEquals(1L, count(em, "select count(m) from Merchant m"));
                assertEquals(1L, count(em, "select count(s) from TxnSupplementalLine s"));
                assertEquals(20L, count(em, "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
                BudgetPlan budget = em.createQuery("from BudgetPlan p", BudgetPlan.class).getSingleResult();
                assertEquals("FY2026 Approved", budget.getName());
                assertEquals(2026, budget.getFiscalYear());
                assertEquals("APPROVED", budget.getVersionCode());
                assertEquals(BudgetPlan.Status.ACTIVE, budget.getStatus());
                BudgetLine budgetLine = em.createQuery("from BudgetLine l", BudgetLine.class).getSingleResult();
                assertEquals("PROGRAM", budgetLine.getBudgetCategory().getCode());
                assertEquals("PROGRAM", budgetLine.getBudgetCategory().getName());
                assertEquals("GENERAL", budgetLine.getFund().getCode());
                assertEquals(YearMonth.of(2026, 7), budgetLine.getPeriodMonth());
                assertEquals(new BigDecimal("125.0000"), budgetLine.getAmount());
                Txn transaction = em.createQuery("from Txn t", Txn.class).getSingleResult();
                assertEquals(TRANSACTION_UUID, transaction.getPortableId());
                assertEquals("Portable Payee", transaction.getPayee().getDisplayName());
                TxnSplit enrichedLine = em.createQuery(
                                "from TxnSplit s where s.merchant is not null", TxnSplit.class)
                        .getSingleResult();
                assertEquals("EVENT", enrichedLine.getActivity().getCode());
                assertEquals("Portable Merchant", enrichedLine.getMerchant().getName());
                TxnSupplementalLine supplemental = em.createQuery(
                                "from TxnSupplementalLine s", TxnSupplementalLine.class)
                        .getSingleResult();
                assertEquals(7, supplemental.getLineOrder());
                assertEquals("PAYABLE", supplemental.getKind());
                FixedAsset fixedAsset = em.createQuery("from FixedAsset a", FixedAsset.class).getSingleResult();
                assertEquals(FIXED_ASSET_UUID, fixedAsset.getPortableId());
                assertEquals("Portable Equipment", fixedAsset.getName());
                assertEquals(Instant.parse("2026-01-15T10:00:00Z"), fixedAsset.getCreatedAt());
                assertEquals(Instant.parse("2026-07-31T11:00:00Z"), fixedAsset.getUpdatedAt());
                FixedAssetDepreciationRun depreciationRun = em.createQuery(
                                "from FixedAssetDepreciationRun r", FixedAssetDepreciationRun.class)
                        .getSingleResult();
                assertEquals(DEPRECIATION_RUN_UUID, depreciationRun.getPortableId());
                assertEquals(fixedAsset.getId(), depreciationRun.getFixedAsset().getId());
                assertEquals(transaction.getId(), depreciationRun.getTransaction().getId());
                assertEquals(new BigDecimal("25.0000"), depreciationRun.getDepreciationAmount());
                InventoryItem inventoryItem = em.createQuery(
                                "from InventoryItem i", InventoryItem.class)
                        .getSingleResult();
                assertEquals(INVENTORY_ITEM_UUID, inventoryItem.getPortableId());
                assertEquals("Portable Regalia", inventoryItem.getName());
                assertEquals(new BigDecimal("3.0000"), inventoryItem.getQuantity());
                assertEquals(Instant.parse("2026-02-01T10:00:00Z"), inventoryItem.getCreatedAt());
                assertEquals(Instant.parse("2026-07-31T11:30:00Z"), inventoryItem.getUpdatedAt());
                InventoryMovement inventoryMovement = em.createQuery(
                                "from InventoryMovement m", InventoryMovement.class)
                        .getSingleResult();
                assertEquals(INVENTORY_MOVEMENT_UUID, inventoryMovement.getPortableId());
                assertEquals(inventoryItem.getId(), inventoryMovement.getInventoryItem().getId());
                assertEquals(transaction.getId(), inventoryMovement.getTransaction().getId());
                assertEquals(new BigDecimal("3.0000"), inventoryMovement.getQuantityChange());
                assertEquals(new BigDecimal("3.0000"), inventoryMovement.getResultingQuantity());
                assertEquals(1L, count(em,
                        "select count(a) from AuditEvent a "
                                + "where a.actionType = 'SCLX_BANKING_RECONCILIATION_IMPORTED'"));
            }

            SclxImportPreview secondPreview = previews.preview(source);
            assertFalse(secondPreview.hasBlockingErrors(), () -> secondPreview.operation().messages().toString());
            assertEquals(20L, secondPreview.operation().counts().identical());
            SclxImportResult second = service.commit(source, secondPreview, "tester");

            assertTrue(second.committed());
            assertEquals(0L, second.counts().created());
            assertEquals(20L, second.counts().identical());
            try (EntityManager em = jpa.em())
            {
                assertEquals(1L, count(em, "select count(t) from Txn t"));
                assertEquals(2L, count(em, "select count(s) from TxnSplit s"));
                assertEquals(1L, count(em, "select count(a) from FixedAsset a"));
                assertEquals(1L, count(em, "select count(r) from FixedAssetDepreciationRun r"));
                assertEquals(1L, count(em, "select count(i) from InventoryItem i"));
                assertEquals(1L, count(em, "select count(m) from InventoryMovement m"));
                assertEquals(20L, count(em, "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
            }
        }
    }

    @Test
    void lateFailureRollsBackProfileMastersTransactionsIdentitiesAndAudit(@TempDir Path tempDir) throws Exception
    {
        Path database = tempDir.resolve("rollback");
        Path source = writeSource(tempDir.resolve("rollback.sclx"));
        try (Jpa jpa = new Jpa(database))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            SclxImportCommitService service = new SclxImportCommitService(
                    jpa,
                    () -> TARGET,
                    writes -> {
                        if (writes == 17)
                        {
                            throw new IllegalStateException("injected late failure");
                        }
                    });

            SclxImportResult result = service.commit(source, preview, "tester");

            assertFalse(result.committed());
            assertTrue(result.rolledBack());
            assertTrue(result.messages().stream()
                    .anyMatch(message -> message.code().equals("SCLX_COMMIT_ROLLED_BACK")));
            try (EntityManager em = jpa.em())
            {
                Company company = company(em);
                assertEquals("Empty Target", company.getDisplayName());
                assertEquals("USD", company.getDefaultCurrency());
                assertEquals("Empty Chart", company.getActiveChartOfAccounts().getName());
                assertEquals(0L, count(em, "select count(a) from Account a"));
                assertEquals(0L, count(em, "select count(f) from Fund f"));
                assertEquals(0L, count(em, "select count(c) from BudgetCategory c"));
                assertEquals(0L, count(em, "select count(p) from BudgetPlan p"));
                assertEquals(0L, count(em, "select count(l) from BudgetLine l"));
                assertEquals(0L, count(em, "select count(t) from Txn t"));
                assertEquals(0L, count(em, "select count(a) from Activity a"));
                assertEquals(0L, count(em, "select count(c) from Counterparty c"));
                assertEquals(0L, count(em, "select count(m) from Merchant m"));
                assertEquals(0L, count(em, "select count(s) from TxnSupplementalLine s"));
                assertEquals(0L, count(em, "select count(a) from FixedAsset a"));
                assertEquals(0L, count(em, "select count(r) from FixedAssetDepreciationRun r"));
                assertEquals(0L, count(em, "select count(i) from InventoryItem i"));
                assertEquals(0L, count(em, "select count(m) from InventoryMovement m"));
                assertEquals(0L, count(em, "select count(i) from InterchangeIdentity i where i.formatCode = 'SCLX'"));
                assertEquals(0L, count(em,
                        "select count(a) from AuditEvent a "
                                + "where a.actionType = 'SCLX_BANKING_RECONCILIATION_IMPORTED'"));
            }
        }
    }

    @Test
    void rejectsBudgetAccountRelationBeforeMutation(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("account-bearing-budget.sclx"));
        String accountId = SclxPortableIdentity.account("SOURCE", "6100");
        Files.writeString(source, Files.readString(source).replace(
                "\"categoryCode\": \"PROGRAM\",",
                "\"accountId\": \"" + accountId + "\",\n"
                        + "                          \"categoryCode\": \"PROGRAM\","));
        try (Jpa jpa = new Jpa(tempDir.resolve("account-bearing-budget")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            SclxImportCommitService service = new SclxImportCommitService(jpa, () -> TARGET);

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> service.commit(source, preview, "tester"));

            assertTrue(failure.getMessage().contains("cannot be preserved"));
            try (EntityManager em = jpa.em())
            {
                assertEquals("Empty Target", company(em).getDisplayName());
                assertEquals(0L, count(em, "select count(a) from Account a"));
                assertEquals(0L, count(em, "select count(p) from BudgetPlan p"));
                assertEquals(0L, count(em, "select count(i) from InterchangeIdentity i"));
            }
        }
    }

    @Test
    void previewBlocksTargetContainingOnlyBudgetCategory(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("budget-category-target.sclx"));
        try (Jpa jpa = new Jpa(tempDir.resolve("budget-category-target")))
        {
            seedEmptyTarget(jpa);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                BudgetCategory category = new BudgetCategory();
                category.setCompany(company(em));
                category.setCode("EXISTING");
                category.setName("Existing Category");
                em.persist(category);
                em.getTransaction().commit();
            }

            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);

            assertTrue(preview.hasBlockingErrors());
            assertTrue(preview.operation().messages().stream()
                    .anyMatch(message -> message.code().equals("SCLX_POPULATED_TARGET_UNSUPPORTED")));
        }
    }

    @Test
    void rejectsInvalidFixedAssetBeforeMutation(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("invalid-fixed-asset.sclx"));
        Files.writeString(source, Files.readString(source).replace(
                "\"usefulLifeMonths\": 60,",
                "\"usefulLifeMonths\": 48,"));
        try (Jpa jpa = new Jpa(tempDir.resolve("invalid-fixed-asset")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> new SclxImportCommitService(jpa, () -> TARGET)
                            .commit(source, preview, "tester"));

            assertTrue(failure.getMessage().contains("usefulLifeMonths"));
            try (EntityManager em = jpa.em())
            {
                assertEquals("Empty Target", company(em).getDisplayName());
                assertEquals(0L, count(em, "select count(a) from Account a"));
                assertEquals(0L, count(em, "select count(a) from FixedAsset a"));
                assertEquals(0L, count(em, "select count(i) from InterchangeIdentity i"));
            }
        }
    }

    @Test
    void rejectsUnresolvedInventoryMovementBeforeMutation(@TempDir Path tempDir) throws Exception
    {
        Path source = writeSource(tempDir.resolve("invalid-inventory.sclx"));
        String itemId = SclxPortableIdentity.inventoryItem("SOURCE", INVENTORY_ITEM_UUID.toString());
        String original = Files.readString(source);
        String itemReference = "\"itemId\": \"" + itemId + "\"";
        int movementReference = original.lastIndexOf(itemReference);
        assertTrue(movementReference >= 0, "inventory movement fixture mutation must apply");
        String changed = original.substring(0, movementReference)
                + "\"itemId\": \"inventory-item:SOURCE:missing\""
                + original.substring(movementReference + itemReference.length());
        Files.writeString(source, changed);
        try (Jpa jpa = new Jpa(tempDir.resolve("invalid-inventory")))
        {
            seedEmptyTarget(jpa);
            SclxImportPreview preview = new SclxImportPreviewService(jpa, () -> TARGET).preview(source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());

            IllegalStateException failure = assertThrows(
                    IllegalStateException.class,
                    () -> new SclxImportCommitService(jpa, () -> TARGET)
                            .commit(source, preview, "tester"));

            assertTrue(failure.getMessage().contains("does not resolve"));
            try (EntityManager em = jpa.em())
            {
                assertEquals("Empty Target", company(em).getDisplayName());
                assertEquals(0L, count(em, "select count(a) from Account a"));
                assertEquals(0L, count(em, "select count(i) from InventoryItem i"));
                assertEquals(0L, count(em, "select count(i) from InterchangeIdentity i"));
            }
        }
    }

    private static void seedEmptyTarget(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(TARGET);
            company.setDisplayName("Empty Target");
            company.setDefaultCurrency("USD");
            em.persist(company);

            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName("Empty Chart");
            chart.setVersion("EMPTY");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            company.setActiveChartOfAccounts(chart);
            em.getTransaction().commit();
        }
    }

    private static Company company(EntityManager em)
    {
        return em.createQuery("""
                select c from Company c
                left join fetch c.activeChartOfAccounts
                where c.code = :code
                """, Company.class)
                .setParameter("code", TARGET)
                .getSingleResult();
    }

    private static long count(EntityManager em, String jpql)
    {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }

    private static long nativeCount(EntityManager em, String table)
    {
        return ((Number) em.createNativeQuery("select count(*) from " + table)
                .getSingleResult()).longValue();
    }

    private static Path writeBankingSource(Path target) throws Exception
    {
        writeSource(target);
        String cash = SclxPortableIdentity.account("SOURCE", "1000");
        String transaction = SclxPortableIdentity.transaction("SOURCE", TRANSACTION_UUID.toString());
        String bankLine = SclxPortableIdentity.transactionLine(transaction, 2);
        String bank = SclxPortableIdentity.bank("SOURCE", BANK_UUID.toString());
        String bankAccount = SclxPortableIdentity.bankAccount("SOURCE", BANK_ACCOUNT_UUID.toString());
        String batch = SclxPortableIdentity.bankImportBatch("SOURCE", IMPORT_BATCH_UUID.toString());
        String statementLine = SclxPortableIdentity.bankStatementLine("SOURCE", STATEMENT_LINE_UUID.toString());
        String issue = SclxPortableIdentity.bankImportIssue("SOURCE", IMPORT_ISSUE_UUID.toString());
        String session = SclxPortableIdentity.reconciliationSession(
                "SOURCE", RECONCILIATION_SESSION_UUID.toString());
        String match = SclxPortableIdentity.reconciliationMatch(
                "SOURCE", RECONCILIATION_MATCH_UUID.toString());
        String banking = """
                      "bankConfiguration": {
                        "banks": [
                          {
                            "bankId": "%s",
                            "name": "Portable Credit Union",
                            "routingNumber": "123456789",
                            "address": "1 Portable Way",
                            "website": null,
                            "contactName": "Bank Contact",
                            "contactPhone": null,
                            "contactEmail": "contact@example.invalid",
                            "notes": "Imported bank",
                            "active": true
                          }
                        ],
                        "accounts": [
                          {
                            "bankAccountId": "%s",
                            "bankId": "%s",
                            "ledgerAccountId": "%s",
                            "name": "Portable Checking",
                            "nickname": "Checking",
                            "institutionName": "Portable Credit Union",
                            "accountType": "BANK",
                            "lastFour": "1234",
                            "maskedAccountNumber": "****1234",
                            "openingDate": "2026-01-01",
                            "statementImportFormat": "OFX",
                            "ofxBankId": "123456789",
                            "ofxAccountId": "PORTABLE-1234",
                            "openingBalance": "0.0000",
                            "active": true,
                            "notes": "Imported configured account"
                          }
                        ]
                      },
                      "bankStatementFacts": {
                        "importBatches": [
                          {
                            "importBatchId": "%s",
                            "bankAccountId": "%s",
                            "sourceName": "portable.ofx",
                            "sourceHash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                            "sourceFormat": "OFX",
                            "status": "ACCEPTED",
                            "importedAt": "2026-07-20T10:00:00Z",
                            "completedAt": "2026-07-20T10:05:00Z",
                            "totalLineCount": 1,
                            "acceptedLineCount": 1,
                            "rejectedLineCount": 0,
                            "issueCount": 1,
                            "notes": "Imported reviewed batch"
                          }
                        ],
                        "statementLines": [
                          {
                            "statementLineId": "%s",
                            "importBatchId": "%s",
                            "bankAccountId": "%s",
                            "sourceRowNumber": 1,
                            "sourceTransactionId": "FITID-1",
                            "deterministicFingerprint": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                            "statementAccountIdentifier": "PORTABLE-1234",
                            "transactionDate": "2026-07-15",
                            "postedDate": "2026-07-16",
                            "amount": "-25.0000",
                            "transactionType": "DEBIT",
                            "name": "Portable Merchant",
                            "memo": "Purchase supplies",
                            "checkNumber": null,
                            "reference": "REF-1",
                            "status": "MATCHED",
                            "dispositionNote": "Matched during source review",
                            "acceptedTransactionId": null,
                            "matchedTransactionId": "%s"
                          }
                        ],
                        "issues": [
                          {
                            "issueId": "%s",
                            "importBatchId": "%s",
                            "statementLineId": "%s",
                            "sourceRowNumber": 1,
                            "severity": "WARNING",
                            "code": "PORTABLE_WARNING",
                            "message": "Portable review warning",
                            "createdAt": "2026-07-20T10:01:00Z"
                          }
                        ],
                        "transactionLineClearance": [
                          {
                            "lineId": "%s",
                            "bankCleared": true,
                            "bankClearedOn": "2026-07-16",
                            "statementLineId": "%s"
                          }
                        ]
                      },
                      "reconciliation": {
                        "sessions": [
                          {
                            "reconciliationSessionId": "%s",
                            "bankAccountId": "%s",
                            "statementStartDate": "2026-07-01",
                            "statementEndDate": "2026-07-31",
                            "statementEndingBalance": "-25.0000",
                            "mismatchPolicy": "WARN_ONLY",
                            "status": "FINALIZED",
                            "notes": "Imported reconciliation",
                            "beginningBalance": "0.0000",
                            "bookBalanceAll": "-25.0000",
                            "bookBalanceCleared": "-25.0000",
                            "differenceAmount": "0.0000",
                            "createdAt": "2026-07-31T12:00:00Z",
                            "updatedAt": "2026-07-31T12:05:00Z"
                          }
                        ],
                        "matches": [
                          {
                            "reconciliationMatchId": "%s",
                            "reconciliationSessionId": "%s",
                            "statementLineId": "%s",
                            "lineId": "%s",
                            "matchStatus": "MATCHED",
                            "resolutionNote": "Exact portable match",
                            "createdAt": "2026-07-31T12:01:00Z",
                            "updatedAt": "2026-07-31T12:02:00Z"
                          }
                        ]
                      },
                      "fixedAssets": {
                """.formatted(
                bank, bankAccount, bank, cash,
                batch, bankAccount,
                statementLine, batch, bankAccount, transaction,
                issue, batch, statementLine,
                bankLine, statementLine,
                session, bankAccount,
                match, session, statementLine, bankLine);
        String source = Files.readString(target);
        String marker = "\"fixedAssets\": {";
        assertTrue(source.contains(marker), "banking fixture insertion marker must exist");
        Files.writeString(target, source.replace(marker, banking));
        return target;
    }

    private static Path writeSource(Path target) throws Exception
    {
        String organizationId = SclxPortableIdentity.organization("SOURCE");
        String cash = SclxPortableIdentity.account("SOURCE", "1000");
        String asset = SclxPortableIdentity.account("SOURCE", "1500");
        String accumulated = SclxPortableIdentity.account("SOURCE", "1590");
        String inventoryAccount = SclxPortableIdentity.account("SOURCE", "1600");
        String expense = SclxPortableIdentity.account("SOURCE", "6100");
        String fund = SclxPortableIdentity.fund("SOURCE", "GENERAL");
        String budget = SclxPortableIdentity.budget("SOURCE", 2026, "APPROVED");
        String budgetLine = SclxPortableIdentity.budgetLine(
                budget, "PROGRAM", null, fund, "2026-07");
        String transaction = SclxPortableIdentity.transaction("SOURCE", TRANSACTION_UUID.toString());
        String debitLine = SclxPortableIdentity.transactionLine(transaction, 1);
        String creditLine = SclxPortableIdentity.transactionLine(transaction, 2);
        String activity = SclxPortableIdentity.activity("SOURCE", "EVENT");
        String counterparty = SclxPortableIdentity.counterparty(
                "SOURCE", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String merchant = SclxPortableIdentity.merchant(
                "SOURCE", "99999999-8888-7777-6666-555555555555");
        String supplemental = SclxPortableIdentity.supplementalDetail(transaction, 1);
        String fixedAsset = SclxPortableIdentity.fixedAsset("SOURCE", FIXED_ASSET_UUID.toString());
        String depreciationRun = SclxPortableIdentity.fixedAssetDepreciationRun(
                "SOURCE", DEPRECIATION_RUN_UUID.toString());
        String inventoryItem = SclxPortableIdentity.inventoryItem(
                "SOURCE", INVENTORY_ITEM_UUID.toString());
        String inventoryMovement = SclxPortableIdentity.inventoryMovement(
                "SOURCE", INVENTORY_MOVEMENT_UUID.toString());
        Files.writeString(target, """
                {
                  "format": "SCLX",
                  "version": "1.3",
                  "exportedAt": "2026-07-31T12:00:00Z",
                  "organization": {
                    "organizationId": "%s",
                    "code": "SOURCE",
                    "name": "Portable Source Company",
                    "baseCurrency": "CAD",
                    "fiscalYearStart": "2026-04-01"
                  },
                  "chartOfAccounts": [
                    {
                      "accountId": "%s",
                      "code": "1000",
                      "name": "Cash",
                      "type": "BANK",
                      "subtype": "CASH",
                      "increaseSide": "DEBIT",
                      "currency": "CAD",
                      "openingBalance": "0.00",
                      "posting": true,
                      "active": true
                    },
                    {
                      "accountId": "%s",
                      "code": "1500",
                      "name": "Equipment",
                      "type": "ASSET",
                      "subtype": "FIXED_ASSET",
                      "increaseSide": "DEBIT",
                      "currency": "CAD",
                      "openingBalance": "0.00",
                      "posting": true,
                      "active": true
                    },
                    {
                      "accountId": "%s",
                      "code": "1590",
                      "name": "Accumulated Depreciation",
                      "type": "ASSET",
                      "subtype": "FIXED_ASSET",
                      "increaseSide": "CREDIT",
                      "currency": "CAD",
                      "openingBalance": "0.00",
                      "posting": true,
                      "active": true
                    },
                    {
                      "accountId": "%s",
                      "code": "1600",
                      "name": "Inventory",
                      "type": "ASSET",
                      "subtype": "INVENTORY",
                      "increaseSide": "DEBIT",
                      "currency": "CAD",
                      "openingBalance": "0.00",
                      "posting": true,
                      "active": true
                    },
                    {
                      "accountId": "%s",
                      "code": "6100",
                      "name": "Supplies",
                      "type": "EXPENSE",
                      "increaseSide": "DEBIT",
                      "currency": "CAD",
                      "openingBalance": "0.00",
                      "posting": true,
                      "active": true
                    }
                  ],
                  "funds": [
                    {
                      "fundId": "%s",
                      "code": "GENERAL",
                      "name": "General Fund",
                      "type": "UNRESTRICTED",
                      "active": true
                    }
                  ],
                  "budgets": [
                    {
                      "budgetId": "%s",
                      "name": "FY2026 Approved",
                      "fiscalYear": 2026,
                      "version": "APPROVED",
                      "active": true,
                      "lines": [
                        {
                          "lineId": "%s",
                          "fundId": "%s",
                          "categoryCode": "PROGRAM",
                          "periodMonth": "2026-07",
                          "amount": "125.0000"
                        }
                      ]
                    }
                  ],
                  "transactions": [
                    {
                      "transactionId": "%s",
                      "transactionDate": "2026-07-15",
                      "description": "Purchase supplies",
                      "status": "ENTERED",
                      "lines": [
                        {
                          "lineId": "%s",
                          "accountId": "%s",
                          "fundId": "%s",
                          "activityId": "%s",
                          "counterpartyId": "%s",
                          "debit": "25.00",
                          "credit": "0"
                        },
                        {
                          "lineId": "%s",
                          "accountId": "%s",
                          "fundId": "%s",
                          "activityId": "%s",
                          "counterpartyId": "%s",
                          "debit": "0",
                          "credit": "25.00"
                        }
                      ]
                    }
                  ],
                  "extensions": {
                    "version": 1,
                    "scaJakartaH2": {
                      "activeChartName": "Portable Chart",
                      "activeChartVersion": "2026",
                      "activities": [
                        {
                          "activityId": "%s",
                          "code": "EVENT",
                          "name": "Portable Event",
                          "active": true
                        }
                      ],
                      "counterparties": {
                        "counterparties": [
                          {
                            "counterpartyId": "%s",
                            "displayName": "Portable Payee",
                            "kind": "ORG",
                            "email": "payee@example.invalid",
                            "phone": null,
                            "notes": "Imported payee",
                            "active": true
                          }
                        ],
                        "merchants": [
                          {
                            "merchantId": "%s",
                            "name": "Portable Merchant",
                            "notes": null,
                            "active": true
                          }
                        ],
                        "transactionLineMerchants": [
                          {
                            "lineId": "%s",
                            "merchantId": "%s"
                          }
                        ]
                      },
                      "supplementalDetails": [
                        {
                          "supplementalDetailId": "%s",
                          "transactionId": "%s",
                          "lineOrder": 7,
                          "kind": "PAYABLE",
                          "entryRef": "AP-1",
                          "counterparty": "Portable Payee",
                          "description": "Portable payable detail",
                          "reference": null,
                          "amount": "25.00",
                          "dueDate": "2026-08-15",
                          "startDate": null,
                          "endDate": null,
                          "notes": "Imported detail"
                        }
                      ],
                      "fixedAssets": {
                        "version": 1,
                        "assets": [
                          {
                            "assetId": "%s",
                            "name": "Portable Equipment",
                            "acquisitionDate": "2026-01-15",
                            "acquisitionCost": "1800.0000",
                            "salvageValue": "300.0000",
                            "usefulLifeMonths": 60,
                            "depreciationMethod": "STRAIGHT_LINE",
                            "openingAccumulatedDepreciation": "0.0000",
                            "status": "ACTIVE",
                            "notes": "Imported fixed asset",
                            "assetAccountId": "%s",
                            "accumulatedDepreciationAccountId": "%s",
                            "depreciationExpenseAccountId": "%s",
                            "fundId": "%s",
                            "createdAt": "2026-01-15T10:00:00Z",
                            "updatedAt": "2026-07-31T11:00:00Z"
                          }
                        ],
                        "depreciationRuns": [
                          {
                            "depreciationRunId": "%s",
                            "assetId": "%s",
                            "runDate": "2026-07-15",
                            "depreciationAmount": "25.0000",
                            "transactionId": "%s",
                            "notes": "Imported completed run",
                            "createdAt": "2026-07-15T12:00:00Z"
                          }
                        ]
                      },
                      "inventory": {
                        "version": 1,
                        "items": [
                          {
                            "itemId": "%s",
                            "name": "Portable Regalia",
                            "itemType": "Regalia",
                            "quantity": "3.0000",
                            "unit": "pieces",
                            "unitValue": "125.0000",
                            "acquisitionDate": "2026-02-01",
                            "custodian": "Quartermaster",
                            "storageLocation": "Locker A",
                            "condition": "GOOD",
                            "status": "ACTIVE",
                            "notes": "Imported inventory item",
                            "inventoryAccountId": "%s",
                            "fundId": "%s",
                            "createdAt": "2026-02-01T10:00:00Z",
                            "updatedAt": "2026-07-31T11:30:00Z"
                          }
                        ],
                        "movements": [
                          {
                            "movementId": "%s",
                            "itemId": "%s",
                            "movementDate": "2026-07-15",
                            "movementType": "RECEIPT",
                            "quantityChange": "3.0000",
                            "resultingQuantity": "3.0000",
                            "unitValue": "125.0000",
                            "transactionId": "%s",
                            "notes": "Imported receipt",
                            "createdAt": "2026-07-15T12:30:00Z"
                          }
                        ]
                      }
                    }
                  }
                }
                """.formatted(
                organizationId,
                cash,
                asset,
                accumulated,
                inventoryAccount,
                expense,
                fund,
                budget,
                budgetLine,
                fund,
                transaction,
                debitLine,
                expense,
                fund,
                activity,
                counterparty,
                creditLine,
                cash,
                fund,
                activity,
                counterparty,
                activity,
                counterparty,
                merchant,
                debitLine,
                merchant,
                supplemental,
                transaction,
                fixedAsset,
                asset,
                accumulated,
                expense,
                fund,
                depreciationRun,
                fixedAsset,
                transaction,
                inventoryItem,
                inventoryAccount,
                fund,
                inventoryMovement,
                inventoryItem,
                transaction));
        return target;
    }
}
