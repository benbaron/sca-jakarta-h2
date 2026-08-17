package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Applies bounded, reviewable source-view corrections without modifying the source file. */
final class SclxImportDispositionApplier
{
    private SclxImportDispositionApplier()
    {
    }

    static Result apply(
            ObjectNode normalizedRoot,
            List<SclxCompatibilityNotice> originalNotices,
            List<SclxImportDispositionSelection> selections)
    {
        ObjectNode root = normalizedRoot.deepCopy();
        List<SclxCompatibilityNotice> notices = new ArrayList<>(originalNotices);
        List<SclxCompatibilityNotice> applied = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> droppedRecords = new HashSet<>();

        List<SclxImportDispositionSelection> ordered = new ArrayList<>(selections);
        ordered.sort(Comparator
                .comparing(SclxImportDispositionApplier::recordContainer)
                .thenComparing(Comparator.comparingInt(
                        SclxImportDispositionApplier::recordIndex).reversed()));
        for (SclxImportDispositionSelection selection : ordered)
        {
            if (selection.disposition() == SclxImportDisposition.NO_CHANGE)
            {
                continue;
            }
            if (!seen.add(selection.key()))
            {
                applied.add(error(selection,
                        "More than one disposition was selected for the same preview message."));
                continue;
            }

            boolean changed = switch (selection.disposition())
            {
                case DROP_RECORD -> dropRecordOnce(
                        root, selection.path(), droppedRecords);
                case MAKE_SUGGESTED_CORRECTION -> applySuggestedCorrection(root, selection);
                case IGNORE, NO_CHANGE -> false;
            };

            if (selection.disposition() == SclxImportDisposition.IGNORE)
            {
                continue;
            }
            if (!changed)
            {
                applied.add(error(selection,
                        "That disposition has no safe correction for this message. Choose No change"
                                + " or a supported record-level action."));
                continue;
            }

            notices.removeIf(notice -> notice.code().equals(selection.code())
                    && notice.path().equals(selection.path()));
            applied.add(new SclxCompatibilityNotice(
                    "SCLX_DISPOSITION_APPLIED",
                    selection.path(),
                    selection.disposition().displayName() + " applied to " + selection.code()
                            + "; the source file itself was not changed.",
                    false));
        }

        notices.addAll(applied);
        return new Result(root, notices);
    }

