package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** H2 regression for P21-S2 canonical Activity/Event Accounting projections. */
class EventAccountingQueryServiceIntegrationTest
{
    @Test
    void activityWorkspaceUsesCanonicalNaturalAmountsConfiguredBankAuthorityAndCompanyScope(
            @TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("event-accounting")))
        {
            seed(jpa);
            EventAccountingQueryService service = new EventAccountingQueryService(jpa, () -> "SCA");

            assertEquals(2, service.listActivities().size());
            assertTrue(service.listActivities().get(0).active());
            assertFalse(service.listActivities().get(1).active());
            assertEquals(2, service.listFunds().size());

            FiscalPeriodRange fiscal = service.fiscalRange(LocalDate.of(2026, 9, 1));
            assertEquals(LocalDate.of(2026, 4, 1), fiscal.fiscalYearStart());
            assertEquals(LocalDate.of(2026, 9, 30), fiscal.periodEnd());

            EventAccountingQueryService.Workspace all = service.workspace(
                    301L,
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    null);

            assertEquals(new BigDecimal("130.0000"), all.summary().income());
            assertEquals(new BigDecimal("30.0000"), all.summary().expenses());
            assertEquals(new BigDecimal("100.0000"), all.summary().net());
            assertEquals(5, all.summary().linkedTransactionCount());
            assertEquals(5, all.summary().linkedSplitCount());

            // Only configured ASSET/BANK/DEBIT rows qualify. The unconfigured
            // bank-like account in transaction 504 is deliberately excluded.
            assertEquals(4, all.relatedBankRows().size());
            assertEquals(new BigDecimal("140.0000"), all.summary().relatedBankInflows());
            assertEquals(new BigDecimal("50.0000"), all.summary().relatedBankOutflows());
            assertEquals(1, all.summary().unclearedRelatedBankRowCount());
            assertTrue(all.relatedBankRows().stream().noneMatch(row -> row.transactionId() == 504L));

            // Negative income is preserved as a correction/reversal rather than
            // discarded by a positive-only aggregation.
            assertTrue(all.linkedRows().stream().anyMatch(row ->
                    row.transactionId() == 503L
                            && row.naturalAmount().compareTo(new BigDecimal("-20.0000")) == 0));

            EventAccountingQueryService.Workspace unrestricted = service.workspace(
                    301L,
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    201L);
            assertEquals(new BigDecimal("90.0000"), unrestricted.summary().income());
            assertEquals(new BigDecimal("30.0000"), unrestricted.summary().expenses());
            assertEquals(4, unrestricted.summary().linkedTransactionCount());
            assertEquals(3, unrestricted.relatedBankRows().size());

            assertThrows(IllegalArgumentException.class, () -> service.workspace(
                    303L,
                    LocalDate.of(2026, 3, 1),
                    LocalDate.of(2026, 3, 31),
                    null));
            assertThrows(IllegalArgumentException.class, () -> service.workspace(
                    301L,
                    LocalDate.of(2026, 3, 31),
                    LocalDate.of(2026, 3, 1),
                    null));
        }
    }

    private static void seed(Jpa jpa)
    {
        try (var em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) "
                    + "VALUES (101, 'SCA Chart', '1', 'ACTIVE')").executeUpdate();
            em.createNativeQuery("INSERT INTO company "
                    + "(id, code, display_name, fiscal_year_start_month, fiscal_year_start_day, active_chart_of_accounts_id) "
                    + "VALUES (201, 'SCA', 'SCA Branch', 4, 1, 101)").executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = 201 WHERE id = 101").executeUpdate();
            em.createNativeQuery("INSERT INTO company (id, code, display_name) "
                    + "VALUES (202, 'OTHER', 'Other Branch')").executeUpdate();

            em.createNativeQuery("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                    + "VALUES (101, 101, '1000', 'Checking', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, normal_balance) "
                    + "VALUES (102, 101, '4000', 'Event Income', 'INCOME', 'CREDIT')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, normal_balance) "
                    + "VALUES (103, 101, '5000', 'Event Expense', 'EXPENSE', 'DEBIT')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account "
                    + "(id, chart_id, code, name, account_type, account_function, subtype, normal_balance) "
                    + "VALUES (104, 101, '1010', 'Unconfigured Bank Asset', 'ASSET', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();

            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) "
                    + "VALUES (201, 201, 'UNR', 'Unrestricted', 'UNRESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO fund (id, company_id, code, name, fund_type) "
                    + "VALUES (202, 201, 'RES', 'Restricted', 'TEMP_RESTRICTED')").executeUpdate();
            em.createNativeQuery("INSERT INTO activity (id, company_id, code, name, is_active) "
                    + "VALUES (301, 201, 'EVENT', 'Event', TRUE)").executeUpdate();
            em.createNativeQuery("INSERT INTO activity (id, company_id, code, name, is_active) "
                    + "VALUES (302, 201, 'OLD', 'Old Event', FALSE)").executeUpdate();
            em.createNativeQuery("INSERT INTO activity (id, company_id, code, name, is_active) "
                    + "VALUES (303, 202, 'FOREIGN', 'Foreign Event', TRUE)").executeUpdate();

            em.createNativeQuery("INSERT INTO bank (id, company_id, name) VALUES (401, 201, 'Example Bank')")
                    .executeUpdate();
            em.createNativeQuery("""
                    INSERT INTO company_bank_account
                        (id, company_id, name, bank_id, account_id, statement_import_format, is_active)
                    VALUES (401, 201, 'Operating Checking', 401, 101, 'CSV', TRUE)
                    """).executeUpdate();

            txn(em, 501, 201, "2026-03-10", "Event receipt");
            split(em, 601, 501, 101, 201, null, "100.0000", true, "2026-03-11");
            split(em, 602, 501, 102, 201, 301L, "100.0000", false, null);

            txn(em, 502, 201, "2026-03-12", "Event expense");
            split(em, 603, 502, 101, 201, null, "-30.0000", false, null);
            split(em, 604, 502, 103, 201, 301L, "30.0000", false, null);

            txn(em, 503, 201, "2026-03-13", "Receipt correction");
            split(em, 605, 503, 101, 201, null, "-20.0000", true, "2026-03-14");
            split(em, 606, 503, 102, 201, 301L, "-20.0000", false, null);

            txn(em, 504, 201, "2026-03-14", "Unconfigured bank-like account");
            split(em, 607, 504, 104, 201, null, "10.0000", false, null);
            split(em, 608, 504, 102, 201, 301L, "10.0000", false, null);

            txn(em, 505, 201, "2026-03-15", "Restricted event receipt");
            split(em, 609, 505, 101, 202, null, "40.0000", true, "2026-03-16");
            split(em, 610, 505, 102, 202, 301L, "40.0000", false, null);

            txn(em, 506, 201, "2026-03-16", "Other event");
            split(em, 611, 506, 101, 201, null, "50.0000", true, "2026-03-17");
            split(em, 612, 506, 102, 201, 302L, "50.0000", false, null);

            em.getTransaction().commit();
        }
    }

    private static void txn(jakarta.persistence.EntityManager em, long id, long companyId, String date, String memo)
    {
        em.createNativeQuery("INSERT INTO txn (id, company_id, txn_date, memo, status) "
                        + "VALUES (:id, :companyId, DATE '" + date + "', :memo, 'ENTERED')")
                .setParameter("id", id)
                .setParameter("companyId", companyId)
                .setParameter("memo", memo)
                .executeUpdate();
    }

    private static void split(
            jakarta.persistence.EntityManager em,
            long id,
            long txnId,
            long accountId,
            long fundId,
            Long activityId,
            String amount,
            boolean cleared,
            String clearedOn)
    {
        String sql = "INSERT INTO txn_split "
                + "(id, txn_id, account_id, fund_id, activity_id, amount_signed, bank_cleared, bank_cleared_on) "
                + "VALUES (:id, :txnId, :accountId, :fundId, :activityId, :amount, :cleared, "
                + (clearedOn == null ? "NULL" : "DATE '" + clearedOn + "'") + ")";
        em.createNativeQuery(sql)
                .setParameter("id", id)
                .setParameter("txnId", txnId)
                .setParameter("accountId", accountId)
                .setParameter("fundId", fundId)
                .setParameter("activityId", activityId)
                .setParameter("amount", new BigDecimal(amount))
                .setParameter("cleared", cleared)
                .executeUpdate();
    }
}
