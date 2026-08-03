package org.nonprofitbookkeeping.interchange.bank;

import org.nonprofitbookkeeping.model.BankingDataFormat;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Bounded RFC 4180-style parser driven only by an explicit validated mapping profile. */
public final class BankCsvParser
{
    static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    static final int MAX_RECORDS = 1_000_000;
    static final int MAX_COLUMNS = 128;
    static final int MAX_RECORD_CHARS = 4 * 1024 * 1024;
    static final int MAX_FIELD_CHARS = 1024 * 1024;

    public ParsedCsv parse(Path source, BankCsvMappingProfileDefinition profile)
    {
        if (source == null || profile == null)
        {
            throw new IllegalArgumentException("Bank CSV source and mapping profile are required.");
        }
        Path exact = source.toAbsolutePath().normalize();
        byte[] bytes = read(exact);
        String csv = decode(bytes, profile.encoding());
        List<CsvRecord> records = records(csv, profile.delimiter());
        if (records.size() < 2)
        {
            throw new IllegalArgumentException("Bank CSV requires a header and at least one data row.");
        }
        List<String> header = records.get(0).fields();
        if (header.size() > MAX_COLUMNS)
        {
            throw new IllegalArgumentException("Bank CSV exceeds 128 columns.");
        }
        Map<String, Integer> headerIndexes = new HashMap<>();
        for (int i = 0; i < header.size(); i++)
        {
            String normalized = BankCsvMappingProfileDefinition.normalizeHeader(header.get(i));
            if (normalized.isBlank() || headerIndexes.putIfAbsent(normalized, i) != null)
            {
                throw new IllegalArgumentException("Bank CSV contains a blank or duplicate normalized header: " + header.get(i) + ".");
            }
        }
        Map<String, Integer> mapped = new HashMap<>();
        for (Map.Entry<String, String> entry : profile.columns().entrySet())
        {
            Integer index = headerIndexes.get(BankCsvMappingProfileDefinition.normalizeHeader(entry.getValue()));
            if (index == null)
            {
                throw new IllegalArgumentException("Bank CSV is missing mapped column: " + entry.getValue() + ".");
            }
            mapped.put(entry.getKey(), index);
        }

        List<BankStatementDocument.Transaction> transactions = new ArrayList<>();
        List<OriginalRow> originalRows = new ArrayList<>();
        String accountId = profile.fixedAccountId();
        String currency = profile.fixedCurrency();
        Set<String> rowAccounts = new HashSet<>();
        Set<String> rowCurrencies = new HashSet<>();
        for (int i = 1; i < records.size(); i++)
        {
            CsvRecord record = records.get(i);
            if (record.fields().stream().allMatch(String::isBlank)) continue;
            if (record.fields().size() != header.size())
            {
                throw rowError(record, "has " + record.fields().size()
                        + " columns but the header has " + header.size() + ".");
            }
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, Integer> entry : mapped.entrySet())
            {
                values.put(entry.getKey(), field(record.fields().get(entry.getValue()), profile));
            }
            String rowAccount = first(profile.fixedAccountId(), values.get("accountId"));
            String rowCurrency = first(profile.fixedCurrency(), values.get("currency")).toUpperCase(Locale.ROOT);
            if (rowAccount.isBlank()) throw rowError(record, "has no source account identity.");
            if (!rowCurrency.matches("[A-Z]{3}")) throw rowError(record, "has an invalid currency.");
            rowAccounts.add(rowAccount.toUpperCase(Locale.ROOT));
            rowCurrencies.add(rowCurrency);
            LocalDate posted = date(values.get("postedDate"), profile, record, "posted date", false);
            LocalDate transaction = date(values.get("transactionDate"), profile, record, "transaction date", false);
            if (posted == null && transaction == null)
            {
                throw rowError(record, "has neither a posted date nor transaction date.");
            }
            BigDecimal amount = amount(values, profile, record);
            transactions.add(new BankStatementDocument.Transaction(
                    record.lineNumber(), transaction, posted, amount,
                    values.get("sourceTransactionId"), values.get("transactionType"),
                    values.get("payeeName"), values.get("memo"), values.get("checkNumber"),
                    values.get("reference"), "", ""));
            originalRows.add(new OriginalRow(record.lineNumber(), record.original(), Map.copyOf(values)));
        }
        if (transactions.isEmpty())
        {
            throw new IllegalArgumentException("Bank CSV contains no data rows.");
        }
        if (rowAccounts.size() != 1)
        {
            throw new IllegalArgumentException("Bank CSV contains more than one source account identity.");
        }
        if (rowCurrencies.size() != 1)
        {
            throw new IllegalArgumentException("Bank CSV contains more than one currency.");
        }
        accountId = rowAccounts.iterator().next();
        currency = rowCurrencies.iterator().next();
        LocalDate start = transactions.stream()
                .map(value -> value.postedDate() == null ? value.transactionDate() : value.postedDate())
                .min(LocalDate::compareTo).orElseThrow();
        LocalDate end = transactions.stream()
                .map(value -> value.postedDate() == null ? value.transactionDate() : value.postedDate())
                .max(LocalDate::compareTo).orElseThrow();
        BankStatementDocument document = new BankStatementDocument(
                exact.getFileName().toString(), BankingDataFormat.CSV,
                BankStatementDocument.Variant.MAPPED_CSV, profile.version(), profile.encoding(),
                new BankStatementDocument.AccountIdentity("", "", accountId, ""),
                currency, start, end, null, null, transactions, List.of());
        return new ParsedCsv(document, originalRows, header);
    }

    static byte[] read(Path exact)
    {
        try
        {
            long size = Files.size(exact);
            if (size <= 0 || size > MAX_FILE_BYTES)
            {
                throw new IllegalArgumentException("Bank CSV must contain 1 to 67108864 bytes.");
            }
            return Files.readAllBytes(exact);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Cannot read bank CSV: " + exact, ex);
        }
    }

    static String decode(byte[] bytes, String encoding)
    {
        if (bytes.length >= 2 && ((bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE)
                || (bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF)))
        {
            throw new IllegalArgumentException("UTF-16/32 bank CSV is not supported.");
        }
        for (byte value : bytes)
        {
            if (value == 0) throw new IllegalArgumentException("Bank CSV contains a prohibited NUL byte.");
        }
        Charset charset = switch (encoding)
        {
            case "UTF-8" -> StandardCharsets.UTF_8;
            case "US-ASCII" -> StandardCharsets.US_ASCII;
            case "WINDOWS-1252" -> Charset.forName("windows-1252");
            default -> throw new IllegalArgumentException("Unsupported bank CSV encoding: " + encoding + ".");
        };
        try
        {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (CharacterCodingException ex)
        {
            throw new IllegalArgumentException("Bank CSV bytes do not match profile encoding " + encoding + ".", ex);
        }
    }

    static List<CsvRecord> records(String csv, char delimiter)
    {
        List<CsvRecord> result = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        StringBuilder original = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        int line = 1;
        int recordLine = 1;
        for (int i = 0; i < csv.length(); i++)
        {
            char ch = csv.charAt(i);
            original.append(ch);
            if (original.length() > MAX_RECORD_CHARS)
            {
                throw new IllegalArgumentException("Bank CSV logical record exceeds 4 MiB at line " + recordLine + ".");
            }
            if (quoted)
            {
                if (ch == '"')
                {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"')
                    {
                        field.append('"');
                        original.append(csv.charAt(++i));
                    }
                    else
                    {
                        quoted = false;
                        afterQuote = true;
                    }
                }
                else
                {
                    field.append(ch);
                    if (ch == '\n') line++;
                    else if (ch == '\r' && (i + 1 >= csv.length() || csv.charAt(i + 1) != '\n')) line++;
                }
            }
            else if (afterQuote)
            {
                if (ch == delimiter)
                {
                    addField(fields, field, recordLine);
                    afterQuote = false;
                }
                else if (ch == '\r' || ch == '\n')
                {
                    addField(fields, field, recordLine);
                    finishRecord(result, fields, original, recordLine, ch);
                    afterQuote = false;
                    if (ch == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n')
                    {
                        i++;
                    }
                    line++;
                    recordLine = line;
                }
                else if (!Character.isWhitespace(ch))
                {
                    throw new IllegalArgumentException("Unexpected content after quoted bank CSV field at line " + recordLine + ".");
                }
            }
            else if (ch == '"')
            {
                if (field.length() != 0)
                {
                    throw new IllegalArgumentException("Unexpected quote in bank CSV field at line " + recordLine + ".");
                }
                quoted = true;
            }
            else if (ch == delimiter)
            {
                addField(fields, field, recordLine);
            }
            else if (ch == '\r' || ch == '\n')
            {
                addField(fields, field, recordLine);
                finishRecord(result, fields, original, recordLine, ch);
                if (ch == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n')
                {
                    i++;
                }
                line++;
                recordLine = line;
            }
            else
            {
                field.append(ch);
            }
            if (field.length() > MAX_FIELD_CHARS)
            {
                throw new IllegalArgumentException("Bank CSV field exceeds 1 MiB at line " + recordLine + ".");
            }
        }
        if (quoted) throw new IllegalArgumentException("Bank CSV contains an unclosed quoted field at line " + recordLine + ".");
        if (field.length() > 0 || !fields.isEmpty() || original.length() > 0)
        {
            addField(fields, field, recordLine);
            finishRecord(result, fields, original, recordLine, '\0');
        }
        if (result.size() > MAX_RECORDS + 1)
        {
            throw new IllegalArgumentException("Bank CSV exceeds 1000000 statement records.");
        }
        return result;
    }

    private static void addField(List<String> fields, StringBuilder field, int line)
    {
        fields.add(field.toString());
        field.setLength(0);
        if (fields.size() > MAX_COLUMNS)
        {
            throw new IllegalArgumentException("Bank CSV exceeds 128 columns at line " + line + ".");
        }
    }

    private static void finishRecord(
            List<CsvRecord> result,
            List<String> fields,
            StringBuilder original,
            int recordLine,
            char lineEnding)
    {
        int ending = lineEnding == '\0' ? 0 : 1;
        String raw = original.substring(0, Math.max(0, original.length() - ending));
        result.add(new CsvRecord(recordLine, raw, List.copyOf(fields)));
        fields.clear();
        original.setLength(0);
        if (result.size() > MAX_RECORDS + 1)
        {
            throw new IllegalArgumentException("Bank CSV exceeds 1000000 statement records.");
        }
    }

    private static String field(String value, BankCsvMappingProfileDefinition profile)
    {
        String result = value == null ? "" : value;
        if (profile.trimFields()) result = result.trim();
        return profile.blankAsNull() && result.isBlank() ? "" : result;
    }

    private static LocalDate date(
            String value,
            BankCsvMappingProfileDefinition profile,
            CsvRecord record,
            String label,
            boolean required)
    {
        if (value == null || value.isBlank())
        {
            if (required) throw rowError(record, "is missing " + label + ".");
            return null;
        }
        Locale locale = profile.locale().isBlank()
                ? Locale.ROOT : Locale.forLanguageTag(profile.locale());
        for (String pattern : profile.dateFormats())
        {
            try
            {
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern, locale));
            }
            catch (DateTimeParseException ignored)
            {
                // Try the next explicit profile pattern.
            }
        }
        throw rowError(record, "has invalid " + label + ": " + value + ".");
    }

    private static BigDecimal amount(
            Map<String, String> values,
            BankCsvMappingProfileDefinition profile,
            CsvRecord record)
    {
        BigDecimal result;
        if (profile.amountMode() == BankCsvMappingProfileDefinition.AmountMode.SIGNED_AMOUNT)
        {
            result = decimal(values.get("amount"), profile, record, "amount", true);
        }
        else
        {
            String debitText = first("", values.get("debit"));
            String creditText = first("", values.get("credit"));
            if (debitText.isBlank() == creditText.isBlank())
            {
                throw rowError(record, "must contain exactly one of debit or credit.");
            }
            BigDecimal debit = debitText.isBlank() ? BigDecimal.ZERO
                    : decimal(debitText, profile, record, "debit", true);
            BigDecimal credit = creditText.isBlank() ? BigDecimal.ZERO
                    : decimal(creditText, profile, record, "credit", true);
            requireNonnegative(debit, record, "debit");
            requireNonnegative(credit, record, "credit");
            result = credit.subtract(debit);
        }
        if (result.signum() == 0) throw rowError(record, "has a zero amount.");
        if (result.scale() > 4 || result.precision() > 19)
        {
            throw rowError(record, "amount exceeds DECIMAL(19,4).");
        }
        return result.setScale(Math.max(0, result.scale()), RoundingMode.UNNECESSARY);
    }

    private static BigDecimal decimal(
            String value,
            BankCsvMappingProfileDefinition profile,
            CsvRecord record,
            String label,
            boolean required)
    {
        String raw = value == null ? "" : value.trim();
        if (raw.isBlank())
        {
            if (required) throw rowError(record, "is missing " + label + ".");
            return BigDecimal.ZERO;
        }
        String grouping = ".".equals(profile.decimalSeparator()) ? "," : ".";
        if ("REJECT".equals(profile.groupingPolicy()) && raw.contains(grouping))
        {
            throw rowError(record, label + " contains prohibited grouping separators.");
        }
        if ("ALLOW".equals(profile.groupingPolicy())) raw = raw.replace(grouping, "");
        if (",".equals(profile.decimalSeparator())) raw = raw.replace(',', '.');
        try
        {
            return new BigDecimal(raw);
        }
        catch (NumberFormatException ex)
        {
            throw rowError(record, "has invalid " + label + ": " + value + ".");
        }
    }

    private static void requireNonnegative(BigDecimal value, CsvRecord record, String label)
    {
        if (value.signum() < 0) throw rowError(record, label + " must be nonnegative.");
    }

    private static String first(String preferred, String fallback)
    {
        return preferred != null && !preferred.isBlank() ? preferred.trim()
                : fallback == null ? "" : fallback.trim();
    }

    private static IllegalArgumentException rowError(CsvRecord record, String message)
    {
        return new IllegalArgumentException("Bank CSV row " + record.lineNumber() + " " + message);
    }

    public record ParsedCsv(
            BankStatementDocument document,
            List<OriginalRow> originalRows,
            List<String> headers)
    {
        public ParsedCsv
        {
            originalRows = List.copyOf(originalRows);
            headers = List.copyOf(headers);
        }
    }

    public record OriginalRow(int sourceRowNumber, String originalText, Map<String, String> mappedValues)
    {
        public OriginalRow
        {
            mappedValues = Map.copyOf(mappedValues);
        }
    }

    record CsvRecord(int lineNumber, String original, List<String> fields) { }
}
