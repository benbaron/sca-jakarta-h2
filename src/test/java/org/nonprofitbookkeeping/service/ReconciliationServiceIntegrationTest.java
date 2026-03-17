package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.repository.JdbcReconciliationRunRepository;
import org.nonprofitbookkeeping.repository.ReconciliationRunRecord;
import org.nonprofitbookkeeping.repository.RepositoryIntegrationSupport;
import org.nonprofitbookkeeping.repository.WorkflowRunStatus;

import javax.sql.DataSource;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ReconciliationServiceIntegrationTest component.
 */
public class ReconciliationServiceIntegrationTest
{
    @Test
    public void recordCompletedRun_persistsRunInRepository()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcReconciliationRunRepository repo = new JdbcReconciliationRunRepository(ds);
        ReconciliationService service = new ReconciliationService(repo);

        ReconciliationRunRecord run = service.recordCompletedRun(
                "BARONY-DRAGON",
                LocalDate.of(2026, 3, 31),
                BankingDataFormat.OFX,
                12,
                "Reconciled March statement");

        ReconciliationRunRecord loaded = repo.findById(run.id()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, loaded.status());
        assertEquals(BankingDataFormat.OFX, loaded.bankFormat());
        assertEquals(12, loaded.importedTransactionCount());
    }
}
