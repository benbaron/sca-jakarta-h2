-- P15-S4: give fixed assets and completed depreciation runs durable, database-independent portable identities.
-- Mutable names, account/fund labels, the linked canonical transaction identity, and local numeric IDs are not stable portable keys.
-- IF NOT EXISTS keeps complete-schema Flyway-history recovery nondestructive.

ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE fixed_asset SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE fixed_asset ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE fixed_asset ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE fixed_asset ADD CONSTRAINT IF NOT EXISTS uq_fixed_asset_portable_id UNIQUE (portable_id);

ALTER TABLE fixed_asset_depreciation_run ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE fixed_asset_depreciation_run SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE fixed_asset_depreciation_run ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE fixed_asset_depreciation_run ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE fixed_asset_depreciation_run ADD CONSTRAINT IF NOT EXISTS uq_fixed_asset_depreciation_run_portable_id UNIQUE (portable_id);
