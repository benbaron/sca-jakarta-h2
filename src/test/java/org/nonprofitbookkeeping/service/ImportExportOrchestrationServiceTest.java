package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ImportExportOrchestrationServiceTest
{
    @TempDir
    Path tempDir;

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
    public void importChartOfAccountsCsvFile_readsFileAndParsesRows() throws IOException
    {
        ImportExportOrchestrationService service = new ImportExportOrchestrationService();

        Path file = tempDir.resolve("coa.csv");
        Files.writeString(file, "code,name,account_type,normal_balance,parent_code\n1000,Operating Bank,ASSET,DEBIT,\n");

        ImportExportOrchestrationService.CoaImportResult result = service.importChartOfAccountsCsvFile(file);

        assertEquals(1, result.rowCount());
        assertEquals("1000", result.rows().get(0).code());
    }

    @Test
    public void importChartOfAccountsCsvFile_rejectsMissingFile()
    {
        ImportExportOrchestrationService service = new ImportExportOrchestrationService();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importChartOfAccountsCsvFile(tempDir.resolve("missing.csv")));

        assertEquals("Cannot import COA CSV: file does not exist -> " + tempDir.resolve("missing.csv"), ex.getMessage());
    }

    @Test
    public void importBankData_recognizesOfxAndExtractsTransactions()
    {
        ImportExportOrchestrationService service = new ImportExportOrchestrationService();

        String ofx = "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>" +
                "<STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260313000000</DTPOSTED><TRNAMT>-25.00</TRNAMT><FITID>FIT-1</FITID><NAME>Stationery Shop</NAME><MEMO>Paper</MEMO></STMTTRN>" +
                "<STMTTRN><TRNTYPE>CREDIT</TRNTYPE><DTPOSTED>20260314000000</DTPOSTED><TRNAMT>100.00</TRNAMT><FITID>FIT-2</FITID><NAME>Donation</NAME><MEMO>Member gift</MEMO></STMTTRN>" +
                "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";

        ImportExportOrchestrationService.BankImportResult result = service.importBankData(ofx, "bank.ofx");

        assertEquals(BankingDataFormat.OFX, result.format());
        assertEquals(2, result.transactionCount());
        assertEquals("FIT-1", result.transactions().get(0).fitId());
        assertEquals("-25.00", result.transactions().get(0).amount().toPlainString());
    }

    @Test
    public void importBankData_recognizesQfxByExtension()
    {
        ImportExportOrchestrationService service = new ImportExportOrchestrationService();

        ImportExportOrchestrationService.BankImportResult result = service.importBankData("<QFX><BANKMSGSRSV1/></QFX>", "bank.qfx");

        assertEquals(BankingDataFormat.QFX, result.format());
        assertEquals(0, result.transactionCount());
    }

    @Test
    public void importBankDataFile_readsStatementAndDerivesTransactionCount() throws IOException
    {
        ImportExportOrchestrationService service = new ImportExportOrchestrationService();

        Path statement = tempDir.resolve("statement.ofx");
        Files.writeString(statement,
                "<OFX><STMTTRN><TRNTYPE>DEBIT</TRNTYPE><TRNAMT>-9.50</TRNAMT><DTPOSTED>20260315000000</DTPOSTED><FITID>FIT-9</FITID><NAME>Coffee</NAME><MEMO>Meeting</MEMO></STMTTRN></OFX>");

        ImportExportOrchestrationService.BankImportResult result = service.importBankDataFile(statement);

        assertEquals(BankingDataFormat.OFX, result.format());
        assertEquals(1, result.transactionCount());
        assertEquals("FIT-9", result.transactions().get(0).fitId());
    }

    @Test
    public void importBankDataFile_rejectsMissingFile()
    {
        ImportExportOrchestrationService service = new ImportExportOrchestrationService();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importBankDataFile(tempDir.resolve("missing.ofx")));

        assertEquals("Cannot import bank statement: file does not exist -> " + tempDir.resolve("missing.ofx"), ex.getMessage());
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
