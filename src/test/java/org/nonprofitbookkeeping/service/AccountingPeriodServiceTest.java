package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AccountingPeriod;
import org.nonprofitbookkeeping.model.AccountingPeriodStatus;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.PeriodReopenEvent;
import org.nonprofitbookkeeping.model.ReopenScope;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AccountingPeriodServiceTest
{
    @Test
    public void closeAndReopen_recordsHistory(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("period-service")))
        {
            AccountingPeriodService service = new AccountingPeriodService(jpa);
            AccountingPeriod period = service.createPeriod(2026, 1,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

            AccountingPeriod closed = service.closePeriod(period.getId(), "treasurer");
            assertEquals(AccountingPeriodStatus.CLOSED, closed.getStatus());
            assertNotNull(closed.getClosedAt());

            AccountingPeriod reopened = service.reopenPeriod(period.getId(), "treasurer",
                    ReopenScope.UNTIL_MANUALLY_CLOSED, "Correct January entry");
            assertEquals(AccountingPeriodStatus.OPEN, reopened.getStatus());

            try (EntityManager em = jpa.em())
            {
                List<PeriodReopenEvent> reopenEvents = em.createQuery(
                        "from PeriodReopenEvent e", PeriodReopenEvent.class).getResultList();
                List<AuditEvent> auditEvents = em.createQuery(
                        "from AuditEvent e order by e.occurredAt", AuditEvent.class).getResultList();

                assertEquals(1, reopenEvents.size());
                assertEquals(AccountingPeriodStatus.CLOSED, reopenEvents.get(0).getPriorStatus());
                assertEquals(2, auditEvents.size());
                assertEquals("PERIOD_CLOSED", auditEvents.get(0).getActionType());
                assertEquals("PERIOD_REOPENED", auditEvents.get(1).getActionType());
            }
        }
    }

    @Test
    public void createPeriod_rejectsInvalidDateRange(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("period-validation")))
        {
            AccountingPeriodService service = new AccountingPeriodService(jpa);
            assertThrows(IllegalArgumentException.class, () -> service.createPeriod(2026, 1,
                    LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31)));
        }
    }

    @Test
    public void createPeriod_enforcesUniqueFiscalYearAndNumber(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("period-unique")))
        {
            AccountingPeriodService service = new AccountingPeriodService(jpa);
            service.createPeriod(2026, 1, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

            assertThrows(RuntimeException.class, () -> service.createPeriod(2026, 1,
                    LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)));
            assertEquals(1, service.listPeriods().size());
        }
    }
}
