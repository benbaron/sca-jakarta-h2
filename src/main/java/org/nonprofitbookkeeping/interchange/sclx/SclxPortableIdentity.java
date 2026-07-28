package org.nonprofitbookkeeping.interchange.sclx;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Creates deterministic, H2-independent portable identities from governed business keys.
 */
public final class SclxPortableIdentity
{
    private static final int MAX_ID_CODE_POINTS = 160;
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private SclxPortableIdentity()
    {
    }

    public static String organization(String companyCode)
    {
        return identity("organization", companyCode);
    }

    public static String account(String companyCode, String accountCode)
    {
        return identity("account", companyCode, accountCode);
    }

    public static String fund(String companyCode, String fundCode)
    {
        return identity("fund", companyCode, fundCode);
    }

    public static String activity(String companyCode, String activityCode)
    {
        return identity("activity", companyCode, activityCode);
    }

    public static String counterparty(String companyCode, String durableCounterpartyKey)
    {
        return identity("counterparty", companyCode, durableCounterpartyKey);
    }

    public static String merchant(String companyCode, String durableMerchantKey)
    {
        return identity("merchant", companyCode, durableMerchantKey);
    }

    public static String budget(String companyCode, int fiscalYear, String version)
    {
        return identity("budget", companyCode, Integer.toString(fiscalYear), version);
    }

    public static String budgetLine(
            String budgetId,
            String categoryCode,
            String accountId,
            String fundId,
            String periodMonth)
    {
        return identity(
                "budget-line",
                budgetId,
                categoryCode,
                nullablePart(accountId),
                nullablePart(fundId),
                nullablePart(periodMonth));
    }

    public static String budgetLine(String budgetId, String categoryCode, String accountId, String fundId)
    {
        return budgetLine(budgetId, categoryCode, accountId, fundId, null);
    }

    public static String transaction(String companyCode, String durableTransactionKey)
    {
        return identity("transaction", companyCode, durableTransactionKey);
    }

    public static String transactionLine(String transactionId, int ordinal)
    {
        if (ordinal < 1)
        {
            throw new IllegalArgumentException("transaction line ordinal must be positive");
        }
        return identity("transaction-line", transactionId, Integer.toString(ordinal));
    }

    public static String supplementalDetail(String transactionId, int ordinal)
    {
        if (ordinal < 1)
        {
            throw new IllegalArgumentException("supplemental detail ordinal must be positive");
        }
        return identity("supplemental-detail", transactionId, Integer.toString(ordinal));
    }

    static String identity(String namespace, String... parts)
    {
        String normalizedNamespace = normalizeRequired(namespace, "namespace").toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalizedNamespace);
        for (int index = 0; index < parts.length; index++)
        {
            result.append(':').append(encode(normalizeRequired(parts[index], "part[" + index + "]")));
        }
        if (result.codePointCount(0, result.length()) > MAX_ID_CODE_POINTS)
        {
            throw new IllegalArgumentException("portable identity exceeds " + MAX_ID_CODE_POINTS + " Unicode code points");
        }
        return result.toString();
    }

    private static String nullablePart(String value)
    {
        return value == null ? "-" : value;
    }

    private static String normalizeRequired(String value, String field)
    {
        Objects.requireNonNull(value, field);
        String normalized = Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
        if (normalized.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String encode(String value)
    {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte current : bytes)
        {
            int unsigned = Byte.toUnsignedInt(current);
            if (isUnreserved(unsigned))
            {
                encoded.append((char) unsigned);
            }
            else
            {
                encoded.append('%').append(HEX.toHexDigits(current));
            }
        }
        return encoded.toString();
    }

    private static boolean isUnreserved(int value)
    {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-' || value == '.' || value == '_' || value == '~';
    }
}
