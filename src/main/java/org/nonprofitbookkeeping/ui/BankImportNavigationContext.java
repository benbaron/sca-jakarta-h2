package org.nonprofitbookkeeping.ui;

import java.util.Optional;
import java.util.OptionalLong;

/** Typed string handoff for exact-scope bank import navigation between production panels. */
final class BankImportNavigationContext
{
    private static final String IMPORT_PREFIX = "bank-import:account=";
    private static final String SESSION_SEPARATOR = ";reconciliation=";
    private static final String RETURN_PREFIX = "bank-import-return:reconciliation=";
    private static final String RECONCILIATION_PREFIX = "reconciliation:session=";

    private BankImportNavigationContext()
    {
    }

    static String forReconciliation(long bankAccountId, long reconciliationSessionId)
    {
        if (bankAccountId <= 0 || reconciliationSessionId <= 0)
        {
            throw new IllegalArgumentException("Configured bank account and reconciliation session IDs must be positive.");
        }
        return IMPORT_PREFIX + bankAccountId + SESSION_SEPARATOR + reconciliationSessionId;
    }

    static Optional<ImportRequest> parseImportRequest(String context)
    {
        if (context == null || !context.startsWith(IMPORT_PREFIX))
        {
            return Optional.empty();
        }
        int separator = context.indexOf(SESSION_SEPARATOR, IMPORT_PREFIX.length());
        if (separator < 0)
        {
            return Optional.empty();
        }
        try
        {
            long bankAccountId = Long.parseLong(context.substring(IMPORT_PREFIX.length(), separator));
            long reconciliationSessionId = Long.parseLong(context.substring(separator + SESSION_SEPARATOR.length()));
            if (bankAccountId <= 0 || reconciliationSessionId <= 0)
            {
                return Optional.empty();
            }
            return Optional.of(new ImportRequest(bankAccountId, reconciliationSessionId));
        }
        catch (NumberFormatException ex)
        {
            return Optional.empty();
        }
    }

    static String returnToReconciliation(long reconciliationSessionId)
    {
        if (reconciliationSessionId <= 0)
        {
            throw new IllegalArgumentException("Reconciliation session ID must be positive.");
        }
        return RETURN_PREFIX + reconciliationSessionId;
    }

    static OptionalLong parseReconciliationReturn(String context)
    {
        if (context == null || !context.startsWith(RETURN_PREFIX))
        {
            return OptionalLong.empty();
        }
        try
        {
            long sessionId = Long.parseLong(context.substring(RETURN_PREFIX.length()));
            return sessionId > 0 ? OptionalLong.of(sessionId) : OptionalLong.empty();
        }
        catch (NumberFormatException ex)
        {
            return OptionalLong.empty();
        }
    }

    static String forReconciliationSession(long reconciliationSessionId)
    {
        if (reconciliationSessionId <= 0)
        {
            throw new IllegalArgumentException("Reconciliation session ID must be positive.");
        }
        return RECONCILIATION_PREFIX + reconciliationSessionId;
    }

    static OptionalLong parseReconciliationSession(String context)
    {
        if (context == null || !context.startsWith(RECONCILIATION_PREFIX))
        {
            return OptionalLong.empty();
        }
        try
        {
            long sessionId = Long.parseLong(context.substring(RECONCILIATION_PREFIX.length()));
            return sessionId > 0 ? OptionalLong.of(sessionId) : OptionalLong.empty();
        }
        catch (NumberFormatException ex)
        {
            return OptionalLong.empty();
        }
    }

    record ImportRequest(long bankAccountId, long reconciliationSessionId)
    {
    }
}
