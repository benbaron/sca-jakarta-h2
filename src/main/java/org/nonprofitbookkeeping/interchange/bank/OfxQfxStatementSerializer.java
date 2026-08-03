package org.nonprofitbookkeeping.interchange.bank;

import org.nonprofitbookkeeping.interchange.InterchangeMessageSeverity;
import org.nonprofitbookkeeping.interchange.InterchangeValidationMessage;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Deterministic standards-shaped OFX 2.x and governed QFX 2.x XML serializer. */
final class OfxQfxStatementSerializer
{
    private static final DateTimeFormatter OFX_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    Serialization serialize(
            BankStatementCsvExportService.Snapshot snapshot,
            BankStatementOfxExportRequest request)
    {
        List<InterchangeValidationMessage> messages = new ArrayList<>();
        snapshot.messages().stream()
                .filter(value -> !value.code().startsWith("BANK_CSV_"))
                .forEach(messages::add);
        String bankId = authoritative(
                snapshot.configuredBankId(), snapshot.rows().stream()
                        .map(BankStatementExportRow::bankId).toList(), "bank ID");
        String accountId = authoritative(
                snapshot.configuredAccountId(), snapshot.rows().stream()
                        .map(BankStatementExportRow::accountId).toList(), "account ID");
        String configuredType = "BANK".equalsIgnoreCase(snapshot.configuredAccountType())
                ? "" : snapshot.configuredAccountType();
        String accountType = authoritative(
                configuredType, snapshot.rows().stream()
                        .map(BankStatementExportRow::accountType).toList(), "account type");
        String currency = authoritative(
                snapshot.companyCurrency(), snapshot.rows().stream()
                        .map(BankStatementExportRow::currency).toList(), "currency").toUpperCase(Locale.ROOT);
        if (bankId.isBlank() || accountId.isBlank() || accountType.isBlank())
        {
            throw new IllegalArgumentException(
                    "OFX/QFX export requires authoritative bank ID, account ID, and account type metadata.");
        }
        String institutionId = uniqueOptional(
                snapshot.rows().stream().map(BankStatementExportRow::institutionId).toList());
        Map<BankStatementExportRow, String> fitIds = fitIds(snapshot.rows(), messages);
        Balance ledger = balance(snapshot.rows(), true, messages);
        Balance available = balance(snapshot.rows(), false, messages);

        StringBuilder xml = new StringBuilder(Math.max(2048, snapshot.rows().size() * 512));
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        if (request.profile() == BankStatementOfxExportRequest.Profile.OFX_2_XML)
        {
            xml.append("<?OFX OFXHEADER=\"200\" VERSION=\"220\" SECURITY=\"NONE\" ")
                    .append("OLDFILEUID=\"NONE\" NEWFILEUID=\"NONE\"?>\n");
        }
        xml.append("<OFX>\n");
        xml.append("  <SIGNONMSGSRSV1><SONRS><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS>")
                .append("<DTSERVER>").append(date(request.throughDate())).append("</DTSERVER>")
                .append("<LANGUAGE>ENG</LANGUAGE>");
        if (!institutionId.isBlank())
        {
            xml.append("<FI><ORG>").append(escape(institutionId)).append("</ORG><FID>")
                    .append(escape(institutionId)).append("</FID></FI>");
        }
        xml.append("</SONRS></SIGNONMSGSRSV1>\n");
        xml.append("  <BANKMSGSRSV1><STMTTRNRS><TRNUID>")
                .append(transactionUid(snapshot, request))
                .append("</TRNUID><STATUS><CODE>0</CODE><SEVERITY>INFO</SEVERITY></STATUS><STMTRS>\n")
                .append("    <CURDEF>").append(currency).append("</CURDEF>")
                .append("<BANKACCTFROM><BANKID>").append(escape(bankId)).append("</BANKID>")
                .append("<ACCTID>").append(escape(accountId)).append("</ACCTID>")
                .append("<ACCTTYPE>").append(escape(accountType)).append("</ACCTTYPE></BANKACCTFROM>\n")
                .append("    <BANKTRANLIST><DTSTART>").append(date(request.fromDate()))
                .append("</DTSTART><DTEND>").append(date(request.throughDate())).append("</DTEND>\n");
        for (BankStatementExportRow row : snapshot.rows())
        {
            String type = row.transactionType();
            if (type.isBlank())
            {
                type = "OTHER";
                messages.add(warning(
                        "BANK_OFX_TRANSACTION_TYPE_DERIVED",
                        "rows." + row.statementLineExternalId() + ".transaction_type",
                        "Missing source transaction type was exported as OFX OTHER."));
            }
            LocalDate posted = row.postedDate();
            if (posted == null)
            {
                posted = row.transactionDate();
                messages.add(warning(
                        "BANK_OFX_POSTED_DATE_DERIVED",
                        "rows." + row.statementLineExternalId() + ".posted_date",
                        "Missing posted date was exported from the retained transaction date."));
            }
            xml.append("      <STMTTRN><TRNTYPE>").append(escape(type)).append("</TRNTYPE>")
                    .append("<DTPOSTED>").append(date(posted)).append("</DTPOSTED>");
            if (row.transactionDate() != null)
            {
                xml.append("<DTUSER>").append(date(row.transactionDate())).append("</DTUSER>");
            }
            xml.append("<TRNAMT>").append(decimal(row.amount())).append("</TRNAMT>")
                    .append("<FITID>").append(escape(fitIds.get(row))).append("</FITID>");
            optional(xml, "CHECKNUM", row.checkNumber());
            optional(xml, "REFNUM", row.reference());
            optional(xml, "NAME", row.payeeName());
            optional(xml, "MEMO", row.memo());
            if (!row.correctionAction().isBlank())
            {
                optional(xml, "CORRECTFITID", row.correctedSourceTransactionId());
                optional(xml, "CORRECTACTION", row.correctionAction());
            }
            xml.append("</STMTTRN>\n");
        }
        xml.append("    </BANKTRANLIST>\n");
        appendBalance(xml, "LEDGERBAL", ledger);
        appendBalance(xml, "AVAILBAL", available);
        xml.append("  </STMTRS></STMTTRNRS></BANKMSGSRSV1>\n</OFX>\n");
        byte[] xmlBytes = xml.toString().getBytes(StandardCharsets.UTF_8);
        if (request.profile() == BankStatementOfxExportRequest.Profile.OFX_2_XML)
        {
            return new Serialization(xmlBytes, messages);
        }
        String header = String.join("\n",
                "OFXHEADER:200",
                "DATA:OFXXML",
                "VERSION:202",
                "SECURITY:NONE",
                "ENCODING:UTF-8",
                "CHARSET:NONE",
                "COMPRESSION:NONE",
                "OLDFILEUID:NONE",
                "NEWFILEUID:NONE",
                "",
                "");
        byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
        byte[] qfx = new byte[headerBytes.length + xmlBytes.length];
        System.arraycopy(headerBytes, 0, qfx, 0, headerBytes.length);
        System.arraycopy(xmlBytes, 0, qfx, headerBytes.length, xmlBytes.length);
        return new Serialization(qfx, messages);
    }

