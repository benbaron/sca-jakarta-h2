package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.interchange.InterchangeIdentityMatch;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SclxNmrLineRoundTripTest
{
    @TempDir Path directory;
    public static final String COMPANY = "NMR_TEST";
    private static final LocalDate DATE = LocalDate.of(2026, 6, 1);
    private static final ObjectMapper JSON = new ObjectMapper();

    private record References(long cash, long income, long liability, long fund, long event)
    {
    }

    private static References seed(Jpa jpa, boolean masters)
    {
        return seed(jpa, masters, COMPANY);
    }

    private static References seed(Jpa jpa, boolean masters, String code)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = new Company();
            company.setCode(code);
            company.setDisplayName("Synthetic NMR company");
            company.setDefaultCurrency("USD");
            em.persist(company);
            ChartOfAccounts chart = new ChartOfAccounts();
            chart.setCompany(company);
            chart.setName("NMR chart");
            chart.setVersion("1");
            chart.setStatus(ChartStatus.ACTIVE);
            em.persist(chart);
            company.setActiveChartOfAccounts(chart);
            References result = null;
            if (masters)
            {
                Account cash = account(em, chart, "1000", AccountType.ASSET, AccountSubtype.CASH, NormalBalance.DEBIT);
                cash.setAccountFunction(AccountFunction.BANK);
                Account income = account(em, chart, "4000", AccountType.INCOME, null, NormalBalance.CREDIT);
                Account liability = account(em, chart, "2300", AccountType.LIABILITY, AccountSubtype.OTHER_LIABILITY, NormalBalance.CREDIT);
                Fund fund = new Fund();
                fund.setCompany(company);
                fund.setCode("GENERAL");
                fund.setName("General");
                fund.setFundType(FundType.UNRESTRICTED);
                fund.setActive(true);
                em.persist(fund);
                Activity event = new Activity();
                event.setCompany(company);
                event.setCode("EVENT");
                event.setName("Synthetic event");
                event.setActive(true);
                em.persist(event);
                result = new References(cash.getId(), income.getId(), liability.getId(), fund.getId(), event.getId());
            }
            em.getTransaction().commit();
            return result;
        }
    }

    private static Account account(EntityManager em, ChartOfAccounts chart, String code,
            AccountType type, AccountSubtype subtype, NormalBalance normal)
    {
        Account account = new Account();
        account.setChart(chart);
        account.setCode(code);
        account.setName(code);
        account.setAccountType(type);
        account.setSubtype(subtype);
        account.setNormalBalance(normal);
        account.setPosting(true);
        account.setActive(true);
        em.persist(account);
        return account;
    }

    private static TransactionLineCommand line(long account, References r, String debit, String credit, boolean nmr)
    {
        return new TransactionLineCommand(account, r.fund(), null, r.event(), null,
                new BigDecimal(debit), new BigDecimal(credit), nmr, "Entered fact");
    }

    private static TransactionSupplementalLineCommand allocation(UUID item, String effect, int index, String amount)
    {
        return new TransactionSupplementalLineCommand("OTHER_LIABILITY", "NMR", "Recipient",
                "Entered NMR obligation", "external/reference", new BigDecimal(amount), null, null, null,
                null, 0, item, effect, index);
    }

    private static void enterHistory(Jpa jpa, References r)
    {
        UUID item = UUID.randomUUID();
        var entry = new TransactionEntryService(jpa, () -> COMPANY);
        entry.enter(new TransactionCommand(DATE, null, "Gross receipt", null, List.of(
                line(r.cash(), r, "100", "0", false), line(r.income(), r, "0", "80", false),
                line(r.liability(), r, "0", "20", true)), List.of(allocation(item, "INCREASE", 2, "20"))));
        var payment = entry.enter(new TransactionCommand(DATE.plusDays(1), null, "Partial remittance", null, List.of(
                line(r.liability(), r, "5", "0", true), line(r.cash(), r, "0", "5", false)),
                List.of(allocation(item, "DECREASE", 0, "5"))));
        new TransactionCorrectionService(jpa, () -> COMPANY)
                .reverse(payment.id(), DATE.plusDays(2), "tester", "Explicit remittance reversal", false);
    }

    private static Path export(Jpa jpa, Path path) throws Exception
    {
        var document = new SclxCoreSnapshotQueryService(jpa, () -> COMPANY).query(Instant.EPOCH);
        byte[] bytes = new SclxJsonSerializer().serialize(document);
        assertArrayEquals(bytes, new SclxJsonSerializer().serialize(document));
        Files.write(path, bytes);
        return path;
    }

    /** Shared real canonical history for the JavaFX preview/Save regression. */
    public static Path writeHistory(Path directory) throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("source-db")))
        {
            enterHistory(jpa, seed(jpa, true));
            return export(jpa, directory.resolve("source.sclx"));
        }
    }

    public static void seedEmptyTarget(Jpa jpa)
    {
        seed(jpa, false);
    }

    private static SclxImportPreview preview(Jpa jpa, Path source)
    {
        return new SclxImportPreviewService(jpa, () -> COMPANY).preview(source);
    }

    private static SclxImportResult commit(Jpa jpa, Path source, SclxImportPreview preview)
    {
        return new SclxImportCommitService(jpa, () -> COMPANY).commit(source, preview, "tester", true, true);
    }

    private static long count(Jpa jpa, String jpql)
    {
        try (EntityManager em = jpa.em())
        {
            return em.createQuery(jpql, Long.class).getSingleResult();
        }
    }

    @Test
    void enteredFlagsAndLiabilityHistorySurviveExportImportRestartAndRetry() throws Exception
    {
        Path source = directory.resolve("history.sclx");
        try (Jpa jpa = new Jpa(directory.resolve("source")))
        {
            enterHistory(jpa, seed(jpa, true));
            assertEquals(3, count(jpa, "select count(s) from TxnSplit s where s.nmr = true"));
            export(jpa, source);
            JsonNode flags = JSON.readTree(source.toFile()).path("extensions").path("scaJakartaH2")
                    .path("transactionNmr").path("lineIds");
            assertEquals(3, flags.size());
            var ids = new java.util.ArrayList<String>();
            flags.forEach(id -> ids.add(id.textValue()));
            assertEquals(ids.stream().sorted().toList(), ids);
        }
        Path target = directory.resolve("target");
        try (Jpa jpa = new Jpa(target))
        {
            seed(jpa, false);
            var preview = preview(jpa, source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertTrue(preview.operation().messages().stream().anyMatch(m -> m.code().equals("SCLX_NMR_LINE_FLAGS")));
            assertTrue(commit(jpa, source, preview).committed());
            assertEquals(3, count(jpa, "select count(s) from TxnSplit s where s.nmr = true"));
            assertEquals(4, count(jpa, "select count(s) from TxnSplit s where s.nmr = false"));
        }
        try (Jpa jpa = new Jpa(target))
        {
            var items = new SupplementalOpenItemQueryService(jpa, () -> COMPANY)
                    .query(SupplementalOpenItemQueryService.Kind.OTHER_LIABILITY, DATE.plusDays(2));
            assertTrue(items.ready(), () -> items.toString());
            assertEquals(0, new BigDecimal("20").compareTo(items.openAmount()));
            assertEquals(7, count(jpa, "select count(s) from TxnSplit s where s.activity.code = 'EVENT' and s.fund.code = 'GENERAL'"));
            assertEquals(1, count(jpa, "select count(t) from Txn t where t.reversalOf is not null"));
            assertEquals(0, count(jpa, "select count(p) from NmrPolicyVersion p"));
            long audits = count(jpa, "select count(a) from AuditEvent a");
            var retry = preview(jpa, source);
            assertTrue(retry.operation().items().stream().allMatch(i -> i.identityMatch() == InterchangeIdentityMatch.IDENTICAL));
            assertTrue(commit(jpa, source, retry).committed());
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            assertEquals(3, count(jpa, "select count(s) from TxnSplit s where s.nmr = true"));
        }
    }

    @Test
    void changedFlagsAndLegacyAbsenceRequireKeepTargetWithoutRewritingHistory() throws Exception
    {
        Path source = writeHistory(directory);
        ObjectNode original = (ObjectNode) JSON.readTree(source.toFile());
        try (Jpa jpa = new Jpa(directory.resolve("target")))
        {
            seed(jpa, false);
            assertTrue(commit(jpa, source, preview(jpa, source)).committed());
            for (boolean legacy : List.of(false, true))
            {
                ObjectNode changed = original.deepCopy();
                ObjectNode app = (ObjectNode) changed.path("extensions").path("scaJakartaH2");
                if (legacy)
                {
                    app.remove("transactionNmr");
                }
                else
                {
                    ((com.fasterxml.jackson.databind.node.ArrayNode) app.path("transactionNmr").path("lineIds")).remove(0);
                }
                JSON.writeValue(source.toFile(), changed);
                var conflicted = preview(jpa, source);
                assertTrue(conflicted.hasBlockingErrors());
                assertTrue(conflicted.operation().items().stream().anyMatch(i -> i.entityType().equals("TRANSACTION")
                        && i.identityMatch() == InterchangeIdentityMatch.CONFLICT));
                assertTrue(conflicted.operation().items().stream().anyMatch(i -> i.entityType().equals("TRANSACTION_LINE")
                        && i.identityMatch() == InterchangeIdentityMatch.CONFLICT));
                assertThrows(IllegalStateException.class, () -> commit(jpa, source, conflicted));
                var keep = conflicted.operation().items().stream()
                        .filter(i -> i.identityMatch() == InterchangeIdentityMatch.CONFLICT)
                        .map(i -> new SclxImportConflictSelection(i.entityType(), i.externalId(), SclxImportConflictChoice.KEEP_TARGET))
                        .toList();
                var accepted = new SclxImportPreviewService(jpa, () -> COMPANY).preview(source, List.of(), keep);
                assertFalse(accepted.hasBlockingErrors(), () -> accepted.operation().messages().toString());
                assertTrue(commit(jpa, source, accepted).committed());
                assertEquals(3, count(jpa, "select count(s) from TxnSplit s where s.nmr = true"));
                assertEquals(3, count(jpa, "select count(t) from Txn t"));
            }
        }
        try (Jpa jpa = new Jpa(directory.resolve("legacy-target")))
        {
            seed(jpa, false);
            assertTrue(commit(jpa, source, preview(jpa, source)).committed());
            assertEquals(0, count(jpa, "select count(s) from TxnSplit s where s.nmr = true"));
            Path exported = export(jpa, directory.resolve("legacy-roundtrip.sclx"));
            assertFalse(JSON.readTree(exported.toFile()).path("extensions").path("scaJakartaH2").has("transactionNmr"));
        }
    }

    private static AuthorizationGuard guard(Jpa jpa, ReservedSecurityRole role)
    {
        Instant now = Instant.now();
        return new AuthorizationGuard(jpa, () -> Optional.of(new AuthenticatedUserSession(
                9001L, "nmr-operator", "NMR operator", COMPANY, Set.of(role), now, now)));
    }

    @Test
    void accountantCanImportFlagsViewerCannotAndLateFailureOrChangedFileLeavesNoWrites() throws Exception
    {
        Path source = writeHistory(directory);
        try (Jpa jpa = new Jpa(directory.resolve("target")))
        {
            seed(jpa, false);
            var preview = preview(jpa, source);
            assertFalse(preview.hasBlockingErrors(), () -> preview.operation().messages().toString());
            assertThrows(AuthorizationException.class, () -> new SclxImportCommitService(jpa, () -> COMPANY,
                    guard(jpa, ReservedSecurityRole.VIEWER)).commit(source, preview, "spoofed", true, true));
            long audits = count(jpa, "select count(a) from AuditEvent a");
            var reached = new java.util.concurrent.atomic.AtomicBoolean();
            var failing = new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.ACCOUNTANT), writes ->
            {
                // Organization + three accounts + fund + event precede the first canonical transaction.
                if (writes == 7)
                {
                    reached.set(true);
                    throw new IllegalStateException("Injected failure after canonical NMR lines");
                }
            });
            assertFalse(failing.commit(source, preview, "spoofed", true, true).committed());
            assertTrue(reached.get());
            assertEquals(0, count(jpa, "select count(t) from Txn t"));
            assertEquals(0, count(jpa, "select count(s) from TxnSplit s"));
            assertEquals(0, count(jpa, "select count(i) from InterchangeIdentity i"));
            assertEquals(audits, count(jpa, "select count(a) from AuditEvent a"));
            var accountant = new SclxImportCommitService(jpa, () -> COMPANY, guard(jpa, ReservedSecurityRole.ACCOUNTANT));
            byte[] original = Files.readAllBytes(source);
            Files.writeString(source, Files.readString(source) + "\n");
            assertThrows(IllegalStateException.class, () -> accountant.commit(source, preview, "spoofed", true, true));
            assertEquals(0, count(jpa, "select count(t) from Txn t"));
            Files.write(source, original);
            assertTrue(accountant.commit(source, preview(jpa, source), "spoofed", true, true).committed());
            assertEquals(3, count(jpa, "select count(s) from TxnSplit s where s.nmr = true"));
        }
    }

    @Test
    void nativeHistoryOwnedByAnotherCompanyCannotBeReused() throws Exception
    {
        try (Jpa jpa = new Jpa(directory.resolve("companies")))
        {
            enterHistory(jpa, seed(jpa, true));
            seed(jpa, false, "OTHER");
            Path source = export(jpa, directory.resolve("foreign.sclx"));
            var preview = new SclxImportPreviewService(jpa, () -> "OTHER").preview(source);
            assertTrue(preview.hasBlockingErrors());
            assertThrows(IllegalStateException.class, () -> new SclxImportCommitService(jpa, () -> "OTHER")
                    .commit(source, preview, "tester", true, true));
            assertEquals(0, count(jpa, "select count(t) from Txn t where t.company.code = 'OTHER'"));
            assertEquals(3, count(jpa, "select count(s) from TxnSplit s where s.nmr = true"));
        }
    }
}
