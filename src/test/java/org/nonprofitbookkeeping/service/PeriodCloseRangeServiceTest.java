package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PeriodCloseRangeServiceTest
{
    @Test
    void emptyCloseRangeLookupIsCompanyScoped(@TempDir Path tempDir)
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("empty-close-range")))
        {
            PeriodCloseRangeService service = new PeriodCloseRangeService(jpa);
            assertFalse(service.findClosedRange("DEFAULT", LocalDate.of(2026, 4, 10)).isPresent());
        }
    }
}
