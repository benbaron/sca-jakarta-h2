-- Replay-safe for the existing untracked-schema recovery baseline.
-- Retain legacy transfer rows; new interactive requests carry durable retry identity.
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS request_id UUID;
ALTER TABLE fund_transfer ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_fund_transfer_request ON fund_transfer(request_id);
CREATE INDEX IF NOT EXISTS ix_fund_transfer_funds_date ON fund_transfer(from_fund_id, to_fund_id, transfer_date);
