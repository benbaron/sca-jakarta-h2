package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ImportPreviewService component.
 */
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

        List<String> headers = parseCsvRecord(header.text());
        requireHeaders(headers, List.of("code", "name", "account_type", "normal_balance"));

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
                List<String> values = parseCsvRecord(row.text());
                Map<String, String> mapped = map(headers, values);
                acceptedRows.add(new CoaCsvMapper.CoaCsvRow(
                        required(mapped, "code"),
                        required(mapped, "name"),
                        required(mapped, "account_type"),
                        required(mapped, "normal_balance"),
                        optional(mapped, "parent_code")));
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


    public CoaCommitResult commitAcceptedCoaRows(List<CoaCsvMapper.CoaCsvRow> acceptedRows,
                                                  CoaRowCommitter committer)
    {
        if (committer == null)
        {
            throw new IllegalArgumentException("Cannot commit COA rows: committer is required.");
        }

        List<CoaCsvMapper.CoaCsvRow> safeRows = acceptedRows == null ? List.of() : List.copyOf(acceptedRows);
        int committed = 0;
        List<String> errors = new ArrayList<>();
        for (CoaCsvMapper.CoaCsvRow row : safeRows)
        {
            try
            {
                committer.commit(row);
                committed++;
            }
            catch (RuntimeException ex)
            {
                errors.add((row == null ? "(null row)" : row.code()) + ": "
                        + (ex.getMessage() == null ? "commit failed" : ex.getMessage()));
            }
        }
        return new CoaCommitResult(safeRows.size(), committed, errors.size(), List.copyOf(errors));
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

    private static void requireHeaders(List<String> headers, List<String> required)
    {
        for (String key : required)
        {
            if (!headers.contains(key))
            {
                throw new IllegalArgumentException("Missing required CSV header: " + key);
            }
        }
    }

    private static Map<String, String> map(List<String> headers, List<String> values)
    {
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < headers.size() && i < values.size(); i++)
        {
            out.put(headers.get(i), values.get(i));
        }
        return out;
    }

    private static String required(Map<String, String> row, String key)
    {
        String value = row.get(key);
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException("Missing required value for column: " + key);
        }
        return value.trim();
    }

    private static String optional(Map<String, String> row, String key)
    {
        String value = row.get(key);
        return value == null ? "" : value.trim();
    }

    private static List<String> parseCsvRecord(String text)
    {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++)
        {
            char ch = text.charAt(i);
            if (inQuotes)
            {
                if (ch == '"')
                {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"')
                    {
                        cur.append('"');
                        i++;
                    }
                    else
                    {
                        inQuotes = false;
                    }
                }
                else
                {
                    cur.append(ch);
                }
            }
            else
            {
                if (ch == '"')
                {
                    inQuotes = true;
                }
                else if (ch == ',')
                {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                else
                {
                    cur.append(ch);
                }
            }
        }

        if (inQuotes)
        {
            throw new IllegalArgumentException("Unterminated quoted field.");
        }

        out.add(cur.toString());
        return out;
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

    public interface CoaRowCommitter
    {
        void commit(CoaCsvMapper.CoaCsvRow row);
    }

    public record CoaCommitResult(int totalAccepted,
                                  int committedCount,
                                  int failedCount,
                                  List<String> errors)
    {
    }

    public record BankPreviewResult(String sourceName,
                                    BankingDataFormat format,
                                    int transactionCount,
                                    List<BankTransactionRecord> transactions)
    {
    }
}
