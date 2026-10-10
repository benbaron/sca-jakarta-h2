-- Replay-safe for recovery of databases with missing Flyway history.
ALTER TABLE txn_split ADD COLUMN IF NOT EXISTS payment_method VARCHAR(16);
ALTER TABLE txn_split ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(80);
ALTER TABLE txn_split ADD COLUMN IF NOT EXISTS payment_issued_on DATE;
ALTER TABLE txn_split ADD COLUMN IF NOT EXISTS payment_delivered_on DATE;
ALTER TABLE txn_split ADD COLUMN IF NOT EXISTS payment_check_key VARCHAR(80);
ALTER TABLE txn_split ADD CONSTRAINT IF NOT EXISTS ck_split_payment_method CHECK
    (payment_method IS NULL OR payment_method IN ('CHECK','EFT','CARD','CASH','OTHER'));
ALTER TABLE txn_split ADD CONSTRAINT IF NOT EXISTS ck_split_payment_facts CHECK
    ((payment_method IS NOT NULL OR (payment_reference IS NULL AND payment_issued_on IS NULL AND payment_delivered_on IS NULL))
    AND (payment_method <> 'CHECK' OR payment_reference IS NOT NULL)
    AND (payment_check_key IS NULL OR (payment_method = 'CHECK' AND payment_reference = payment_check_key))
    AND (payment_delivered_on IS NULL OR (payment_issued_on IS NOT NULL AND payment_delivered_on >= payment_issued_on)));
CREATE UNIQUE INDEX IF NOT EXISTS uq_split_issuing_check ON txn_split(account_id, payment_check_key);
CREATE INDEX IF NOT EXISTS ix_split_payment_reference ON txn_split(payment_reference, account_id);
