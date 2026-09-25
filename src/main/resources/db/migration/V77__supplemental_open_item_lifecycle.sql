ALTER TABLE txn_supplemental_line ADD COLUMN IF NOT EXISTS item_id UUID;
ALTER TABLE txn_supplemental_line ADD COLUMN IF NOT EXISTS txn_split_id BIGINT;
ALTER TABLE txn_supplemental_line ADD COLUMN IF NOT EXISTS item_effect VARCHAR(20);

ALTER TABLE txn_supplemental_line
    ADD CONSTRAINT IF NOT EXISTS fk_txn_supplemental_line_split
    FOREIGN KEY (txn_split_id) REFERENCES txn_split(id);

ALTER TABLE txn_supplemental_line
    ADD CONSTRAINT IF NOT EXISTS ck_txn_supplemental_line_lifecycle
    CHECK (
        (item_id IS NULL AND txn_split_id IS NULL AND item_effect IS NULL)
        OR
        (item_id IS NOT NULL AND txn_split_id IS NOT NULL
            AND item_effect IN ('INCREASE', 'DECREASE') AND amount > 0)
    );

CREATE INDEX IF NOT EXISTS ix_txn_supplemental_line_item
    ON txn_supplemental_line(item_id);
CREATE INDEX IF NOT EXISTS ix_txn_supplemental_line_split
    ON txn_supplemental_line(txn_split_id);
