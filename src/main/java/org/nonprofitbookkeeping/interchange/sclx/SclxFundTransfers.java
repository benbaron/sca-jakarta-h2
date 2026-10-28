package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.nonprofitbookkeeping.model.*;
import org.nonprofitbookkeeping.service.FundTransferService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Versioned operational facts over the existing canonical transfer/correction graph. */
final class SclxFundTransfers
{
    static final String KEY = "fundTransfers", TYPE = "FUND_TRANSFER", IDENTITY = "_scaFundTransfer";
    record Fact(String id, String from, String to, String transaction, FundTransferService.ImportFact value)
    {
    }

    static Map<String, Object> export(String company, List<FundTransfer> values)
    {
        List<Map<String, Object>> facts = new ArrayList<>();
        for (FundTransfer transfer : values)
        {
            if (transfer.getFromFund().getCompany() == null || transfer.getToFund().getCompany() == null
                    || !company.equals(transfer.getFromFund().getCompany().getCode()) || !company.equals(transfer.getToFund().getCompany().getCode())
                    || transfer.getPostedTxn() != null && (transfer.getPostedTxn().getCompany() == null || !company.equals(transfer.getPostedTxn().getCompany().getCode())))
                throw invalid("cross-company transfer cannot be exported");
            Map<String, Object> fact = new LinkedHashMap<>();
            fact.put("transferId", SclxPortableIdentity.fundTransfer(company, transfer.getPortableId().toString()));
            fact.put("date", transfer.getTransferDate()); fact.put("fromFundId", SclxPortableIdentity.fund(company, transfer.getFromFund().getCode()));
            fact.put("toFundId", SclxPortableIdentity.fund(company, transfer.getToFund().getCode())); fact.put("amount", transfer.getAmount());
            fact.put("memo", transfer.getMemo()); fact.put("status", transfer.getStatus().name());
            fact.put("transactionId", transfer.getPostedTxn() == null ? null : SclxPortableIdentity.transaction(company, transfer.getPostedTxn().getPortableId().toString()));
            fact.put("requestId", transfer.getRequestId() == null ? null : transfer.getRequestId().toString()); fact.put("requestHash", transfer.getRequestHash());
            fact.put("createdAt", transfer.getCreatedAt()); fact.put("updatedAt", transfer.getUpdatedAt());
            facts.add(Collections.unmodifiableMap(fact));
        }
        facts.sort(Comparator.comparing(f -> (String) f.get("transferId")));
        return Map.of("version", 1, "transfers", List.copyOf(facts));
    }

    static List<Fact> parse(JsonNode root)
    {
        JsonNode ext = root.path("extensions").path("scaJakartaH2").path(KEY);
        if (ext.isMissingNode()) return List.of();
        fields(ext, Set.of("version", "transfers"));
        if (!ext.path("version").isIntegralNumber() || !ext.path("version").canConvertToInt()
                || ext.path("version").intValue() != 1 || !ext.path("transfers").isArray()) throw invalid("requires version 1 and transfers array");
        Map<String, JsonNode> funds = index(root.path("funds"), "fundId"), transactions = index(root.path("transactions"), "transactionId"), accounts = index(root.path("chartOfAccounts"), "accountId");
        Set<String> ids = new HashSet<>(); Set<UUID> portableIds = new HashSet<>(), requests = new HashSet<>(); Map<String, Fact> linked = new HashMap<>(); List<Fact> result = new ArrayList<>();
        for (JsonNode row : ext.path("transfers"))
        {
            fields(row, Set.of("transferId", "date", "fromFundId", "toFundId", "amount", "memo", "status", "transactionId", "requestId", "requestHash", "createdAt", "updatedAt"));
            try
            {
                String id = text(row, "transferId"), from = text(row, "fromFundId"), to = text(row, "toFundId"), txn = nullable(row, "transactionId", 160);
                UUID portable = UUID.fromString(id.substring(id.lastIndexOf(':') + 1));
                if (!id.startsWith("fund-transfer:") || !ids.add(id) || !portableIds.add(portable) || from.equals(to) || !funds.containsKey(from) || !funds.containsKey(to)) throw invalid("duplicate or unresolved transfer/fund identity");
                String request = nullable(row, "requestId", 36);
                UUID requestId = request == null ? null : UUID.fromString(request);
                if (requestId != null && !requests.add(requestId)) throw invalid("duplicate native request identity");
                var value = new FundTransferService.ImportFact(portable, LocalDate.parse(text(row, "date")), new BigDecimal(text(row, "amount")),
                        nullable(row, "memo", 500), FundTransferStatus.valueOf(text(row, "status")), requestId,
                        nullable(row, "requestHash", 64), instant(row, "createdAt"), instant(row, "updatedAt"));
                if (value.date().getYear() < 1900 || value.date().getYear() > 9999) throw invalid("date outside supported range");
                Fact fact = new Fact(id, from, to, txn, value);
                if (txn == null)
                {
                    if (value.status() == FundTransferStatus.POSTED) throw invalid("saved transfer requires canonical transaction");
                }
                else
                {
                    if (linked.putIfAbsent(txn, fact) != null || !transactions.containsKey(txn)) throw invalid("duplicate or dangling canonical link");
                    requireShape(fact, transactions.get(txn), accounts);
                }
                result.add(fact);
            }
            catch (RuntimeException ex)
            {
                throw new IllegalStateException("fundTransfers: invalid retained transfer: " + ex.getMessage(), ex);
            }
        }
        for (JsonNode txn : transactions.values())
        {
            if (!"REVERSAL".equals(txn.path("correctionType").asText())) continue;
            Fact parent = linked.get(txn.path("correctionOfTransactionId").asText()), inverse = linked.get(txn.path("transactionId").asText());
            if ((parent == null) != (inverse == null) || parent != null && (!parent.from().equals(inverse.to()) || !parent.to().equals(inverse.from())
                    || parent.value().amount().compareTo(inverse.value().amount()) != 0)) throw invalid("missing or contradictory transfer reversal facts");
        }
        return result.stream().sorted(Comparator.comparing(Fact::id)).toList();
    }

