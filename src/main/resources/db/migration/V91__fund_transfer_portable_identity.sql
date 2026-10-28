-- Preserve all existing transfer facts, including unlinked legacy DRAFT/VOID history.
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE fund_transfer SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE fund_transfer ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE fund_transfer ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE fund_transfer ADD CONSTRAINT IF NOT EXISTS uq_fund_transfer_portable_id UNIQUE (portable_id);
