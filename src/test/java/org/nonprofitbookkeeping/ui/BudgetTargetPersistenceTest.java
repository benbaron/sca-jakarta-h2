package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BudgetTargetPersistenceTest component.
 */
class BudgetTargetPersistenceTest
{
    @Test
    void writeThenRead_roundTripsDeterministically() throws Exception
    {
        StringWriter writer = new StringWriter();
        BudgetTargetPersistence.writeTo(Map.of("PROJ", BigDecimal.valueOf(5), "GEN", new BigDecimal("12.50")), writer);

        Map<String, BigDecimal> read = BudgetTargetPersistence.readFrom(new StringReader(writer.toString()));

        assertEquals(Map.of("GEN", new BigDecimal("12.50"), "PROJ", BigDecimal.valueOf(5)), read);
    }

    @Test
    void readFrom_ignoresMalformedRows() throws Exception
    {
        String content = "GOOD=10.00\nBAD=abc\n=11\n";
        Map<String, BigDecimal> read = BudgetTargetPersistence.readFrom(new StringReader(content));

        assertEquals(Map.of("GOOD", new BigDecimal("10.00")), read);
    }
}
