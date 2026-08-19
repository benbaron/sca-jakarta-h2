package org.nonprofitbookkeeping.report;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.service.CompanyView;
import org.nonprofitbookkeeping.service.FinancialReportDisplayFormat;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReportPresentationMetadataTest
{
    @Test
    void headingsComeOnlyFromCompanyMetadata()
    {
        CompanyView company = new CompanyView(
                7L,
                "LOCAL",
                "Configured Local Group",
                "Configured Legal Entity",
                "Barony",
                "Configured Parent Organization",
                true,
                7,
                1,
                "CAD");

        ReportPresentationMetadata metadata = ReportPresentationMetadata.from(company);

        assertEquals("Configured Parent Organization", metadata.organizationHeading());
        assertEquals("Configured Local Group", metadata.companyHeading());
        assertEquals("Configured Legal Entity EXCHEQUER REPORT",
                metadata.exchequerReportHeading());
        assertFalse(metadata.exchequerReportHeading().contains("Creative Anachronism"));
        assertEquals("Q2 Report — 2026-07-01 to 2026-12-31",
                metadata.periodHeading(
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 12, 31),
                        FinancialReportDisplayFormat.plain()));
    }
}
