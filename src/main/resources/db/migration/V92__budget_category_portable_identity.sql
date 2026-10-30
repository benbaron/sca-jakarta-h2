ALTER TABLE budget_category ADD COLUMN IF NOT EXISTS portable_id UUID;
UPDATE budget_category SET portable_id = RANDOM_UUID() WHERE portable_id IS NULL;
ALTER TABLE budget_category ALTER COLUMN portable_id SET DEFAULT RANDOM_UUID();
ALTER TABLE budget_category ALTER COLUMN portable_id SET NOT NULL;
ALTER TABLE budget_category ADD CONSTRAINT IF NOT EXISTS uq_budget_category_portable_id UNIQUE (portable_id);
