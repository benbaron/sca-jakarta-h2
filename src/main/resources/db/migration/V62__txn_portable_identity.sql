-- P15-S4: give every canonical transaction a durable, database-independent portable identity.
-- IF NOT EXISTS keeps complete-schema Flyway-history recovery nondestructive.
ALTER TABLE txn ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE txn SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE txn ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE txn ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE txn ADD CONSTRAINT IF NOT EXISTS uq_txn_portable_id UNIQUE (portable_id);
