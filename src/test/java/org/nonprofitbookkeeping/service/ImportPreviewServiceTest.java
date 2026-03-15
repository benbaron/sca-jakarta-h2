package org.nonprofitbookkeeping.service;

import org.junit.jupiter.api.Test;
import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ImportPreviewServiceTest
{
    private final ImportPreviewService service = new ImportPreviewService(new ImportExportOrchestrationService());

    @Test
    public void previewCoaCsv_reportsDuplicateCodeWarnings() throws Exception
    {
        Path csv = Files.createTempFile("coa-preview", ".csv");
        Files.writeString(csv, """
                code,name,account_type,normal_balance,parent_code
                1000,Cash,ASSET,DEBIT,
                1000,Cash Duplicate,ASSET,DEBIT,
                1100,AR,ASSET,DEBIT,1000
                """);

        ImportPreviewService.CoaPreviewResult result = service.previewCoaCsv(csv);

        assertEquals(3, result.totalRowCount());
        assertEquals(3, result.acceptedCount());
        assertEquals(0, result.rejectedCount());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("Duplicate account code"));
    }

    @Test
    public void previewCoaCsv_reportsRejectedRowsWithErrorReason() throws Exception
    {
        Path csv = Files.createTempFile("coa-preview-invalid", ".csv");
        Files.writeString(csv, """
                code,name,account_type,normal_balance,parent_code
                1000,Cash,ASSET,DEBIT,
                1200,,ASSET,DEBIT,
                1300,Prepaid,ASSET,DEBIT,1000
                """);

        ImportPreviewService.CoaPreviewResult result = service.previewCoaCsv(csv);

        assertEquals(3, result.totalRowCount());
        assertEquals(2, result.acceptedCount());
        assertEquals(1, result.rejectedCount());
        assertTrue(result.rejectedRows().get(0).errorReason().contains("Missing required value for column: name"));
    }

    @Test
    public void previewCoaCsv_acceptsMultilineQuotedRows() throws Exception
    {
        Path csv = Files.createTempFile("coa-preview-multiline", ".csv");
        Files.writeString(csv, """
                code,name,account_type,normal_balance,parent_code
                1000,"Office
                Checking",ASSET,DEBIT,
                1100,"AR ""External""",ASSET,DEBIT,1000
                """);

        ImportPreviewService.CoaPreviewResult result = service.previewCoaCsv(csv);

        assertEquals(2, result.totalRowCount());
        assertEquals(2, result.acceptedCount());
        assertEquals(0, result.rejectedCount());
        assertTrue(result.acceptedRows().get(0).name().contains("Checking"));
        assertTrue(result.acceptedRows().get(1).name().contains("\"External\""));
    }

    @Test
    public void previewCoaCsv_rejectsUnterminatedQuotedRows() throws Exception
    {
        Path csv = Files.createTempFile("coa-preview-unterminated", ".csv");
        Files.writeString(csv, """
                code,name,account_type,normal_balance,parent_code
                1000,"Office
                Checking,ASSET,DEBIT,
                1100,AR,ASSET,DEBIT,1000
                """);

        ImportPreviewService.CoaPreviewResult result = service.previewCoaCsv(csv);

        assertEquals(1, result.totalRowCount());
        assertEquals(0, result.acceptedCount());
        assertEquals(1, result.rejectedCount());
        assertEquals("Unterminated quoted field.", result.rejectedRows().get(0).errorReason());
    }

    @Test
    public void previewBankStatement_extractsFormatAndTransactionCount() throws Exception
    {
        Path ofx = Files.createTempFile("bank-preview", ".ofx");
        Files.writeString(ofx, """
                <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
                <STMTTRN><TRNTYPE>DEBIT</TRNTYPE><DTPOSTED>20260301000000</DTPOSTED><TRNAMT>-5.00</TRNAMT><FITID>FIT-1</FITID><NAME>Fee</NAME><MEMO>x</MEMO></STMTTRN>
                </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
                """);

        ImportPreviewService.BankPreviewResult result = service.previewBankStatement(ofx);

        assertEquals(BankingDataFormat.OFX, result.format());
        assertEquals(1, result.transactionCount());
    }
}
