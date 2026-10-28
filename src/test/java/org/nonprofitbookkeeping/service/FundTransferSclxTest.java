package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.sclx.*;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.nonprofitbookkeeping.service.FundTransferTestFixture.*;

class FundTransferSclxTest
{
    @Test
    void portableLedgerPreservesFourLinesAndDatedReversalWithOperationalLinks(@TempDir Path directory) throws Exception
    {
        try (Jpa source = new Jpa(directory.resolve("source")); Jpa target = new Jpa(directory.resolve("target")))
        {
            var fixture = seed(source);
            var service = new FundTransferService(source);
            var saved = service.save(fixture.command(UUID.randomUUID(), "75"));
            service.reverse(saved.id(), DATE.plusDays(5), "Correct allocation");
            Path file = directory.resolve("transfers.sclx");
            var result = new SclxFileExportService(new SclxCoreSnapshotQueryService(source), () -> directory.resolve("source"))
                    .export(new SclxExportRequest(file, Instant.now(), false));
            assertFalse(result.deferredSections().contains(SclxExportSection.FUND_TRANSFERS));
            assertEquals(2, result.counts().fundTransfers());
            var preview = new SclxImportPreviewService(target, () -> "DEFAULT").preview(file);
            assertFalse(preview.hasBlockingErrors(), preview.operation().messages().toString());
            var imported = new SclxImportCommitService(target, () -> "DEFAULT").commit(file, preview, "Tester");
            assertTrue(imported.committed(), imported.toString());
            try (EntityManager em = target.em())
            {
                assertEquals(3L, em.createQuery("select count(t) from Txn t", Long.class).getSingleResult());
                assertEquals(10L, em.createQuery("select count(s) from TxnSplit s", Long.class).getSingleResult());
                Txn reversal = em.createQuery("from Txn t where t.reversalOf is not null", Txn.class).getSingleResult();
                assertEquals(DATE.plusDays(5), reversal.getTxnDate());
                assertEquals(DATE, reversal.getReversalOf().getTxnDate());
                assertEquals(4L, em.createQuery("select count(s) from TxnSplit s where s.txn = :txn", Long.class).setParameter("txn", reversal).getSingleResult());
                assertEquals(0, em.createQuery("select sum(s.amountSigned) from TxnSplit s where s.account.code = '1500'", java.math.BigDecimal.class).getSingleResult().signum());
            }
            assertEquals(2, new FundTransferService(target).list().size(), "Operational links must survive without duplicate accounting");
            assertEquals(2, service.list().size(), "Export must not mutate source history");
        }
    }
}
