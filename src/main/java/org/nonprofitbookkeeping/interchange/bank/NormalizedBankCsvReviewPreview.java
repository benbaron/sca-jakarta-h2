package org.nonprofitbookkeeping.interchange.bank;

import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;

import java.nio.file.Path;
import java.util.List;

/** Exact-file, exact-target preview for normalized bank CSV semantic re-import. */
public record NormalizedBankCsvReviewPreview(
        Path source,
        String sourceHash,
        String companyCode,
        long bankAccountId,
        String configuredAccountName,
        NormalizedBankCsvDocument document,
        BankStatementAccountMatcher.Status accountMatchStatus,
        List<InterchangeValidationMessage> messages)
{
    public NormalizedBankCsvReviewPreview
    {
        source = source == null ? null : source.toAbsolutePath().normalize();
        sourceHash = sourceHash == null ? "" : sourceHash.trim();
        companyCode = companyCode == null ? "" : companyCode.trim();
        configuredAccountName = configuredAccountName == null ? "" : configuredAccountName.trim();
        messages = messages == null ? List.of() : List.copyOf(messages);
        if (source == null || sourceHash.isBlank() || companyCode.isBlank() || document == null)
        {
            throw new IllegalArgumentException("Normalized bank CSV preview scope is incomplete.");
        }
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
