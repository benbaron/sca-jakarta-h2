-- User-entered attribution only; existing amounts, labels and balances are untouched.
ALTER TABLE txn_supplemental_line ADD COLUMN IF NOT EXISTS nmr_movement VARCHAR(30);
ALTER TABLE txn_supplemental_line ADD COLUMN IF NOT EXISTS nmr_evidence_reference VARCHAR(500);
ALTER TABLE txn_supplemental_line ADD CONSTRAINT IF NOT EXISTS ck_nmr_movement_kind
    CHECK (nmr_movement IS NULL OR nmr_movement IN ('COLLECTION','BRANCH_REMITTANCE','MERCHANT_DISCHARGE','REFUND','ADJUSTMENT'));
ALTER TABLE txn_supplemental_line ADD CONSTRAINT IF NOT EXISTS ck_nmr_movement_designation
    CHECK ((nmr_movement IS NULL AND nmr_evidence_reference IS NULL) OR nmr_recipient_id IS NOT NULL);
ALTER TABLE txn_supplemental_line ADD CONSTRAINT IF NOT EXISTS ck_nmr_movement_effect
    CHECK (nmr_movement IS NULL OR nmr_movement = 'ADJUSTMENT'
        OR (nmr_movement = 'COLLECTION' AND item_effect = 'INCREASE')
        OR (nmr_movement IN ('BRANCH_REMITTANCE','MERCHANT_DISCHARGE','REFUND') AND item_effect = 'DECREASE'));
ALTER TABLE txn_supplemental_line ADD CONSTRAINT IF NOT EXISTS ck_nmr_evidence
    CHECK (nmr_evidence_reference IS NULL OR length(trim(nmr_evidence_reference)) > 0);
ALTER TABLE txn_supplemental_line ADD CONSTRAINT IF NOT EXISTS ck_nmr_merchant_evidence
    CHECK (nmr_movement IS NULL OR nmr_movement <> 'MERCHANT_DISCHARGE' OR nmr_evidence_reference IS NOT NULL);
