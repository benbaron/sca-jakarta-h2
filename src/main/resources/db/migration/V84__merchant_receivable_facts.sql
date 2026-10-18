-- Optional facts; no existing amount, classification, balance or policy is changed.
ALTER TABLE txn_supplemental_line ADD COLUMN IF NOT EXISTS merchant_role VARCHAR(30);
ALTER TABLE txn_supplemental_line ADD COLUMN IF NOT EXISTS merchant_withheld_unconfirmed NUMERIC(19,4);
ALTER TABLE txn_supplemental_line ADD COLUMN IF NOT EXISTS merchant_evidence_reference VARCHAR(500);
ALTER TABLE txn_supplemental_line ADD CONSTRAINT IF NOT EXISTS ck_merchant_receivable_role
    CHECK (merchant_role IS NULL OR (kind = 'RECEIVABLE' AND item_id IS NOT NULL AND txn_split_id IS NOT NULL AND item_effect IS NOT NULL
        AND ((merchant_role = 'GROSS_RECEIPT' AND item_effect = 'INCREASE')
          OR (merchant_role = 'SETTLEMENT' AND item_effect = 'DECREASE'))));
ALTER TABLE txn_supplemental_line ADD CONSTRAINT IF NOT EXISTS ck_merchant_observation
    CHECK ((merchant_withheld_unconfirmed IS NULL OR (merchant_role IS NOT NULL AND merchant_withheld_unconfirmed >= 0
        AND merchant_evidence_reference IS NOT NULL))
        AND (merchant_evidence_reference IS NULL OR (merchant_role IS NOT NULL AND length(trim(merchant_evidence_reference)) > 0)));
CREATE TABLE IF NOT EXISTS txn_supplemental_merchant_component (
    supplemental_line_id BIGINT NOT NULL REFERENCES txn_supplemental_line(id) ON DELETE CASCADE,
    txn_split_id BIGINT NOT NULL REFERENCES txn_split(id) ON DELETE CASCADE,
    component VARCHAR(30) NOT NULL CHECK (component IN ('PAYOUT', 'FEE', 'NMR_DISCHARGE')),
    PRIMARY KEY (supplemental_line_id, txn_split_id),
    CONSTRAINT uq_merchant_component_split UNIQUE (txn_split_id)
);
