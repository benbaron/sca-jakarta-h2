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
        boolean confirmOpeningBalances,
        CoaReviewEdits reviewEdits,
        Long targetChartId)
{
    public CoaImportRequest(Path sourceFile, CoaImportMode mode, String targetChartName,
            String targetChartVersion, Map<String, String> codeMappings, boolean confirmOpeningBalances)
    {
        this(sourceFile, mode, targetChartName, targetChartVersion, codeMappings, confirmOpeningBalances, null, null);
    }

    public CoaImportRequest(Path sourceFile, CoaImportMode mode, String targetChartName,
            String targetChartVersion, Map<String, String> codeMappings, boolean confirmOpeningBalances,
            CoaReviewEdits reviewEdits)
    {
        this(sourceFile, mode, targetChartName, targetChartVersion, codeMappings, confirmOpeningBalances, reviewEdits, null);
    }

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
        if (mode == CoaImportMode.CREATE_NEW_CHART && targetChartId != null)
        {
            throw new IllegalArgumentException("A new-chart import cannot target an existing chart.");
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