    private static boolean applySuggestedCorrection(
            ObjectNode root,
            SclxImportDispositionSelection selection)
    {
        if (Set.of("SCLX_DATE_REQUIRED", "SCLX_DATE_INVALID").contains(selection.code()))
        {
            JsonNode record = nearestArrayRecord(root, selection.path());
            if (record != null && noPostingLines(record))
            {
                return dropRecord(root, selection.path());
            }
            return false;
        }
        if ("SCLX_DONOR_ACCOUNT_TYPE_UNSUPPORTED".equals(selection.code()))
        {
            JsonNode record = nearestArrayRecord(root, selection.path());
            if (record instanceof ObjectNode account)
            {
                String type = text(account, "type");
                if ("REVENUE".equalsIgnoreCase(type))
                {
                    account.put("type", "INCOME");
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean noPostingLines(JsonNode transaction)
    {
        JsonNode lines = transaction.path("lines");
        if (!lines.isArray())
        {
            return true;
        }
        for (JsonNode line : lines)
        {
            if (decimal(line.get("debit")).signum() != 0
                    || decimal(line.get("credit")).signum() != 0)
            {
                return false;
            }
        }
        return true;
    }

    private static BigDecimal decimal(JsonNode value)
    {
        if (value == null || value.isNull())
        {
            return BigDecimal.ZERO;
        }
        try
        {
            return new BigDecimal(value.asText().trim());
        }
        catch (NumberFormatException ex)
        {
            return BigDecimal.ONE;
        }
    }

    private static boolean dropRecord(ObjectNode root, String path)
    {
        List<PathToken> tokens = tokens(path);
        int arrayIndex = -1;
        for (int index = tokens.size() - 1; index >= 0; index--)
        {
            if (tokens.get(index) instanceof IndexToken)
            {
                arrayIndex = index;
                break;
            }
        }
        if (arrayIndex >= 0)
        {
            JsonNode container = traverse(root, tokens.subList(0, arrayIndex));
            int recordIndex = ((IndexToken) tokens.get(arrayIndex)).index();
            if (container instanceof ArrayNode array
                    && recordIndex >= 0 && recordIndex < array.size())
            {
                array.remove(recordIndex);
                return true;
            }
            return false;
        }

        if (tokens.size() == 1 && tokens.get(0) instanceof FieldToken field)
        {
            return root.remove(field.name()) != null;
        }
        return false;
    }

    private static boolean dropRecordOnce(
            ObjectNode root,
            String path,
            Set<String> droppedRecords)
    {
        String key = recordKey(path);
        if (droppedRecords.contains(key))
        {
            return true;
        }
        boolean removed = dropRecord(root, path);
        if (removed)
        {
            droppedRecords.add(key);
        }
        return removed;
    }

    private static JsonNode nearestArrayRecord(ObjectNode root, String path)
    {
        List<PathToken> tokens = tokens(path);
        int arrayIndex = -1;
        for (int index = tokens.size() - 1; index >= 0; index--)
        {
            if (tokens.get(index) instanceof IndexToken)
            {
                arrayIndex = index;
                break;
            }
        }
        if (arrayIndex < 0)
        {
            return null;
        }
        JsonNode container = traverse(root, tokens.subList(0, arrayIndex));
        int recordIndex = ((IndexToken) tokens.get(arrayIndex)).index();
        return container instanceof ArrayNode array
                && recordIndex >= 0 && recordIndex < array.size()
                ? array.get(recordIndex)
                : null;
    }

    private static JsonNode traverse(JsonNode root, List<PathToken> tokens)
    {
        JsonNode current = root;
        for (PathToken token : tokens)
        {
            if (token instanceof FieldToken field)
            {
                current = current == null ? null : current.get(field.name());
            }
            else if (token instanceof IndexToken index)
            {
                current = current != null && current.isArray()
                        && index.index() >= 0 && index.index() < current.size()
                        ? current.get(index.index())
                        : null;
            }
            if (current == null)
            {
                return null;
            }
        }
        return current;
    }

    private static List<PathToken> tokens(String path)
    {
        if (path == null || !path.startsWith("$."))
        {
            return List.of();
        }
        List<PathToken> result = new ArrayList<>();
        String value = path.substring(2);
        int start = 0;
        while (start < value.length())
        {
            int dot = value.indexOf('.', start);
            int bracket = value.indexOf('[', start);
            int end = smallestPositive(dot, bracket, value.length());
            if (end > start)
            {
                result.add(new FieldToken(value.substring(start, end)));
            }
            if (end == value.length())
            {
                break;
            }
            if (value.charAt(end) == '.')
            {
                start = end + 1;
                continue;
            }
            int close = value.indexOf(']', end + 1);
            if (close < 0)
            {
                return List.of();
            }
            try
            {
                result.add(new IndexToken(Integer.parseInt(value.substring(end + 1, close))));
            }
            catch (NumberFormatException ex)
            {
                return List.of();
            }
            start = close + 1;
            if (start < value.length() && value.charAt(start) == '.')
            {
                start++;
            }
        }
        return List.copyOf(result);
    }

    private static int smallestPositive(int first, int second, int fallback)
    {
        int result = fallback;
        if (first >= 0)
        {
            result = Math.min(result, first);
        }
        if (second >= 0)
        {
            result = Math.min(result, second);
        }
        return result;
    }

    private static String recordContainer(SclxImportDispositionSelection selection)
    {
        int bracket = selection.path().lastIndexOf('[');
        return bracket < 0 ? selection.path() : selection.path().substring(0, bracket);
    }

    private static int recordIndex(SclxImportDispositionSelection selection)
    {
        int bracket = selection.path().lastIndexOf('[');
        int close = bracket < 0 ? -1 : selection.path().indexOf(']', bracket + 1);
        if (bracket < 0 || close < 0)
        {
            return -1;
        }
        try
        {
            return Integer.parseInt(selection.path().substring(bracket + 1, close));
        }
        catch (NumberFormatException ex)
        {
            return -1;
        }
    }

    private static String recordKey(String path)
    {
        int bracket = path.lastIndexOf('[');
        int close = bracket < 0 ? -1 : path.indexOf(']', bracket + 1);
        return bracket < 0 || close < 0 ? path : path.substring(0, close + 1);
    }

    private static String text(JsonNode value, String field)
    {
        JsonNode node = value == null ? null : value.get(field);
        return node != null && node.isTextual() ? node.textValue().trim() : "";
    }

    private static SclxCompatibilityNotice error(
            SclxImportDispositionSelection selection,
            String message)
    {
        return new SclxCompatibilityNotice(
                "SCLX_DISPOSITION_UNSUPPORTED",
                selection.path(),
                selection.disposition().displayName() + " could not be applied to "
                        + selection.code() + ". " + message,
                true);
    }

    record Result(ObjectNode root, List<SclxCompatibilityNotice> notices)
    {
        Result
        {
            notices = List.copyOf(notices);
        }
    }

    private sealed interface PathToken permits FieldToken, IndexToken
    {
    }

    private record FieldToken(String name) implements PathToken
    {
    }

    private record IndexToken(int index) implements PathToken
    {
    }
}
