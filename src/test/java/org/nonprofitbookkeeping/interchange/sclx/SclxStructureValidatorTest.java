package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxStructureValidatorTest
{
    private final SclxDocumentParser parser = new SclxDocumentParser();
    private final SclxStructureValidator validator = new SclxStructureValidator();

    @Test
    void validatesCoreCountsAndReferences()
    {
        SclxParsedDocument document = parser.parse("""
                {
                  "format":"SCLX",
                  "version":"1.3",
                  "chartOfAccounts":[{"accountId":"acct-cash"},{"accountId":"acct-expense"}],
                  "funds":[{"fundId":"fund-general"}],
                  "budgets":[{"budgetId":"budget-1","fundId":"fund-general","lines":[{"accountId":"acct-expense"}]}],
                  "transactions":[{"transactionId":"txn-1","lines":[
                    {"lineId":"line-1","accountId":"acct-expense","fundId":"fund-general","budgetId":"budget-1"},
                    {"lineId":"line-2","accountId":"acct-cash","fundId":"fund-general"}
                  ]}]
                }
                """.getBytes(StandardCharsets.UTF_8));

        SclxStructureValidation result = validator.validate(document);

        assertTrue(result.valid(), result.errors().toString());
        assertEquals(2L, result.counts().accounts());
        assertEquals(1L, result.counts().funds());
        assertEquals(1L, result.counts().budgets());
        assertEquals(1L, result.counts().transactions());
        assertEquals(2L, result.counts().transactionLines());
        assertEquals(7L, result.counts().totalEntities());
    }

    @Test
    void rejectsDuplicateIdentitiesAndMissingReferences()
    {
        SclxParsedDocument document = parser.parse("""
                {
                  "format":"SCLX",
                  "version":"1.2",
                  "chartOfAccounts":[{"accountId":"acct-1"},{"accountId":"acct-1"}],
                  "transactions":[{"transactionId":"txn-1","lines":[
                    {"lineId":"line-1","accountId":"missing"},
                    {"lineId":"line-1","accountId":"acct-1"}
                  ]}]
                }
                """.getBytes(StandardCharsets.UTF_8));

        SclxStructureValidation result = validator.validate(document);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(message -> message.contains("duplicates portable identity acct-1")));
        assertTrue(result.errors().stream().anyMatch(message -> message.contains("does not resolve: missing")));
        assertTrue(result.errors().stream().anyMatch(message -> message.contains("duplicates portable identity line-1")));
    }

    @Test
    void warnsForUnknownBoundedRootField()
    {
        SclxParsedDocument document = parser.parse("""
                {"format":"SCLX","version":"1.0","futureSection":[]}
                """.getBytes(StandardCharsets.UTF_8));

        SclxStructureValidation result = validator.validate(document);

        assertTrue(result.valid());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("futureSection"));
    }
}