    private static Map<BankStatementExportRow, String> fitIds(
            List<BankStatementExportRow> rows, List<InterchangeValidationMessage> messages)
    {
        Map<BankStatementExportRow, String> result = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (BankStatementExportRow row : rows)
        {
            String candidate = row.sourceTransactionId();
            if (candidate.isBlank() || !used.add(candidate.toUpperCase(Locale.ROOT)))
            {
                candidate = "SCA-" + digest("FITID|" + row.statementLineExternalId()).substring(0, 32);
                while (!used.add(candidate.toUpperCase(Locale.ROOT)))
                {
                    candidate += "X";
                }
                messages.add(warning(
                        "BANK_OFX_FITID_DERIVED",
                        "rows." + row.statementLineExternalId() + ".source_transaction_id",
                        "A deterministic export FITID was derived without changing durable source identity."));
            }
            result.put(row, candidate);
        }
        return result;
    }

    private static Balance balance(
            List<BankStatementExportRow> rows,
            boolean ledger,
            List<InterchangeValidationMessage> messages)
    {
        Map<LocalDate, Set<BigDecimal>> values = new HashMap<>();
        for (BankStatementExportRow row : rows)
        {
            BigDecimal value = ledger ? row.ledgerBalance() : row.availableBalance();
            if (row.statementEndDate() != null && value != null)
            {
                values.computeIfAbsent(row.statementEndDate(), ignored -> new HashSet<>())
                        .add(value.stripTrailingZeros());
            }
        }
        if (values.isEmpty())
        {
            messages.add(warning(
                    ledger ? "BANK_OFX_LEDGER_BALANCE_UNAVAILABLE" : "BANK_OFX_AVAILABLE_BALANCE_UNAVAILABLE",
                    ledger ? "statement.ledger_balance" : "statement.available_balance",
                    "No authoritative imported " + (ledger ? "ledger" : "available")
                            + " balance was available; the OFX/QFX balance element was omitted."));
            return null;
        }
        LocalDate date = values.keySet().stream().max(LocalDate::compareTo).orElseThrow();
        Set<BigDecimal> latest = values.get(date);
        if (latest.size() != 1)
        {
            messages.add(warning(
                    ledger ? "BANK_OFX_LEDGER_BALANCE_CONFLICT" : "BANK_OFX_AVAILABLE_BALANCE_CONFLICT",
                    ledger ? "statement.ledger_balance" : "statement.available_balance",
                    "Conflicting authoritative balances at the latest statement date were omitted."));
            return null;
        }
        return new Balance(latest.iterator().next(), date);
    }

