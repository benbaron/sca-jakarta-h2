package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.repository.JdbcPeriodCloseRunRepository;
import org.nonprofitbookkeeping.repository.PeriodCloseRunRecord;
import org.nonprofitbookkeeping.repository.RepositoryIntegrationSupport;
import org.nonprofitbookkeeping.repository.WorkflowRunStatus;

import javax.sql.DataSource;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * PeriodCloseServiceIntegrationTest component.
 */
public class PeriodCloseServiceIntegrationTest
{
    @Test
    public void recordCompletedClose_persistsRunInRepository()
    {
        DataSource ds = RepositoryIntegrationSupport.migratedDataSource();
        JdbcPeriodCloseRunRepository repo = new JdbcPeriodCloseRunRepository(ds);
        PeriodCloseService service = new PeriodCloseService(repo);

        PeriodCloseRunRecord run = service.recordCompletedClose(
                "BARONY-DRAGON",
                LocalDate.of(2026, 3, 31),
                null,
                "Closed March books");

        PeriodCloseRunRecord loaded = repo.findById(run.id()).orElseThrow();
        assertEquals(WorkflowRunStatus.COMPLETED, loaded.status());
        assertNull(loaded.producedTransactionId());
        assertEquals("Closed March books", loaded.notes());
    }
}
