package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.NmrMovementKind;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;

class NmrSettlementTest
{
    @TempDir Path directory;

    @Test
    void classifiedOpeningCollectionsMerchantAndBranchReconcileAcrossPeriodsAndDatedReversals()
    {
        Path database = directory.resolve("classified");
        try (Jpa jpa = new Jpa(database))
        {
            var fixture = NmrSettlementTestFixture.seed(jpa); var history = fixture.history(jpa, true, true);
            var query = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            var period = query.queryNmrPeriod(DATE, DATE.plusDays(2));
            assertTrue(period.ready(), () -> period.toString());
            amount("40", sum(period, "opening")); amount("0", sum(period, "closing"));
            amount("100", classified(period, NmrMovementKind.COLLECTION));
            amount("30", classified(period, NmrMovementKind.MERCHANT_DISCHARGE));
            amount("110", classified(period, NmrMovementKind.BRANCH_REMITTANCE));
            assertTrue(period.summaries().stream().allMatch(s -> s.difference().signum() == 0));
            assertEquals("processor/settlement/001", fixture.base().entry(jpa).load(history.merchantTransaction()).supplementalLines().get(0).nmrEvidenceReference());
            try (EntityManager em = jpa.em())
            {
                amount("900", em.createQuery("select sum(s.amountSigned) from TxnSplit s where s.account.id = :account", BigDecimal.class).setParameter("account", fixture.base().income()).getSingleResult());
                amount("20", em.createQuery("select sum(s.amountSigned) from TxnSplit s where s.account.id = :account", BigDecimal.class).setParameter("account", fixture.fee()).getSingleResult());
                assertEquals(0, em.createQuery("select count(p) from NmrPolicyVersion p", Long.class).getSingleResult());
            }
            var execution = new ReportExecutionService(new FinancialReportService(jpa, () -> COMPANY), FinancialReportDisplayFormat.plain(), null, null, query, ReportPresentationMetadata.EMPTY);
            var report = execution.execute(new ReportRequest(ReportDefinition.NMR_PERIOD_MOVEMENTS, DATE, DATE.plusDays(2), ReportFundOption.ALL_FUNDS, 1));
            assertEquals(DATE, report.request().startDate()); assertTrue(report.text().contains("Movement coverage: COMPLETE"));
            assertTrue(report.text().contains("Showing 1 of 2 item summaries"));
            var total = report.tableModel().rows().stream().filter(r -> "PERIOD TOTAL".equals(r.value("record"))).findFirst().orElseThrow();
            amount("110", (BigDecimal)total.value("branch")); amount("30", (BigDecimal)total.value("merchantPayment"));
            var completeReport = execution.execute(new ReportRequest(ReportDefinition.NMR_PERIOD_MOVEMENTS, DATE, DATE.plusDays(2), ReportFundOption.ALL_FUNDS, 400));
            assertTrue(completeReport.csv().contains("processor/settlement/001"));
            assertTrue(completeReport.csv().contains("Synthetic processor"));
            assertThrows(PostingException.class, () -> fixture.base().entry(jpa).enter(new TransactionCommand(DATE.plusDays(2), null, "Duplicate discharge", null,
                    List.of(fixture.line(fixture.base().liability(), "1", "0", fixture.merchant()), fixture.line(fixture.base().cash(), "0", "1", null)),
                    List.of(fixture.nmr(history.currentItem(), "DECREASE", 0, "1", "MERCHANT_DISCHARGE", "duplicate/evidence")))));
            new TransactionCorrectionService(jpa, () -> COMPANY).reverse(history.merchantTransaction(), DATE.plusDays(3), "tester", "Reverse confirmed discharge", false);
            assertEquals(period, query.queryNmrPeriod(DATE, DATE.plusDays(2)), "Later inverse must not alter the earlier cutoff");
            var corrected = query.queryNmrPeriod(DATE.plusDays(3), DATE.plusDays(3));
            assertTrue(corrected.ready()); amount("0", sum(corrected, "opening")); amount("30", sum(corrected, "closing"));
            amount("-30", classified(corrected, NmrMovementKind.MERCHANT_DISCHARGE));
            assertTrue(corrected.movements().stream().allMatch(NmrPeriodQueryService.Movement::correction));
            assertTrue(corrected.movements().stream().allMatch(m -> "processor/settlement/001".equals(m.evidence())));
        }
        try (Jpa jpa = new Jpa(database))
        {
            var period = new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryNmrPeriod(DATE, DATE.plusDays(3));
            assertTrue(period.ready()); amount("30", sum(period, "closing"));
        }
    }

