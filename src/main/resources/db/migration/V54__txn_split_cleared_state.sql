ALTER TABLE txn_split ADD COLUMN IF NOT EXISTS bank_cleared BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE txn_split ADD COLUMN IF NOT EXISTS bank_cleared_on DATE;
ALTER TABLE txn_split ADD COLUMN IF NOT EXISTS matched_bank_statement_line_id BIGINT;

ALTER TABLE txn_split ADD CONSTRAINT IF NOT EXISTS fk_txn_split_matched_bank_statement_line
    FOREIGN KEY (matched_bank_statement_line_id) REFERENCES bank_statement_line(id);
ALTER TABLE txn_split ADD CONSTRAINT IF NOT EXISTS ck_txn_split_cleared_fields
    CHECK (bank_cleared OR (bank_cleared_on IS NULL AND matched_bank_statement_line_id IS NULL));

CREATE INDEX IF NOT EXISTS ix_txn_split_bank_cleared ON txn_split(account_id, bank_cleared, bank_cleared_on);
CREATE INDEX IF NOT EXISTS ix_txn_split_matched_statement_line ON txn_split(matched_bank_statement_line_id);
