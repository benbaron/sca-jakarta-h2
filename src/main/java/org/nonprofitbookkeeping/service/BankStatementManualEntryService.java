package org.nonprofitbookkeeping.service;

import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.BankImportBatch;
import org.nonprofitbookkeeping.model.BankStatementLine;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.CompanyBankAccount;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Persists one explicitly entered bank statement fact inside a caller-owned transaction. */
public final class BankStatementManualEntryService
{
    public void addLine(
            EntityManager em,
            Company company,
            CompanyBankAccount bankAccount,
            LocalDate date,
            BigDecimal amount,
            String description,
            String reference)
    {
        Objects.requireNonNull(em, "em");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(bankAccount, "bankAccount");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(amount, "amount");
        if (!em.getTransaction().isActive())
        {
            throw new IllegalStateException("Manual bank statement entry requires an active caller-owned transaction.");
        }

        BankImportBatch batch = new BankImportBatch();
        batch.setCompany(company);
        batch.setBankAccount(bankAccount);
        batch.setSourceFormat(BankImportBatch.SourceFormat.OTHER);
        batch.setSourceName("Manual reconciliation entry");
        batch.setTotalLineCount(1);
        em.persist(batch);

        String sourceName = "Manual reconciliation entry";
        String fixedDescription = description == null ? null : description.strip();
        String fixedReference = reference == null ? null : reference.strip();
        BankStatementLine line = new BankStatementLine();
        line.setBatch(batch);
        line.setCompany(company);
        line.setBankAccount(bankAccount);
        line.setSourceRowNumber(1);
        line.setSourceTransactionId(fixedReference == null || fixedReference.isBlank()
                ? sourceName + "-1"
                : fixedReference);
        line.setDeterministicFingerprint(UUID.nameUUIDFromBytes(
                (sourceName + "1" + date + amount + fixedDescription)
                        .getBytes(StandardCharsets.UTF_8)).toString());
        line.setStatementAccountIdentifier(bankAccount.getMaskedAccountNumber());
        line.setTransactionDate(date);
        line.setPostedDate(date);
        line.setAmount(amount.setScale(4, java.math.RoundingMode.HALF_UP));
        line.setName(fixedDescription);
        line.setReference(fixedReference);
        line.setStatus(BankStatementLine.Status.IMPORTED);
        em.persist(line);
    }
}