    @Test
    void unconfirmedWithholdingLeavesReceivableAndLiabilityOpenWithoutInventedDischarge()
    {
        try (Jpa jpa = new Jpa(directory.resolve("withholding")))
        {
            var fixture = NmrSettlementTestFixture.seed(jpa); fixture.history(jpa, false, false);
            var query = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            var period = query.queryNmrPeriod(DATE, DATE.plusDays(2));
            assertTrue(period.ready()); amount("140", sum(period, "closing"));
            amount("0", classified(period, NmrMovementKind.MERCHANT_DISCHARGE));
            amount("30", query.query(SupplementalOpenItemQueryService.Kind.RECEIVABLE, DATE.plusDays(2)).openAmount());
        }
    }

    @Test
    void invalidSettlementFactsAndOverpaymentsRejectAtomicallyWhileUnclassifiedNetZeroIsVisible()
    {
        try (Jpa jpa = new Jpa(directory.resolve("invalid")))
        {
            var fixture = NmrSettlementTestFixture.seed(jpa); var base = fixture.base(); UUID item = UUID.randomUUID();
            base.entry(jpa).enter(base.receipt(item, base.recipient(), base.event()));
            for (var facts : List.of(new String[]{"COLLECTION", "evidence"}, new String[]{"MERCHANT_DISCHARGE", null}, new String[]{"MERCHANT_DISCHARGE", "evidence"},
                    new String[]{"REFUND", "x".repeat(501)}, new String[]{"NOT_A_KIND", "evidence"}))
            {
                var command = new TransactionCommand(DATE.plusDays(1), null, "Invalid attempted payment", null,
                        List.of(fixture.line(base.liability(), "5", "0", null), fixture.line(base.cash(), "0", "5", null)),
                        List.of(fixture.nmr(item, "DECREASE", 0, "5", facts[0], facts[1])));
                assertThrows(PostingException.class, () -> base.entry(jpa).enter(command));
            }
            try (EntityManager em = jpa.em())
            {
                assertEquals(1, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(1, em.createQuery("select count(a) from AuditEvent a where a.actionType = 'TRANSACTION_ENTERED'", Long.class).getSingleResult());
            }
            base.entry(jpa).enter(base.payment(item, "20", base.recipient(), base.event()));
            var period = new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryNmrPeriod(DATE, DATE.plusDays(2));
            assertFalse(period.ready()); amount("0", sum(period, "closing"));
            amount("40", period.summaries().get(0).unclassifiedGross()); amount("0", period.summaries().get(0).unclassified());
            assertTrue(period.closing().ready());
        }
    }

    @Test
    void refundAndBothAdjustmentDirectionsRemainExplicitAndReconcile()
    {
        try (Jpa jpa = new Jpa(directory.resolve("refund-adjustment")))
        {
            var fixture = NmrSettlementTestFixture.seed(jpa); var base = fixture.base(); UUID item = UUID.randomUUID();
            for (var value : List.of(new String[]{"INCREASE", "20", "COLLECTION"}, new String[]{"DECREASE", "5", "REFUND"},
                    new String[]{"INCREASE", "2", "ADJUSTMENT"}, new String[]{"DECREASE", "1", "ADJUSTMENT"}))
            {
                boolean increase = value[0].equals("INCREASE");
                base.entry(jpa).enter(new TransactionCommand(DATE, null, "Explicit " + value[2], null,
                        List.of(fixture.line(base.liability(), increase ? "0" : value[1], increase ? value[1] : "0", null),
                                fixture.line(base.cash(), increase ? value[1] : "0", increase ? "0" : value[1], null)),
                        List.of(fixture.nmr(item, value[0], 0, value[1], value[2], "external/reference"))));
            }
            var period = new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryNmrPeriod(DATE, DATE);
            assertTrue(period.ready()); amount("20", classified(period, NmrMovementKind.COLLECTION));
            amount("5", classified(period, NmrMovementKind.REFUND)); amount("1", classified(period, NmrMovementKind.ADJUSTMENT));
            amount("16", sum(period, "closing"));
        }
    }

    private static BigDecimal classified(NmrPeriodQueryService.Result result, NmrMovementKind kind)
    {
        return result.summaries().stream().map(s -> s.amount(kind)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sum(NmrPeriodQueryService.Result result, String key)
    {
        return result.summaries().stream().map(s -> key.equals("opening") ? s.opening() : s.closing()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void amount(String expected, BigDecimal actual)
    {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
