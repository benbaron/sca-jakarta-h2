package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared command validation for canonical transaction entry.
 */
public class TransactionCommandValidator
{
    public TransactionValidationResult validate(TransactionCommand command)
    {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (command == null)
        {
            errors.add("Transaction command is required.");
            return TransactionValidationResult.invalid(errors, warnings);
        }

        if (command.date() == null)
        {
            errors.add("Transaction date is required.");
        }
        else if (command.date().isAfter(LocalDate.now()))
        {
            warnings.add("Transaction date is in the future.");
        }

        List<TransactionLineCommand> lines = command.lines();
        if (lines.size() < 2)
        {
            errors.add("A transaction requires at least two meaningful lines.");
        }

        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        int meaningfulLines = 0;

        for (int i = 0; i < lines.size(); i++)
        {
            TransactionLineCommand line = lines.get(i);
            int row = i + 1;
            if (line == null)
            {
                errors.add("Line " + row + " is required.");
                continue;
            }

            if (line.accountId() == null)
            {
                errors.add("Line " + row + " account is required.");
            }
            if (line.fundId() == null)
            {
                errors.add("Line " + row + " fund is required.");
            }

            BigDecimal debit = normalize(line.debit());
            BigDecimal credit = normalize(line.credit());
            if (debit.compareTo(BigDecimal.ZERO) < 0)
            {
                errors.add("Line " + row + " debit cannot be negative.");
            }
            if (credit.compareTo(BigDecimal.ZERO) < 0)
            {
                errors.add("Line " + row + " credit cannot be negative.");
            }
            if (debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) > 0)
            {
                errors.add("Line " + row + " cannot contain both debit and credit.");
            }
            if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0)
            {
                errors.add("Line " + row + " must contain a non-zero debit or credit.");
            }
            else
            {
                meaningfulLines++;
                debitTotal = debitTotal.add(debit);
                creditTotal = creditTotal.add(credit);
            }
        }

        if (meaningfulLines < 2)
        {
            errors.add("A transaction requires at least two non-zero accounting lines.");
        }
        if (debitTotal.compareTo(creditTotal) != 0)
        {
            errors.add("Transaction debits must equal credits. Debits=" + debitTotal + " Credits=" + creditTotal + ".");
        }

        if (errors.isEmpty())
        {
            return TransactionValidationResult.ok(warnings);
        }
        return TransactionValidationResult.invalid(errors, warnings);
    }

    private BigDecimal normalize(BigDecimal amount)
    {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
