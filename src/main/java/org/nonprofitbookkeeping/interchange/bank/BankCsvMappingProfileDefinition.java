package org.nonprofitbookkeeping.interchange.bank;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Strict, immutable projection of an SCA bank CSV mapping profile. */
public record BankCsvMappingProfileDefinition(
        String profileName,
        String version,
        char delimiter,
        String encoding,
        List<String> dateFormats,
        String locale,
        String decimalSeparator,
        String groupingPolicy,
        AmountMode amountMode,
        String debitCreditConvention,
        String fixedCurrency,
        String fixedAccountId,
        boolean trimFields,
        boolean blankAsNull,
        Map<String, String> columns,
        String canonicalJson)
{
    private static final Set<String> ROOT_FIELDS = Set.of(
            "format", "version", "profileName", "delimiter", "encoding", "dateFormats",
            "locale", "decimalSeparator", "groupingPolicy", "amountMode",
            "debitCreditConvention", "fixedCurrency", "fixedAccountId",
            "trimFields", "blankAsNull", "columns");
    private static final Set<String> COLUMN_FIELDS = Set.of(
            "postedDate", "transactionDate", "amount", "debit", "credit",
            "sourceTransactionId", "transactionType", "payeeName", "memo",
            "checkNumber", "reference", "currency", "accountId");

    public BankCsvMappingProfileDefinition
    {
        columns = Map.copyOf(columns);
        dateFormats = List.copyOf(dateFormats);
    }

    public static BankCsvMappingProfileDefinition parse(String json)
    {
        if (json == null || json.isBlank())
        {
            throw new IllegalArgumentException("Bank CSV mapping profile JSON is required.");
        }
        try
        {
            JsonFactory factory = JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxNestingDepth(16)
                            .maxStringLength(1_048_576)
                            .maxNumberLength(100)
                            .build())
                    .build();
            ObjectMapper mapper = new ObjectMapper(factory)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            JsonNode root = mapper.readTree(json);
            requireObject(root, "Bank CSV mapping profile");
            rejectUnknown(root, ROOT_FIELDS, "profile");
            require("SCA-BANK-CSV-PROFILE".equals(text(root, "format")),
                    "Bank CSV mapping profile format must be SCA-BANK-CSV-PROFILE.");
            String version = requiredText(root, "version", 20);
            require("1.0".equals(version), "Unsupported bank CSV mapping profile version: " + version + ".");
            String profileName = requiredText(root, "profileName", 160);
            char delimiter = delimiter(requiredText(root, "delimiter", 4));
            String encoding = requiredText(root, "encoding", 20).toUpperCase(Locale.ROOT);
            require(Set.of("UTF-8", "US-ASCII", "WINDOWS-1252").contains(encoding),
                    "Unsupported bank CSV profile encoding: " + encoding + ".");

            JsonNode formatsNode = root.get("dateFormats");
            require(formatsNode != null && formatsNode.isArray()
                            && !formatsNode.isEmpty() && formatsNode.size() <= 10,
                    "Bank CSV profile requires 1 to 10 date formats.");
            List<String> dateFormats = new java.util.ArrayList<>();
            for (JsonNode value : formatsNode)
            {
                require(value.isTextual() && !value.textValue().isBlank()
                                && value.textValue().length() <= 80,
                        "Bank CSV date formats must be nonblank strings of at most 80 characters.");
                String pattern = value.textValue().trim();
                dateFormats.add(pattern);
            }
            String localeTag = text(root, "locale");
            boolean localeSensitive = dateFormats.stream()
                    .anyMatch(pattern -> pattern.contains("MMM") || pattern.contains("E") || pattern.contains("L"));
            require(!localeSensitive || !localeTag.isBlank(),
                    "Month-name or locale-sensitive bank CSV date formats require an explicit locale.");
            Locale dateLocale = localeTag.isBlank() ? Locale.ROOT : Locale.forLanguageTag(localeTag);
            require(localeTag.isBlank() || !dateLocale.getLanguage().isBlank(),
                    "Bank CSV locale must be a valid BCP 47 language tag.");
            for (String pattern : dateFormats)
            {
                java.time.format.DateTimeFormatter.ofPattern(pattern, dateLocale);
            }

            String decimalSeparator = text(root, "decimalSeparator");
            if (decimalSeparator.isBlank()) decimalSeparator = ".";
            require(Set.of(".", ",").contains(decimalSeparator),
                    "Bank CSV decimalSeparator must be period or comma.");
            String groupingPolicy = text(root, "groupingPolicy").toUpperCase(Locale.ROOT);
            if (groupingPolicy.isBlank()) groupingPolicy = "REJECT";
            require(Set.of("REJECT", "ALLOW").contains(groupingPolicy),
                    "Bank CSV groupingPolicy must be REJECT or ALLOW.");

            AmountMode amountMode;
            try
            {
                amountMode = AmountMode.valueOf(requiredText(root, "amountMode", 30).toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ex)
            {
                throw new IllegalArgumentException("Bank CSV amountMode must be SIGNED_AMOUNT or DEBIT_CREDIT.", ex);
            }
            String convention = text(root, "debitCreditConvention").toUpperCase(Locale.ROOT);
            if (amountMode == AmountMode.DEBIT_CREDIT)
            {
                require("CREDIT_MINUS_DEBIT_ASSET_ACCOUNT".equals(convention),
                        "Debit/credit profiles require CREDIT_MINUS_DEBIT_ASSET_ACCOUNT.");
            }

            JsonNode columnsNode = root.get("columns");
            requireObject(columnsNode, "Bank CSV profile columns");
            rejectUnknown(columnsNode, COLUMN_FIELDS, "columns");
            Map<String, String> columns = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = columnsNode.fields();
            Set<String> normalizedTargets = new java.util.HashSet<>();
            while (fields.hasNext())
            {
                Map.Entry<String, JsonNode> entry = fields.next();
                require(entry.getValue().isTextual() && !entry.getValue().textValue().isBlank()
                                && entry.getValue().textValue().length() <= 260,
                        "Bank CSV mapped column names must be nonblank strings of at most 260 characters.");
                String target = entry.getValue().textValue().trim();
                require(normalizedTargets.add(normalizeHeader(target)),
                        "Bank CSV profile maps more than one field to column: " + target + ".");
                columns.put(entry.getKey(), target);
            }
            require(columns.containsKey("postedDate") || columns.containsKey("transactionDate"),
                    "Bank CSV profile requires a postedDate or transactionDate column.");
            if (amountMode == AmountMode.SIGNED_AMOUNT)
            {
                require(columns.containsKey("amount"), "Signed-amount profile requires an amount column.");
            }
            else
            {
                require(columns.containsKey("debit") && columns.containsKey("credit"),
                        "Debit/credit profile requires debit and credit columns.");
            }
            String fixedCurrency = text(root, "fixedCurrency").toUpperCase(Locale.ROOT);
            require(!fixedCurrency.isBlank() || columns.containsKey("currency"),
                    "Bank CSV profile requires fixedCurrency or a currency column.");
            require(fixedCurrency.isBlank() || fixedCurrency.matches("[A-Z]{3}"),
                    "Bank CSV fixedCurrency must be a three-letter code.");
            String fixedAccountId = text(root, "fixedAccountId");
            require(!fixedAccountId.isBlank() || columns.containsKey("accountId"),
                    "Bank CSV profile requires fixedAccountId or an accountId column.");

            boolean trimFields = booleanValue(root, "trimFields", true);
            boolean blankAsNull = booleanValue(root, "blankAsNull", true);
            return new BankCsvMappingProfileDefinition(
                    profileName, version, delimiter, encoding, dateFormats, localeTag,
                    decimalSeparator, groupingPolicy, amountMode, convention,
                    fixedCurrency, fixedAccountId, trimFields, blankAsNull,
                    columns, mapper.writeValueAsString(root));
        }
        catch (IllegalArgumentException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException("Invalid bank CSV mapping profile JSON.", ex);
        }
    }

    public String persistedDelimiter()
    {
        return delimiter == '\t' ? "TAB" : String.valueOf(delimiter);
    }

    static String normalizeHeader(String value)
    {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static char delimiter(String value)
    {
        if ("\\t".equals(value) || "TAB".equalsIgnoreCase(value)) return '\t';
        require(value.length() == 1 && ",;|".indexOf(value.charAt(0)) >= 0,
                "Bank CSV delimiter must be comma, tab, semicolon, or pipe.");
        return value.charAt(0);
    }

    private static String requiredText(JsonNode root, String field, int maxLength)
    {
        String value = text(root, field);
        require(!value.isBlank() && value.length() <= maxLength,
                "Bank CSV profile field " + field + " is required and limited to " + maxLength + " characters.");
        return value;
    }

    private static String text(JsonNode root, String field)
    {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || value.isNull()) return "";
        require(value.isTextual(), "Bank CSV profile field " + field + " must be a string.");
        return value.textValue().trim();
    }

    private static boolean booleanValue(JsonNode root, String field, boolean defaultValue)
    {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) return defaultValue;
        require(value.isBoolean(), "Bank CSV profile field " + field + " must be boolean.");
        return value.booleanValue();
    }

    private static void rejectUnknown(JsonNode node, Set<String> allowed, String path)
    {
        node.fieldNames().forEachRemaining(field -> require(allowed.contains(field),
                "Unknown bank CSV profile field " + path + "." + field + "."));
    }

    private static void requireObject(JsonNode node, String label)
    {
        require(node != null && node.isObject(), label + " must be a JSON object.");
    }

    private static void require(boolean condition, String message)
    {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public enum AmountMode
    {
        SIGNED_AMOUNT,
        DEBIT_CREDIT
    }
}
