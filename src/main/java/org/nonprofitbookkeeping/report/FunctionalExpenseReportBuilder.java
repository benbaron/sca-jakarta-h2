package org.nonprofitbookkeeping.report;

import org.nonprofitbookkeeping.model.ExpenseFunction;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;
import org.nonprofitbookkeeping.service.FunctionalExpenseQueryService;
import java.util.*;

/** Shared typed/text/CSV presentation with untruncated canonical expense controls. */
public final class FunctionalExpenseReportBuilder
{
    private FunctionalExpenseReportBuilder()
    {
    }

    public static ReportResult result(ReportRequest request, FunctionalExpenseQueryService.Result result, FinancialReportDisplayFormat format)
    {
        List<ReportTableModel.Column> columns = new ArrayList<>();
        for (String key : List.of("record", "function", "transaction", "split", "account", "fund", "event", "memo"))
        {
            columns.add(new ReportTableModel.Column(key, key, ReportTableModel.ValueFormat.TEXT, key.equals("memo") ? 240 : 155));
        }
        columns.add(new ReportTableModel.Column("date", "Transaction date", ReportTableModel.ValueFormat.DATE, 130));
        columns.add(new ReportTableModel.Column("net", "Net expense (DR − CR)", ReportTableModel.ValueFormat.MONEY, 190));
        columns.add(new ReportTableModel.Column("unknownGross", "Unclassified gross", ReportTableModel.ValueFormat.MONEY, 190));
        columns.add(new ReportTableModel.Column("status", "Status / explanation", ReportTableModel.ValueFormat.TEXT, 420));
        List<ReportTableModel.Row> rows = new ArrayList<>();
        for (ExpenseFunction function : ExpenseFunction.values())
        {
            rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.TOTAL, Map.of("record", "FUNCTION TOTAL", "function", function.label(), "net", result.totals().get(function))));
        }
        rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.TOTAL, Map.of("record", "FUNCTION TOTAL", "function", "Unclassified",
                "net", result.unclassified(), "unknownGross", result.unknownGross(), "status", "No account/Event/Budget default is inferred.")));
        rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.TOTAL, Map.of("record", "GL CONTROL", "net", result.gl(),
                "status", "All canonical expense activity in this scope; AR + OA + FR + Unclassified. Opening balances excluded.")));
        for (var detail : result.details().stream().limit(request.rowLimit()).toList())
        {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("record", "DETAIL"); values.put("transaction", detail.transactionId()); values.put("split", detail.splitId());
            values.put("date", detail.date()); values.put("account", detail.accountCode() + " — " + detail.accountName());
            values.put("fund", detail.fund()); values.put("event", detail.event()); values.put("memo", detail.memo());
            values.put("function", detail.function() == null ? "Unclassified" : detail.function().label()); values.put("net", detail.net());
            values.put("status", detail.correction() ? "Dated reversal; source function retained" : detail.function() == null
                    ? "Unclassified — review expense purpose in Journal; no default inferred" : "Explicit expense purpose");
            if (detail.function() == null)
            {
                values.put("unknownGross", detail.net().abs());
            }
            rows.add(new ReportTableModel.Row(detail.function() == null ? ReportTableModel.RowStyle.STATUS_WARNING : ReportTableModel.RowStyle.DETAIL, values));
        }
        rows.add(new ReportTableModel.Row(ReportTableModel.RowStyle.STATUS_WARNING, Map.of("record", "COVERAGE", "status",
                "Showing " + Math.min(request.rowLimit(), result.details().size()) + " of " + result.details().size()
                        + " expense lines. Totals/control include all lines; net-zero unknowns do not imply complete classification. Transaction-date GL basis; no official form mapping is inferred.")));
        var table = new ReportTableModel(request.definition().id(), request.definition().displayName(),
                format.formatDate(result.start()) + " through " + format.formatDate(result.end())
                        + " — " + request.fund().displayLabel() + " — " + request.domainFilter().summary()
                        + (result.ready() ? " — Coverage: COMPLETE" : " — Coverage: INCOMPLETE"), columns, rows);
        return NmrLiabilityReportBuilder.render(request, table, format);
    }
}
