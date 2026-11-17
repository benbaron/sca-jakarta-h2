ALTER TABLE chart_of_accounts ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE chart_of_accounts SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE chart_of_accounts ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE chart_of_accounts ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE chart_of_accounts ADD CONSTRAINT IF NOT EXISTS uq_chart_portable_id UNIQUE (portable_id);
