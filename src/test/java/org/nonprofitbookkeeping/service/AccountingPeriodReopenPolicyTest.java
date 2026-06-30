package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.AccountingPeriod;
import org.nonprofitbookkeeping.model.AccountingPeriodStatus;
import org.nonprofitbookkeeping.model.ClosedPeriodPolicy;
import org.nonprofitbookkeeping.model.ReopenScope;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AccountingPeriodReopenPolicyTest
{
    @Test
    public void warnAndReopen_allowsBlankReasonWhenNotOtherwiseRequired(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("reopen-warning")))
        {
            AccountingPeriodService service = new AccountingPeriodService(jpa);
            AccountingPeriod period = closedPeriod(service);

            AccountingPeriod reopened = service.reopenPeriod(
                    period.getId(),
                    "treasurer",
                    ReopenScope.CURRENT_SESSION,
                    " ",
                    ClosedPeriodPolicy.WARN_AND_REOPEN,
                    false);

            assertEquals(AccountingPeriodStatus.OPEN, reopened.getStatus());
            assertEquals(1L, count(jpa, "PeriodReopenEvent"));
        }
    }

    @Test
    public void requireReasonPolicy_rejectsBlankReasonWithoutHistory(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("reopen-reason-policy")))
        {
            AccountingPeriodService service = new AccountingPeriodService(jpa);
            AccountingPeriod period = closedPeriod(service);

            assertThrows(IllegalArgumentException.class, () -> service.reopenPeriod(
                    period.getId(),
                    "treasurer",
                    ReopenScope.UNTIL_MANUALLY_CLOSED,
                    null,
                    ClosedPeriodPolicy.REQUIRE_REASON,
                    false));

            assertEquals(AccountingPeriodStatus.CLOSED,
                    service.findPeriodContaining(LocalDate.of(2026, 1, 15)).orElseThrow().getStatus());
            assertEquals(0L, count(jpa, "PeriodReopenEvent"));
            assertEquals(1L, count(jpa, "AuditEvent"));
        }
    }

    @Test
    public void organizationReasonRequirement_appliesToWarningPolicy(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("reopen-reason-setting")))
        {
            AccountingPeriodService service = new AccountingPeriodService(jpa);
            AccountingPeriod period = closedPeriod(service);

            assertThrows(IllegalArgumentException.class, () -> service.reopenPeriod(
                    period.getId(),
                    "treasurer",
                    ReopenScope.SINGLE_TRANSACTION,
                    "",
                    ClosedPeriodPolicy.WARN_AND_REOPEN,
                    true));

            AccountingPeriod reopened = service.reopenPeriod(
                    period.getId(),
                    "treasurer",
                    ReopenScope.SINGLE_TRANSACTION,
                    "Correct miscoded restricted-fund expense",
                    ClosedPeriodPolicy.WARN_AND_REOPEN,
                    true);

            assertEquals(AccountingPeriodStatus.OPEN, reopened.getStatus());
            assertEquals(1L, count(jpa, "PeriodReopenEvent"));
            assertEquals(2L, count(jpa, "AuditEvent"));
        }
    }

    @Test
    public void formalAdjustmentPolicy_rejectsDirectReopenWithoutHistory(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("formal-adjustment")))
        {
            AccountingPeriodService service = new AccountingPeriodService(jpa);
            AccountingPeriod period = closedPeriod(service);

            FormalAdjustmentRequiredException exception = assertThrows(
                    FormalAdjustmentRequiredException.class,
                    () -> service.reopenPeriod(
                            period.getId(),
                            "treasurer",
                            ReopenScope.UNTIL_MANUALLY_CLOSED,
                            "Need correction",
                            ClosedPeriodPolicy.REQUIRE_FORMAL_ADJUSTMENT,
                            false));

            assertEquals(period.getId().longValue(), exception.getAccountingPeriodId());
            assertEquals(AccountingPeriodStatus.CLOSED,
                    service.findPeriodContaining(LocalDate.of(2026, 1, 15)).orElseThrow().getStatus());
            assertEquals(0L, count(jpa, "PeriodReopenEvent"));
            assertEquals(1L, count(jpa, "AuditEvent"));
        }
    }

    @Test
    public void policyAndScopeAreRequired(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("reopen-validation")))
        {
            AccountingPeriodService service = new AccountingPeriodService(jpa);
            AccountingPeriod period = closedPeriod(service);

            assertThrows(IllegalArgumentException.class, () -> service.reopenPeriod(
                    period.getId(),
                    "treasurer",
                    null,
                    "Reason",
                    ClosedPeriodPolicy.WARN_AND_REOPEN,
                    false));
            assertThrows(IllegalArgumentException.class, () -> service.reopenPeriod(
                    period.getId(),
                    "treasurer",
                    ReopenScope.CURRENT_SESSION,
                    "Reason",
                    null,
                    false));
        }
    }

    private static AccountingPeriod closedPeriod(AccountingPeriodService service)
    {
        AccountingPeriod period = service.createPeriod(
                2026,
                1,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31));
        return service.closePeriod(period.getId(), "treasurer");
    }

    private static long count(Jpa jpa, String entityName)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery("select count(e) from " + entityName + " e", Long.class)
                    .getSingleResult();
        }
    }
}
