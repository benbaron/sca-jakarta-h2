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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixedAssetStatusLifecycleTest
{
    private static final long CHART_ID = 31_001L;
    private static final long COMPANY_ID = 31_001L;
    private static final long FUND_ID = 31_001L;
    private static final long ASSET_ACCOUNT_ID = 31_001L;
    private static final long ACCUMULATED_ACCOUNT_ID = 31_002L;
    private static final long EXPENSE_ACCOUNT_ID = 31_003L;

    @Test
    void ordinaryUpdateCannotChangeStatusAndExplicitTransitionsAreAudited(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fixed-asset-status")))
        {
            seed(jpa);
            FixedAssetService service = new FixedAssetService(
                    jpa, new TransactionEntryService(jpa, () -> "SCA"), () -> "SCA");
            FixedAssetView asset = service.create(command("Laptop", FixedAsset.Status.ACTIVE));

            IllegalArgumentException direct = assertThrows(IllegalArgumentException.class,
                    () -> service.update(asset.id(), command("Laptop", FixedAsset.Status.INACTIVE)));
            assertEquals("Fixed asset status changes use the explicit lifecycle action", direct.getMessage());
            assertEquals(FixedAsset.Status.ACTIVE, service.load(asset.id()).status());

            FixedAssetView inactive = service.changeStatus(
                    asset.id(), FixedAsset.Status.INACTIVE, "tester", "Placed in storage");
            assertEquals(FixedAsset.Status.INACTIVE, inactive.status());

            FixedAssetView renamed = service.update(
                    asset.id(), command("Laptop in storage", FixedAsset.Status.INACTIVE));
            assertEquals("Laptop in storage", renamed.name());
            assertEquals(FixedAsset.Status.INACTIVE, renamed.status());

            FixedAssetView active = service.changeStatus(
                    asset.id(), FixedAsset.Status.ACTIVE, "tester", "Returned to service");
            assertEquals(FixedAsset.Status.ACTIVE, active.status());

            try (EntityManager em = jpa.em())
            {
                Long auditCount = em.createQuery(
                                "select count(a) from AuditEvent a where a.actionType = :action "
                                        + "and a.entityType = :type and a.entityId = :id", Long.class)
                        .setParameter("action", "FIXED_ASSET_STATUS_CHANGED")
                        .setParameter("type", "FixedAsset")
                        .setParameter("id", Long.toString(asset.id()))
                        .getSingleResult();
                assertEquals(2L, auditCount.longValue());
            }
        }
    }

    @Test
    void interactiveCreationStartsActiveAndDisposedHistoryCannotBeReactivated(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("fixed-asset-terminal")))
        {
            seed(jpa);
            FixedAssetService service = new FixedAssetService(
                    jpa, new TransactionEntryService(jpa, () -> "SCA"), () -> "SCA");

            IllegalArgumentException inactiveCreate = assertThrows(IllegalArgumentException.class,
                    () -> service.create(command("Stored asset", FixedAsset.Status.INACTIVE)));
            assertTrue(inactiveCreate.getMessage().contains("must start ACTIVE"));

            FixedAssetView asset = service.create(command("Disposed history", FixedAsset.Status.ACTIVE));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("update fixed_asset set status = 'DISPOSED' where id = ?")
                        .setParameter(1, asset.id())
                        .executeUpdate();
                em.getTransaction().commit();
            }

            IllegalStateException reactivate = assertThrows(IllegalStateException.class,
                    () -> service.changeStatus(
                            asset.id(), FixedAsset.Status.ACTIVE, "tester", "unsafe restore"));
            assertTrue(reactivate.getMessage().contains("reverse its Sale or Retirement"));

            IllegalArgumentException directDisposed = assertThrows(IllegalArgumentException.class,
                    () -> service.update(asset.id(), command("Changed", FixedAsset.Status.DISPOSED)));
            assertEquals(
                    "DISPOSED is created only by the governed Sale or Retirement workflow",
                    directDisposed.getMessage());
        }
    }

    private static FixedAssetCommand command(String name, FixedAsset.Status status)
    {
        return new FixedAssetCommand(
                "SCA", ASSET_ACCOUNT_ID, ACCUMULATED_ACCOUNT_ID, EXPENSE_ACCOUNT_ID, FUND_ID,
                name, LocalDate.of(2026, 1, 1), new BigDecimal("1200.0000"), BigDecimal.ZERO,
                36, FixedAsset.DepreciationMethod.STRAIGHT_LINE, BigDecimal.ZERO, status, "Test asset");
    }

    private static void seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (?, 'SCA Chart', '1', 'ACTIVE')")
                    .setParameter(1, CHART_ID).executeUpdate();
            em.createNativeQuery("INSERT INTO company (id, code, display_name, active_chart_of_accounts_id) VALUES (?, 'SCA', 'SCA Branch', ?)")
                    .setParameter(1, COMPANY_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = ? WHERE id = ?")
                    .setParameter(1, COMPANY_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) VALUES (?, ?, 'OPERATING', 'Operating', 'UNRESTRICTED')")
                    .setParameter(1, FUND_ID).setParameter(2, COMPANY_ID).executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (?, ?, '1500', 'Equipment', 'ASSET', 'FIXED_ASSET', 'DEBIT')")
                    .setParameter(1, ASSET_ACCOUNT_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES (?, ?, '1590', 'Accumulated Depreciation', 'ASSET', 'FIXED_ASSET', 'CREDIT')")
                    .setParameter(1, ACCUMULATED_ACCOUNT_ID).setParameter(2, CHART_ID).executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (?, ?, '6100', 'Depreciation Expense', 'EXPENSE', 'DEBIT')")
                    .setParameter(1, EXPENSE_ACCOUNT_ID).setParameter(2, CHART_ID).executeUpdate();
            em.getTransaction().commit();
        }
    }
}