    private static void appendBalance(StringBuilder xml, String element, Balance balance)
    {
        if (balance != null)
        {
            xml.append("    <").append(element).append("><BALAMT>")
                    .append(decimal(balance.amount())).append("</BALAMT><DTASOF>")
                    .append(date(balance.asOf())).append("</DTASOF></").append(element).append(">\n");
        }
    }

    private static String authoritative(String configured, List<String> imported, String label)
    {
        if (configured != null && !configured.isBlank())
        {
            return configured.trim();
        }
        Set<String> values = imported.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (values.size() > 1)
        {
            throw new IllegalArgumentException("OFX/QFX export has conflicting authoritative " + label + " values.");
        }
        return values.isEmpty() ? "" : values.iterator().next();
    }

    private static String uniqueOptional(List<String> imported)
    {
        Set<String> values = imported.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return values.size() == 1 ? values.iterator().next() : "";
    }

    private static String transactionUid(
            BankStatementCsvExportService.Snapshot snapshot, BankStatementOfxExportRequest request)
    {
        return "SCA-" + digest(String.join("|", request.profile().name(), request.companyCode(),
                snapshot.bankAccountExternalId(), request.fromDate().toString(), request.throughDate().toString()))
                .substring(0, 32);
    }

    private static String digest(String value)
    {
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private static void optional(StringBuilder xml, String element, String value)
    {
        if (value != null && !value.isBlank())
        {
            xml.append('<').append(element).append('>').append(escape(value))
                    .append("</").append(element).append('>');
        }
    }

    private static String date(LocalDate value)
    {
        return value.format(OFX_DATE);
    }

    private static String decimal(BigDecimal value)
    {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
    }

    private static String escape(String value)
    {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static InterchangeValidationMessage warning(String code, String path, String message)
    {
        return new InterchangeValidationMessage(
                InterchangeMessageSeverity.WARNING, code, path, message, false);
    }

    record Serialization(byte[] bytes, List<InterchangeValidationMessage> messages)
    {
        Serialization
        {
            bytes = bytes.clone();
            messages = List.copyOf(messages);
        }

        @Override
        public byte[] bytes()
        {
            return bytes.clone();
        }
    }

    private record Balance(BigDecimal amount, LocalDate asOf) { }
}
