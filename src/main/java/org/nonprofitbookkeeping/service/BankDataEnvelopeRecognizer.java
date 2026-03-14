package org.nonprofitbookkeeping.service;

import org.nonprofitbookkeeping.model.BankingDataFormat;

/**
 * Recognizes import banking envelope format by payload markers or filename.
 */
public class BankDataEnvelopeRecognizer
{
    public BankingDataFormat recognize(String payload, String sourceName)
    {
        String body = payload == null ? "" : payload.trim().toUpperCase();
        String file = sourceName == null ? "" : sourceName.trim().toUpperCase();

        if (body.contains("<QFX") || file.endsWith(".QFX"))
        {
            return BankingDataFormat.QFX;
        }
        if (body.contains("<OFX") || file.endsWith(".OFX"))
        {
            return BankingDataFormat.OFX;
        }

        throw new IllegalArgumentException("Unsupported banking envelope; expected OFX or QFX payload/filename.");
    }
}
