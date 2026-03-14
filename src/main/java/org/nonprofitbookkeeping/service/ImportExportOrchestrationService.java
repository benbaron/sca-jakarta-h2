package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.io.IOException;
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
