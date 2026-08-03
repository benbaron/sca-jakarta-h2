package org.nonprofitbookkeeping.interchange.bank;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nonprofitbookkeeping.model.Bank;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.BankingDataFormat;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;
import org.nonprofitbookkeeping.model.ImportIssue;
import org.nonprofitbookkeeping.model.Txn;
import org.nonprofitbookkeeping.persistence.Jpa;
import org.nonprofitbookkeeping.service.BankAccountCommand;
import org.nonprofitbookkeeping.service.BankCommand;
import org.nonprofitbookkeeping.service.BankConfigurationService;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BankStatementCsvExportServiceTest
{
    @Test
    public void exportsDeterministicScopedNormalizedCsvWithPortableIdentities(@TempDir Path tempDir) throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("normalized-csv-export")))
        {
            Seed seed = seed(jpa);
            persistReviewFacts(jpa, seed.scaAccountId(), "SCA");
            persistReviewFacts(jpa, seed.otherAccountId(), "OTHER");
            BankStatementCsvExportService service = new BankStatementCsvExportService(
                    jpa, () -> tempDir.resolve("active-database"));
            Path output = tempDir.resolve("sca-bank.csv");
            BankStatementExportRequest request = new BankStatementExportRequest(
                    "SCA", seed.scaAccountId(),
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), output, false);

            BankStatementExportResult first = service.export(request);
            byte[] firstBytes = Files.readAllBytes(output);
            List<List<String>> csv = parseCsv(Files.readString(output));

            assertEquals(NormalizedBankCsvSerializer.HEADER, String.join(",", csv.get(0)));
            assertEquals(3, csv.size());
            assertEquals("1.0", csv.get(1).get(0));
            assertEquals("SCA-source.ofx", csv.get(1).get(3));
            assertEquals("FIT-EARLY", csv.get(1).get(13));
            assertEquals("FIT-LATE", csv.get(2).get(13));
            assertEquals("Gift, \"summer\"\nbenefit", csv.get(2).get(16));
            assertEquals("PROBABLE", csv.get(1).get(27));
            assertEquals("EXACT", csv.get(2).get(27));
            assertEquals(36, csv.get(1).get(2).length());
            assertEquals(36, csv.get(1).get(4).length());
            assertEquals(36, csv.get(2).get(28).length());
            assertFalse(Files.readString(output).contains("OTHER-source.ofx"));
            assertEquals(2, first.rowCount());
            assertEquals(firstBytes.length, first.byteCount());
            assertTrue(first.messages().stream()
                    .anyMatch(value -> value.code().equals("BANK_CSV_PAYEE_ID_UNAVAILABLE")));

            assertThrows(IllegalArgumentException.class, () -> service.export(request));
            assertArrayEquals(firstBytes, Files.readAllBytes(output));
            BankStatementExportResult second = service.export(new BankStatementExportRequest(
                    "SCA", seed.scaAccountId(),
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), output, true));
            assertEquals(first.sha256(), second.sha256());
            assertArrayEquals(firstBytes, Files.readAllBytes(output));
        }
    }

    @Test
    public void rejectsEmptyRangeAndCrossCompanyAccountWithoutCreatingAFile(@TempDir Path tempDir) throws Exception
    {
        try (Jpa jpa = new Jpa(tempDir.resolve("normalized-csv-scope")))
        {
            Seed seed = seed(jpa);
            persistReviewFacts(jpa, seed.scaAccountId(), "SCA");
            BankStatementCsvExportService service = new BankStatementCsvExportService(
                    jpa, () -> tempDir.resolve("active-database"));
            Path empty = tempDir.resolve("empty.csv");
            assertThrows(IllegalArgumentException.class, () -> service.export(new BankStatementExportRequest(
                    "SCA", seed.scaAccountId(),
                    LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31), empty, false)));
            assertFalse(Files.exists(empty));

            Path crossCompany = tempDir.resolve("cross-company.csv");
            assertThrows(IllegalArgumentException.class, () -> service.export(new BankStatementExportRequest(
                    "SCA", seed.otherAccountId(),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), crossCompany, false)));
            assertFalse(Files.exists(crossCompany));

            Path activeDatabase = tempDir.resolve("active-database.mv.db");
            Files.writeString(activeDatabase, "database");
            assertThrows(IllegalArgumentException.class, () -> service.export(new BankStatementExportRequest(
                    "SCA", seed.scaAccountId(),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), activeDatabase, true)));
            assertEquals("database", Files.readString(activeDatabase));
        }
    }

    @Test
    public void productionExporterDoesNotTreatCanonicalTransactionsAsStatementRows() throws Exception
    {
        String source = Files.readString(Path.of(
                "src/main/java/org/nonprofitbookkeeping/interchange/bank/BankStatementCsvExportService.java"));
        assertFalse(source.contains("from Txn"));
        assertFalse(source.contains("TxnSplit"));
        assertTrue(source.contains("from BankStatementLine"));
        assertTrue(source.contains("mt.portableId"));
    }

    private static Seed seed(Jpa jpa)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            em.createNativeQuery("INSERT INTO chart_of_accounts (id, name, version, status) VALUES "
                    + "(101, 'SCA Chart', '1', 'ACTIVE'), (102, 'Other Chart', '1', 'ACTIVE')")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO company (code, display_name, default_currency, active_chart_of_accounts_id) VALUES "
                    + "('SCA', 'SCA Branch', 'USD', 101), ('OTHER', 'Other Branch', 'USD', 102)")
                    .executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = "
                    + "(SELECT id FROM company WHERE code = 'SCA') WHERE id = 101")
                    .executeUpdate();
            em.createNativeQuery("UPDATE chart_of_accounts SET company_id = "
                    + "(SELECT id FROM company WHERE code = 'OTHER') WHERE id = 102")
                    .executeUpdate();
            em.createNativeQuery("INSERT INTO account (id, chart_id, code, name, account_type, subtype, normal_balance) VALUES "
                    + "(101, 101, '1000', 'SCA Checking', 'BANK', 'CASH', 'DEBIT'), "
                    + "(102, 102, '1000', 'Other Checking', 'BANK', 'CASH', 'DEBIT')")
                    .executeUpdate();
            em.getTransaction().commit();
        }

        BankConfigurationService configuration = new BankConfigurationService(jpa);
        Bank scaBank = configuration.createBank(new BankCommand(
                "SCA", "SCA Bank", "111000111", null, null, null, null, null, null, true));
        CompanyBankAccount sca = configuration.createBankAccount(new BankAccountCommand(
                "SCA", scaBank.getId(), 101L, "****4321", "SCA Checking",
                LocalDate.of(2026, 1, 1), BigDecimal.ZERO, BankingDataFormat.OFX,
                "111000111", "SCA-4321", null, true));
        Bank otherBank = configuration.createBank(new BankCommand(
                "OTHER", "Other Bank", "222000222", null, null, null, null, null, null, true));
        CompanyBankAccount other = configuration.createBankAccount(new BankAccountCommand(
                "OTHER", otherBank.getId(), 102L, "****9876", "Other Checking",
                LocalDate.of(2026, 1, 1), BigDecimal.ZERO, BankingDataFormat.OFX,
                "222000222", "OTHER-9876", null, true));
        return new Seed(sca.getId(), other.getId());
    }

    private static void persistReviewFacts(Jpa jpa, long bankAccountId, String companyCode)
    {
        try (EntityManager em = jpa.em())
        {
            em.getTransaction().begin();
            Company company = em.createQuery("select c from Company c where c.code = :code", Company.class)
                    .setParameter("code", companyCode).getSingleResult();
            CompanyBankAccount account = em.find(CompanyBankAccount.class, bankAccountId);
            BankImportBatch batch = new BankImportBatch();
            batch.setCompany(company);
            batch.setBankAccount(account);
            batch.setSourceName(companyCode + "-source.ofx");
            batch.setSourceFormat(BankImportBatch.SourceFormat.OFX);
            batch.setSourceInstitutionId(companyCode + "-FI");
            batch.setSourceBankId(companyCode + "-BANK");
            batch.setSourceAccountId(companyCode + "-ACCOUNT");
            batch.setSourceAccountType("CHECKING");
            batch.setCurrency("USD");
            batch.setStatementStartDate(LocalDate.of(2026, 6, 1));
            batch.setStatementEndDate(LocalDate.of(2026, 6, 30));
            batch.setLedgerBalance(new BigDecimal("1250.2500"));
            batch.setStatus(BankImportBatch.Status.IMPORTED);
            batch.setTotalLineCount(2);
            em.persist(batch);

            BankStatementLine early = line(
                    batch, company, account, 1, "FIT-EARLY", LocalDate.of(2026, 6, 3),
                    new BigDecimal("100.2500"), "Donation", BankStatementLine.Status.IMPORTED);
            BankStatementLine late = line(
                    batch, company, account, 2, "FIT-LATE", LocalDate.of(2026, 6, 20),
                    new BigDecimal("-25.0000"), "Gift, \"summer\"\r\nbenefit", BankStatementLine.Status.DUPLICATE);
            Txn matched = new Txn();
            matched.setCompany(company);
            matched.setTxnDate(LocalDate.of(2026, 6, 20));
            matched.setMemo("Explicitly matched canonical transaction");
            em.persist(matched);
            late.setMatchedTransaction(matched);
            em.persist(early);
            em.persist(late);
            ImportIssue probable = new ImportIssue();
            probable.setBatch(batch);
            probable.setStatementLine(early);
            probable.setSourceRowNumber(1);
            probable.setSeverity(ImportIssue.Severity.WARNING);
            probable.setCode("PROBABLE_DUPLICATE");
            probable.setMessage("Probable duplicate row.");
            em.persist(probable);
            em.getTransaction().commit();
        }
    }

    private static BankStatementLine line(
            BankImportBatch batch,
            Company company,
            CompanyBankAccount account,
            int rowNumber,
            String sourceId,
            LocalDate postedDate,
            BigDecimal amount,
            String payee,
            BankStatementLine.Status status)
    {
        BankStatementLine line = new BankStatementLine();
        line.setBatch(batch);
        line.setCompany(company);
        line.setBankAccount(account);
        line.setSourceRowNumber(rowNumber);
        line.setSourceTransactionId(sourceId);
        line.setDeterministicFingerprint("fingerprint-" + sourceId);
        line.setStatementAccountIdentifier(batch.getSourceAccountId());
        line.setTransactionDate(postedDate.minusDays(1));
        line.setPostedDate(postedDate);
        line.setAmount(amount);
        line.setCurrency("USD");
        line.setTransactionType(amount.signum() > 0 ? "CREDIT" : "DEBIT");
        line.setName(payee);
        line.setMemo("Memo for " + sourceId);
        line.setStatus(status);
        return line;
    }

    private static List<List<String>> parseCsv(String csv)
    {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < csv.length(); index++)
        {
            char value = csv.charAt(index);
            if (quoted)
            {
                if (value == '"' && index + 1 < csv.length() && csv.charAt(index + 1) == '"')
                {
                    field.append('"');
                    index++;
                }
                else if (value == '"')
                {
                    quoted = false;
                }
                else
                {
                    field.append(value);
                }
            }
            else if (value == '"')
            {
                quoted = true;
            }
            else if (value == ',')
            {
                row.add(field.toString());
                field.setLength(0);
            }
            else if (value == '\n')
            {
                row.add(field.toString());
                field.setLength(0);
                rows.add(List.copyOf(row));
                row.clear();
            }
            else
            {
                field.append(value);
            }
        }
        return List.copyOf(rows);
    }

    private record Seed(long scaAccountId, long otherAccountId) { }
}
