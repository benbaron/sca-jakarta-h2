package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.BankingDataFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ImportExportOrchestrationServiceTest
{
    @Test
    public void importChartOfAccountsCsv_parsesDeterministicRows()
    {
        ImportExportOrchestrationService service = new ImportExportOrchestrationService();

        String csv = "code,name,account_type,normal_balance,parent_code\n" +
                "1000,Operating Bank,ASSET,DEBIT,\n" +
                "1100,\"Accounts, Receivable\",ASSET,DEBIT,1000\n";

        ImportExportOrchestrationService.CoaImportResult result = service.importChartOfAccountsCsv(csv);

        assertEquals(2, result.rowCount());
        assertEquals("1000", result.rows().get(0).code());
        assertEquals("Accounts, Receivable", result.rows().get(1).name());
        assertEquals("1000", result.rows().get(1).parentCode());
    }

    @Test
    public void importChartOfAccountsCsv_rejectsMissingRequiredHeader()
    {
        ImportExportOrchestrationService service = new ImportExportOrchestrationService();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importChartOfAccountsCsv("code,name,account_type\n1000,Bank,ASSET\n"));

        assertEquals("Missing required CSV header: normal_balance", ex.getMessage());
    }

    @Test
    public void importBankData_recognizesOfxAndQfxEnvelopes()
    {
        ImportExportOrchestrationService service = new ImportExportOrchestrationService();

        assertEquals(BankingDataFormat.OFX, service.importBankData("<OFX><BANKMSGSRSV1/>", "bank.ofx").format());
        assertEquals(BankingDataFormat.QFX, service.importBankData("<QFX><BANKMSGSRSV1/>", "bank.qfx").format());
    }

    @Test
    public void importBankData_rejectsUnknownEnvelope()
    {
        ImportExportOrchestrationService service = new ImportExportOrchestrationService();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importBankData("<XML></XML>", "statement.xml"));

        assertEquals("Unsupported banking envelope; expected OFX or QFX payload/filename.", ex.getMessage());
    }
}
