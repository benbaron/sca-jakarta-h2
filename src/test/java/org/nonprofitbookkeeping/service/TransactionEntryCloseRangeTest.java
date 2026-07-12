package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionEntryCloseRangeTest
{
    @Test
    void entryHonorsActiveCompanyCloseRange(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("transaction-entry-close")))
        {
            seedReferenceData(jpa);
            PeriodCloseRangeService closeService = new PeriodCloseRangeService(jpa);
            closeService.closeRange(
                    "OTHER",
                    LocalDate.of(2026, 4, 1),
                    LocalDate.of(2026, 4, 30),
                    "CUSTOM",
                    "treasurer",
                    null);

            TransactionEntryService defaultCompany = new TransactionEntryService(jpa, () -> "DEFAULT");
            TransactionView entered = defaultCompany.enter(command(LocalDate.of(2026, 4, 10)));
            assertEquals(LocalDate.of(2026, 4, 10), entered.date());

            closeService.closeRange(
                    "DEFAULT",
                    LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 5, 31),
                    "CALCULATED",
                    "treasurer",
                    "May close");
            assertThrows(ClosedPeriodRangeException.class, () ->
                    defaultCompany.enter(command(LocalDate.of(2026, 5, 10))));
        }
    }

    private static TransactionCommand command(LocalDate date)
    {
        return new TransactionCommand(
                date,
                null,
                "Test transaction",
                null,
                List.of(
                        new TransactionLineCommand(
                                1L, 1L, null, null, null,
                                new BigDecimal("25.00"), BigDecimal.ZERO, false, null),
                        new TransactionLineCommand(
                                2L, 1L, null, null, null,
                                BigDecimal.ZERO, new BigDecimal("25.00"), false, null)));
    }

    private static void seedReferenceData(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES (1, 'Test', '1', 'ACTIVE')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (1, 1, '1000', 'Cash', 'ASSET', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, normal_balance) VALUES (2, 1, '4000', 'Income', 'INCOME', 'CREDIT')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, code, name, fund_type) VALUES (1, 'OPERATING', 'Operating', 'UNRESTRICTED')")
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }
}
