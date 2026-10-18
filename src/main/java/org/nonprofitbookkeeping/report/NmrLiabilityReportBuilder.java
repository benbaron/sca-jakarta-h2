package org.nonprofitbookkeeping.report;

import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.NmrLiabilityQueryService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only NMR detail and conservative GL coverage; exports share the same typed result. */
public final class NmrLiabilityReportBuilder
{
    private NmrLiabilityReportBuilder()
    {
    }

    public static ReportResult result(ReportRequest request, NmrLiabilityQueryService.Result projection,
            FinancialReportDisplayFormat format)
    {
        List<ReportTableModel.Column> columns = List.of(
                column("record", "Record", ReportTableModel.ValueFormat.TEXT, 105),
                column("transaction", "Transaction", ReportTableModel.ValueFormat.TEXT, 105),
                column("item", "Item ID", ReportTableModel.ValueFormat.TEXT, 230),
                column("account", "Liability account", ReportTableModel.ValueFormat.TEXT, 130),
                column("fund", "Fund", ReportTableModel.ValueFormat.TEXT, 120),
                column("recipient", "NMR recipient", ReportTableModel.ValueFormat.TEXT, 190),
                column("event", "Event", ReportTableModel.ValueFormat.TEXT, 190),
                column("date", "Opening date", ReportTableModel.ValueFormat.DATE, 120),
                column("increases", "Net increases to cutoff", ReportTableModel.ValueFormat.MONEY, 140),
                column("reductions", "Net reductions to cutoff", ReportTableModel.ValueFormat.MONEY, 140),
                column("nmr", "NMR outstanding", ReportTableModel.ValueFormat.MONEY, 140),
                column("ledger", "Other liability GL", ReportTableModel.ValueFormat.MONEY, 140),
                column("difference", "Unclassified difference", ReportTableModel.ValueFormat.MONEY, 150),
                column("status", "Status / explanation", ReportTableModel.ValueFormat.TEXT, 370));
        List<ReportTableModel.Row> rows = new ArrayList<>();
        for (var control : projection.controls())
        {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("record", "CONTROL");
            values.put("account", control.accountCode());
            values.put("fund", control.fundCode());
            values.put("nmr", control.nmr());
            values.put("ledger", control.ledger());
            values.put("difference", control.unclassified());
            values.put("status", control.unclassified().signum() == 0 ? "TIED"
                    : "UNCLASSIFIED — review Journal allocations/opening balance; this difference is not inferred NMR.");
            rows.add(new ReportTableModel.Row(control.unclassified().signum() == 0
                    ? ReportTableModel.RowStyle.TOTAL : ReportTableModel.RowStyle.STATUS_WARNING, values));
        }
        for (var item : projection.items().stream().limit(request.rowLimit()).toList())
        {
            var dimension = item.nmr();
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("record", "ITEM");
            values.put("transaction", item.openingTransactionId());
            values.put("item", item.itemId().toString());
            values.put("account", dimension.accountCode());
            values.put("fund", dimension.fundCode());
            values.put("recipient", dimension.recipientName());
            values.put("event", dimension.eventCode() + " — " + dimension.eventName());
            values.put("date", item.openingDate());
            values.put("increases", item.increases());
            values.put("reductions", item.reductions());
            values.put("nmr", item.openBalance());
            values.put("status", item.status() + (item.explanation().isBlank() ? "" : " — " + item.explanation()));
            rows.add(new ReportTableModel.Row(item.authoritative() && item.openBalance().signum() >= 0
                    ? ReportTableModel.RowStyle.DETAIL : ReportTableModel.RowStyle.STATUS_WARNING, values));
        }
        for (var flag : projection.legacyFlags())
        {
            rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.STATUS_WARNING, Map.of(
                    "record", "LEGACY FLAG", "transaction", flag.transactionId(), "date", flag.date(),
                    "account", flag.accountCode(), "fund", flag.fundCode(),
                    "status", "Split #" + flag.splitId() + " has an NMR flag without explicit item detail. Open the transaction in Journal; no NMR amount is inferred.")));
        }
        for (var gap : projection.gaps())
        {
            rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.STATUS_WARNING, Map.of(
                    "record", "GAP", "transaction", gap.transactionId(), "account", gap.accountCode(), "date", gap.date(),
                    "ledger", gap.ledger(), "difference", gap.ledger().subtract(gap.explained()), "status", gap.repair())));
        }
        String status = projection.ready() ? "NMR coverage: COMPLETE" : "NMR coverage: INCOMPLETE — review unclassified balances, flags or allocation gaps";
        String explanation = "Legacy flagged lines without explicit item detail: " + projection.legacyFlaggedLines()
                + ". Supplemental integrity: " + (projection.lifecycleReady() ? "complete" : "NOT READY; inspect Other Liabilities report / Journal")
                + ". Showing " + Math.min(request.rowLimit(), projection.items().size()) + " of " + projection.items().size()
                + " NMR items; controls use all items. Amounts are cumulative to cutoff, including dated reversals. "
                + "Unclassified includes other liabilities; no NMR classification is inferred. No policy record is required.";
        rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.STATUS_WARNING, Map.of("record", "COVERAGE", "status", explanation)));
        var table = new ReportTableModel(request.definition().id(), request.definition().displayName(),
                "As of " + format.formatDate(projection.cutoff()) + " — " + status, columns, rows);
        StringBuilder text = new StringBuilder(table.title()).append('\n').append(table.subtitle()).append('\n');
        text.append(columns.stream().map(ReportTableModel.Column::label).collect(java.util.stream.Collectors.joining(" | "))).append('\n');
        StringBuilder csv = new StringBuilder();
        csv.append(columns.stream().map(c -> quote(c.key())).collect(java.util.stream.Collectors.joining(","))).append('\n');
        csv.append(columns.stream().map(c -> quote(c.key().equals("status") ? table.subtitle() : "")).collect(java.util.stream.Collectors.joining(","))).append('\n');
        for (var row : rows)
        {
            List<String> displayed = new ArrayList<>();
            List<String> raw = new ArrayList<>();
            for (var column : columns)
            {
                Object value = row.values().get(column.key());
                raw.add(quote(value == null ? "" : value.toString()));
                displayed.add(value == null ? "" : value instanceof java.math.BigDecimal amount ? format.formatMoney(amount)
                        : value instanceof java.time.LocalDate date ? format.formatDate(date) : value.toString());
            }
            text.append(String.join(" | ", displayed)).append('\n');
            csv.append(String.join(",", raw)).append('\n');
        }
        return new ReportResult(request, text.toString(), csv.toString(), null, null, table);
    }

    private static ReportTableModel.Column column(String key, String label, ReportTableModel.ValueFormat format, double width)
    {
        return new ReportTableModel.Column(key, label, format, width);
    }

    private static String quote(String text)
    {
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
