-- P15-S4: give counterparties and merchants durable, database-independent portable identities.
-- Names are mutable and counterparty names are not unique, so they cannot be portable keys.
-- IF NOT EXISTS keeps complete-schema Flyway-history recovery nondestructive.
ALTER TABLE counterparty ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE counterparty SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE counterparty ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE counterparty ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE counterparty ADD CONSTRAINT IF NOT EXISTS uq_counterparty_portable_id UNIQUE (portable_id);

ALTER TABLE merchant ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE merchant SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE merchant ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE merchant ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE merchant ADD CONSTRAINT IF NOT EXISTS uq_merchant_portable_id UNIQUE (portable_id);
