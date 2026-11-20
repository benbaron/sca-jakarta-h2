package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.sclx.*;
import org.nonprofitbookkeeping.persistence.Jpa;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BankingWorkspaceSclxTest
{
    @TempDir Path directory;

    @Test
    void groupedMatchUndoAndInheritedOpeningSurviveExportImportRestartAndReplay() throws Exception
    {
        Path file = directory.resolve("banking.sclx");
        try (Jpa source = new Jpa(directory.resolve("source")))
        {
            BankReconciliationMutationIntegrityTest.seed(source);
            try (var em = source.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("insert into fund(id,company_id,code,name,fund_type) values(2002,200,'EVENT','Event Fund','UNRESTRICTED')").executeUpdate();
                em.createNativeQuery("update txn_split set amount_signed=-10 where id=5001").executeUpdate();
                em.createNativeQuery("insert into txn_split(id,txn_id,account_id,fund_id,amount_signed) values(5009,4001,1001,2002,-15.75)").executeUpdate();
                em.getTransaction().commit();
            }
            var service = new BankReconciliationWorkspaceService(source);
            long first = service.start(new BankReconciliationWorkspaceService.StartCommand("SCA", 3001L,
                                       LocalDate.of(2026, 3, 31), new BigDecimal("-25.75"), null, null)).sessionId();
            service.confirmOpening(first, LocalDate.of(2026, 3, 1), BigDecimal.ZERO, true);
            service.matchBankItem(first, 7001, 5001);
            service.save(first, true);
            long next = service.start(new BankReconciliationWorkspaceService.StartCommand("SCA", 3001L,
                                      LocalDate.of(2026, 4, 30), new BigDecimal("-28.75"), null, null)).sessionId();
            try (var em = source.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("insert into txn(id,company_id,txn_date,memo,status) values(4020,200,DATE '2026-04-10','April bank fee','ENTERED')").executeUpdate();
                em.createNativeQuery("insert into txn_split(id,txn_id,account_id,fund_id,amount_signed) values(5020,4020,1001,2001,-1),(5022,4020,1001,2002,-2),(5021,4020,1002,2001,3)").executeUpdate();
                em.createNativeQuery("update txn_split set bank_cleared=true,bank_cleared_on=DATE '2026-04-02' where id=5020").executeUpdate();
                em.getTransaction().commit();
            }
            var manual = service.addManualLine(new BankReconciliationWorkspaceService.ManualStatementLineCommand(next,
                                               LocalDate.of(2026, 4, 11), new BigDecimal("-3"), "April bank fee", "fee"));
            service.matchBankItem(next, manual.statementEntries().get(0).statementLineId(), 5020);
            var document = new SclxCoreSnapshotQueryService(source, ()->"SCA").query(Instant.EPOCH);
            Files.write(file, new SclxJsonSerializer().serialize(document));
        }
        Path database = directory.resolve("target");
        try (Jpa target = new Jpa(database))
        {
            try (var em = target.em())
            {
                em.getTransaction().begin();
                em.createNativeQuery("insert into company(code,display_name) values('SCA','Target branch')").executeUpdate();
                em.getTransaction().commit();
            }
            var preview = new SclxImportPreviewService(target, ()->"SCA").preview(file);
            assertFalse(preview.hasBlockingErrors(), ()->preview.operation().messages().toString());
            assertTrue(new SclxImportCommitService(target, ()->"SCA").commit(file, preview, "tester", true, true).committed());
        }
        try (Jpa target = new Jpa(database))
        {
            var service = new BankReconciliationWorkspaceService(target);
            var sessions = service.listSessions("SCA");
            var first = sessions.stream().filter(row->row.status() ==
                                                 BankReconciliationWorkspaceService.SessionStatus.FINALIZED).findFirst().orElseThrow();
            var next = sessions.stream().filter(row->row.statementEndDate().equals(LocalDate.of(2026, 4,
                                                30))).findFirst().orElseThrow();
            assertEquals(first.id(), service.opening(next.id()).sourceSessionId());
            assertEquals(0, new BigDecimal("-25.75").compareTo(service.opening(next.id()).clearedOpening()));
            var snapshot = service.load(next.id());
            assertEquals(0, snapshot.balances().difference().signum());
            long bank = snapshot.bankAccountId();
            var item = new BankingWorkspaceQuery(target).journal("SCA", bank, LocalDate.of(2026, 4, 30)).stream()
            .filter(row->row.description().equals("April bank fee")).findFirst().orElseThrow();
            var replay = new SclxImportPreviewService(target, ()->"SCA").preview(file);
            assertFalse(replay.hasBlockingErrors(), ()->replay.operation().messages().toString());
            assertTrue(new SclxImportCommitService(target, ()->"SCA").commit(file, replay, "tester", true, true).committed());
            assertEquals(2, service.listSessions("SCA").size());
            service.unmatchBankItem(next.id(), item.statementLineId(), item.anchorSplitId());
            var restored = new BankingWorkspaceQuery(target).journal("SCA", bank, LocalDate.of(2026, 4, 30)).stream()
            .filter(row->row.anchorSplitId() == item.anchorSplitId()).findFirst().orElseThrow();
            assertFalse(restored.cleared());
            assertNull(restored.clearedOn());
            assertNull(restored.statementLineId());
            assertTrue(restored.mixedClearing(), "Undo restores each Fund allocation's distinct prior evidence");
            try (var em = target.em())
            {
                var members = restored.splitIds().stream().map(id->em.find(org.nonprofitbookkeeping.model.TxnSplit.class, id)).toList();
                assertTrue(members.stream().filter(line->line.getAmountSigned().compareTo(new BigDecimal("-1")) == 0)
                           .allMatch(line->line.isBankCleared() && LocalDate.of(2026, 4, 2).equals(line.getBankClearedOn())));
                assertTrue(members.stream().filter(line->line.getAmountSigned().compareTo(new BigDecimal("-2")) == 0)
                           .allMatch(line->!line.isBankCleared() && line.getBankClearedOn() == null));
            }
        }
    }

    @Test
    void openingProvenanceAndFractionalCentsRejectDuringPreviewBeforeWrites() throws Exception
    {
        Path file = directory.resolve("source.sclx");
        try (Jpa source = new Jpa(directory.resolve("invalid-source")))
        {
            BankReconciliationMutationIntegrityTest.seed(source);
            var service = new BankReconciliationWorkspaceService(source);
            long id = service.start(new BankReconciliationWorkspaceService.StartCommand("SCA", 3001L, LocalDate.of(2026, 3, 31),
                                    BigDecimal.ZERO, null, null)).sessionId();
            service.confirmOpening(id, LocalDate.of(2026, 3, 1), BigDecimal.ZERO, true);
            Files.write(file, new SclxJsonSerializer().serialize(new SclxCoreSnapshotQueryService(source,
                        ()->"SCA").query(Instant.EPOCH)));
        }
        try (Jpa target = new Jpa(directory.resolve("invalid-target")))
        {
            for (boolean fractional : new boolean[] {true, false})
            {
                ObjectNode root = (ObjectNode)new ObjectMapper().readTree(file.toFile());
                ObjectNode session = (ObjectNode)
                                     root.path("extensions").path("scaJakartaH2").path("reconciliation").path("sessions").get(0);
                if (fractional)
                {
                    session.put("openingAmount", new BigDecimal("0.001"));
                }
                else
                {
                    session.put("openingSourceSessionId", "missing-session");
                }
                Path bad = directory.resolve(fractional ? "fractional.sclx" : "dangling.sclx");
                new ObjectMapper().writeValue(bad.toFile(), root);
                var preview = new SclxImportPreviewService(target, ()->"DEFAULT").preview(bad);
                assertTrue(preview.hasBlockingErrors());
                assertTrue(new BankReconciliationWorkspaceService(target).listSessions("DEFAULT").isEmpty());
            }
        }
    }
}
