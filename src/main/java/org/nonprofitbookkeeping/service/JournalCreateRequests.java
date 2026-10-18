package org.nonprofitbookkeeping.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.nonprofitbookkeeping.model.AuditEvent;
import org.nonprofitbookkeeping.model.Company;
import org.nonprofitbookkeeping.model.Txn;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Native create receipts reuse the one canonical transaction and its retained create audit. */
final class JournalCreateRequests
{
    private static final String PREFIX = "Journal create request v1 SHA-256: ";
    private static final ObjectMapper JSON = new ObjectMapper();

    private JournalCreateRequests()
    {
    }

    static String reason(Company company, TransactionCommand command)
    {
        boolean classified = command.lines().stream().anyMatch(line -> line.expenseFunction() != null);
        List<Object> lines = new ArrayList<>();
        for (var line : command.lines())
        {
            var payment = line.payment();
            List<Object> facts = new ArrayList<>(Arrays.asList(line.accountId(), line.fundId(), line.budgetCategoryId(), line.activityId(), line.merchantId(),
                    money(line.debit()), money(line.credit()), line.nmr(), line.notes(), payment == null ? null : Arrays.asList(
                            payment.method().name(), payment.reference(), date(payment.issuedOn()), date(payment.deliveredOn()),
                            payment.evidenceReference(), date(payment.reviewedOn()), payment.reviewNote())));
            if (classified)
            {
                facts.add(line.expenseFunction() == null ? null : line.expenseFunction().name());
            }
            lines.add(facts);
        }
        List<Object> details = new ArrayList<>();
        int order = 0;
        for (var row : command.supplementalLines())
        {
            var merchant = row.merchantSettlement();
            List<Object> merchantFacts = merchant == null ? null : Arrays.asList(merchant.role(), exactMoney(merchant.withheldUnconfirmed()),
                    text(merchant.evidenceReference()), merchant.components().stream()
                            .sorted(Comparator.comparing(MerchantSettlementCommand.Component::kind).thenComparing(MerchantSettlementCommand.Component::transactionLineIndex))
                            .map(c -> Arrays.asList(c.kind(), c.transactionLineIndex())).toList());
            details.add(Arrays.asList(row.kind(), text(row.entryRef()), text(row.counterparty()), row.description().trim(), text(row.reference()),
                    money(row.amount()), date(row.dueDate()), date(row.startDate()), date(row.endDate()), text(row.notes()),
                    row.lineOrder() == null ? order : row.lineOrder(), row.itemId() == null ? null : row.itemId().toString(), row.itemEffect(), row.transactionLineIndex(),
                    row.nmrRecipientId(), text(row.nmrMovement()), text(row.nmrEvidenceReference()), merchantFacts));
            order++;
        }
        try
        {
            byte[] input = JSON.writeValueAsBytes(Arrays.asList(classified ? "journal-create-v2" : "journal-create-v1", company.getId(), date(command.date()), command.payeeId(),
                    command.memo(), command.bankAccountId(), lines, details));
            return (classified ? "Journal create request v2 SHA-256: " : PREFIX) + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        }
        catch (JsonProcessingException | NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("Cannot fingerprint the journal create request.", ex);
        }
    }

    static Txn replay(EntityManager em, Company company, UUID requestId, String reason)
    {
        var receipts = em.createQuery("from AuditEvent a where a.portableId = :request", AuditEvent.class)
                .setParameter("request", requestId).getResultList();
        var transactions = em.createQuery("from Txn t where t.portableId = :request", Txn.class)
                .setParameter("request", requestId).getResultList();
        if (receipts.isEmpty())
        {
            if (!transactions.isEmpty())
            {
                throw new PostingException("Journal save identity already belongs to another operation. Open the existing transaction or use New.");
            }
            return null;
        }
        AuditEvent receipt = receipts.get(0);
        if (receipt.getCompany() == null || !company.getId().equals(receipt.getCompany().getId())
                || !"TRANSACTION_ENTERED".equals(receipt.getActionType()) || !"Txn".equals(receipt.getEntityType()))
        {
            throw new PostingException("Journal save request is already bound to another company or operation.");
        }
        if (!Objects.equals(receipt.getReason(), reason))
        {
            throw new PostingException("This journal save request was already accepted with different details. Open the saved transaction or use New for another entry.");
        }
        if (transactions.isEmpty())
        {
            throw new PostingException("The transaction for this save request was deleted. A retry cannot recreate it; use New for a distinct entry.");
        }
        Txn txn = transactions.get(0);
        if (txn.getCompany() == null || !company.getId().equals(txn.getCompany().getId()))
        {
            throw new PostingException("Journal save request and transaction ownership do not agree.");
        }
        return txn;
    }

    private static String money(BigDecimal value)
    {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private static String exactMoney(BigDecimal value)
    {
        return value == null ? null : value.setScale(4, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String date(java.time.LocalDate value)
    {
        return value == null ? null : value.toString();
    }

    private static String text(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
