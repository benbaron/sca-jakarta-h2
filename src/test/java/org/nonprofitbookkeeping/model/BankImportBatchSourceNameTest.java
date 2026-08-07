package org.nonprofitbookkeeping.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankImportBatchSourceNameTest
{
    @Test
    void sourceNamePersistsLogicalNameWhileSourcePathCanRetainFullPath()
    {
        BankImportBatch batch = new BankImportBatch();
        String logicalName = "march-owner-upload.csv";
        String temporaryPath = "/home/runner/work/" + "deep-temporary-directory/".repeat(20) + logicalName;
        assertTrue(temporaryPath.length() > 260);

        batch.setSourceName(temporaryPath);
        batch.setSourcePath(temporaryPath);

        assertEquals(logicalName, batch.getSourceName());
        assertEquals(temporaryPath, batch.getSourcePath());

        batch.setSourceName("C:\\Users\\owner\\AppData\\Local\\Temp\\statement.qfx");
        assertEquals("statement.qfx", batch.getSourceName());
    }

    @Test
    void overlongLogicalSourceNameIsRejectedInsteadOfSilentlyTruncated()
    {
        BankImportBatch batch = new BankImportBatch();
        String logicalName = "x".repeat(261);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> batch.setSourceName(logicalName));

        assertTrue(error.getMessage().contains("260"));
    }
}
