package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CsvParseTest
{
    @Test
    public void parseCsvLine_handlesQuotes() throws Exception
    {
        Method m = CoaFundIo.class.getDeclaredMethod("parseCsvLine", String.class);
        m.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> vals = (List<String>) m.invoke(null, "code,name\n");
        assertEquals(2, vals.size());

        @SuppressWarnings("unchecked")
        List<String> vals2 = (List<String>) m.invoke(null, "\"A\",\"Hello, \"\"world\"\"\"");
        assertEquals("A", vals2.get(0));
        assertEquals("Hello, \"world\"", vals2.get(1));
    }
}