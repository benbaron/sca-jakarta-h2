package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;

class NmrFinancialAcceptanceTest
{
    @TempDir Path directory;

    @Test
    void yearBoundaryOpeningWithholdingDischargeAndBranchPaymentReconcileWithoutRepeatedEntries()
    {
        Path database = directory.resolve("acceptance"); LocalDate start = LocalDate.of(2026, 1, 1); LocalDate end = start.plusDays(3);
        try (Jpa jpa = new Jpa(database))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var n = f.nmr(); var b = n.base(); var entry = b.entry(jpa); UUID opening = UUID.randomUUID();
            entry.enter(new TransactionCommand(start.minusDays(1), null, "Prior-year opening", null,
                    List.of(n.line(b.cash(), "40", "0", null), n.line(b.liability(), "0", "40", null)),
                    List.of(n.nmr(opening, "INCREASE", 1, "40", "COLLECTION", null))));
            var commands = List.of(on(start, f.receipt()), on(start.plusDays(1), f.payout()), on(start.plusDays(2), f.discharge()),
                    new TransactionCommand(end, null, "Branch remits identified old and current obligations", null,
                            List.of(n.line(b.liability(), "110", "0", null), n.line(b.cash(), "0", "110", null)),
                            List.of(n.nmr(opening, "DECREASE", 0, "40", "BRANCH_REMITTANCE", "branch/001"),
                                    n.nmr(f.liabilityItem(), "DECREASE", 0, "70", "BRANCH_REMITTANCE", "branch/001"))));
            for (var command : commands)
            {
                UUID request = UUID.randomUUID(); long id = entry.enter(command, request).id(); assertEquals(id, entry.enter(command, request).id());
            }
            var query = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            amount("140", query.queryNmr(start.plusDays(1)).items().stream().map(i -> i.openBalance()).reduce(BigDecimal.ZERO, BigDecimal::add));
            amount("110", query.queryNmr(start.plusDays(2)).items().stream().map(i -> i.openBalance()).reduce(BigDecimal.ZERO, BigDecimal::add));
            amount("0", query.queryNmr(end).items().stream().map(i -> i.openBalance()).reduce(BigDecimal.ZERO, BigDecimal::add));
            var merchant = query.queryMerchantReceivables(start, start.plusDays(1)).summaries().get(0);
            amount("30", merchant.closing()); amount("30", merchant.latestObservation().amount()); amount("0", merchant.nmr());
            merchant = query.queryMerchantReceivables(start, end).summaries().get(0);
            amount("1000", merchant.gross()); amount("950", merchant.payout()); amount("20", merchant.fee()); amount("30", merchant.nmr()); amount("0", merchant.closing());
            try (EntityManager em = jpa.em())
            {
                assertEquals(5L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(0L, em.createQuery("select count(p) from NmrPolicyVersion p", Long.class).getSingleResult());
                // Independently reconcile canonical splits, not a second posting path.
                for (var expectation : Map.of(b.cash(), "880", b.income(), "900", b.liability(), "0", n.fee(), "20", n.receivable(), "0").entrySet())
                {
                    amount(expectation.getValue(), em.createQuery("select sum(s.amountSigned) from TxnSplit s where s.account.id = :id", BigDecimal.class).setParameter("id", expectation.getKey()).getSingleResult());
                }
            }
        }
        try (Jpa jpa = new Jpa(database))
        {
            var query = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            var execution = new ReportExecutionService(new FinancialReportService(jpa, () -> COMPANY), FinancialReportDisplayFormat.plain(), null, null, query, ReportPresentationMetadata.EMPTY);
            for (var definition : List.of(ReportDefinition.NMR_LIABILITIES, ReportDefinition.NMR_PERIOD_MOVEMENTS, ReportDefinition.MERCHANT_RECEIVABLE_MOVEMENTS))
            {
                var report = execution.execute(new ReportRequest(definition, start, end, ReportFundOption.ALL_FUNDS, 1));
                assertTrue(report.text().contains("COMPLETE"), report::text);
                assertFalse(report.csv().isBlank());
                if (definition == ReportDefinition.NMR_PERIOD_MOVEMENTS)
                {
                    var total = report.tableModel().rows().stream().filter(r -> "PERIOD TOTAL".equals(r.value("record"))).findFirst().orElseThrow();
                    amount("40", (BigDecimal)total.value("opening")); amount("100", (BigDecimal)total.value("collections")); amount("110", (BigDecimal)total.value("branch")); amount("30", (BigDecimal)total.value("merchantPayment")); amount("0", (BigDecimal)total.value("closing"));
                }
            }
        }
    }

    private static TransactionCommand on(LocalDate date, TransactionCommand c)
    {
        return new TransactionCommand(date, c.payeeId(), c.memo(), c.bankAccountId(), c.lines(), c.supplementalLines());
    }

    private static void amount(String expected, BigDecimal actual)
    {
        assertNotNull(actual, expected); assertEquals(0, new BigDecimal(expected).compareTo(actual), expected + " vs " + actual);
    }
}
