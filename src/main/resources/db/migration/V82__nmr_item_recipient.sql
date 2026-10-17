-- Explicit item designation only; no conversion of legacy flags or liability balances.
ALTER TABLE txn_supplemental_line ADD COLUMN IF NOT EXISTS nmr_recipient_id BIGINT;
ALTER TABLE txn_supplemental_line ADD CONSTRAINT IF NOT EXISTS fk_supplemental_nmr_recipient
    FOREIGN KEY (nmr_recipient_id) REFERENCES counterparty(id);
ALTER TABLE txn_supplemental_line ADD CONSTRAINT IF NOT EXISTS ck_supplemental_nmr_item
    CHECK (nmr_recipient_id IS NULL OR (kind = 'OTHER_LIABILITY' AND item_id IS NOT NULL
        AND txn_split_id IS NOT NULL AND item_effect IS NOT NULL));
CREATE INDEX IF NOT EXISTS ix_supplemental_nmr_recipient ON txn_supplemental_line(nmr_recipient_id);
