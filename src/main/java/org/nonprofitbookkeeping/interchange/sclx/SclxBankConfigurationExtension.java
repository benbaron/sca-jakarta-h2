package org.nonprofitbookkeeping.interchange.sclx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Typed contract for selected-company bank and configured bank-account export. */
final class SclxBankConfigurationExtension
{
    static final String KEY = "bankConfiguration";
    private static final String PATH = "extensions.scaJakartaH2.bankConfiguration";
    private static final Set<String> ROOT_KEYS = Set.of("banks", "accounts");
    private static final Set<String> BANK_KEYS = Set.of(
            "bankId", "name", "routingNumber", "address", "website", "contactName",
            "contactPhone", "contactEmail", "notes", "active");
    private static final Set<String> ACCOUNT_KEYS = Set.of(
            "bankAccountId", "bankId", "ledgerAccountId", "name", "nickname",
            "institutionName", "accountType", "lastFour", "maskedAccountNumber",
            "openingDate", "statementImportFormat", "ofxBankId", "ofxAccountId",
            "openingBalance", "active", "notes");
    private static final Set<String> IMPORT_FORMATS = Set.of("OFX", "QFX", "QIF", "CSV");

    private SclxBankConfigurationExtension()
    {
    }

    static Map<String, Object> value(
            List<Map<String, Object>> banks,
            List<Map<String, Object>> accounts)
    {
        return Map.of("banks", List.copyOf(banks), "accounts", List.copyOf(accounts));
    }

    static Map<String, Object> bankEntry(
            String bankId,
            String name,
            String routingNumber,
            String address,
            String website,
            String contactName,
            String contactPhone,
            String contactEmail,
            String notes,
            boolean active)
    {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("bankId", requireText(bankId, "bankId"));
        entry.put("name", requireText(name, "name"));
        entry.put("routingNumber", optionalText(routingNumber));
        entry.put("address", optionalText(address));
        entry.put("website", optionalText(website));
        entry.put("contactName", optionalText(contactName));
        entry.put("contactPhone", optionalText(contactPhone));
        entry.put("contactEmail", optionalText(contactEmail));
        entry.put("notes", optionalText(notes));
        entry.put("active", active);
        return java.util.Collections.unmodifiableMap(entry);
    }

    static Map<String, Object> accountEntry(
            String bankAccountId,
            String bankId,
            String ledgerAccountId,
            String name,
            String nickname,
            String institutionName,
            String accountType,
            String lastFour,
            String maskedAccountNumber,
            LocalDate openingDate,
            String statementImportFormat,
            String ofxBankId,
            String ofxAccountId,
            BigDecimal openingBalance,
            boolean active,
            String notes)
    {
        if (statementImportFormat != null && !IMPORT_FORMATS.contains(statementImportFormat))
        {
            throw new IllegalArgumentException("unsupported statement import format: " + statementImportFormat);
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("bankAccountId", requireText(bankAccountId, "bankAccountId"));
        entry.put("bankId", optionalText(bankId));
        entry.put("ledgerAccountId", optionalText(ledgerAccountId));
        entry.put("name", requireText(name, "name"));
        entry.put("nickname", optionalText(nickname));
        entry.put("institutionName", optionalText(institutionName));
        entry.put("accountType", optionalText(accountType));
        entry.put("lastFour", optionalText(lastFour));
        entry.put("maskedAccountNumber", optionalText(maskedAccountNumber));
        entry.put("openingDate", openingDate);
        entry.put("statementImportFormat", statementImportFormat);
        entry.put("ofxBankId", optionalText(ofxBankId));
        entry.put("ofxAccountId", optionalText(ofxAccountId));
        entry.put("openingBalance", Objects.requireNonNull(openingBalance, "openingBalance"));
        entry.put("active", active);
        entry.put("notes", optionalText(notes));
        return java.util.Collections.unmodifiableMap(entry);
    }

    static Data data(SclxExportDocument.Extensions extensions)
    {
        Objects.requireNonNull(extensions, "extensions");
        Object raw = extensions.scaJakartaH2().get(KEY);
        if (raw == null)
        {
            return new Data(List.of(), List.of());
        }
        if (!(raw instanceof Map<?, ?> root))
        {
            throw new IllegalArgumentException(PATH + " must be an object");
        }
        if (!root.keySet().equals(ROOT_KEYS))
        {
            throw new IllegalArgumentException(PATH + " has unsupported fields");
        }

        List<BankEntry> banks = new ArrayList<>();
        List<Map<?, ?>> bankMaps = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "banks", PATH), PATH + ".banks", BANK_KEYS);
        for (int index = 0; index < bankMaps.size(); index++)
        {
            Map<?, ?> map = bankMaps.get(index);
            String path = PATH + ".banks[" + index + ']';
            banks.add(new BankEntry(
                    SclxExtensionValueReader.text(map, "bankId", path),
                    SclxExtensionValueReader.text(map, "name", path),
                    SclxExtensionValueReader.optionalText(map, "routingNumber", path),
                    SclxExtensionValueReader.optionalText(map, "address", path),
                    SclxExtensionValueReader.optionalText(map, "website", path),
                    SclxExtensionValueReader.optionalText(map, "contactName", path),
                    SclxExtensionValueReader.optionalText(map, "contactPhone", path),
                    SclxExtensionValueReader.optionalText(map, "contactEmail", path),
                    SclxExtensionValueReader.optionalText(map, "notes", path),
                    SclxExtensionValueReader.flag(map, "active", path)));
        }

