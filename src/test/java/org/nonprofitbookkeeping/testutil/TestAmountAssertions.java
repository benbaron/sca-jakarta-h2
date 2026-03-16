package org.nonprofitbookkeeping.testutil;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TestAmountAssertions component.
 */
public final class TestAmountAssertions
{
    private TestAmountAssertions()
    {
    }

    public static void assertAmountEquals(String expected, BigDecimal actual)
    {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
