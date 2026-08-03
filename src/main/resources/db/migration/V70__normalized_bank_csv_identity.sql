-- P15-S7-C2: retain exact normalized-CSV source identities and PAYEEID for semantic round trips.
ALTER TABLE bank_import_batch
    ADD COLUMN IF NOT EXISTS source_external_id VARCHAR(200);

ALTER TABLE bank_statement_line
    ADD COLUMN IF NOT EXISTS source_external_id VARCHAR(200);

ALTER TABLE bank_statement_line
    ADD COLUMN IF NOT EXISTS source_payee_id VARCHAR(200);

CREATE INDEX IF NOT EXISTS ix_bank_import_batch_source_external
    ON bank_import_batch(company_id, bank_account_id, source_external_id);

CREATE INDEX IF NOT EXISTS ix_bank_statement_line_source_external
    ON bank_statement_line(company_id, bank_account_id, source_external_id);
