-- Factual review/evidence only; no amount, status or cleared-state inference.
ALTER TABLE txn_split ADD COLUMN IF NOT EXISTS payment_evidence_reference VARCHAR(500);
ALTER TABLE txn_split ADD COLUMN IF NOT EXISTS payment_reviewed_on DATE;
ALTER TABLE txn_split ADD COLUMN IF NOT EXISTS payment_review_note VARCHAR(1000);
ALTER TABLE txn_split ADD CONSTRAINT IF NOT EXISTS ck_split_payment_review
    CHECK ((payment_reviewed_on IS NULL OR payment_review_note IS NOT NULL)
       AND (payment_method IS NOT NULL OR (payment_evidence_reference IS NULL
         AND payment_reviewed_on IS NULL AND payment_review_note IS NULL)));
