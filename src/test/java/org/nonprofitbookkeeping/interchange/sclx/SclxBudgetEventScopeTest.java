package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SclxBudgetEventScopeTest
{
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parserRetainsOptionalEventIdentityAndSeparatesEventScopes() throws Exception
    {
        var parsed = SclxBudgetImportData.parse(mapper.readTree("""
                [{
                  "budgetId":"budget:TEST:2026:ACTIVE",
                  "name":"FY 2026",
                  "fiscalYear":2026,
                  "version":"ACTIVE",
                  "active":true,
                  "lines":[
                    {"lineId":"line-a","fundId":"fund:TEST:OPERATING","activityId":"activity:TEST:EVENT-A","categoryCode":"PROGRAM","amount":"10.0000"},
                    {"lineId":"line-b","fundId":"fund:TEST:OPERATING","activityId":"activity:TEST:EVENT-B","categoryCode":"PROGRAM","amount":"20.0000"},
                    {"lineId":"line-general","categoryCode":"PROGRAM","amount":"30.0000"}
                  ]
                }]
                """));

        assertEquals(3, parsed.budgets().get(0).lines().size());
        assertEquals("activity:TEST:EVENT-A", parsed.budgets().get(0).lines().stream()
                .filter(line -> line.externalId().equals("line-a")).findFirst().orElseThrow().activityId());
        assertEquals(null, parsed.budgets().get(0).lines().stream()
                .filter(line -> line.externalId().equals("line-general")).findFirst().orElseThrow().activityId());
    }

    @Test
    void parserRejectsDuplicateCategoryFundEventPeriodScope() throws Exception
    {
        var json = mapper.readTree("""
                [{
                  "budgetId":"budget:TEST:2026:ACTIVE",
                  "name":"FY 2026",
                  "fiscalYear":2026,
                  "version":"ACTIVE",
                  "active":true,
                  "lines":[
                    {"lineId":"line-a","activityId":"activity:TEST:EVENT-A","categoryCode":"PROGRAM","amount":"10.0000"},
                    {"lineId":"line-b","activityId":"activity:TEST:EVENT-A","categoryCode":"PROGRAM","amount":"20.0000"}
                  ]
                }]
                """);

        assertThrows(IllegalStateException.class, () -> SclxBudgetImportData.parse(json));
    }
}
