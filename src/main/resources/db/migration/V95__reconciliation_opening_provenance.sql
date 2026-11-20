ALTER TABLE bank_reconciliation_session ADD COLUMN IF NOT EXISTS opening_amount DECIMAL(19, 2);
ALTER TABLE bank_reconciliation_session ADD COLUMN IF NOT EXISTS opening_source_session_id BIGINT;
ALTER TABLE bank_reconciliation_session ADD CONSTRAINT IF NOT EXISTS fk_reconciliation_opening_source
    FOREIGN KEY (opening_source_session_id) REFERENCES bank_reconciliation_session(id);
ALTER TABLE bank_reconciliation_session ADD CONSTRAINT IF NOT EXISTS ck_reconciliation_opening_source
    CHECK (opening_source_session_id IS NULL OR opening_amount IS NOT NULL);
ALTER TABLE bank_reconciliation_match ADD COLUMN IF NOT EXISTS prior_cleared BOOLEAN;
ALTER TABLE bank_reconciliation_match ADD COLUMN IF NOT EXISTS prior_cleared_on DATE;
