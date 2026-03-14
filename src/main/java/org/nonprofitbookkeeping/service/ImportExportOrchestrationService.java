package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.util.List;

/**
 * Stage C orchestration contract for import/export entry points.
 */
public class ImportExportOrchestrationService
{
    private final CoaCsvMapper coaCsvMapper;
    private final BankDataEnvelopeRecognizer bankRecognizer;

    public ImportExportOrchestrationService()
    {
        this(new CoaCsvMapper(), new BankDataEnvelopeRecognizer());
    }

    public ImportExportOrchestrationService(CoaCsvMapper coaCsvMapper, BankDataEnvelopeRecognizer bankRecognizer)
    {
        this.coaCsvMapper = coaCsvMapper;
        this.bankRecognizer = bankRecognizer;
    }

    public CoaImportResult importChartOfAccountsCsv(String csv)
    {
        List<CoaCsvMapper.CoaCsvRow> rows = coaCsvMapper.parse(csv);
        return new CoaImportResult(rows.size(), rows);
    }

    public BankImportResult importBankData(String payload, String sourceName)
    {
        BankingDataFormat format = bankRecognizer.recognize(payload, sourceName);
        return new BankImportResult(format, sourceName == null ? "" : sourceName);
    }

    public record CoaImportResult(int rowCount, List<CoaCsvMapper.CoaCsvRow> rows)
    {
    }

    public record BankImportResult(BankingDataFormat format, String sourceName)
    {
    }
}
