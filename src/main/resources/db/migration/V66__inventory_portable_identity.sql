-- P15-S4: give inventory items and movements durable, database-independent portable identities.
-- Mutable item facts, transaction links, and local numeric IDs are not stable portable keys.
-- IF NOT EXISTS keeps complete-schema Flyway-history recovery nondestructive.

ALTER TABLE inventory_item ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE inventory_item SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE inventory_item ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE inventory_item ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE inventory_item ADD CONSTRAINT IF NOT EXISTS uq_inventory_item_portable_id UNIQUE (portable_id);

ALTER TABLE inventory_movement ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE inventory_movement SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE inventory_movement ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE inventory_movement ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE inventory_movement ADD CONSTRAINT IF NOT EXISTS uq_inventory_movement_portable_id UNIQUE (portable_id);
