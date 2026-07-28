-- P15-S4: give banking and reconciliation records durable, database-independent portable identities.
-- Names, display labels, source paths, and local numeric IDs are not stable portable keys.
-- IF NOT EXISTS keeps complete-schema Flyway-history recovery nondestructive.

ALTER TABLE bank ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE bank SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE bank ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE bank ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE bank ADD CONSTRAINT IF NOT EXISTS uq_bank_portable_id UNIQUE (portable_id);

ALTER TABLE company_bank_account ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE company_bank_account SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE company_bank_account ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE company_bank_account ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE company_bank_account ADD CONSTRAINT IF NOT EXISTS uq_company_bank_account_portable_id UNIQUE (portable_id);

ALTER TABLE bank_import_batch ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE bank_import_batch SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE bank_import_batch ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE bank_import_batch ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE bank_import_batch ADD CONSTRAINT IF NOT EXISTS uq_bank_import_batch_portable_id UNIQUE (portable_id);

ALTER TABLE bank_statement_line ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE bank_statement_line SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE bank_statement_line ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE bank_statement_line ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE bank_statement_line ADD CONSTRAINT IF NOT EXISTS uq_bank_statement_line_portable_id UNIQUE (portable_id);

ALTER TABLE import_issue ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE import_issue SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE import_issue ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE import_issue ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE import_issue ADD CONSTRAINT IF NOT EXISTS uq_import_issue_portable_id UNIQUE (portable_id);

ALTER TABLE bank_reconciliation_session ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE bank_reconciliation_session SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE bank_reconciliation_session ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE bank_reconciliation_session ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE bank_reconciliation_session ADD CONSTRAINT IF NOT EXISTS uq_bank_reconciliation_session_portable_id UNIQUE (portable_id);

ALTER TABLE bank_reconciliation_match ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE bank_reconciliation_match SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE bank_reconciliation_match ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE bank_reconciliation_match ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE bank_reconciliation_match ADD CONSTRAINT IF NOT EXISTS uq_bank_reconciliation_match_portable_id UNIQUE (portable_id);
