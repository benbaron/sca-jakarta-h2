ALTER TABLE app_role ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;
UPDATE app_role SET is_active = TRUE WHERE is_active IS NULL;
ALTER TABLE app_role ALTER COLUMN is_active SET NOT NULL;

ALTER TABLE app_role ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
UPDATE app_role SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
ALTER TABLE app_role ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE app_role ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
UPDATE app_role SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE app_role ALTER COLUMN updated_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS ix_app_role_active ON app_role (is_active);

ALTER TABLE user_company_role ADD COLUMN IF NOT EXISTS start_date DATE;
UPDATE user_company_role
SET start_date = COALESCE(CAST(created_at AS DATE), CURRENT_DATE)
WHERE start_date IS NULL;
ALTER TABLE user_company_role ALTER COLUMN start_date SET NOT NULL;

ALTER TABLE user_company_role ADD COLUMN IF NOT EXISTS end_date DATE;
ALTER TABLE user_company_role ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE user_company_role ADD COLUMN IF NOT EXISTS end_reason VARCHAR(1000);
ALTER TABLE user_company_role ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP;
UPDATE user_company_role SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE user_company_role ALTER COLUMN updated_at SET NOT NULL;
UPDATE user_company_role SET end_date = start_date
WHERE is_active = FALSE AND end_date IS NULL;

ALTER TABLE user_company_role DROP CONSTRAINT IF EXISTS uq_user_company_role;
ALTER TABLE user_company_role ADD CONSTRAINT IF NOT EXISTS uq_user_company_role_period
    UNIQUE (user_id, company_id, role_id, start_date);
ALTER TABLE user_company_role ADD CONSTRAINT IF NOT EXISTS ck_user_company_role_dates
    CHECK (end_date IS NULL OR end_date >= start_date);
ALTER TABLE user_company_role ADD CONSTRAINT IF NOT EXISTS ck_user_company_role_active_end
    CHECK ((is_active = TRUE AND end_date IS NULL AND revoked_at IS NULL)
        OR (is_active = FALSE AND end_date IS NOT NULL));

CREATE INDEX IF NOT EXISTS ix_user_company_role_company_active
    ON user_company_role (company_id, is_active);