        List<AccountEntry> accounts = new ArrayList<>();
        List<Map<?, ?>> accountMaps = SclxExtensionValueReader.objects(
                SclxExtensionValueReader.array(root, "accounts", PATH), PATH + ".accounts", ACCOUNT_KEYS);
        for (int index = 0; index < accountMaps.size(); index++)
        {
            Map<?, ?> map = accountMaps.get(index);
            String path = PATH + ".accounts[" + index + ']';
            String format = SclxExtensionValueReader.optionalText(map, "statementImportFormat", path);
            if (format != null && !IMPORT_FORMATS.contains(format))
            {
                throw new IllegalArgumentException(path + ".statementImportFormat is unsupported: " + format);
            }
            accounts.add(new AccountEntry(
                    SclxExtensionValueReader.text(map, "bankAccountId", path),
                    SclxExtensionValueReader.optionalText(map, "bankId", path),
                    SclxExtensionValueReader.optionalText(map, "ledgerAccountId", path),
                    SclxExtensionValueReader.text(map, "name", path),
                    SclxExtensionValueReader.optionalText(map, "nickname", path),
                    SclxExtensionValueReader.optionalText(map, "institutionName", path),
                    SclxExtensionValueReader.optionalText(map, "accountType", path),
                    SclxExtensionValueReader.optionalText(map, "lastFour", path),
                    SclxExtensionValueReader.optionalText(map, "maskedAccountNumber", path),
                    SclxExtensionValueReader.date(map, "openingDate", path, true),
                    format,
                    SclxExtensionValueReader.optionalText(map, "ofxBankId", path),
                    SclxExtensionValueReader.optionalText(map, "ofxAccountId", path),
                    SclxExtensionValueReader.decimal(map, "openingBalance", path, false),
                    SclxExtensionValueReader.flag(map, "active", path),
                    SclxExtensionValueReader.optionalText(map, "notes", path)));
        }
        return new Data(List.copyOf(banks), List.copyOf(accounts));
    }

    static Set<String> uniqueBankIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        data.banks().forEach(entry -> requireUnique(ids, entry.bankId(), "bank"));
        return ids;
    }

    static Set<String> uniqueBankAccountIds(Data data)
    {
        Set<String> ids = new HashSet<>();
        data.accounts().forEach(entry -> requireUnique(ids, entry.bankAccountId(), "bank account"));
        return ids;
    }

    private static void requireUnique(Set<String> ids, String identity, String type)
    {
        if (!ids.add(identity))
        {
            throw new IllegalArgumentException("duplicate " + type + " portable identity: " + identity);
        }
    }

    private static String requireText(String value, String field)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String optionalText(String value)
    {
        return value == null || value.isBlank() ? null : value.strip();
    }

    record Data(List<BankEntry> banks, List<AccountEntry> accounts)
    {
        Data
        {
            banks = List.copyOf(banks);
            accounts = List.copyOf(accounts);
        }
    }

    record BankEntry(
            String bankId,
            String name,
            String routingNumber,
            String address,
            String website,
            String contactName,
            String contactPhone,
            String contactEmail,
            String notes,
            boolean active)
    {
    }

    record AccountEntry(
            String bankAccountId,
            String bankId,
            String ledgerAccountId,
            String name,
            String nickname,
            String institutionName,
            String accountType,
            String lastFour,
            String maskedAccountNumber,
            LocalDate openingDate,
            String statementImportFormat,
            String ofxBankId,
            String ofxAccountId,
            BigDecimal openingBalance,
            boolean active,
            String notes)
    {
    }
}
