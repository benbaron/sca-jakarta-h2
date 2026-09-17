package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlternateWritableServiceRetirementSourceTest
{
    private static final Path MAIN = Path.of("src/main/java");
    private static final Path SERVICE = MAIN.resolve("org/nonprofitbookkeeping/service");
    private static final Pattern RETIRED_TYPE = Pattern.compile(
            "\\b(?:PostingService|AccountingPeriodService|CoaFundIo)\\b");

    @Test
    void obsoleteAlternateWriterSourcesStayRetired()
    {
        assertFalse(Files.exists(SERVICE.resolve("PostingService.java")));
        assertFalse(Files.exists(SERVICE.resolve("AccountingPeriodService.java")));
        assertFalse(Files.exists(SERVICE.resolve("CoaFundIo.java")));
    }

    @Test
    void productionSourcesDoNotReferenceRetiredWriterTypes() throws IOException
    {
        try (Stream<Path> files = Files.walk(MAIN))
        {
            List<Path> violations = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(AlternateWritableServiceRetirementSourceTest::containsRetiredType)
                    .toList();
            assertTrue(violations.isEmpty(),
                    () -> "Retired alternate writer type referenced by production source: " + violations);
        }
    }

    @Test
    void canonicalWriterAuthoritiesRemainPresent()
    {
        assertTrue(Files.exists(SERVICE.resolve("TransactionEntryService.java")));
        assertTrue(Files.exists(SERVICE.resolve("PeriodCloseRangeService.java")));
        assertTrue(Files.exists(SERVICE.resolve("CoaCsvImportService.java")));
        assertTrue(Files.exists(SERVICE.resolve("AccountAdminService.java")));
        assertTrue(Files.exists(SERVICE.resolve("FundAdminService.java")));
        assertTrue(Files.exists(MAIN.resolve(
                "org/nonprofitbookkeeping/interchange/coa/ChartOfAccountsJsonImportService.java")));
    }

    private static boolean containsRetiredType(Path path)
    {
        try
        {
            return RETIRED_TYPE.matcher(Files.readString(path)).find();
        }
        catch (IOException ex)
        {
            throw new IllegalStateException("Could not inspect production source " + path, ex);
        }
    }
}
