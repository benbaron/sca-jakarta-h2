package org.nonprofitbookkeeping.ui;

import org.nonprofitbookkeeping.service.TransactionCommand;
import org.nonprofitbookkeeping.service.TransactionCommandValidator;
import org.nonprofitbookkeeping.service.TransactionLineCommand;
import org.nonprofitbookkeeping.service.TransactionValidationResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared row model for spreadsheet-like transaction line editors.
 */
public class TransactionLineEditorModel
{
    private final TransactionCommandValidator validator;
    private final Map<Long, Option> accounts = new LinkedHashMap<>();
    private final Map<Long, Option> funds = new LinkedHashMap<>();
    private final Map<Long, Option> budgetCategories = new LinkedHashMap<>();
    private final Map<Long, Option> activities = new LinkedHashMap<>();
    private final Map<Long, Option> merchants = new LinkedHashMap<>();
    private final Map<Long, Option> counterparties = new LinkedHashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private boolean dirty;

    public TransactionLineEditorModel(TransactionCommandValidator validator)
    {
        this.validator = Objects.requireNonNull(validator, "validator");
        addRow();
        addRow();
        dirty = false;
    }

    public void replaceOptions(ReferenceData referenceData)
    {
        replace(accounts, referenceData.accounts());
        replace(funds, referenceData.funds());
        replace(budgetCategories, referenceData.budgetCategories());
        replace(activities, referenceData.activities());
        replace(merchants, referenceData.merchants());
        replace(counterparties, referenceData.counterparties());
    }

    public Row addRow()
    {
        Row row = new Row();
        rows.add(row);
        dirty = true;
        return row;
    }

    public boolean removeRow(int index)
    {
        if (index < 0 || index >= rows.size() || rows.size() <= 2)
        {
            return false;
        }
        rows.remove(index);
        dirty = true;
        return true;
    }

    public Totals totals()
    {
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (Row row : rows)
        {
            debitTotal = debitTotal.add(normalize(row.debit()));
            creditTotal = creditTotal.add(normalize(row.credit()));
        }
        return new Totals(debitTotal, creditTotal, debitTotal.subtract(creditTotal));
    }

    public TransactionValidationResult validate(LocalDate date, Long payeeId, String memo, Long bankAccountId)
    {
        return validator.validate(toCommand(date, payeeId, memo, bankAccountId));
    }

    public TransactionCommand toCommand(LocalDate date, Long payeeId, String memo, Long bankAccountId)
    {
        return new TransactionCommand(date, payeeId, memo, bankAccountId,
                rows.stream()
                        .filter(Row::hasAnyAccountingInput)
                        .map(Row::toCommand)
                        .toList());
    }

    public List<Row> rows()
    {
        return rows;
    }

    public boolean isDirty()
    {
        return dirty || rows.stream().anyMatch(Row::isDirty);
    }

    public void markClean()
    {
        dirty = false;
        rows.forEach(Row::markClean);
    }

    private static void replace(Map<Long, Option> target, List<Option> source)
    {
        target.clear();
        source.stream()
                .sorted(Comparator.comparing(Option::label))
                .forEach(option -> target.put(option.id(), option));
    }

    private static BigDecimal normalize(BigDecimal value)
    {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static Option option(Long id, String code, String name)
    {
        return new Option(id, code, name);
    }

    public static final class Row
    {
        private Long accountId;
        private Long fundId;
        private Long budgetCategoryId;
        private Long activityId;
        private Long merchantId;
        private Long counterpartyId;
        private BigDecimal debit;
        private BigDecimal credit;
        private boolean nmr;
        private String notes;
        private boolean dirty;

        public TransactionLineCommand toCommand()
        {
            return new TransactionLineCommand(accountId, fundId, budgetCategoryId, activityId, merchantId,
                    debit, credit, nmr, notes);
        }

        boolean hasAnyAccountingInput()
        {
            return accountId != null || fundId != null || budgetCategoryId != null || activityId != null
                    || merchantId != null || counterpartyId != null || normalize(debit).signum() != 0
                    || normalize(credit).signum() != 0 || nmr || (notes != null && !notes.isBlank());
        }

        public Optional<String> fieldError()
        {
            if (normalize(debit).signum() > 0 && normalize(credit).signum() > 0)
            {
                return Optional.of("Enter either debit or credit, not both.");
            }
            if (normalize(debit).signum() < 0 || normalize(credit).signum() < 0)
            {
                return Optional.of("Debit and credit cannot be negative.");
            }
            return Optional.empty();
        }

        public Long accountId() { return accountId; }
        public void setAccountId(Long accountId) { this.accountId = accountId; dirty = true; }
        public Long fundId() { return fundId; }
        public void setFundId(Long fundId) { this.fundId = fundId; dirty = true; }
        public Long budgetCategoryId() { return budgetCategoryId; }
        public void setBudgetCategoryId(Long budgetCategoryId) { this.budgetCategoryId = budgetCategoryId; dirty = true; }
        public Long activityId() { return activityId; }
        public void setActivityId(Long activityId) { this.activityId = activityId; dirty = true; }
        public Long merchantId() { return merchantId; }
        public void setMerchantId(Long merchantId) { this.merchantId = merchantId; dirty = true; }
        public Long counterpartyId() { return counterpartyId; }
        public void setCounterpartyId(Long counterpartyId) { this.counterpartyId = counterpartyId; dirty = true; }
        public BigDecimal debit() { return debit; }
        public void setDebit(BigDecimal debit) { this.debit = debit; dirty = true; }
        public BigDecimal credit() { return credit; }
        public void setCredit(BigDecimal credit) { this.credit = credit; dirty = true; }
        public boolean nmr() { return nmr; }
        public void setNmr(boolean nmr) { this.nmr = nmr; dirty = true; }
        public String notes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; dirty = true; }
        public boolean isDirty() { return dirty; }
        public void markClean() { dirty = false; }
    }

    public record Option(Long id, String code, String name)
    {
        public String label()
        {
            String safeCode = code == null ? "" : code;
            String safeName = name == null ? "" : name;
            return (safeCode + " — " + safeName).trim();
        }
    }

    public record ReferenceData(List<Option> accounts,
                                List<Option> funds,
                                List<Option> budgetCategories,
                                List<Option> activities,
                                List<Option> merchants,
                                List<Option> counterparties)
    {
        public ReferenceData
        {
            accounts = List.copyOf(accounts == null ? List.of() : accounts);
            funds = List.copyOf(funds == null ? List.of() : funds);
            budgetCategories = List.copyOf(budgetCategories == null ? List.of() : budgetCategories);
            activities = List.copyOf(activities == null ? List.of() : activities);
            merchants = List.copyOf(merchants == null ? List.of() : merchants);
            counterparties = List.copyOf(counterparties == null ? List.of() : counterparties);
        }
    }

    public record Totals(BigDecimal debitTotal, BigDecimal creditTotal, BigDecimal difference)
    {
    }
}
