package org.nonprofitbookkeeping.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts transaction slices from OFX/QFX statement payloads.
 */
public class OfxQfxTransactionExtractor
{
    private static final Pattern TRANSACTION_BLOCK_PATTERN = Pattern.compile("(?is)<STMTTRN>(.*?)</STMTTRN>");

    public List<BankTransactionRecord> extract(String payload)
    {
        String body = payload == null ? "" : payload;
        Matcher matcher = TRANSACTION_BLOCK_PATTERN.matcher(body);
        List<BankTransactionRecord> transactions = new ArrayList<>();

        while (matcher.find())
        {
            String block = matcher.group(1);
            transactions.add(new BankTransactionRecord(
                    extractTag(block, "FITID"),
                    extractTag(block, "DTPOSTED"),
                    parseAmount(extractTag(block, "TRNAMT")),
                    extractTag(block, "TRNTYPE"),
                    extractTag(block, "NAME"),
                    extractTag(block, "MEMO")));
        }

        return List.copyOf(transactions);
    }

    private BigDecimal parseAmount(String raw)
    {
        if (raw == null || raw.isBlank())
        {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(raw.trim());
    }

    private String extractTag(String block, String tag)
    {
        Pattern enclosed = Pattern.compile("(?is)<" + tag + ">(.*?)</" + tag + ">", Pattern.CASE_INSENSITIVE);
        Matcher enclosedMatch = enclosed.matcher(block);
        if (enclosedMatch.find())
        {
            return enclosedMatch.group(1).trim();
        }

        Pattern oneLine = Pattern.compile("(?im)<" + tag + ">([^<\\r\\n]+)");
        Matcher oneLineMatch = oneLine.matcher(block);
        if (oneLineMatch.find())
        {
            return oneLineMatch.group(1).trim();
        }

        return "";
    }
}
