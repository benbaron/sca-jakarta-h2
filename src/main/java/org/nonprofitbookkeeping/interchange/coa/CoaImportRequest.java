package org.nonprofitbookkeeping.interchange.coa;

import java.nio.file.Path;
import java.util.Map;

/** Immutable import request used for preview and commit revalidation. */
public record CoaImportRequest(
        Path sourceFile,
        CoaImportMode mode,
        String targetChartName,
        String targetChartVersion,
        Map<String, String> codeMappings,
        boolean confirmOpeningBalances)
{
    public CoaImportRequest
    {
        if (sourceFile == null)
        {
            throw new IllegalArgumentException("sourceFile is required");
        }
        if (mode == null)
        {
            throw new IllegalArgumentException("mode is required");
        }
        sourceFile = sourceFile.toAbsolutePath().normalize();
        targetChartName = normalize(targetChartName);
        targetChartVersion = normalize(targetChartVersion);
        codeMappings = codeMappings == null ? Map.of() : Map.copyOf(codeMappings);
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim();
    }
}
