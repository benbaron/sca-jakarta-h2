package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Strict pre-mutation projection of canonical SCLX transaction correction relationships. */
final class SclxCorrectionImportData
{
    private final List<CorrectionValue> relationships;

    private SclxCorrectionImportData(List<CorrectionValue> relationships)
    {
        this.relationships = List.copyOf(relationships);
    }

    static SclxCorrectionImportData parse(JsonNode root)
    {
        JsonNode transactions = root.path("transactions");
        if (!transactions.isArray())
        {
            throw new IllegalStateException("transactions must be an array.");
        }

        Map<String, JsonNode> byId = new LinkedHashMap<>();
        Map<String, String> statusById = new HashMap<>();
        List<CorrectionValue> relationships = new ArrayList<>();
        for (JsonNode transaction : transactions)
        {
            String transactionId = requiredText(transaction, "transactionId");
            if (byId.put(transactionId, transaction) != null)
            {
                throw new IllegalStateException("Duplicate transactionId: " + transactionId + ".");
            }
            String status = requiredText(transaction, "status");
            if (!Set.of("ENTERED", "REVERSED").contains(status))
            {
                throw new IllegalStateException(
                        "Unsupported transaction status for " + transactionId + ": " + status + ".");
            }
            statusById.put(transactionId, status);
        }

        Set<String> reversalTargets = new HashSet<>();
        Set<String> replacementTargets = new HashSet<>();
        for (Map.Entry<String, JsonNode> entry : byId.entrySet())
        {
            String transactionId = entry.getKey();
            JsonNode transaction = entry.getValue();
            String correctionType = optionalText(transaction, "correctionType");
            String correctedTransactionId = optionalText(transaction, "correctionOfTransactionId");
            if ((correctionType == null) != (correctedTransactionId == null))
            {
                throw new IllegalStateException("Transaction " + transactionId
                        + " must provide correctionType and correctionOfTransactionId together.");
            }
            if (correctionType == null)
            {
                continue;
            }
            if (!Set.of("REVERSAL", "REPLACEMENT").contains(correctionType))
            {
                throw new IllegalStateException(
                        "Unsupported correctionType for " + transactionId + ": " + correctionType + ".");
            }
            if (transactionId.equals(correctedTransactionId))
            {
                throw new IllegalStateException("A transaction cannot correct itself: " + transactionId + ".");
            }
            if (!byId.containsKey(correctedTransactionId))
            {
                throw new IllegalStateException("correctionOfTransactionId does not resolve for "
                        + transactionId + ": " + correctedTransactionId + ".");
            }
            Set<String> targets = "REVERSAL".equals(correctionType) ? reversalTargets : replacementTargets;
            if (!targets.add(correctedTransactionId))
            {
                throw new IllegalStateException("More than one " + correctionType.toLowerCase()
                        + " targets transaction " + correctedTransactionId + ".");
            }
            relationships.add(new CorrectionValue(transactionId, correctionType, correctedTransactionId));
        }

        for (String replacementTarget : replacementTargets)
        {
            if (!reversalTargets.contains(replacementTarget))
            {
                throw new IllegalStateException("Replacement target " + replacementTarget
                        + " has no matching reversal transaction.");
            }
        }
        for (Map.Entry<String, String> entry : statusById.entrySet())
        {
            String requiredStatus = reversalTargets.contains(entry.getKey()) ? "REVERSED" : "ENTERED";
            if (!requiredStatus.equals(entry.getValue()))
            {
                throw new IllegalStateException("Transaction " + entry.getKey() + " must have status "
                        + requiredStatus + " for its correction relationships.");
            }
        }

        rejectCycles(relationships);
        return new SclxCorrectionImportData(relationships);
    }

    List<CorrectionValue> relationships()
    {
        return relationships;
    }

    private static void rejectCycles(List<CorrectionValue> relationships)
    {
        Map<String, String> parentByTransaction = new HashMap<>();
        for (CorrectionValue relationship : relationships)
        {
            parentByTransaction.put(relationship.transactionId(), relationship.correctedTransactionId());
        }
        for (String transactionId : parentByTransaction.keySet())
        {
            Set<String> path = new HashSet<>();
            String current = transactionId;
            while (current != null)
            {
                if (!path.add(current))
                {
                    throw new IllegalStateException(
                            "Transaction correction relationships contain a cycle at " + current + ".");
                }
                current = parentByTransaction.get(current);
            }
        }
    }

    private static String requiredText(JsonNode value, String field)
    {
        String result = optionalText(value, field);
        if (result == null)
        {
            throw new IllegalStateException(field + " is required and must be a nonblank string.");
        }
        return result;
    }

    private static String optionalText(JsonNode value, String field)
    {
        JsonNode node = value.get(field);
        if (node == null || node.isNull())
        {
            return null;
        }
        if (!node.isTextual())
        {
            throw new IllegalStateException(field + " must be a string or null.");
        }
        String result = node.textValue().strip();
        return result.isEmpty() ? null : result;
    }

    record CorrectionValue(String transactionId, String correctionType, String correctedTransactionId)
    {
    }
}
