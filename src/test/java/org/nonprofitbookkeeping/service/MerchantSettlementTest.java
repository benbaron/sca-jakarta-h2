package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.report.*;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.NmrItemTestFixture.*;
import static org.nonprofitbookkeeping.service.MerchantSettlementTestFixture.*;

class MerchantSettlementTest
{
    @TempDir Path directory;

    @Test
    void realPayoutFeeWithholdingAndLaterDischargeReconcileWithoutInventedPaymentsAcrossCutoffsRestartAndReversal()
    {
        Path database = directory.resolve("history");
        try (Jpa jpa = new Jpa(database))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var entry = f.nmr().base().entry(jpa);
            entry.enter(f.receipt()); long payout = entry.enter(f.payout()).id();
            var query = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            var withheld = query.queryMerchantReceivables(DATE, DATE.plusDays(1));
            assertTrue(withheld.ready(), withheld::toString); var s = withheld.summaries().get(0);
            amount("1000", s.gross()); amount("950", s.payout()); amount("20", s.fee()); amount("0", s.nmr());
            amount("30", s.closing()); amount("0", s.difference()); amount("30", s.latestObservation().amount());
            assertEquals(DATE.plusDays(1), s.latestObservation().date());
            amount("100", query.queryNmr(DATE.plusDays(1)).items().get(0).openBalance());
            var loaded = entry.load(payout).supplementalLines().get(0).merchantSettlement();
            assertEquals(Set.copyOf(f.payout().supplementalLines().get(0).merchantSettlement().components()), Set.copyOf(loaded.components()));
            amount("30", loaded.withheldUnconfirmed());
            long discharge = entry.enter(f.discharge()).id();
            var paid = query.queryMerchantReceivables(DATE.plusDays(1), DATE.plusDays(2));
            s = paid.summaries().get(0); amount("1000", s.opening()); amount("0", s.gross()); amount("30", s.nmr()); amount("0", s.closing());
            amount("0", s.latestObservation().amount()); assertTrue(paid.ready());
            assertEquals(withheld, query.queryMerchantReceivables(DATE, DATE.plusDays(1)));
            new TransactionCorrectionService(jpa, () -> COMPANY).reverse(discharge, DATE.plusDays(3), "tester", "Correction", false);
            assertEquals(paid, query.queryMerchantReceivables(DATE.plusDays(1), DATE.plusDays(2)));
            s = query.queryMerchantReceivables(DATE.plusDays(3), DATE.plusDays(3)).summaries().get(0);
            amount("-30", s.nmr()); amount("30", s.closing()); amount("30", s.corrections()); amount("30", s.latestObservation().amount());
            try (EntityManager em = jpa.em())
            {
                assertEquals(0, em.createQuery("select count(p) from NmrPolicyVersion p", Long.class).getSingleResult());
                assertTrue(em.createQuery("select a.afterValue from AuditEvent a where a.actionType = 'TRANSACTION_ENTERED'", String.class)
                        .getResultList().stream().anyMatch(v -> v.contains("withheldObserved=30.0000") && v.contains("PAYOUT:")));
            }
        }
        try (Jpa jpa = new Jpa(database))
        {
            var query = new SupplementalOpenItemQueryService(jpa, () -> COMPANY);
            var result = query.queryMerchantReceivables(DATE, DATE.plusDays(3)); assertTrue(result.ready());
            amount("30", result.summaries().get(0).closing());
            var execution = new ReportExecutionService(new FinancialReportService(jpa, () -> COMPANY), FinancialReportDisplayFormat.plain(), null, null, query, ReportPresentationMetadata.EMPTY);
            var report = execution.execute(new ReportRequest(ReportDefinition.MERCHANT_RECEIVABLE_MOVEMENTS, DATE, DATE.plusDays(3), ReportFundOption.ALL_FUNDS, 1));
            assertTrue(report.text().contains("Coverage: COMPLETE")); assertTrue(report.text().contains("Showing 1 of 1 summaries and 1 of 4 movements"));
            var total = report.tableModel().rows().stream().filter(r -> "PERIOD TOTAL".equals(r.value("record"))).findFirst().orElseThrow();
            amount("1000", (BigDecimal)total.value("gross")); amount("950", (BigDecimal)total.value("payout")); amount("0", (BigDecimal)total.value("nmr"));
            assertNull(total.value("observation"), "Observations must never be summed as money movements");
            var full = execution.execute(new ReportRequest(ReportDefinition.MERCHANT_RECEIVABLE_MOVEMENTS, DATE, DATE.plusDays(3), ReportFundOption.ALL_FUNDS, 400));
            assertTrue(full.csv().contains("processor/withheld/001")); assertTrue(full.csv().contains("Synthetic processor"));
        }
    }

    @Test
    void invalidComponentsObservationsAndDimensionsRollBackCompletelyAndUnknownOffsetsNeverBecomeFees()
    {
        try (Jpa jpa = new Jpa(directory.resolve("invalid")))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var entry = f.nmr().base().entry(jpa); entry.enter(f.receipt());
            var invalid = List.of(facts("GROSS_RECEIPT", "30", "evidence"), facts("BAD", null, null),
                    facts("SETTLEMENT", "30", null), facts("SETTLEMENT", "-1", "evidence"), facts("SETTLEMENT", "1.00001", "evidence"),
                    facts("SETTLEMENT", "30", "x".repeat(501)), facts("SETTLEMENT", null, null, component("PAYOUT", 1)),
                    facts("SETTLEMENT", null, null, component("FEE", 0)), facts("SETTLEMENT", null, null, component("NMR_DISCHARGE", 1)),
                    facts("SETTLEMENT", null, null, component("PAYOUT", 9)), facts("SETTLEMENT", null, null, component("PAYOUT", 0), component("FEE", 0)));
            for (var facts : invalid)
            {
                assertThrows(RuntimeException.class, () -> entry.enter(f.payout(facts)), () -> facts.toString());
            }
            var command = f.payout(); var lines = new ArrayList<>(command.lines());
            lines.set(0, f.nmr().line(f.nmr().base().cash(), "950", "0", null));
            assertThrows(PostingException.class, () -> entry.enter(new TransactionCommand(command.date(), null, command.memo(), null, lines, command.supplementalLines())));
            try (EntityManager em = jpa.em())
            {
                assertEquals(1, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(1, em.createQuery("select count(a) from AuditEvent a where a.actionType = 'TRANSACTION_ENTERED'", Long.class).getSingleResult());
                assertEquals(0L, em.createNativeQuery("select count(*) from txn_supplemental_merchant_component", Long.class).getSingleResult());
            }
            entry.enter(f.payout(facts("SETTLEMENT", "30", "evidence", component("PAYOUT", 0))));
            var result = new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryMerchantReceivables(DATE, DATE.plusDays(1));
            assertFalse(result.ready()); var s = result.summaries().get(0);
            amount("0", s.fee()); amount("-20", s.unexplained()); amount("20", s.unknownGross()); amount("30", s.closing());
        }
    }

    @Test
    void updateReplaceAndDeleteKeepComponentOwnershipAndFourDecimalObservationsAtomic()
    {
        try (Jpa jpa = new Jpa(directory.resolve("lifecycle")))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var entry = f.nmr().base().entry(jpa); long receipt = entry.enter(f.receipt()).id();
            long payout = entry.enter(f.payout()).id();
            var revised = f.payout(facts("SETTLEMENT", "30.1234", "revised/evidence", component("PAYOUT", 0), component("FEE", 1)));
            entry.update(payout, revised); amount("30.1234", entry.load(payout).supplementalLines().get(0).merchantSettlement().withheldUnconfirmed());
            var correction = new TransactionCorrectionService(jpa, () -> COMPANY);
            assertThrows(PostingException.class, () -> correction.delete(receipt, "tester", "Cannot remove consumed source"));
            amount("30", new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryMerchantReceivables(DATE, DATE.plusDays(1)).summaries().get(0).closing());
            correction.delete(payout, "tester", "Remove application");
            try (EntityManager em = jpa.em())
            {
                assertEquals(0L, em.createNativeQuery("select count(*) from txn_supplemental_merchant_component", Long.class).getSingleResult());
            }
            amount("1000", new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryMerchantReceivables(DATE, DATE.plusDays(1)).summaries().get(0).closing());
        }
    }

    @Test
    void unclassifiedReceiptAndPaymentDoNotCancelIntoCompleteCoverage()
    {
        try (Jpa jpa = new Jpa(directory.resolve("unknown")))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var entry = f.nmr().base().entry(jpa); var receipt = f.receipt();
            var details = receipt.supplementalLines().stream().map(c -> withFacts(c, null)).toList();
            entry.enter(new TransactionCommand(receipt.date(), null, receipt.memo(), null, receipt.lines(), details));
            entry.enter(f.payout(null));
            var s = new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryMerchantReceivables(DATE, DATE.plusDays(1));
            assertFalse(s.ready()); amount("1970", s.summaries().get(0).unknownGross()); amount("30", s.summaries().get(0).unexplained());
        }
    }

    @Test
    void multipleItemsRequireDistinctComponentLinesAndTotalsRemainComplete()
    {
        try (Jpa jpa = new Jpa(directory.resolve("multiple")))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var entry = f.nmr().base().entry(jpa);
            UUID second = UUID.randomUUID(); var receipt = f.receipt();
            entry.enter(new TransactionCommand(DATE, null, "Two explicit items", null, receipt.lines(), List.of(
                    withFacts(f.nmr().receivable(f.receivableItem(), "INCREASE", 0, "500"), facts("GROSS_RECEIPT", null, null)),
                    withFacts(f.nmr().receivable(second, "INCREASE", 0, "500"), facts("GROSS_RECEIPT", null, null)), receipt.supplementalLines().get(1))));
            var n = f.nmr(); var lines = List.of(n.line(n.base().cash(), "475", "0", n.merchant()), n.line(n.fee(), "25", "0", n.merchant()),
                    n.line(n.base().cash(), "475", "0", n.merchant()), n.line(n.fee(), "25", "0", n.merchant()), n.line(n.receivable(), "0", "1000", n.merchant()));
            var first = withFacts(n.receivable(f.receivableItem(), "DECREASE", 4, "500"), facts("SETTLEMENT", null, null, component("PAYOUT", 0), component("FEE", 1)));
            var duplicate = withFacts(n.receivable(second, "DECREASE", 4, "500"), facts("SETTLEMENT", null, null, component("PAYOUT", 0), component("FEE", 3)));
            assertThrows(RuntimeException.class, () -> entry.enter(new TransactionCommand(DATE.plusDays(1), null, "Duplicate claim", null, lines, List.of(first, duplicate))));
            var secondAllocation = withFacts(n.receivable(second, "DECREASE", 4, "500"), facts("SETTLEMENT", null, null, component("PAYOUT", 2), component("FEE", 3)));
            entry.enter(new TransactionCommand(DATE.plusDays(1), null, "Split components", null, lines, List.of(first, secondAllocation)));
            var query = new SupplementalOpenItemQueryService(jpa, () -> COMPANY); var result = query.queryMerchantReceivables(DATE, DATE.plusDays(1));
            assertTrue(result.ready()); assertEquals(2, result.summaries().size());
            assertTrue(result.summaries().stream().allMatch(row -> row.closing().signum() == 0));
            var execution = new ReportExecutionService(new FinancialReportService(jpa, () -> COMPANY), FinancialReportDisplayFormat.plain(), null, null, query, ReportPresentationMetadata.EMPTY);
            var report = execution.execute(new ReportRequest(ReportDefinition.MERCHANT_RECEIVABLE_MOVEMENTS, DATE, DATE.plusDays(1), ReportFundOption.ALL_FUNDS, 1));
            var total = report.tableModel().rows().stream().filter(row -> "PERIOD TOTAL".equals(row.value("record"))).findFirst().orElseThrow();
            amount("950", (BigDecimal)total.value("payout")); amount("50", (BigDecimal)total.value("fee")); assertTrue(report.text().contains("Showing 1 of 2 summaries"));
        }
    }

    @Test
    void replacementCopiesFactsAndViewerCannotChangeObservation()
    {
        try (Jpa jpa = new Jpa(directory.resolve("replacement")))
        {
            var f = MerchantSettlementTestFixture.seed(jpa); var entry = f.nmr().base().entry(jpa); entry.enter(f.receipt()); long payout = entry.enter(f.payout()).id();
            var now = java.time.Instant.now();
            var guard = new AuthorizationGuard(jpa, () -> Optional.of(new AuthenticatedUserSession(99L, "viewer", "Viewer", COMPANY,
                    Set.of(ReservedSecurityRole.VIEWER), now, now)));
            assertThrows(AuthorizationException.class, () -> new TransactionEntryService(jpa, () -> COMPANY, guard).update(payout, f.payout()));
            var correction = new TransactionCorrectionService(jpa, () -> COMPANY).reverse(payout, DATE.plusDays(2), "tester", "Replace", true);
            var replacement = entry.load(correction.replacementTransactionId());
            assertEquals(Set.copyOf(f.payout().supplementalLines().get(0).merchantSettlement().components()), Set.copyOf(replacement.supplementalLines().get(0).merchantSettlement().components()));
            amount("30", replacement.supplementalLines().get(0).merchantSettlement().withheldUnconfirmed());
            var result = new SupplementalOpenItemQueryService(jpa, () -> COMPANY).queryMerchantReceivables(DATE, DATE.plusDays(2));
            assertTrue(result.ready()); amount("950", result.summaries().get(0).payout()); amount("30", result.summaries().get(0).closing());
        }
    }

    private static void amount(String expected, BigDecimal actual)
    {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> expected + " != " + actual);
    }
}
