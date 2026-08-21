package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.FixedAsset;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FixedAssetServiceTest
{
    private static final long CHART_ID = 10_001L;
    private static final long COMPANY_ID = 10_001L;
    private static final long FUND_ID = 10_001L;
    private static final long ASSET_ACCOUNT_ID = 10_001L;
    private static final long ACCUMULATED_DEPRECIATION_ACCOUNT_ID = 10_002L;
    private static final long CHECKING_ACCOUNT_ID = 10_003L;
    private static final long DEPRECIATION_EXPENSE_ACCOUNT_ID = 10_004L;

    @Test
    public void createAssetCalculatesAccumulatedDepreciationAndBookValue(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fixed-assets")))
        {
            seedCompanyAccountsAndFund(jpa);
            FixedAssetService service = new FixedAssetService(jpa, new TransactionEntryService(jpa, () -> "SCA"), () -> "SCA");

            FixedAssetView asset = service.create(assetCommand("Laptop", new BigDecimal("1200.0000"), new BigDecimal("0.0000"), 36));

            assertNotNull(asset.id());
            assertEquals("Laptop", asset.name());
            assertEquals(new BigDecimal("1200.0000"), asset.acquisitionCost());
            assertEquals(new BigDecimal("0.0000"), asset.accumulatedDepreciation());
            assertEquals(new BigDecimal("1200.0000"), asset.currentBookValue());
            assertEquals(new BigDecimal("33.3333"), asset.nextDepreciationAmount());
        }
    }

    @Test
    public void depreciationRunCreatesCanonicalTransactionAndRunRecord(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fixed-asset-depreciation")))
        {
            seedCompanyAccountsAndFund(jpa);
            FixedAssetService service = new FixedAssetService(jpa, new TransactionEntryService(jpa, () -> "SCA"), () -> "SCA");
            FixedAssetView asset = service.create(assetCommand("Trailer", new BigDecimal("840.0000"), new BigDecimal("0.0000"), 84));

            DepreciationRunView run = service.runMonthlyDepreciation(asset.id(), LocalDate.of(2026, 4, 30), "April depreciation");

            assertNotNull(run.id());
            assertEquals(asset.id(), run.fixedAssetId());
            assertEquals(new BigDecimal("10.0000"), run.depreciationAmount());
            assertNotNull(run.transactionId());
            assertEquals(1, service.listDepreciationRuns("SCA").size());
            assertEquals(new BigDecimal("10.0000"), service.load(asset.id()).accumulatedDepreciation());
            assertLedgerTransaction(jpa, run.transactionId());
        }
    }

    @Test
    public void fixedAssetAccountMustUseFixedAssetSubtype(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fixed-asset-validation")))
        {
            seedCompanyAccountsAndFund(jpa);
            FixedAssetService service = new FixedAssetService(jpa, new TransactionEntryService(jpa, () -> "SCA"), () -> "SCA");

            FixedAssetCommand bad = new FixedAssetCommand(
                    "SCA",
                    CHECKING_ACCOUNT_ID,
                    ACCUMULATED_DEPRECIATION_ACCOUNT_ID,
                    DEPRECIATION_EXPENSE_ACCOUNT_ID,
                    FUND_ID,
                    "Invalid",
                    LocalDate.of(2026, 1, 1),
                    new BigDecimal("100.0000"),
                    BigDecimal.ZERO,
                    36,
                    FixedAsset.DepreciationMethod.STRAIGHT_LINE,
                    BigDecimal.ZERO,
                    FixedAsset.Status.ACTIVE,
                    "");

            assertThrows(IllegalArgumentException.class, () -> service.create(bad));
        }
    }

    private static FixedAssetCommand assetCommand(String name, BigDecimal cost, BigDecimal salvage, int usefulLifeMonths)
    {
        return new FixedAssetCommand(
                "SCA",
                ASSET_ACCOUNT_ID,
                ACCUMULATED_DEPRECIATION_ACCOUNT_ID,
                DEPRECIATION_EXPENSE_ACCOUNT_ID,
                FUND_ID,
                name,
                LocalDate.of(2026, 1, 1),
                cost,
                salvage,
                usefulLifeMonths,
                FixedAsset.DepreciationMethod.STRAIGHT_LINE,
                BigDecimal.ZERO,
                FixedAsset.Status.ACTIVE,
                "Test asset");
    }

    private static void seedCompanyAccountsAndFund(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (?, 'SCA Chart', '1', 'ACTIVE')")
                    .setParameter(1, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO company (id, code, display_name, active_chart_of_accounts_id) VALUES (?, 'SCA', 'SCA Branch', ?)")
                    .setParameter(1, COMPANY_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = ? WHERE id = ?")
                    .setParameter(1, COMPANY_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) VALUES (?, ?, 'OPERATING', 'Operating', 'UNRESTRICTED')")
                    .setParameter(1, FUND_ID)
                    .setParameter(2, COMPANY_ID)
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (?, ?, '1500', 'Equipment', 'ASSET', 'FIXED_ASSET', 'DEBIT')")
                    .setParameter(1, ASSET_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (?, ?, '1590', 'Accumulated Depreciation', 'ASSET', 'FIXED_ASSET', 'CREDIT')")
                    .setParameter(1, ACCUMULATED_DEPRECIATION_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, account_function, subtype, normal_balance) VALUES (?, ?, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .setParameter(1, CHECKING_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (?, ?, '6100', 'Depreciation Expense', 'EXPENSE', 'DEBIT')")
                    .setParameter(1, DEPRECIATION_EXPENSE_ACCOUNT_ID)
                    .setParameter(2, CHART_ID)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }

    private static void assertLedgerTransaction(Jpa jpa, long txnId)
    {
        try (EntityManager em = jpa.em())
        {
            Number txnCount = (Number) em.createNativeQuery("SELECT COUNT(*) FROM txn WHERE id = ? AND status = 'ENTERED'")
                    .setParameter(1, txnId)
                    .getSingleResult();
            Number splitCount = (Number) em.createNativeQuery("SELECT COUNT(*) FROM txn_split WHERE txn_id = ?")
                    .setParameter(1, txnId)
                    .getSingleResult();
            BigDecimal expense = (BigDecimal) em.createNativeQuery("SELECT amount_signed FROM txn_split WHERE txn_id = ? AND account_id = ?")
                    .setParameter(1, txnId)
                    .setParameter(2, DEPRECIATION_EXPENSE_ACCOUNT_ID)
                    .getSingleResult();
            BigDecimal accumulated = (BigDecimal) em.createNativeQuery("SELECT amount_signed FROM txn_split WHERE txn_id = ? AND account_id = ?")
                    .setParameter(1, txnId)
                    .setParameter(2, ACCUMULATED_DEPRECIATION_ACCOUNT_ID)
                    .getSingleResult();

            assertEquals(1L, txnCount.longValue());
            assertEquals(2L, splitCount.longValue());
            assertTrue(expense.compareTo(new BigDecimal("10.0000")) == 0);
            assertTrue(accumulated.compareTo(new BigDecimal("10.0000")) == 0);
        }
    }
}
