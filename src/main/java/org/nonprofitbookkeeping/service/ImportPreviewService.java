package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImportPreviewService
{
    private final ImportExportOrchestrationService orchestrationService;

    public ImportPreviewService()
    {
        this(new ImportExportOrchestrationService());
    }

    public ImportPreviewService(ImportExportOrchestrationService orchestrationService)
    {
        this.orchestrationService = orchestrationService;
    }

    public CoaPreviewResult previewCoaCsv(Path path)
    {
        ImportExportOrchestrationService.CoaImportResult result = orchestrationService.importChartOfAccountsCsvFile(path);
        List<String> warnings = duplicateWarnings(result.rows());
        return new CoaPreviewResult(path.getFileName().toString(), result.rowCount(), warnings, result.rows());
    }

    public BankPreviewResult previewBankStatement(Path path)
    {
        ImportExportOrchestrationService.BankImportResult result = orchestrationService.importBankDataFile(path);
        return new BankPreviewResult(path.getFileName().toString(), result.format(), result.transactionCount(), result.transactions());
    }

    private static List<String> duplicateWarnings(List<CoaCsvMapper.CoaCsvRow> rows)
    {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CoaCsvMapper.CoaCsvRow row : rows)
        {
            counts.merge(row.code(), 1, Integer::sum);
        }

        List<String> warnings = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet())
        {
            if (e.getValue() > 1)
            {
                warnings.add("Duplicate account code in import file: " + e.getKey() + " (" + e.getValue() + " rows)");
            }
        }
        return warnings;
    }

    public record CoaPreviewResult(String sourceName,
                                   int rowCount,
                                   List<String> warnings,
                                   List<CoaCsvMapper.CoaCsvRow> rows)
    {
    }

    public record BankPreviewResult(String sourceName,
                                    BankingDataFormat format,
                                    int transactionCount,
                                    List<BankTransactionRecord> transactions)
    {
    }
}
