package org.nonprofitbookkeeping.report;

import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.MerchantReceivableQueryService;
import org.nonprofitbookkeeping.service.SupplementalOpenItemQueryService;
import java.math.BigDecimal;
import java.util.*;

/** Common typed/text/CSV presentation; observation amounts are never included in financial totals. */
public final class MerchantReceivableReportBuilder
{
    private static final List<String> MONEY = List.of("opening", "gross", "payout", "fee", "nmr", "unexplained", "unknownGross", "corrections", "closing", "difference");

    private MerchantReceivableReportBuilder()
    {
    }

    public static ReportResult result(ReportRequest request, MerchantReceivableQueryService.Result result, FinancialReportDisplayFormat format)
    {
        List<ReportTableModel.Column> columns = new ArrayList<>();
        for (String key : List.of("record", "transaction", "sourceTransaction", "item", "merchant", "account", "fund", "event", "role", "evidence"))
        {
            columns.add(new ReportTableModel.Column(key, key.equals("sourceTransaction") ? "Source transaction" : key,
                    ReportTableModel.ValueFormat.TEXT, key.equals("evidence") ? 230 : 150));
        }
        columns.add(new ReportTableModel.Column("date", "Effective date", ReportTableModel.ValueFormat.DATE, 120));
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("opening", "Opening receivable"); labels.put("gross", "Net gross receipts"); labels.put("payout", "Net payout");
        labels.put("fee", "Net fees"); labels.put("nmr", "Net confirmed NMR discharge"); labels.put("unexplained", "Unexplained change");
        labels.put("unknownGross", "Unexplained gross"); labels.put("corrections", "Correction effects (included)");
        labels.put("closing", "Closing receivable"); labels.put("difference", "Equation difference");
        labels.put("observation", "Observed withholding (not a balance)"); labels.put("change", "Receivable change");
        labels.put("ledger", "Receivable GL"); labels.put("explained", "Allocated GL"); labels.put("unmatched", "Unmatched gross");
        labels.put("glDifference", "GL/detail difference");
        labels.forEach((key, label) -> columns.add(new ReportTableModel.Column(key, label, ReportTableModel.ValueFormat.MONEY, 175)));
        columns.add(new ReportTableModel.Column("observationDate", "Original observation date", ReportTableModel.ValueFormat.DATE, 160));
        columns.add(new ReportTableModel.Column("status", "Status / explanation", ReportTableModel.ValueFormat.TEXT, 480));
        List<ReportTableModel.Row> rows = new ArrayList<>();
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("record", "PERIOD TOTAL");
        MONEY.forEach(key -> totals.put(key, new BigDecimal("0.0000")));
        for (var summary : result.summaries())
        {
            amounts(summary).forEach((key, value) -> totals.merge(key, value, (a, b) -> ((BigDecimal)a).add((BigDecimal)b)));
        }
        totals.put("status", "Opening + gross - payout - fees - confirmed NMR + unexplained = closing. Correction effects already included; withholding observations are excluded.");
        rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.TOTAL, totals));
        for (var summary : result.summaries().stream().limit(request.rowLimit()).toList())
        {
            Map<String, Object> values = new LinkedHashMap<>(amounts(summary));
            values.put("record", "ITEM SUMMARY"); values.put("item", summary.item().itemId().toString());
            values.put("transaction", summary.item().openingTransactionId()); values.put("merchant", summary.merchant());
            values.put("account", summary.account()); values.put("fund", summary.fund()); values.put("event", summary.event());
            if (summary.latestObservation() != null)
            {
                values.put("observation", summary.latestObservation().amount());
                values.put("observationDate", summary.latestObservation().date());
                values.put("sourceTransaction", summary.latestObservation().transactionId());
                values.put("evidence", summary.latestObservation().evidence());
            }
            values.put("status", (summary.unknownGross().signum() == 0 && !summary.merchant().isBlank() ? summary.item().status() : "INCOMPLETE — review explicit allocation/component facts")
                    + "; observation is latest uncancelled recorded fact, not current settlement proof or an outstanding-withholding balance");
            rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.DETAIL, values));
        }
        for (var movement : result.movements().stream().limit(request.rowLimit()).toList())
        {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("record", "MOVEMENT"); values.put("transaction", movement.transactionId()); values.put("sourceTransaction", movement.sourceTransactionId());
            values.put("date", movement.date()); values.put("item", movement.itemId().toString()); values.put("merchant", movement.merchant());
            values.put("account", movement.account()); values.put("fund", movement.fund()); values.put("event", movement.event()); values.put("role", movement.role());
            values.put("gross", movement.gross()); values.put("payout", movement.payout()); values.put("fee", movement.fee()); values.put("nmr", movement.nmr());
            values.put("unexplained", movement.unexplained()); values.put("change", movement.change());
            if (movement.observation() != null)
            {
                values.put("observation", movement.observation());
            }
            values.put("evidence", movement.evidence() == null ? "" : movement.evidence());
            values.put("status", movement.correction() ? "Dated correction; source observation is retained for context, not a new observation" : "Entered allocation; observation (if present) is nonfinancial");
            rows.add(new ReportTableModel.Row(movement.unexplained().signum() == 0 ? ReportTableModel.RowStyle.DETAIL : ReportTableModel.RowStyle.STATUS_WARNING, values));
        }
        coverage(rows, result.opening(), "OPENING");
        coverage(rows, result.closing(), "CLOSING");
        rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.STATUS_WARNING, Map.of("record", "COVERAGE", "status",
                "Showing " + Math.min(request.rowLimit(), result.summaries().size()) + " of " + result.summaries().size() + " summaries and "
                        + Math.min(request.rowLimit(), result.movements().size()) + " of " + result.movements().size()
                        + " movements. Totals/GL controls include all receivables, including unclassified ones. Withholding never reduces receivable or NMR; observations are not summed.")));
        var table = new ReportTableModel(request.definition().id(), request.definition().displayName(),
                format.formatDate(result.start()) + " through " + format.formatDate(result.end()) + (result.ready() ? " — Coverage: COMPLETE" : " — Coverage: INCOMPLETE"), columns, rows);
        return NmrLiabilityReportBuilder.render(request, table, format);
    }

    private static Map<String, BigDecimal> amounts(MerchantReceivableQueryService.Summary s)
    {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("opening", s.opening()); values.put("gross", s.gross()); values.put("payout", s.payout()); values.put("fee", s.fee());
        values.put("nmr", s.nmr()); values.put("unexplained", s.unexplained()); values.put("unknownGross", s.unknownGross());
        values.put("corrections", s.corrections()); values.put("closing", s.closing()); values.put("difference", s.difference());
        return values;
    }

    private static void coverage(List<ReportTableModel.Row> rows, SupplementalOpenItemQueryService.Result result, String label)
    {
        for (var control : result.reconciliation().controls())
        {
            rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.TOTAL, Map.of("record", label + " GL CONTROL", "account", control.code(),
                    "ledger", control.ledger(), "explained", control.explained(), "unmatched", control.unmatched(), "glDifference", control.difference(), "date", result.asOfDate())));
        }
        for (var gap : result.reconciliation().gaps())
        {
            rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.STATUS_WARNING, Map.of("record", label + " ALLOCATION GAP", "transaction", gap.transactionId(),
                    "account", gap.accountCode(), "unmatched", gap.unmatched(), "status", gap.repair(), "date", result.asOfDate())));
        }
        for (var diagnostic : result.rows().stream().filter(r -> !r.authoritative()).toList())
        {
            rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.STATUS_WARNING, Map.of("record", label + " LEGACY", "transaction", diagnostic.openingTransactionId(),
                    "status", diagnostic.explanation(), "date", result.asOfDate())));
        }
    }
}