    static JsonNode identityDocument(JsonNode root)
    {
        parse(root); JsonNode copy = root.deepCopy(); Map<String, JsonNode> linked = new HashMap<>();
        for (JsonNode row : root.path("extensions").path("scaJakartaH2").path(KEY).path("transfers"))
            if (!row.path("transactionId").isNull()) linked.put(row.path("transactionId").asText(), row);
        for (JsonNode txn : copy.path("transactions"))
        {
            if (txn.has(IDENTITY)) throw invalid("reserved identity field");
            JsonNode fact = linked.get(txn.path("transactionId").asText());
            if (fact != null) ((ObjectNode)txn).set(IDENTITY, fact.deepCopy());
        }
        return copy;
    }

    static long count(SclxExportDocument.Extensions extensions)
    {
        Object ext = extensions.scaJakartaH2().get(KEY);
        if (ext == null) return 0;
        if (!(ext instanceof Map<?,?> value) || !(value.get("transfers") instanceof List<?> rows)) throw invalid("invalid exported shape");
        return rows.size();
    }

    private static void requireShape(Fact fact, JsonNode txn, Map<String, JsonNode> accounts)
    {
        if (!fact.value().date().toString().equals(txn.path("transactionDate").asText()) || txn.path("lines").size() != 4) throw invalid("canonical transfer date/four-line shape differs");
        Set<String> scopes = new HashSet<>(), accountIds = new HashSet<>(); int assetLines = 0;
        for (JsonNode line : txn.path("lines"))
        {
            String fund = line.path("fundId").asText(), accountId = line.path("accountId").asText(); JsonNode account = accounts.get(accountId);
            if (account == null) throw invalid("unresolved transfer account");
            boolean asset = "ASSET".equals(account.path("type").asText()) && "DEBIT".equals(account.path("increaseSide").asText());
            boolean equity = "EQUITY".equals(account.path("type").asText()) && "CREDIT".equals(account.path("increaseSide").asText());
            if ((!asset && !equity) || account.hasNonNull("subtype") || !scopes.add(accountId + "|" + fund)) throw invalid("transfer requires ordinary allocation/equity accounts");
            if (asset) assetLines++;
            accountIds.add(accountId);
            boolean debit = fund.equals(fact.from()) && equity || fund.equals(fact.to()) && asset;
            if (!fund.equals(fact.from()) && !fund.equals(fact.to())
                    || new BigDecimal(line.path(debit ? "debit" : "credit").asText()).compareTo(fact.value().amount()) != 0
                    || new BigDecimal(line.path(debit ? "credit" : "debit").asText()).signum() != 0) throw invalid("transfer amount/direction differs from canonical lines");
        }
        if (accountIds.size() != 2 || assetLines != 2) throw invalid("transfer requires one allocation and one equity account");
    }

    private static Map<String, JsonNode> index(JsonNode rows, String field)
    {
        Map<String, JsonNode> result = new HashMap<>();
        for (JsonNode row : rows) if (result.putIfAbsent(text(row, field), row) != null) throw invalid("duplicate core identity");
        return result;
    }

    private static void fields(JsonNode node, Set<String> expected)
    {
        Set<String> actual = new HashSet<>(); node.fieldNames().forEachRemaining(actual::add);
        if (!node.isObject() || !actual.equals(expected)) throw invalid("requires exactly " + expected);
    }

    private static String text(JsonNode row, String field)
    {
        JsonNode value = row.path(field);
        if (!value.isTextual() && !value.isNumber() || value.asText().isBlank()) throw invalid("required " + field);
        return value.asText();
    }

    private static String nullable(JsonNode row, String field, int max)
    {
        JsonNode value = row.path(field);
        if (value.isNull()) return null;
        if (!value.isTextual() || value.textValue().length() > max) throw invalid("invalid " + field);
        return value.textValue();
    }

    private static Instant instant(JsonNode row, String field)
    {
        Instant value = Instant.parse(text(row, field));
        if (value.isBefore(Instant.parse("1900-01-01T00:00:00Z")) || value.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))) throw invalid("timestamp outside supported range");
        return value;
    }

    private static IllegalStateException invalid(String detail)
    {
        return new IllegalStateException("fundTransfers: " + detail);
    }
}
