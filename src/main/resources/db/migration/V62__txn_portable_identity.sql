-- P15-S4: give every canonical transaction a durable, database-independent portable identity.
ALTER TABLE txn ADD COLUMN portable_id UUID DEFAULT RANDOM_UUID();
UPDATE txn SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE txn ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE txn ADD CONSTRAINT uq_txn_portable_id UNIQUE (portable_id);
