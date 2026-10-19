package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.AssetAcquisitionTestFixture.*;

class FixedAssetAcquisitionTest
{
    @TempDir Path directory;

    static AssetAcquisitionCommand purchase(AssetAcquisitionTestFixture f, String name, String cost)
    {
        return new AssetAcquisitionCommand(null, f.command(name, cost), AssetAcquisitionSource.NEW_PURCHASE, f.base().bank(), null);
    }

    static AssetAcquisitionCommand link(AssetAcquisitionTestFixture f, String name, String cost, long split)
    {
        return new AssetAcquisitionCommand(null, f.command(name, cost), AssetAcquisitionSource.EXISTING_ENTRY, null, split);
    }

    static long invoice(Jpa jpa, AssetAcquisitionTestFixture f, String cost)
    {
        long id = new TransactionEntryService(jpa).enter(new TransactionCommand(DATE, null, "Shared equipment invoice", null,
                List.of(FundTransferTestFixture.line(f.asset(), f.base().source(), cost, "0"),
                        FundTransferTestFixture.line(f.base().bank(), f.base().source(), "0", cost)))).id();
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select s.id from TxnSplit s where s.txn.id = :txn and s.account.id = :asset", Long.class)
                    .setParameter("txn", id).setParameter("asset", f.asset()).getSingleResult();
        }
    }

    @Test
    void purchaseIsAtomicPreciseAndRetryReturnsRetainedAssetAfterRestartAndMetadataEdit()
    {
        Path database = directory.resolve("purchase"); UUID key = UUID.randomUUID();
        AssetAcquisitionCommand command; AssetAcquisitionPreview preview; long id;
        try (Jpa jpa = new Jpa(database))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa); var service = f.service(jpa);
            command = purchase(f, "Equipment", "1000.1234");
            preview = service.previewAcquisition(command, key);
            assertEquals(1, count(jpa, "select count(t) from Txn t"));
            assertEquals(0, count(jpa, "select count(a) from FixedAsset a"));
            var saved = service.recordAcquisition(preview); id = saved.id();
            assertEquals(AssetAcquisitionSource.NEW_PURCHASE, saved.acquisition().source());
            assertEquals(new BigDecimal("1000.1234"), saved.acquisition().sourceDebit());
            assertEquals(new BigDecimal("1000.1234"), saved.acquisition().claimed());
            assertEquals(new BigDecimal("0.0000"), saved.acquisition().remaining());
            var report = new org.nonprofitbookkeeping.report.AssetInventoryReportQueryService(jpa, () -> COMPANY)
                    .fixedAssetRegister(new org.nonprofitbookkeeping.report.AssetInventoryReportQueryService.FixedAssetReportRequest(
                            DATE, DATE, f.base().source(), f.asset(), null, null, 100));
            assertEquals(new BigDecimal("1000.1234"), report.domainGross());
            assertEquals(report.domainGross(), report.ledgerGross());
            assertEquals(new BigDecimal("0.0000"), report.difference());
            assertEquals(id, service.recordAcquisition(preview).id());
            assertEquals(2, count(jpa, "select count(t) from Txn t"));
            assertEquals(1, count(jpa, "select count(a) from FixedAsset a"));
            try (EntityManager em = jpa.em())
            {
                assertEquals(new BigDecimal("1000.1234"), em.createQuery("select sum(s.amountSigned) from TxnSplit s where s.account.id = :account", BigDecimal.class)
                        .setParameter("account", f.asset()).getSingleResult());
                assertEquals(2L, em.createQuery("select count(s) from TxnSplit s where s.txn.id = :txn", Long.class)
                        .setParameter("txn", saved.acquisition().transactionId()).getSingleResult());
            }
            service.update(id, f.command("Renamed equipment", "1000.1234"));
        }
        try (Jpa jpa = new Jpa(database))
        {
            var service = new FixedAssetService(jpa);
            long audits = count(jpa, "select count(a) from AuditEvent a");
            assertEquals("Renamed equipment", service.recordAcquisition(preview).name());
            assertEquals(id, service.previewAcquisition(command, key).acceptedAssetId());
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            var changed = new AssetAcquisitionCommand(null, new FixedAssetCommand(COMPANY, command.asset().assetAccountId(),
                    command.asset().accumulatedDepreciationAccountId(), command.asset().depreciationExpenseAccountId(), command.asset().fundId(),
                    "Different accepted request", DATE, command.asset().acquisitionCost(), BigDecimal.ZERO, 60,
                    FixedAsset.DepreciationMethod.STRAIGHT_LINE, BigDecimal.ZERO, FixedAsset.Status.ACTIVE, "Synthetic acquisition"),
                    command.source(), command.bankAccountId(), null);
            assertThrows(IllegalStateException.class, () -> service.previewAcquisition(changed, key));
        }
    }

    @Test
    void explicitRegisterOnlyAndLegacyRegistersCreateNoAccountingAndNoGuessedLinks()
    {
        try (Jpa jpa = new Jpa(directory.resolve("opening")))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa); var service = f.service(jpa);
            var legacy = service.create(f.command("Legacy", "1000"));
            assertNull(legacy.acquisition().source()); assertNull(legacy.acquisition().transactionId());
            var opening = service.recordAcquisition(service.previewAcquisition(new AssetAcquisitionCommand(null,
                    f.command("Opening", "1000"), AssetAcquisitionSource.REGISTER_ONLY, null, null), UUID.randomUUID()));
            assertEquals(AssetAcquisitionSource.REGISTER_ONLY, opening.acquisition().source());
            assertNull(opening.acquisition().sourceDebit()); assertNull(opening.acquisition().splitId());
            var marked = service.recordAcquisition(service.previewAcquisition(new AssetAcquisitionCommand(legacy.id(),
                    f.command("Legacy", "1000"), AssetAcquisitionSource.REGISTER_ONLY, null, null), UUID.randomUUID()));
            assertEquals(legacy.id(), marked.id());
            assertEquals(AssetAcquisitionSource.REGISTER_ONLY, marked.acquisition().source());
            assertEquals(2, count(jpa, "select count(a) from FixedAsset a"));
            assertEquals(1, count(jpa, "select count(t) from Txn t"));
            assertThrows(NullPointerException.class, () -> service.previewAcquisition(new AssetAcquisitionCommand(null,
                    f.command("No default", "1000"), null, null, null), UUID.randomUUID()));
        }
    }

    @Test
    void sharedInvoiceClaimsTieExactlyAndStaleExcessDuplicateAndForeignClaimsCannotWrite()
    {
        try (Jpa jpa = new Jpa(directory.resolve("claims")))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa); var service = f.service(jpa); long split = invoice(jpa, f, "1000");
            var stale = service.previewAcquisition(link(f, "Stale", "500", split), UUID.randomUUID());
            service.recordAcquisition(service.previewAcquisition(link(f, "First", "600", split), UUID.randomUUID()));
            assertThrows(IllegalStateException.class, () -> service.recordAcquisition(stale));
            var second = service.recordAcquisition(service.previewAcquisition(link(f, "Second", "400", split), UUID.randomUUID()));
            assertEquals(new BigDecimal("1000.0000"), second.acquisition().claimed());
            assertEquals(new BigDecimal("0.0000"), second.acquisition().remaining());
            assertThrows(IllegalStateException.class, () -> service.previewAcquisition(link(f, "Excess", "0.0001", split), UUID.randomUUID()));
            assertEquals(2, count(jpa, "select count(a) from FixedAsset a"));
            assertEquals(2, count(jpa, "select count(t) from Txn t"));
            assertTrue(service.acquisitionCandidates().isEmpty());
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                Company foreign = new Company(); foreign.setCode("OTHER"); foreign.setDisplayName("Other"); em.persist(foreign);
                Txn transaction = new Txn(); transaction.setCompany(foreign); transaction.setTxnDate(DATE); transaction.setMemo("Foreign"); transaction.setStatus("ENTERED"); em.persist(transaction);
                TxnSplit foreignLine = new TxnSplit(); foreignLine.setTxn(transaction); foreignLine.setAccount(em.find(Account.class, f.asset()));
                foreignLine.setFund(em.find(Fund.class, f.base().source())); foreignLine.setAmountSigned(new BigDecimal("1000")); em.persist(foreignLine);
                em.getTransaction().commit();
                assertThrows(IllegalArgumentException.class, () -> service.previewAcquisition(link(f, "Foreign", "100", foreignLine.getId()), UUID.randomUUID()));
            }
        }
    }

    @Test
    void existingRegisterLinksWithoutRepostingAndUnlinkRetainsAuditBeforeOrdinaryCorrection()
    {
        try (Jpa jpa = new Jpa(directory.resolve("existing")))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa); var service = f.service(jpa); long split = invoice(jpa, f, "250");
            var existing = service.create(f.command("Historical", "250"));
            var preview = service.previewAcquisition(new AssetAcquisitionCommand(existing.id(), f.command("Historical", "250"),
                    AssetAcquisitionSource.EXISTING_ENTRY, null, split), UUID.randomUUID());
            var linked = service.recordAcquisition(preview); assertEquals(existing.id(), linked.id());
            long transaction = linked.acquisition().transactionId();
            assertEquals(1, count(jpa, "select count(a) from FixedAsset a")); assertEquals(2, count(jpa, "select count(t) from Txn t"));
            var entries = new TransactionEntryService(jpa); var corrections = new TransactionCorrectionService(jpa);
            assertThrows(PostingException.class, () -> entries.update(transaction, new TransactionCommand(DATE, null, "Rewrite", null,
                    List.of(FundTransferTestFixture.line(f.asset(), f.base().source(), "250", "0"), FundTransferTestFixture.line(f.base().bank(), f.base().source(), "0", "250")))));
            assertThrows(PostingException.class, () -> corrections.delete(transaction, "tester", "Delete linked purchase"));
            assertThrows(PostingException.class, () -> corrections.reverse(transaction, DATE.plusDays(1), "tester", "Reverse linked purchase", false));
            assertThrows(IllegalStateException.class, () -> service.update(existing.id(), f.command("Historical", "249")));
            assertThrows(IllegalArgumentException.class, () -> new AccountAdminService(jpa).save(new AccountCommand(f.asset(), "1600", "Changed",
                    AccountType.EXPENSE, null, NormalBalance.DEBIT, null, null, true)));
            var removed = service.removeAcquisitionLink(existing.id(), "Link was entered in error");
            assertEquals(AssetAcquisitionSource.REGISTER_ONLY, removed.acquisition().source());
            assertNull(removed.acquisition().splitId());
            long audits = count(jpa, "select count(a) from AuditEvent a");
            assertNull(service.recordAcquisition(preview).acquisition().splitId());
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            assertNotNull(corrections.reverse(transaction, DATE.plusDays(1), "tester", "Correct unlinked purchase", false).reversalTransactionId());
            assertEquals(1, count(jpa, "select count(a) from AuditEvent a where a.actionType = 'FIXED_ASSET_ACQUISITION_UNLINKED'"));
        }
    }

    @Test
    void permissionClosedReconciledAndPrecisionGuardsRejectBeforeDurableChangesButExactAcknowledgementStillWorks()
    {
        try (Jpa jpa = new Jpa(directory.resolve("guards")))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa); var service = f.service(jpa);
            long viewer;
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin(); AppUser user = new AppUser(); user.setUsername("viewer");
                user.setDisplayName("Viewer"); em.persist(user); em.getTransaction().commit(); viewer = user.getId();
            }
            Instant now = Instant.now();
            var guard = new AuthorizationGuard(jpa, () -> Optional.of(new AuthenticatedUserSession(viewer, "viewer", "Viewer", COMPANY,
                    Set.of(ReservedSecurityRole.VIEWER), now, now)));
            var guarded = new FixedAssetService(jpa, new TransactionEntryService(jpa), () -> COMPANY, guard);
            assertThrows(AuthorizationException.class, () -> guarded.recordAcquisition(null));
            assertThrows(AuthorizationException.class, () -> guarded.removeAcquisitionLink(1, null));
            assertThrows(IllegalArgumentException.class, () -> service.previewAcquisition(purchase(f, "Precision", "10.12345"), UUID.randomUUID()));
            var accepted = service.previewAcquisition(purchase(f, "Retained", "100"), UUID.randomUUID());
            var saved = service.recordAcquisition(accepted);
            var unaccepted = service.previewAcquisition(purchase(f, "Closed", "100"), UUID.randomUUID());
            var closed = new PeriodCloseRangeService(jpa).closeRange(COMPANY, DATE, DATE, "CALCULATED", "tester", "Close acquisitions");
            assertThrows(RuntimeException.class, () -> service.recordAcquisition(unaccepted));
            assertEquals(saved.id(), service.recordAcquisition(accepted).id());
            assertThrows(RuntimeException.class, () -> service.removeAcquisitionLink(saved.id(), "Protected"));
            assertEquals(1, count(jpa, "select count(a) from FixedAsset a"));
            new PeriodCloseRangeService(jpa).reopenRange(closed.id(), "tester", "Reopen", ClosedPeriodPolicy.WARN_AND_REOPEN, false);
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                UUID run = UUID.randomUUID();
                em.createNativeQuery("insert into reconciliation_run(id,group_code,statement_ending_on,bank_format,imported_transaction_count,status,notes) values (?, 'DEFAULT', DATE '2026-04-30', 'OFX', 1, 'COMPLETED', 'Synthetic')").setParameter(1, run).executeUpdate();
                em.createNativeQuery("insert into txn_reconciliation_protection(txn_id,reconciliation_run_id,protected_by,notes) values (?,?,'tester','Synthetic')")
                        .setParameter(1, saved.acquisition().transactionId()).setParameter(2, run).executeUpdate();
                em.getTransaction().commit();
            }
            assertThrows(IllegalStateException.class, () -> service.removeAcquisitionLink(saved.id(), "Reconciled"));
        }
    }

    @Test
    void finalizedBankRangeBlocksFreshPurchaseAndStalePreviewWithoutPartialWrites()
    {
        try (Jpa jpa = new Jpa(directory.resolve("bank-range")))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa); var service = f.service(jpa);
            var command = purchase(f, "Protected bank purchase", "1000");
            var preview = service.previewAcquisition(command, UUID.randomUUID());
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                var company = em.createQuery("from Company c where c.code = 'DEFAULT'", Company.class).getSingleResult();
                em.createNativeQuery("insert into company_bank_account(id,company_id,account_id,name,institution_name,account_type,last_four) values (30001,?,?,'Checking','Test bank','CHECKING','1234')")
                        .setParameter(1, company.getId()).setParameter(2, f.base().bank()).executeUpdate();
                em.createNativeQuery("insert into bank_reconciliation_session(id,company_id,bank_account_id,statement_start_date,statement_end_date,mismatch_policy,status) values (30001,?,30001,DATE '2026-04-01',DATE '2026-04-30','WARN_ONLY','FINALIZED')")
                        .setParameter(1, company.getId()).executeUpdate();
                em.getTransaction().commit();
            }
            assertThrows(IllegalStateException.class, () -> service.previewAcquisition(command, UUID.randomUUID()));
            assertThrows(IllegalStateException.class, () -> service.recordAcquisition(preview));
            assertEquals(0, count(jpa, "select count(a) from FixedAsset a"));
            assertEquals(1, count(jpa, "select count(t) from Txn t"));
        }
    }

    @Test
    void lateDomainAuditFailureRollsBackAssetTransactionSplitsAndReceiptBeforeCorrectedRetry()
    {
        try (Jpa jpa = new Jpa(directory.resolve("rollback")))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa); var service = f.service(jpa); UUID key = UUID.randomUUID();
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("alter table audit_event add constraint synthetic_acquisition_failure check (action_type <> 'FIXED_ASSET_ACQUISITION_ACCEPTED')").executeUpdate();
                em.getTransaction().commit();
            }
            var preview = service.previewAcquisition(purchase(f, "Rollback", "1000"), key);
            assertThrows(RuntimeException.class, () -> service.recordAcquisition(preview));
            assertEquals(0, count(jpa, "select count(a) from FixedAsset a")); assertEquals(1, count(jpa, "select count(t) from Txn t"));
            try (EntityManager em = jpa.em())
            {
                em.getTransaction().begin(); em.createNativeQuery("alter table audit_event drop constraint synthetic_acquisition_failure").executeUpdate(); em.getTransaction().commit();
            }
            var saved = service.recordAcquisition(service.previewAcquisition(purchase(f, "Corrected", "1001"), key));
            assertEquals("Corrected", saved.name()); assertEquals(1, count(jpa, "select count(a) from FixedAsset a"));
        }
    }

    @Test
    void concurrentSameRequestCreatesOneAssetAndPurchaseAndSharedSourceCannotBeOverclaimed() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("concurrency")))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa); var service = f.service(jpa);
            var preview = service.previewAcquisition(purchase(f, "Concurrent", "1000"), UUID.randomUUID());
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try
            {
                var first = pool.submit(() -> service.recordAcquisition(preview));
                var second = pool.submit(() -> service.recordAcquisition(preview));
                assertEquals(first.get(30, TimeUnit.SECONDS).id(), second.get(30, TimeUnit.SECONDS).id());
                assertEquals(1, count(jpa, "select count(a) from FixedAsset a"));
                assertEquals(2, count(jpa, "select count(t) from Txn t"));
                long split = invoice(jpa, f, "1000");
                var a = service.previewAcquisition(link(f, "Claim A", "600", split), UUID.randomUUID());
                var b = service.previewAcquisition(link(f, "Claim B", "600", split), UUID.randomUUID());
                var one = pool.submit(() -> service.recordAcquisition(a));
                var two = pool.submit(() -> service.recordAcquisition(b));
                int successes = 0;
                for (var task : List.of(one, two))
                {
                    try
                    {
                        task.get(30, TimeUnit.SECONDS); successes++;
                    }
                    catch (ExecutionException ex)
                    {
                        assertInstanceOf(IllegalStateException.class, ex.getCause());
                    }
                }
                assertEquals(1, successes);
            }
            finally
            {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void completedDepreciationAndDisposalPreserveAcquisitionAndRetainItsBasisAndSource()
    {
        try (Jpa jpa = new Jpa(directory.resolve("downstream")))
        {
            var f = AssetAcquisitionTestFixture.seed(jpa); var service = f.service(jpa);
            var asset = service.recordAcquisition(service.previewAcquisition(purchase(f, "Equipment", "600"), UUID.randomUUID()));
            var run = service.runMonthlyDepreciation(asset.id(), DATE.plusMonths(1), "Monthly depreciation");
            assertEquals(new BigDecimal("10.0000"), run.depreciationAmount());
            assertEquals(asset.acquisition(), service.load(asset.id()).acquisition());
            assertThrows(IllegalStateException.class, () -> service.removeAcquisitionLink(asset.id(), "Used basis"));
            var disposal = service.recordLifecycleEvent(service.previewLifecycleEvent(asset.id(), new FixedAssetLifecycleCommand(
                    FixedAssetLifecycleEvent.EventType.RETIREMENT, DATE.plusMonths(2), BigDecimal.ZERO, BigDecimal.ZERO,
                    null, null, f.base().expense(), "Retire")), "tester");
            assertEquals(FixedAsset.Status.DISPOSED, service.load(asset.id()).status());
            assertEquals(asset.acquisition(), service.load(asset.id()).acquisition());
            assertNotNull(disposal.transactionId());
        }
    }

    static long count(Jpa jpa, String query)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(query, Long.class).getSingleResult();
        }
    }
}
