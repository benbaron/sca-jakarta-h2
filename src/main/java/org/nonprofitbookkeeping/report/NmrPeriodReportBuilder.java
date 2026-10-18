package org.nonprofitbookkeeping.report;

import org.nonprofitbookkeeping.model.NmrMovementKind;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.NmrPeriodQueryService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Period presentation with full summary/control totals and explicitly limited detail. */
public final class NmrPeriodReportBuilder
{
    private NmrPeriodReportBuilder()
    {
    }

    public static ReportResult result(ReportRequest request, NmrPeriodQueryService.Result result, FinancialReportDisplayFormat format)
    {
        List<ReportTableModel.Column> columns = new ArrayList<>();
        for (String key : List.of("record", "transaction", "sourceTransaction", "item", "account", "fund", "recipient", "event", "movement", "merchant", "evidence"))
        {
            columns.add(new ReportTableModel.Column(key, switch (key)
            {
                case "sourceTransaction" -> "Source transaction";
                case "transaction" -> "Transaction";
                case "evidence" -> "Evidence reference";
                default -> Character.toUpperCase(key.charAt(0)) + key.substring(1);
            }, ReportTableModel.ValueFormat.TEXT, key.equals("evidence") ? 230 : 150));
        }
        columns.add(new ReportTableModel.Column("date", "Effective date", ReportTableModel.ValueFormat.DATE, 120));
        Map<String, String> moneyColumns = new LinkedHashMap<>();
        moneyColumns.put("opening", "Opening"); moneyColumns.put("collections", "Net collections");
        moneyColumns.put("branch", "Net branch remittance"); moneyColumns.put("merchantPayment", "Net merchant discharge");
        moneyColumns.put("refunds", "Net refunds"); moneyColumns.put("adjustments", "Signed adjustments");
        moneyColumns.put("unclassified", "Unclassified change"); moneyColumns.put("unknownGross", "Unclassified gross");
        moneyColumns.put("corrections", "Correction effects (included)"); moneyColumns.put("closing", "Closing");
        moneyColumns.put("difference", "Equation difference"); moneyColumns.put("change", "Liability change");
        moneyColumns.put("ledger", "Other liability GL"); moneyColumns.put("unclassifiedGl", "Unclassified GL difference");
        moneyColumns.forEach((key, label) -> columns.add(new ReportTableModel.Column(key, label, ReportTableModel.ValueFormat.MONEY, 155)));
        columns.add(new ReportTableModel.Column("status", "Status / explanation", ReportTableModel.ValueFormat.TEXT, 420));
        List<ReportTableModel.Row> rows = new ArrayList<>();
        Map<String, Object> totals = new LinkedHashMap<>(); totals.put("record", "PERIOD TOTAL");
        for (String key : List.of("opening", "collections", "branch", "merchantPayment", "refunds", "adjustments", "unclassified", "unknownGross", "corrections", "closing", "difference"))
        {
            totals.put(key, new BigDecimal("0.0000"));
        }
        for (var summary : result.summaries())
        {
            var values = amounts(summary);
            values.forEach((key, value) -> totals.merge(key, value, (a, b) -> ((BigDecimal)a).add((BigDecimal)b)));
        }
        totals.put("status", "All items; opening + collections - branch - merchant - refunds + adjustments + unclassified = closing. Correction effects are already included.");
        rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.TOTAL, totals));
        for (var summary : result.summaries().stream().limit(request.rowLimit()).toList())
        {
            var item = summary.item(); var dimension = item.nmr();
            Map<String, Object> values = new LinkedHashMap<>(amounts(summary));
            values.put("record", "ITEM SUMMARY"); values.put("item", item.itemId().toString()); values.put("transaction", item.openingTransactionId());
            values.put("account", dimension.accountCode()); values.put("fund", dimension.fundCode());
            values.put("recipient", dimension.recipientName()); values.put("event", dimension.eventCode() + " — " + dimension.eventName());
            values.put("status", summary.unclassifiedGross().signum() == 0 && summary.difference().signum() == 0 ? item.status() : "INCOMPLETE — review unlabeled movements / equation difference");
            rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.DETAIL, values));
        }
        for (var movement : result.movements().stream().limit(request.rowLimit()).toList())
        {
            Map<String, Object> values = new LinkedHashMap<>(); values.put("record", "MOVEMENT");
            values.put("item", movement.itemId().toString()); values.put("transaction", movement.transactionId());
            values.put("sourceTransaction", movement.sourceTransactionId()); values.put("date", movement.date());
            values.put("movement", movement.kind() == null ? "UNCLASSIFIED" : movement.kind().name());
            values.put("change", movement.change()); values.put("merchant", movement.merchantName() == null ? "" : movement.merchantName());
            values.put("evidence", movement.evidence() == null ? "" : movement.evidence());
            values.put("status", movement.correction() ? "Dated correction; reverses/restores source attribution" : "Entered allocation");
            rows.add(new ReportTableModel.Row(movement.kind() == null ? ReportTableModel.RowStyle.STATUS_WARNING : ReportTableModel.RowStyle.DETAIL, values));
        }
        addCoverage(rows, request, result.opening(), "OPENING", format);
        addCoverage(rows, request, result.closing(), "CLOSING", format);
        String disclosure = "Showing " + Math.min(request.rowLimit(), result.summaries().size()) + " of " + result.summaries().size()
                + " item summaries and " + Math.min(request.rowLimit(), result.movements().size()) + " of " + result.movements().size()
                + " movements. Period totals and GL controls include all facts. Net categories include dated reversals. "
                + "Unknown movements are not assumed payments. Withholding alone does not discharge NMR; receivable/payout/fee tracking remains in Journal and receivable reports.";
        rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.STATUS_WARNING, Map.of("record", "COVERAGE", "status", disclosure)));
        var table = new ReportTableModel(request.definition().id(), request.definition().displayName(),
                format.formatDate(result.start()) + " through " + format.formatDate(result.end())
                        + (result.ready() ? " — Movement coverage: COMPLETE" : " — Movement coverage: INCOMPLETE"), columns, rows);
        return NmrLiabilityReportBuilder.render(request, table, format);
    }

    private static Map<String, BigDecimal> amounts(NmrPeriodQueryService.Summary summary)
    {
        Map<String, BigDecimal> result = new LinkedHashMap<>(); result.put("opening", summary.opening());
        result.put("collections", summary.amount(NmrMovementKind.COLLECTION)); result.put("branch", summary.amount(NmrMovementKind.BRANCH_REMITTANCE));
        result.put("merchantPayment", summary.amount(NmrMovementKind.MERCHANT_DISCHARGE)); result.put("refunds", summary.amount(NmrMovementKind.REFUND));
        result.put("adjustments", summary.amount(NmrMovementKind.ADJUSTMENT)); result.put("unclassified", summary.unclassified());
        result.put("unknownGross", summary.unclassifiedGross()); result.put("corrections", summary.corrections());
        result.put("closing", summary.closing()); result.put("difference", summary.difference()); return result;
    }

    private static void addCoverage(List<ReportTableModel.Row> rows, ReportRequest request,
            org.nonprofitbookkeeping.service.NmrLiabilityQueryService.Result projection, String label, FinancialReportDisplayFormat format)
    {
        var coverage = NmrLiabilityReportBuilder.result(request, projection, format).tableModel();
        for (var row : coverage.rows())
        {
            if ("ITEM".equals(row.value("record")))
            {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>(row.values());
            values.put("record", label + " " + row.value("record"));
            if (values.containsKey("nmr"))
            {
                values.put(label.equals("OPENING") ? "opening" : "closing", values.remove("nmr"));
            }
            if (values.containsKey("difference"))
            {
                values.put("unclassifiedGl", values.remove("difference"));
            }
            values.putIfAbsent("date", projection.cutoff());
            rows.add(new ReportTableModel.Row(row.style(), values));
        }
    }
}
