ALTER TABLE txn ADD COLUMN IF NOT EXISTS transfer_or_check_reference VARCHAR(200);
ALTER TABLE txn ADD COLUMN IF NOT EXISTS source_statement_date DATE;
ALTER TABLE txn ADD COLUMN IF NOT EXISTS incoming_check_transfer_date DATE;
