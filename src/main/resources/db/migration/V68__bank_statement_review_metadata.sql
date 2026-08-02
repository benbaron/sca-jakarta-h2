ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS source_variant VARCHAR(30);
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS source_version VARCHAR(20);
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS source_encoding VARCHAR(20);
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS source_institution_id VARCHAR(120);
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS source_bank_id VARCHAR(80);
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS source_account_id VARCHAR(160);
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS source_account_type VARCHAR(80);
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS currency VARCHAR(3);
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS statement_start_date DATE;
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS statement_end_date DATE;
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS ledger_balance DECIMAL(19, 4);
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS available_balance DECIMAL(19, 4);
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS account_match_status VARCHAR(30);
ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS account_identity_confirmed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE bank_import_batch ADD CONSTRAINT IF NOT EXISTS ck_bank_import_batch_account_match
    CHECK (account_match_status IS NULL OR account_match_status IN ('EXACT', 'CONFIRMATION_REQUIRED'));

ALTER TABLE bank_statement_line ADD COLUMN IF NOT EXISTS currency VARCHAR(3);
ALTER TABLE bank_statement_line ADD COLUMN IF NOT EXISTS correction_action VARCHAR(20);
ALTER TABLE bank_statement_line ADD COLUMN IF NOT EXISTS corrected_source_transaction_id VARCHAR(160);

CREATE INDEX IF NOT EXISTS ix_bank_import_batch_source_identity
    ON bank_import_batch(company_id, bank_account_id, source_format, source_hash);

CREATE INDEX IF NOT EXISTS ix_bank_statement_line_scoped_source_id
    ON bank_statement_line(company_id, bank_account_id, source_transaction_id);
