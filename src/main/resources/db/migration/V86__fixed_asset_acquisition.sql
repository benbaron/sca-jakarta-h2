ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS acquisition_source VARCHAR(24);
ALTER TABLE fixed_asset ADD COLUMN IF NOT EXISTS acquisition_split_id BIGINT;
ALTER TABLE fixed_asset ADD CONSTRAINT IF NOT EXISTS fk_fixed_asset_acquisition_split
    FOREIGN KEY (acquisition_split_id) REFERENCES txn_split(id);
ALTER TABLE fixed_asset ADD CONSTRAINT IF NOT EXISTS ck_fixed_asset_acquisition_source
    CHECK (((acquisition_source IS NULL OR acquisition_source = 'REGISTER_ONLY') AND acquisition_split_id IS NULL)
        OR (acquisition_source IS NOT NULL AND acquisition_source IN ('NEW_PURCHASE', 'EXISTING_ENTRY') AND acquisition_split_id IS NOT NULL));
CREATE INDEX IF NOT EXISTS ix_fixed_asset_acquisition_split ON fixed_asset(acquisition_split_id);
