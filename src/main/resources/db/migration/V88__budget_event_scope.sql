ALTER TABLE budget_line ADD COLUMN IF NOT EXISTS activity_id BIGINT;

ALTER TABLE budget_line ADD CONSTRAINT IF NOT EXISTS fk_budget_line_activity
    FOREIGN KEY (activity_id) REFERENCES activity (id);

ALTER TABLE budget_line ADD COLUMN IF NOT EXISTS activity_scope BIGINT
    GENERATED ALWAYS AS (COALESCE(activity_id, -1));

ALTER TABLE budget_line DROP CONSTRAINT IF EXISTS uq_budget_line_scope;
ALTER TABLE budget_line ADD CONSTRAINT uq_budget_line_scope
    UNIQUE (budget_plan_id, budget_category_id, fund_scope, activity_scope, period_scope);

CREATE INDEX IF NOT EXISTS ix_budget_line_activity ON budget_line (activity_id);
