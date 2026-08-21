-- BANK is an operational account function, not a top-level accounting type.
-- Preserve existing CASH/non-CASH subtype values while moving all legacy BANK
-- accounts under ASSET and recording their banking role explicitly.
ALTER TABLE account ADD COLUMN IF NOT EXISTS account_function VARCHAR(40);

UPDATE account
SET account_function = 'BANK'
WHERE account_type = 'BANK'
  AND account_function IS NULL;

UPDATE account
SET account_type = 'ASSET'
WHERE account_type = 'BANK';

CREATE INDEX IF NOT EXISTS ix_account_function ON account(account_function);
