package org.nonprofitbookkeeping.interchange.bank;

import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.service.BankImportNormalizationService;

import java.nio.file.Path;
import java.util.List;

/** Exact-file, exact-target preview used by the durable statement-review commit. */
public record BankStatementReviewPreview(
        Path source,
        String sourceHash,
        String companyCode,
        long bankAccountId,
        String configuredAccountName,
        BankStatementDocument document,
        BankStatementAccountMatcher.Status accountMatchStatus,
        List<BankImportNormalizationService.NormalizedBankStatementLine> lines,
        List<InterchangeValidationMessage> messages)
{
    public BankStatementReviewPreview
    {
        source = source == null ? null : source.toAbsolutePath().normalize();
        if (source == null || sourceHash == null || sourceHash.isBlank())
        {
            throw new IllegalArgumentException("Preview source and hash are required.");
        }
        companyCode = companyCode == null ? "" : companyCode.trim();
        configuredAccountName = configuredAccountName == null ? "" : configuredAccountName.trim();
        lines = lines == null ? List.of() : List.copyOf(lines);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public boolean hasBlockingMessages()
    {
        return messages.stream().anyMatch(InterchangeValidationMessage::blocking);
    }

    public boolean commitAllowed(boolean identityConfirmed)
    {
        return !hasBlockingMessages()
                && accountMatchStatus != BankStatementAccountMatcher.Status.BLOCKING
                && (accountMatchStatus != BankStatementAccountMatcher.Status.CONFIRMATION_REQUIRED
                    || identityConfirmed);
    }
}
