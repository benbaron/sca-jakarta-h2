package org.nonprofitbookkeeping.interchange.sclx;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxFundHierarchyLifecycleTest
{
    private final SclxDocumentParser parser = new SclxDocumentParser();

    @Test
    void structureRejectsActiveFundBeneathInactiveParent()
    {
        SclxParsedDocument document = parser.parse("""
                {
                  "format":"SCLX",
                  "version":"1.3",
                  "funds":[
                    {"fundId":"fund-parent","active":false},
                    {"fundId":"fund-child","parentFundId":"fund-parent","active":true}
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8));

        SclxStructureValidation result = new SclxStructureValidator().validate(document);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(message ->
                message.contains("active fund beneath inactive parent fund fund-parent")),
                result.errors().toString());
    }

    @Test
    void structureRejectsCircularFundHierarchy()
    {
        SclxParsedDocument document = parser.parse("""
                {
                  "format":"SCLX",
                  "version":"1.3",
                  "funds":[
                    {"fundId":"fund-a","parentFundId":"fund-b","active":false},
                    {"fundId":"fund-b","parentFundId":"fund-a","active":false}
                  ]
                }
                """.getBytes(StandardCharsets.UTF_8));

        SclxStructureValidation result = new SclxStructureValidator().validate(document);

        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(message -> message.contains("circular fund hierarchy")),
                result.errors().toString());
    }

    @Test
    void exportRejectsActiveFundBeneathInactiveParent()
    {
        SclxExportDocument document = SclxExportDocument.version13(
                Instant.parse("2026-08-27T00:00:00Z"),
                new SclxExportDocument.Organization(
                        "company:TEST", "TEST", "Test Company", "USD", LocalDate.of(2026, 1, 1)),
                List.of(),
                List.of(
                        new SclxExportDocument.Fund(
                                "fund-parent", "PARENT", "Parent", "UNRESTRICTED", null,
                                false, null, null, null),
                        new SclxExportDocument.Fund(
                                "fund-child", "CHILD", "Child", "UNRESTRICTED", "fund-parent",
                                true, null, null, null)),
                List.of(),
                List.of(),
                new SclxExportDocument.Extensions(1, Map.of()));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SclxExportDocumentValidator().validate(document));
        assertTrue(ex.getMessage().contains("active fund fund-child has inactive parent fund fund-parent"));
    }
}
