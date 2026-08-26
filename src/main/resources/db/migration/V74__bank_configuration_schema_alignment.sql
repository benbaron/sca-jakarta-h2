ALTER TABLE chart_of_accounts ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE chart_of_accounts ADD CONSTRAINT IF NOT EXISTS fk_chart_company FOREIGN KEY (company_id) REFERENCES company(id);
CREATE INDEX IF NOT EXISTS ix_chart_company ON chart_of_accounts(company_id);

ALTER TABLE bank ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE bank SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE bank ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE bank ADD CONSTRAINT IF NOT EXISTS uq_bank_portable_id UNIQUE (portable_id);

ALTER TABLE company_bank_account ADD COLUMN IF NOT EXISTS portable_id UUID;
ALTER TABLE company_bank_account ADD COLUMN IF NOT EXISTS bank_id BIGINT;
ALTER TABLE company_bank_account ADD COLUMN IF NOT EXISTS account_id BIGINT;
ALTER TABLE company_bank_account ADD COLUMN IF NOT EXISTS nickname VARCHAR(160);
ALTER TABLE company_bank_account ADD COLUMN IF NOT EXISTS masked_account_number VARCHAR(80);
ALTER TABLE company_bank_account ADD COLUMN IF NOT EXISTS opening_date DATE;
ALTER TABLE company_bank_account ADD COLUMN IF NOT EXISTS statement_import_format VARCHAR(20);
ALTER TABLE company_bank_account ADD COLUMN IF NOT EXISTS ofx_bank_id VARCHAR(80);
ALTER TABLE company_bank_account ADD COLUMN IF NOT EXISTS ofx_account_id VARCHAR(120);

UPDATE company_bank_account SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE company_bank_account ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE company_bank_account ADD CONSTRAINT IF NOT EXISTS uq_company_bank_account_portable_id UNIQUE (portable_id);

ALTER TABLE company_bank_account ADD CONSTRAINT IF NOT EXISTS fk_company_bank_account_bank FOREIGN KEY (bank_id) REFERENCES bank(id);
ALTER TABLE company_bank_account ADD CONSTRAINT IF NOT EXISTS fk_company_bank_account_account FOREIGN KEY (account_id) REFERENCES account(id);
ALTER TABLE company_bank_account ADD CONSTRAINT IF NOT EXISTS uq_company_bank_account_account UNIQUE (company_id, account_id);
ALTER TABLE company_bank_account ADD CONSTRAINT IF NOT EXISTS ck_company_bank_account_import_format CHECK (statement_import_format IS NULL OR statement_import_format IN ('OFX', 'QFX', 'QIF', 'CSV'));

CREATE INDEX IF NOT EXISTS ix_company_bank_account_bank ON company_bank_account(bank_id);
CREATE INDEX IF NOT EXISTS ix_company_bank_account_account ON company_bank_account(account_id);
