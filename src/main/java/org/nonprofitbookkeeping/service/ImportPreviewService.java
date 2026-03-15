package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ImportPreviewService
{
    private final ImportExportOrchestrationService orchestrationService;
    private final CoaCsvMapper coaCsvMapper;

    public ImportPreviewService()
    {
        this(new ImportExportOrchestrationService(), new CoaCsvMapper());
    }

    public ImportPreviewService(ImportExportOrchestrationService orchestrationService)
    {
        this(orchestrationService, new CoaCsvMapper());
    }

    public ImportPreviewService(ImportExportOrchestrationService orchestrationService, CoaCsvMapper coaCsvMapper)
    {
        this.orchestrationService = orchestrationService;
        this.coaCsvMapper = coaCsvMapper;
    }

    public CoaPreviewResult previewCoaCsv(Path path)
    {
        if (path == null)
        {
            throw new IllegalArgumentException("Cannot preview COA CSV: file path is required.");
        }

        String csv;
        try
        {
            csv = Files.readString(path);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Cannot preview COA CSV: failed reading file -> " + path, ex);
        }

        if (csv.isBlank())
        {
            return new CoaPreviewResult(path.getFileName().toString(), 0, 0, 0, List.of(), List.of(), List.of());
        }

        List<LogicalCsvRow> rows = splitLogicalRows(csv);
        if (rows.isEmpty())
        {
            return new CoaPreviewResult(path.getFileName().toString(), 0, 0, 0, List.of(), List.of(), List.of());
        }

        LogicalCsvRow header = rows.get(0);
        if (header.unterminated())
        {
            throw new IllegalArgumentException("Cannot preview COA CSV: header contains an unterminated quoted field.");
        }

        String headerText = header.text();
        List<CoaCsvMapper.CoaCsvRow> acceptedRows = new ArrayList<>();
        List<RejectedCoaRow> rejectedRows = new ArrayList<>();

        for (int i = 1; i < rows.size(); i++)
        {
            LogicalCsvRow row = rows.get(i);
            if (row.text().isBlank())
            {
                continue;
            }

            if (row.unterminated())
            {
                rejectedRows.add(new RejectedCoaRow(row.lineNumber(), row.text(), "Unterminated quoted field."));
                continue;
            }

            try
            {
                List<CoaCsvMapper.CoaCsvRow> parsed = coaCsvMapper.parse(headerText + "\n" + row.text());
                if (parsed.isEmpty())
                {
                    rejectedRows.add(new RejectedCoaRow(row.lineNumber(), row.text(), "No row parsed from record."));
                }
                else
                {
                    acceptedRows.add(parsed.get(0));
                }
            }
            catch (RuntimeException ex)
            {
                rejectedRows.add(new RejectedCoaRow(row.lineNumber(), row.text(), ex.getMessage() == null ? "Invalid row." : ex.getMessage()));
            }
        }

        List<String> warnings = duplicateWarnings(acceptedRows);
        int totalRows = acceptedRows.size() + rejectedRows.size();
        return new CoaPreviewResult(
                path.getFileName().toString(),
                totalRows,
                acceptedRows.size(),
                rejectedRows.size(),
                warnings,
                acceptedRows,
                rejectedRows);
    }

    public BankPreviewResult previewBankStatement(Path path)
    {
        ImportExportOrchestrationService.BankImportResult result = orchestrationService.importBankDataFile(path);
        return new BankPreviewResult(path.getFileName().toString(), result.format(), result.transactionCount(), result.transactions());
    }

    private static List<LogicalCsvRow> splitLogicalRows(String csv)
    {
        List<LogicalCsvRow> rows = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inQuotes = false;
        int line = 1;
        int rowStartLine = 1;

        for (int i = 0; i < csv.length(); i++)
        {
            char ch = csv.charAt(i);
            if (ch == '"')
            {
                if (inQuotes && i + 1 < csv.length() && csv.charAt(i + 1) == '"')
                {
                    current.append('"').append('"');
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
                current.append(ch);
                continue;
            }

            if ((ch == '\n' || ch == '\r') && !inQuotes)
            {
                rows.add(new LogicalCsvRow(rowStartLine, current.toString(), false));
                current.setLength(0);

                if (ch == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n')
                {
                    i++;
                }
                line++;
                rowStartLine = line;
                continue;
            }

            current.append(ch);
            if (ch == '\n')
            {
                line++;
            }
        }

        if (current.length() > 0 || csv.endsWith("\n") || csv.endsWith("\r"))
        {
            rows.add(new LogicalCsvRow(rowStartLine, current.toString(), inQuotes));
        }

        return rows;
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

    private record LogicalCsvRow(int lineNumber, String text, boolean unterminated)
    {
    }

    public record CoaPreviewResult(String sourceName,
                                   int totalRowCount,
                                   int acceptedCount,
                                   int rejectedCount,
                                   List<String> warnings,
                                   List<CoaCsvMapper.CoaCsvRow> acceptedRows,
                                   List<RejectedCoaRow> rejectedRows)
    {
    }

    public record RejectedCoaRow(int lineNumber, String rawLine, String errorReason)
    {
    }

    public record BankPreviewResult(String sourceName,
                                    BankingDataFormat format,
                                    int transactionCount,
                                    List<BankTransactionRecord> transactions)
    {
    }
}
