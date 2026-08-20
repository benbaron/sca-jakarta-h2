package org.nonprofitbookkeeping.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.report.template.SemanticReportValueSet;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SemanticReportTableModelBuilderTest
{
    private static final FinancialReportDisplayFormat FORMAT = new FinancialReportDisplayFormat()
    {
        @Override
        public String formatDate(LocalDate value)
        {
            return "DATE[" + value + "]";
        }

        @Override
        public String formatMoney(BigDecimal value)
        {
            return "MONEY[" + value.setScale(2) + "]";
        }
    };

    @Test
    void tableReportPreservesColumnsAndVisibleSemanticFormatting() throws Exception
    {
        JsonNode template = new ObjectMapper().readTree("""
                {
                  "templateId": "TransactionsList",
                  "title": "Transactions",
                  "subtitle": "Selected activity",
                  "type": "tableReport",
                  "tableKey": "rows",
                  "columns": [
                    {"label":"Date","field":"date","format":"date","width":10},
                    {"label":"Amount","field":"amount","format":"currency","width":12},
                    {"label":"Memo","field":"memo","width":24}
                  ]
                }
                """);
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.putTable("rows", List.of(Map.of(
                "date", LocalDate.of(2026, 6, 30),
                "amount", new BigDecimal("123.4"),
                "memo", "A long visible memo")));

        ReportTableModel model =
                SemanticReportTableModelBuilder.build(template, values, FORMAT);

        assertEquals(3, model.columns().size());
        assertEquals(ReportTableModel.ValueFormat.MONEY, model.columns().get(1).format());
        assertEquals("DATE[2026-06-30]", model.rows().get(0).value("date"));
        assertEquals("MONEY[123.40]", model.rows().get(0).value("amount"));
    }

    @Test
    void sectionReportPreservesSectionAndTotalRoles() throws Exception
    {
        JsonNode template = new ObjectMapper().readTree("""
                {
                  "templateId":"Summary",
                  "title":"Summary",
                  "type":"sectionReport",
                  "sections":[{
                    "title":"Status",
                    "rows":[{
                      "type":"totalRow",
                      "label":"Total",
                      "valueKey":"total",
                      "format":"currency"
                    }]
                  }]
                }
                """);
        SemanticReportValueSet values = new SemanticReportValueSet();
        values.put("total", BigDecimal.ZERO);

        ReportTableModel model =
                SemanticReportTableModelBuilder.build(template, values, FORMAT);

        assertEquals(ReportTableModel.RowStyle.SECTION, model.rows().get(0).style());
        assertEquals(ReportTableModel.RowStyle.TOTAL, model.rows().get(1).style());
        assertEquals("-", model.rows().get(1).value("amount"));
    }
}
