package org.nonprofitbookkeeping.ui;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DiagnosticsPanelTest component.
 */
public class DiagnosticsPanelTest
{
    @Test
    public void duplicateCodes_returnsOnlyDuplicates()
    {
        Map<String, Integer> duplicates = DiagnosticsPanel.duplicateCodes(
                Arrays.asList("1000", "2000", "1000", "", null, "F01", "F01", "F02"));

        assertEquals(2, duplicates.size());
        assertEquals(2, duplicates.get("1000"));
        assertEquals(2, duplicates.get("F01"));
        assertTrue(!duplicates.containsKey("2000"));
    }
}
