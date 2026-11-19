package org.nonprofitbookkeeping.interchange.exchequer;

import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;
import org.nonprofitbookkeeping.interchange.csv.CsvRecordReader;
import org.nonprofitbookkeeping.interchange.csv.CsvRecordReader.Record;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.nonprofitbookkeeping.interchange.exchequer.ExchequerReportCsvDocument.*;

/** Reads the 2026-03 Exchequer Ledger CSV into review facts; never filters by quarter or writes a ledger. */
public final class ExchequerReportCsvImporter
{
    public static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    private static final int[] SPLIT_STARTS = {32, 58, 84, 110};
    private static final String TEMPLATE_VERSION = "2026-03";
    private static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("M/d/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Pattern MONEY = Pattern.compile("[+-]?\\$?(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?");
    private static final Pattern PURPOSE = Pattern.compile("^(AR|OA|FR)(?:\\s*\\([^)]*\\))?$", Pattern.CASE_INSENSITIVE);
    private static final CsvRecordReader.Limits LIMITS = new CsvRecordReader.Limits(
            "Exchequer CSV", 1024, 10_000, 4 * 1024 * 1024, 1024 * 1024, 2_000_000);

    public ExchequerReportCsvDocument read(Path source)
    {
        return read(source, StandardCharsets.UTF_8);
    }

    /** Non-UTF-8 input requires an explicit charset; malformed bytes are never replaced silently. */
    public ExchequerReportCsvDocument read(Path source, Charset encoding)
    {
        if (source == null || encoding == null)
        {
            throw new IllegalArgumentException("Exchequer CSV source and encoding are required");
        }
        Path exact = source.toAbsolutePath().normalize();
        byte[] bytes;
        try (var stream = Files.newInputStream(exact))
        {
            bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
        }
        catch (IOException ex)
        {
            throw new IllegalArgumentException("Cannot read Exchequer CSV", ex);
        }
        if (bytes.length > MAX_FILE_BYTES)
        {
            throw new IllegalArgumentException("Exchequer CSV exceeds 64 MiB");
        }
        String text;
        try
        {
            text = encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (java.nio.charset.CharacterCodingException ex)
        {
            throw new IllegalArgumentException("Exchequer CSV bytes do not match the explicit " + encoding.name() + " encoding", ex);
        }
        if (text.startsWith("\uFEFF"))
        {
            text = text.substring(1);
        }
        List<Record> records = CsvRecordReader.read(text, ',', LIMITS);
        requireLayout(records);
        int width = records.get(6).fields().size();
        List<InterchangeValidationMessage> messages = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        Set<Integer> seenRows = new HashSet<>();
        int numbered = 0;
        int placeholders = 0;
        int helpers = 0;
        Record opening = records.get(7);
        if (opening.fields().size() != width)
        {
            throw new IllegalArgumentException("Exchequer opening row width differs from the header");
        }
        OpeningBalance openingBalance = new OpeningBalance(opening.lineNumber(), records.get(5).fields().get(1),
                date(opening.fields().get(3), opening.lineNumber(), "opening date", messages),
                decimal(opening.fields().get(1), opening.lineNumber(), "opening balance", messages), opening.fields());
        for (int index = 8; index < records.size(); index++)
        {
            Record record = records.get(index);
            List<String> fields = record.fields();
            if (fields.size() != width)
            {
                if (fields.stream().allMatch(String::isBlank))
                {
                    helpers++;
                    continue;
                }
                throw new IllegalArgumentException("Exchequer CSV row " + record.lineNumber() + " width differs from the header");
            }
            boolean numberedRow = !fields.get(0).isBlank();
            if (numberedRow)
            {
                numbered++;
            }
            if (!substantive(fields))
            {
                if (numberedRow)
                {
                    placeholders++;
                }
                else
                {
                    helpers++;
                }
                continue;
            }
            int before = messages.size();
            Integer rowNumber = integer(fields.get(0), record.lineNumber(), "worksheet row", messages);
            if (rowNumber == null)
            {
                error(messages, record.lineNumber(), "ROW_NUMBER", "A populated record needs a positive worksheet row number");
            }
            else if (!seenRows.add(rowNumber))
            {
                error(messages, record.lineNumber(), "DUPLICATE_ROW", "Duplicate worksheet row number " + rowNumber);
            }
            rows.add(row(record, rowNumber, before, messages));
        }
        Set<Integer> retained = new HashSet<>();
        rows.stream().map(Row::worksheetRow).filter(java.util.Objects::nonNull).forEach(retained::add);
        for (Row row : rows)
        {
            if (row.linkedRow() != null && !retained.contains(row.linkedRow()))
                warning(messages, row.sourceLine(), "UNRESOLVED_LINK", "Linked worksheet row is not present among retained records; no relationship was inferred");
        }
        Metadata metadata = new Metadata(records.get(1).fields().get(1), records.get(2).fields().get(1), TEMPLATE_VERSION,
                openingBalance, records.subList(0, 7).stream().map(Record::fields).toList());
        return new ExchequerReportCsvDocument(exact, sha256(bytes), encoding.name(), metadata, records.size(), numbered,
                placeholders, helpers, rows, messages);
    }

    private static Row row(Record record, Integer rowNumber, int before, List<InterchangeValidationMessage> messages)
    {
        List<String> fields = record.fields();
        int line = record.lineNumber();
        LocalDate date = date(fields.get(3), line, "transaction date", messages);
        LocalDate statement = statementDate(fields.get(4), line, messages);
        LocalDate incoming = date(fields.get(6), line, "incoming date", messages);
        LocalDate reversed = date(fields.get(16), line, "reversal date", messages);
        BigDecimal net = decimal(fields.get(2), line, "net amount", messages);
        List<Split> splits = new ArrayList<>();
        for (int ordinal = 0; ordinal < SPLIT_STARTS.length; ordinal++)
        {
            int start = SPLIT_STARTS[ordinal];
            if (!splitHasInputs(fields, start))
            {
                continue;
            }
            Integer splitRow = integer(fields.get(start), line, "split row", messages);
            if (splitRow != null && !splitRow.equals(rowNumber))
            {
                error(messages, line, "SPLIT_ROW", "Split row number differs from its parent worksheet row");
            }
            BigDecimal amount = decimal(fields.get(start + 1), line, "split amount", messages);
            String income = fields.get(start + 2), expense = fields.get(start + 3);
            if (!income.isBlank() && !expense.isBlank())
            {
                error(messages, line, "SPLIT_CATEGORY", "A split cannot have both an income and expense category");
            }
            if (amount != null && amount.signum() != 0 && income.isBlank() && expense.isBlank())
                error(messages, line, "SPLIT_CATEGORY", "A nonzero split needs exactly one source category");
            String purpose = fields.get(start + 4);
            String purposeCode = null;
            if (!purpose.isBlank())
            {
                var match = PURPOSE.matcher(purpose.strip());
                if (match.matches())
                {
                    purposeCode = match.group(1).toUpperCase(Locale.ROOT);
                }
                else
                {
                    warning(messages, line, "PURPOSE", "Unknown used-for value retained without guessing an expense function");
                }
            }
            splits.add(new Split(ordinal + 1, splitRow, amount, income, expense, purpose, purposeCode,
                    fields.get(start + 5), decimal(fields.get(start + 6), line, "quantity", messages)));
        }
        boolean monetary = net != null && net.signum() != 0 || splits.stream().anyMatch(s -> s.amount() != null && s.amount().signum() != 0);
        BigDecimal sum = BigDecimal.ZERO;
        for (Split split : splits)
        {
            if (split.amount() == null)
            {
                if (monetary)
                {
                    error(messages, line, "SPLIT_AMOUNT", "A populated split needs an amount");
                }
                else
                {
                    warning(messages, line, "NON_POSTING_SPLIT", "Incomplete zero-value split retained for review, not posting");
                }
            }
            if (split.bankEffect() != null)
            {
                sum = sum.add(split.bankEffect());
            }
        }
        if (monetary)
        {
            if (date == null)
            {
                error(messages, line, "DATE_REQUIRED", "A monetary entry needs its stated transaction date");
            }
            if (net == null)
            {
                error(messages, line, "NET_REQUIRED", "A monetary entry needs its source net amount");
            }
            else if (net.compareTo(sum) != 0)
            {
                error(messages, line, "SPLIT_BALANCE", "This CSV candidate must balance individually: debit minus credit must be zero. Exact difference: "
                        + net.subtract(sum).toPlainString() + ". Source net differs from signed income minus expense splits");
            }
            if (fields.get(9).isBlank())
            {
                error(messages, line, "BANK_REQUIRED", "A monetary entry needs a source bank-account label");
            }
            if (fields.get(13).isBlank())
            {
                error(messages, line, "FUND_REQUIRED", "A monetary entry needs a source Fund label");
            }
        }
        else
        {
            warning(messages, line, "NON_POSTING", "Zero-value or note-only record retained for review; no journal entry or reversal inferred");
        }
        Timing bankTiming = timing(fields.get(10), line, "bank timing", messages);
        Timing budgetTiming = timing(fields.get(12), line, "budget timing", messages);
        Integer linked = integer(fields.get(15), line, "linked row", messages);
        boolean invalid = messages.subList(before, messages.size()).stream().anyMatch(InterchangeValidationMessage::blocking);
        RowKind kind = invalid ? RowKind.INVALID : monetary ? RowKind.POSTING : RowKind.NON_POSTING;
        List<InterchangeValidationMessage> computed = new ArrayList<>();
        BigDecimal balance = decimal(fields.get(1), line, "computed running balance", computed);
        if (!computed.isEmpty())
        {
            warning(messages, line, "COMPUTED_BALANCE", "Computed running balance is unavailable; raw value retained without changing entry facts");
        }
        return new Row(line, rowNumber, date, statement, incoming, reversed, net, balance,
                fields.get(5), fields.get(7), fields.get(8), fields.get(9), bankTiming,
                fields.get(11), budgetTiming, fields.get(13), fields.get(14), linked,
                splits, kind, record.original(), fields);
    }

    private static boolean substantive(List<String> fields)
    {
        for (int column : new int[]{3, 4, 5, 6, 7, 8, 11, 14, 15, 16})
        {
            if (!fields.get(column).isBlank())
            {
                return true;
            }
        }
        String net = fields.get(2).strip();
        if (!net.isEmpty() && !zeroLiteral(net))
        {
            return true;
        }
        for (int start : SPLIT_STARTS)
        {
            if (splitHasInputs(fields, start))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean zeroLiteral(String value)
    {
        if (value.startsWith("(") && value.endsWith(")"))
        {
            value = value.substring(1, value.length() - 1).strip();
        }
        return value.length() <= 128 && MONEY.matcher(value).matches()
                && new BigDecimal(value.replace(",", "").replace("$", "")).signum() == 0;
    }

    private static boolean splitHasInputs(List<String> fields, int start)
    {
        for (int offset : new int[]{1, 2, 3, 5, 6})
        {
            if (!fields.get(start + offset).isBlank())
            {
                return true;
            }
        }
        return false;
    }

    private static void requireLayout(List<Record> records)
    {
        if (records.size() < 8)
        {
            throw new IllegalArgumentException("Exchequer CSV needs seven header rows and an opening row");
        }
        List<String> header = records.get(6).fields();
        if (header.size() < 117)
        {
            throw new IllegalArgumentException("Exchequer CSV is missing its four split blocks");
        }
        for (int i = 0; i < 7; i++)
        {
            if (records.get(i).fields().size() != header.size())
            {
                throw new IllegalArgumentException("Exchequer header rows have inconsistent widths");
            }
        }
        String[] core = {"row", "balance*", "transaction", "date", "on statement", "or chq #", "or tfr date",
                "legal name of person/organization", "details/notes", "bank account", "the bank", "budget category", "the budget", "fund", "merchant", "row #", "reversed"};
        for (int i = 0; i < core.length; i++)
        {
            requireHeader(header.get(i), core[i]);
        }
        for (int start : SPLIT_STARTS)
        {
            String[] split = {"row", "amount", "income category", "expense category", "(exp. type)", "num", "#purch"};
            for (int offset = 0; offset < split.length; offset++)
            {
                requireHeader(header.get(start + offset), split[offset]);
            }
        }
        if (!records.get(0).fields().get(116).contains("EXCHEQUER REPORT (" + TEMPLATE_VERSION + ")"))
            throw new IllegalArgumentException("Unsupported Exchequer template version; expected " + TEMPLATE_VERSION);
        if (!records.get(7).fields().get(0).isBlank())
        {
            throw new IllegalArgumentException("Expected a separate opening balance row");
        }
    }

    private static void requireHeader(String actual, String expected)
    {
        if (!actual.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).equals(expected))
            throw new IllegalArgumentException("Exchequer CSV header mismatch; expected " + expected);
    }

    private static LocalDate statementDate(String raw, int line, List<InterchangeValidationMessage> messages)
    {
        if (Set.of("not cashed", "uncashed", "not cleared", "outstanding", "pending", "n/a")
                .contains(raw.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ")))
        {
            warning(messages, line, "STATEMENT_STATUS", "Statement status text retained; no statement date or cleared state inferred");
            return null;
        }
        return date(raw, line, "statement date", messages);
    }

    private static LocalDate date(String raw, int line, String label, List<InterchangeValidationMessage> messages)
    {
        if (raw.isBlank())
        {
            return null;
        }
        try
        {
            return raw.strip().contains("/") ? LocalDate.parse(raw.strip(), US_DATE) : LocalDate.parse(raw.strip());
        }
        catch (DateTimeParseException ex)
        {
            error(messages, line, "DATE", "Invalid " + label + "; expected M/d/yyyy or ISO date");
            return null;
        }
    }

    private static BigDecimal decimal(String raw, int line, String label, List<InterchangeValidationMessage> messages)
    {
        if (raw.isBlank())
        {
            return null;
        }
        String value = raw.strip();
        boolean parentheses = value.startsWith("(") && value.endsWith(")");
        if (parentheses)
        {
            value = value.substring(1, value.length() - 1).strip();
        }
        if (value.length() > 128 || !MONEY.matcher(value).matches() || parentheses && (value.startsWith("-") || value.startsWith("+")))
        {
            error(messages, line, "DECIMAL", "Invalid " + label + "; expected an exact US decimal value");
            return null;
        }
        BigDecimal result = new BigDecimal(value.replace(",", "").replace("$", ""));
        if (parentheses)
        {
            result = result.negate();
        }
        BigDecimal normalized = result.stripTrailingZeros();
        if (normalized.scale() > ("quantity".equals(label) ? 4 : 2) || normalized.precision() - normalized.scale() > 15)
        {
            error(messages, line, "DECIMAL_RANGE", label + " exceeds allowed precision (money requires at most two decimal places); no rounding was applied");
            return null;
        }
        return result;
    }

    private static Integer integer(String raw, int line, String label, List<InterchangeValidationMessage> messages)
    {
        if (raw.isBlank())
        {
            return null;
        }
        try
        {
            if (!raw.strip().matches("[0-9]+"))
            {
                throw new NumberFormatException();
            }
            int result = Integer.parseInt(raw.strip());
            if (result < 1)
            {
                throw new NumberFormatException();
            }
            return result;
        }
        catch (NumberFormatException ex)
        {
            error(messages, line, "ROW_NUMBER", "Invalid positive integer for " + label);
            return null;
        }
    }

    private static Timing timing(String raw, int line, String label, List<InterchangeValidationMessage> messages)
    {
        if (raw.isBlank())
        {
            return Timing.NONE;
        }
        try
        {
            return Timing.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ex)
        {
            error(messages, line, "TIMING", "Unknown " + label + "; source value retained");
            return null;
        }
    }

    private static void error(List<InterchangeValidationMessage> messages, int line, String code, String detail)
    {
        messages.add(new InterchangeValidationMessage(InterchangeMessageSeverity.ERROR, "EXCHEQUER_" + code, "line[" + line + "]", detail, true));
    }

    private static void warning(List<InterchangeValidationMessage> messages, int line, String code, String detail)
    {
        messages.add(new InterchangeValidationMessage(InterchangeMessageSeverity.WARNING, "EXCHEQUER_" + code, "line[" + line + "]", detail, false));
    }

    private static String sha256(byte[] bytes)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
