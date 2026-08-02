package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SclxCorrectionImportDataTest
{
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsOneReversalAndReplacementForAReversedOriginal() throws Exception
    {
        SclxCorrectionImportData data = SclxCorrectionImportData.parse(document("""
                [
                  {"transactionId":"original","status":"REVERSED"},
                  {"transactionId":"reversal","status":"ENTERED","correctionType":"REVERSAL","correctionOfTransactionId":"original"},
                  {"transactionId":"replacement","status":"ENTERED","correctionType":"REPLACEMENT","correctionOfTransactionId":"original"}
                ]
                """));

        assertEquals(2, data.relationships().size());
        assertEquals("REVERSAL", data.relationships().get(0).correctionType());
        assertEquals("REPLACEMENT", data.relationships().get(1).correctionType());
    }

    @Test
    void rejectsReplacementWithoutMatchingReversal() throws Exception
    {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                SclxCorrectionImportData.parse(document("""
                        [
                          {"transactionId":"original","status":"REVERSED"},
                          {"transactionId":"replacement","status":"ENTERED","correctionType":"REPLACEMENT","correctionOfTransactionId":"original"}
                        ]
                        """)));

        assertTrue(failure.getMessage().contains("no matching reversal"));
    }

    @Test
    void rejectsStatusMismatchAndCycles() throws Exception
    {
        IllegalStateException statusFailure = assertThrows(IllegalStateException.class, () ->
                SclxCorrectionImportData.parse(document("""
                        [
                          {"transactionId":"original","status":"ENTERED"},
                          {"transactionId":"reversal","status":"ENTERED","correctionType":"REVERSAL","correctionOfTransactionId":"original"}
                        ]
                        """)));
        assertTrue(statusFailure.getMessage().contains("must have status REVERSED"));

        IllegalStateException cycleFailure = assertThrows(IllegalStateException.class, () ->
                SclxCorrectionImportData.parse(document("""
                        [
                          {"transactionId":"a","status":"REVERSED","correctionType":"REVERSAL","correctionOfTransactionId":"b"},
                          {"transactionId":"b","status":"REVERSED","correctionType":"REVERSAL","correctionOfTransactionId":"a"}
                        ]
                        """)));
        assertTrue(cycleFailure.getMessage().contains("cycle"));
    }

    private JsonNode document(String transactions) throws Exception
    {
        return mapper.readTree("{\"transactions\":" + transactions + "}");
    }
}
