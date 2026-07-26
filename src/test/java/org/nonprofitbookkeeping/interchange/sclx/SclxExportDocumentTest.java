package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SclxExportDocumentTest
{
    @Test
    void createsGovernedVersion13DocumentWithDefensiveCopies()
    {
        var accounts = new java.util.ArrayList<SclxExportDocument.Account>();
        accounts.add(new SclxExportDocument.Account(
                "acct-1000", "1000", "Cash", "ASSET", "CASH", "DEBIT", null,
                "USD", BigDecimal.ZERO, true, true));

        SclxExportDocument document = SclxExportDocument.version13(
                Instant.parse("2026-07-26T18:00:00Z"),
                new SclxExportDocument.Organization(
                        "company-demo", "DEMO", "Demo Company", "USD", LocalDate.of(2026, 1, 1)),
                accounts,
                List.of(),
                List.of(),
                List.of(),
                new SclxExportDocument.Extensions(1, Map.of()));

        accounts.clear();

        assertEquals("SCLX", document.format());
        assertEquals("1.3", document.version());
        assertEquals(1, document.chartOfAccounts().size());
        assertThrows(UnsupportedOperationException.class, () -> document.chartOfAccounts().clear());
    }

    @Test
    void rejectsWrongWriterVersionAndInvalidPostingLine()
    {
        assertThrows(IllegalArgumentException.class, () -> new SclxExportDocument(
                "SCLX",
                "1.2",
                Instant.EPOCH,
                new SclxExportDocument.Organization(
                        "company-demo", "DEMO", "Demo Company", "USD", LocalDate.of(2026, 1, 1)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new SclxExportDocument.Extensions(1, Map.of())));

        assertThrows(IllegalArgumentException.class, () -> new SclxExportDocument.TransactionLine(
                "line-1", "acct-1000", null, null, null,
                BigDecimal.TEN, BigDecimal.ONE, "invalid"));
    }
}
