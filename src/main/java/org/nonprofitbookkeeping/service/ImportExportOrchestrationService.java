package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Stage C orchestration contract for import/export entry points.
 */
public class ImportExportOrchestrationService
{
    private final CoaCsvMapper coaCsvMapper;
    private final BankDataEnvelopeRecognizer bankRecognizer;
    private final OfxQfxTransactionExtractor ofxQfxTransactionExtractor;

    public ImportExportOrchestrationService()
    {
        this(new CoaCsvMapper(), new BankDataEnvelopeRecognizer(), new OfxQfxTransactionExtractor());
    }

    public ImportExportOrchestrationService(CoaCsvMapper coaCsvMapper,
                                            BankDataEnvelopeRecognizer bankRecognizer,
                                            OfxQfxTransactionExtractor ofxQfxTransactionExtractor)
    {
        this.coaCsvMapper = coaCsvMapper;
        this.bankRecognizer = bankRecognizer;
        this.ofxQfxTransactionExtractor = ofxQfxTransactionExtractor;
    }

    public CoaImportResult importChartOfAccountsCsv(String csv)
    {
        List<CoaCsvMapper.CoaCsvRow> rows = coaCsvMapper.parse(csv);
        return new CoaImportResult(rows.size(), rows);
    }

    public CoaImportResult importChartOfAccountsCsvFile(Path path)
    {
        String source = readRequiredFile(path, "COA CSV");
        return importChartOfAccountsCsv(source);
    }

    public void exportChartOfAccountsCsvFile(List<CoaCsvMapper.CoaCsvRow> rows, Path path)
    {
        writeRequiredFile(path, coaCsvMapper.write(rows), "COA CSV");
    }

    public BankImportResult importBankData(String payload, String sourceName)
    {
        BankingDataFormat format = bankRecognizer.recognize(payload, sourceName);
        List<BankTransactionRecord> transactions = ofxQfxTransactionExtractor.extract(payload);
        return new BankImportResult(format, sourceName == null ? "" : sourceName, transactions.size(), transactions);
    }

    public BankImportResult importBankDataFile(Path path)
    {
        String payload = readRequiredFile(path, "bank statement");
        return importBankData(payload, path.getFileName().toString());
    }

    public void exportBankDataFile(BankingDataFormat format, List<BankTransactionRecord> transactions, Path path)
    {
        if (format == null)
        {
            throw new IllegalArgumentException("Cannot export bank statement: format is required.");
        }
        List<BankTransactionRecord> safeTransactions = transactions == null ? List.of() : transactions;
        String rootTag = format.name();

        StringBuilder out = new StringBuilder();
        out.append("<").append(rootTag).append("><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>");
        for (BankTransactionRecord transaction : safeTransactions)
        {
            out.append("<STMTTRN>")
                    .append(tag("TRNTYPE", transaction.transactionType()))
                    .append(tag("DTPOSTED", transaction.postedOn()))
                    .append(tag("TRNAMT", transaction.amount() == null ? "0" : transaction.amount().toPlainString()))
                    .append(tag("FITID", transaction.fitId()))
                    .append(tag("NAME", transaction.name()))
                    .append(tag("MEMO", transaction.memo()))
                    .append("</STMTTRN>");
        }
        out.append("</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></").append(rootTag).append(">");

        writeRequiredFile(path, out.toString(), "bank statement");
    }

    private static String tag(String tag, String value)
    {
        String safe = escapeXml(value == null ? "" : value);
        return "<" + tag + ">" + safe + "</" + tag + ">";
    }

    private static String escapeXml(String value)
    {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String readRequiredFile(Path path, String label)
    {
        if (path == null)
        {
            throw new IllegalArgumentException("Cannot import " + label + ": file path is required.");
        }
        if (!Files.exists(path) || !Files.isRegularFile(path))
        {
            throw new IllegalArgumentException("Cannot import " + label + ": file does not exist -> " + path);
        }
        try
        {
            return Files.readString(path);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Cannot import " + label + ": failed reading file -> " + path, ex);
        }
    }

    private void writeRequiredFile(Path path, String body, String label)
    {
        if (path == null)
        {
            throw new IllegalArgumentException("Cannot export " + label + ": file path is required.");
        }
        try
        {
            Path parent = path.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }
            Files.writeString(path, body == null ? "" : body, StandardCharsets.UTF_8);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Cannot export " + label + ": failed writing file -> " + path, ex);
        }
    }

    public record CoaImportResult(int rowCount, List<CoaCsvMapper.CoaCsvRow> rows)
    {
    }

    public record BankImportResult(BankingDataFormat format,
                                   String sourceName,
                                   int transactionCount,
                                   List<BankTransactionRecord> transactions)
    {
    }
}
