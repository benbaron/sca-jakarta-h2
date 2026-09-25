package org.nonprofitbookkeeping.report;

import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.SupplementalOpenItemQueryService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds text/CSV/table presentations from the shared canonical supplemental projection. */
public final class SupplementalOpenItemReportBuilder
{
    private SupplementalOpenItemReportBuilder()
    {
    }

    public static ReportResult result(
            ReportRequest request,
            SupplementalOpenItemQueryService.Result projection,
            FinancialReportDisplayFormat format)
    {
        List<SupplementalOpenItemQueryService.Row> rows = projection.rows().stream()
                .limit(request.rowLimit())
                .toList();
        return new ReportResult(
                request,
                text(projection, rows, format),
                csv(rows),
                null,
                null,
                table(request, projection, rows));
    }

    private static String text(
            SupplementalOpenItemQueryService.Result projection,
            List<SupplementalOpenItemQueryService.Row> rows,
            FinancialReportDisplayFormat format)
    {
        StringBuilder out = new StringBuilder();
        out.append(projection.kind().displayName()).append(" as of ")
                .append(format.formatDate(projection.asOfDate())).append(System.lineSeparator());
        out.append("Item | Date | Reference | Description | Increases | Reductions | Open | Status")
                .append(System.lineSeparator());
        for (SupplementalOpenItemQueryService.Row row : rows)
        {
            out.append(row.itemId() == null ? "" : row.itemId()).append(" | ")
                    .append(format.formatDate(row.openingDate())).append(" | ")
                    .append(safe(row.entryRef())).append(" | ")
                    .append(safe(row.description())).append(" | ")
                    .append(format.formatMoney(row.increases())).append(" | ")
                    .append(format.formatMoney(row.reductions())).append(" | ")
                    .append(format.formatMoney(row.openBalance())).append(" | ")
                    .append(row.status());
            if (!safe(row.explanation()).isBlank())
            {
                out.append(" — ").append(row.explanation());
            }
            out.append(System.lineSeparator());
        }
        return out.toString();
    }

    private static String csv(List<SupplementalOpenItemQueryService.Row> rows)
    {
        StringBuilder out = new StringBuilder("item_id,transaction_id,date,entry_ref,counterparty,description,reference,increases,reductions,open_balance,due_date,start_date,end_date,status,notes,explanation\n");
        for (SupplementalOpenItemQueryService.Row row : rows)
        {
            List<String> values = List.of(
                    row.itemId() == null ? "" : row.itemId().toString(),
                    Long.toString(row.openingTransactionId()),
                    row.openingDate() == null ? "" : row.openingDate().toString(),
                    safe(row.entryRef()), safe(row.counterparty()), safe(row.description()), safe(row.reference()),
                    row.increases().toPlainString(), row.reductions().toPlainString(), row.openBalance().toPlainString(),
                    row.dueDate() == null ? "" : row.dueDate().toString(),
                    row.startDate() == null ? "" : row.startDate().toString(),
                    row.endDate() == null ? "" : row.endDate().toString(),
                    safe(row.status()), safe(row.notes()), safe(row.explanation()));
            out.append(values.stream().map(SupplementalOpenItemReportBuilder::csvValue)
                    .collect(java.util.stream.Collectors.joining(","))).append('\n');
        }
        return out.toString();
    }

    private static ReportTableModel table(
            ReportRequest request,
            SupplementalOpenItemQueryService.Result projection,
            List<SupplementalOpenItemQueryService.Row> rows)
    {
        List<ReportTableModel.Column> columns = List.of(
                new ReportTableModel.Column("item", "Item ID", ReportTableModel.ValueFormat.TEXT, 210),
                new ReportTableModel.Column("date", "Opening Date", ReportTableModel.ValueFormat.DATE, 115),
                new ReportTableModel.Column("entryRef", "Reference", ReportTableModel.ValueFormat.TEXT, 140),
                new ReportTableModel.Column("counterparty", "Counterparty", ReportTableModel.ValueFormat.TEXT, 160),
                new ReportTableModel.Column("description", "Description", ReportTableModel.ValueFormat.TEXT, 220),
                new ReportTableModel.Column("increases", "Increases", ReportTableModel.ValueFormat.MONEY, 110),
                new ReportTableModel.Column("reductions", "Reductions", ReportTableModel.ValueFormat.MONEY, 110),
                new ReportTableModel.Column("open", "Open Balance", ReportTableModel.ValueFormat.MONEY, 120),
                new ReportTableModel.Column("due", "Due / Start", ReportTableModel.ValueFormat.DATE, 115),
                new ReportTableModel.Column("end", "End", ReportTableModel.ValueFormat.DATE, 115),
                new ReportTableModel.Column("status", "Status", ReportTableModel.ValueFormat.TEXT, 130),
                new ReportTableModel.Column("notes", "Notes / Explanation", ReportTableModel.ValueFormat.TEXT, 260));
        List<ReportTableModel.Row> tableRows = new ArrayList<>();
        for (SupplementalOpenItemQueryService.Row row : rows)
        {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("item", row.itemId() == null ? "" : row.itemId().toString());
            values.put("date", row.openingDate());
            values.put("entryRef", safe(row.entryRef()));
            values.put("counterparty", safe(row.counterparty()));
            values.put("description", safe(row.description()));
            values.put("increases", row.increases());
            values.put("reductions", row.reductions());
            values.put("open", row.openBalance());
            values.put("due", row.dueDate() == null ? row.startDate() : row.dueDate());
            values.put("end", row.endDate());
            values.put("status", row.status());
            String notes = safe(row.notes());
            if (!safe(row.explanation()).isBlank())
            {
                notes = notes.isBlank() ? row.explanation() : notes + " — " + row.explanation();
            }
            values.put("notes", notes);
            ReportTableModel.RowStyle style = switch (row.status())
            {
                case "OPEN", "CLOSED" -> ReportTableModel.RowStyle.DETAIL;
                default -> ReportTableModel.RowStyle.STATUS_WARNING;
            };
            tableRows.add(new ReportTableModel.Row(style, values));
        }
        return new ReportTableModel(
                request.definition().id(),
                projection.kind().displayName(),
                "As of " + request.asOfDate(),
                columns,
                tableRows);
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }

    private static String csvValue(String value)
    {
        String escaped = safe(value).replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
