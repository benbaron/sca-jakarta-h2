-- P15-S4: give factual audit events durable, database-independent portable identities.
-- Local numeric IDs and polymorphic entity_id text are not stable portable keys.
-- IF NOT EXISTS keeps complete-schema Flyway-history recovery nondestructive.

ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE audit_event SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE audit_event ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE audit_event ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE audit_event ADD CONSTRAINT IF NOT EXISTS uq_audit_event_portable_id UNIQUE (portable_id);
