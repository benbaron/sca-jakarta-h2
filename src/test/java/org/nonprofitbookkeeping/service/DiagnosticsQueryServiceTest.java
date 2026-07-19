package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsQueryServiceTest
{
    private static final Instant NOW = Instant.parse("2026-07-18T12:00:00Z");

    @Test
    void queryReturnsTypedFactualCountsAndDuplicateWarnings()
    {
        DiagnosticsQueryService service = new DiagnosticsQueryService(
                () -> { },
                () -> List.of("1000", "2000", "1000"),
                () -> 2,
                () -> List.of("GENERAL", "GENERAL", "BOARD"),
                () -> 2,
                () -> "DEFAULT",
                () -> Path.of("data/test.mv.db"),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "17-test");

        DiagnosticsQueryService.Report report = service.query();

        assertTrue(report.available());
        assertEquals(DiagnosticsQueryService.CheckStatus.OK, report.datasourceStatus());
        assertEquals(DiagnosticsQueryService.CheckStatus.WARNING, report.qualityStatus());
        assertEquals(new DiagnosticsQueryService.Count(2, 3), report.accounts());
        assertEquals(new DiagnosticsQueryService.Count(2, 3), report.funds());
        assertEquals(2, report.duplicateAccountCodes().get("1000"));
        assertEquals(2, report.duplicateFundCodes().get("GENERAL"));
        assertEquals(NOW, report.runtimeTimestamp());
        assertEquals("17-test", report.javaVersion());
        assertEquals("DEFAULT", report.activeCompanyCode());
        assertEquals(Path.of("data/test.mv.db").toAbsolutePath().normalize(), report.activeDatabasePath());
        assertThrows(
                UnsupportedOperationException.class,
                () -> report.duplicateAccountCodes().put("3000", 2));
    }

    @Test
    void failedDatasourceReturnsUnavailableResultWithoutQueryingDomainData()
    {
        DiagnosticsQueryService service = new DiagnosticsQueryService(
                () -> { throw new IllegalStateException("database unavailable"); },
                () -> { throw new AssertionError("accounts must not be queried"); },
                () -> { throw new AssertionError("account count must not be queried"); },
                () -> { throw new AssertionError("funds must not be queried"); },
                () -> { throw new AssertionError("fund count must not be queried"); },
                () -> "DEFAULT",
                () -> Path.of("broken.mv.db"),
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> "17-test");

        DiagnosticsQueryService.Report report = service.query();

        assertFalse(report.available());
        assertEquals(DiagnosticsQueryService.CheckStatus.FAILED, report.datasourceStatus());
        assertEquals(DiagnosticsQueryService.CheckStatus.FAILED, report.qualityStatus());
        assertEquals(new DiagnosticsQueryService.Count(0, 0), report.accounts());
        assertEquals(new DiagnosticsQueryService.Count(0, 0), report.funds());
        assertEquals("database unavailable", report.failureMessage());
    }
}
