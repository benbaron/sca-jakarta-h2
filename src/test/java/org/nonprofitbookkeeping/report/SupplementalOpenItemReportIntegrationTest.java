package org.nonprofitbookkeeping.report;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.FinancialReportService;
import org.nonprofitbookkeeping.service.SupplementalOpenItemQueryService;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplementalOpenItemReportIntegrationTest
{
    @Test
    void allSixSupplementalDefinitionsExecuteThroughSharedReportResultPipeline(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("supplemental-reports")))
        {
            seedReceivable(jpa);
            ReportExecutionService execution = new ReportExecutionService(
                    new FinancialReportService(jpa, () -> "TEST"),
                    FinancialReportDisplayFormat.plain(),
                    null,
                    null,
                    new SupplementalOpenItemQueryService(jpa, () -> "TEST"),
                    ReportPresentationMetadata.EMPTY);

            List<ReportDefinition> definitions = List.of(
                    ReportDefinition.ACCOUNTS_RECEIVABLE,
                    ReportDefinition.ACCOUNTS_PAYABLE,
                    ReportDefinition.PREPAID_EXPENSES,
                    ReportDefinition.DEFERRED_REVENUE,
                    ReportDefinition.OTHER_ASSETS,
                    ReportDefinition.OTHER_LIABILITIES);

            for (ReportDefinition definition : definitions)
            {
                ReportResult result = execution.execute(new ReportRequest(
                        definition,
                        LocalDate.of(2026, 6, 30),
                        LocalDate.of(2026, 6, 30),
                        ReportFundOption.ALL_FUNDS,
                        400));

                assertEquals(definition, result.request().definition());
                assertTrue(result.tabular());
                assertTrue(result.text().contains(definition.displayName()));
                assertTrue(result.csv().startsWith("item_id,transaction_id,date"));
            }

            ReportResult receivables = execution.execute(new ReportRequest(
                    ReportDefinition.ACCOUNTS_RECEIVABLE,
                    LocalDate.of(2026, 6, 30),
                    LocalDate.of(2026, 6, 30),
                    ReportFundOption.ALL_FUNDS,
                    400));
            assertTrue(receivables.text().contains("100.0000"));
            assertEquals(1, receivables.tableModel().rows().size());
        }
    }

    private static void seedReceivable(Jpa jpa)
    {
        UUID itemId = UUID.randomUUID();
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("insert into company (id, code, display_name) values (1, 'TEST', 'Test')")
                    .executeUpdate();
            em.createNativeQuery("insert into chart_of_accounts (id, company_id, name, version, status) values (1,1,'Test','1','ACTIVE')")
                    .executeUpdate();
            em.createNativeQuery("update company set active_chart_of_accounts_id = 1 where id = 1")
                    .executeUpdate();
            em.createNativeQuery("""
                    insert into account (id, chart_id, code, name, account_type, subtype, normal_balance)
                    values
                    (1,1,'1100','Receivable','ASSET','RECEIVABLE','DEBIT'),
                    (2,1,'4000','Income','INCOME',null,'CREDIT')
                    """).executeUpdate();
            em.createNativeQuery("insert into fund (id, company_id, code, name, fund_type) values (1,1,'OPERATING','Operating','UNRESTRICTED')")
                    .executeUpdate();
            em.createNativeQuery("insert into txn (id, company_id, txn_date, memo, status) values (1,1,DATE '2026-01-10','invoice','ENTERED')")
                    .executeUpdate();
            em.createNativeQuery("""
                    insert into txn_split (id, txn_id, account_id, fund_id, amount_signed)
                    values (1,1,1,1,100.0000), (2,1,2,1,-100.0000)
                    """).executeUpdate();
            em.createNativeQuery("""
                    insert into txn_supplemental_line
                        (id, txn_id, line_order, kind, entry_ref, description, amount,
                         item_id, txn_split_id, item_effect)
                    values (1,1,0,'RECEIVABLE','INV-1','Invoice',100.0000,?,?, 'INCREASE')
                    """)
                    .setParameter(1, itemId)
                    .setParameter(2, 1L)
                    .executeUpdate();
            em.getTransaction().commit();
        }
    }
}
