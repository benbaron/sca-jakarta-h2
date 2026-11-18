package org.nonprofitbookkeeping.interchange.csv;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Strict, bounded CSV lexing shared by format readers; performs no interpretation or writes. */
public final class CsvRecordReader
{
    private CsvRecordReader()
    {
    }

    public static List<Record> read(String text, char delimiter, Limits limits)
    {
        Objects.requireNonNull(text, "CSV text");
        Objects.requireNonNull(limits, "CSV limits");
        if (delimiter == '"' || delimiter == '\r' || delimiter == '\n' || delimiter == '\0')
        {
            throw new IllegalArgumentException("Invalid CSV delimiter");
        }
        List<Record> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        StringBuilder original = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        int line = 1;
        int startLine = 1;
        long cells = 0;
        for (int index = 0; index < text.length(); index++)
        {
            char ch = text.charAt(index);
            if (ch == '\0')
            {
                throw failure(limits, startLine, "contains a NUL character");
            }
            original.append(ch);
            if (original.length() > limits.recordCharacters())
            {
                throw failure(limits, startLine, "logical record is too large");
            }
            if (quoted)
            {
                if (ch == '"')
                {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"')
                    {
                        field.append('"');
                        original.append(text.charAt(++index));
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
                    if (ch == '\n' || ch == '\r' && (index + 1 == text.length() || text.charAt(index + 1) != '\n')) line++;
                }
            }
            else if (afterQuote && ch != delimiter && ch != '\r' && ch != '\n')
            {
                if (!Character.isWhitespace(ch))
                {
                    throw failure(limits, startLine, "has unexpected content after a quoted field");
                }
            }
            else if (ch == '"')
            {
                if (field.length() != 0)
                {
                    throw failure(limits, startLine, "has an unexpected quote");
                }
                quoted = true;
            }
            else if (ch == delimiter)
            {
                addField(fields, field, limits, startLine);
                if (++cells > limits.cells())
                {
                    throw failure(limits, startLine, "exceeds its cell limit");
                }
                afterQuote = false;
            }
            else if (ch == '\r' || ch == '\n')
            {
                addField(fields, field, limits, startLine);
                if (++cells > limits.cells())
                {
                    throw failure(limits, startLine, "exceeds its cell limit");
                }
                addRecord(records, fields, original.substring(0, original.length() - 1), startLine, limits);
                original.setLength(0);
                afterQuote = false;
                if (ch == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                line++;
                startLine = line;
            }
            else
            {
                field.append(ch);
            }
            if (field.length() > limits.fieldCharacters())
            {
                throw failure(limits, startLine, "field is too large");
            }
        }
        if (quoted)
        {
            throw failure(limits, startLine, "has an unclosed quoted field");
        }
        if (field.length() > 0 || !fields.isEmpty() || original.length() > 0)
        {
            addField(fields, field, limits, startLine);
            if (++cells > limits.cells())
            {
                throw failure(limits, startLine, "exceeds its cell limit");
            }
            addRecord(records, fields, original.toString(), startLine, limits);
        }
        return List.copyOf(records);
    }

    private static void addField(List<String> fields, StringBuilder field, Limits limits, int line)
    {
        fields.add(field.toString());
        field.setLength(0);
        if (fields.size() > limits.columns())
        {
            throw failure(limits, line, "exceeds " + limits.columns() + " columns");
        }
    }

    private static void addRecord(List<Record> records, List<String> fields, String raw, int line, Limits limits)
    {
        records.add(new Record(line, raw, fields));
        fields.clear();
        if (records.size() > limits.records())
        {
            throw failure(limits, line, "exceeds " + limits.records() + " records");
        }
    }

    private static IllegalArgumentException failure(Limits limits, int line, String reason)
    {
        return new IllegalArgumentException(limits.label() + " at line " + line + " " + reason + ".");
    }

    public record Limits(String label, int columns, int records, int recordCharacters, int fieldCharacters, long cells)
    {
        public Limits
        {
            if (label == null || label.isBlank() || columns < 1 || records < 1
                    || recordCharacters < 1 || fieldCharacters < 1 || cells < 1)
            {
                throw new IllegalArgumentException("CSV limits must be positive and have a label");
            }
        }
    }

    public record Record(int lineNumber, String original, List<String> fields)
    {
        public Record
        {
            fields = List.copyOf(fields);
        }
    }
}
