package org.nonprofitbookkeeping.interchange.sclx;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SclxNmrMovementMetadataTest
{
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void emptyVersionTwoFactsKeepVersionOneIdentityAndUnsupportedShapesReject() throws Exception
    {
        var legacy = json.readTree("{\"version\":1,\"recipientId\":\"party:synthetic\"}");
        var empty = json.readTree("{\"version\":2,\"recipientId\":\"party:synthetic\",\"movement\":null,\"evidenceReference\":null}");
        assertEquals(SclxNmrItemMetadata.normalized(legacy), SclxNmrItemMetadata.normalized(empty));
        for (String raw : java.util.List.of("{\"version\":2,\"recipientId\":\"party:synthetic\"}",
                "{\"version\":2,\"recipientId\":\"party:synthetic\",\"movement\":\" \" ,\"evidenceReference\":null}",
                "{\"version\":2,\"recipientId\":\"party:synthetic\",\"movement\":null,\"evidenceReference\":42}"))
        {
            assertThrows(IllegalStateException.class, () -> SclxNmrItemMetadata.facts(json.readTree(raw)));
        }
    }
}
