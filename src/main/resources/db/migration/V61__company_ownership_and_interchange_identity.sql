-- P15-S1: make selected-company interchange ownership explicit without guessing
-- ambiguous multi-company history. All new ownership columns are nullable in this
-- migration; application services require ownership for new writes and diagnostics
-- retain unresolved historical rows for explicit repair.

ALTER TABLE chart_of_accounts ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE txn ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE fund ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE budget_category ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE budget_plan ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE activity ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE counterparty ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE merchant ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE accounting_period ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE audit_event ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE period_close_range ADD COLUMN IF NOT EXISTS company_id BIGINT;
ALTER TABLE period_close_event ADD COLUMN IF NOT EXISTS company_id BIGINT;

ALTER TABLE chart_of_accounts ADD CONSTRAINT IF NOT EXISTS fk_chart_company FOREIGN KEY (company_id) REFERENCES company(id);
ALTER TABLE txn ADD CONSTRAINT IF NOT EXISTS fk_txn_company FOREIGN KEY (company_id) REFERENCES company(id);
ALTER TABLE fund ADD CONSTRAINT IF NOT EXISTS fk_fund_company FOREIGN KEY (company_id) REFERENCES company(id);
ALTER TABLE budget_category ADD CONSTRAINT IF NOT EXISTS fk_budget_category_company FOREIGN KEY (company_id) REFERENCES company(id);
ALTER TABLE budget_plan ADD CONSTRAINT IF NOT EXISTS fk_budget_plan_company FOREIGN KEY (company_id) REFERENCES company(id);
ALTER TABLE activity ADD CONSTRAINT IF NOT EXISTS fk_activity_company FOREIGN KEY (company_id) REFERENCES company(id);
ALTER TABLE counterparty ADD CONSTRAINT IF NOT EXISTS fk_counterparty_company FOREIGN KEY (company_id) REFERENCES company(id);
ALTER TABLE merchant ADD CONSTRAINT IF NOT EXISTS fk_merchant_company FOREIGN KEY (company_id) REFERENCES company(id);
ALTER TABLE accounting_period ADD CONSTRAINT IF NOT EXISTS fk_accounting_period_company FOREIGN KEY (company_id) REFERENCES company(id);
ALTER TABLE audit_event ADD CONSTRAINT IF NOT EXISTS fk_audit_event_company FOREIGN KEY (company_id) REFERENCES company(id);
ALTER TABLE period_close_range ADD CONSTRAINT IF NOT EXISTS fk_period_close_range_company FOREIGN KEY (company_id) REFERENCES company(id);
ALTER TABLE period_close_event ADD CONSTRAINT IF NOT EXISTS fk_period_close_event_company FOREIGN KEY (company_id) REFERENCES company(id);

CREATE INDEX IF NOT EXISTS ix_chart_company ON chart_of_accounts(company_id);
CREATE INDEX IF NOT EXISTS ix_txn_company_date ON txn(company_id, txn_date);
CREATE INDEX IF NOT EXISTS ix_fund_company_active ON fund(company_id, is_active);
CREATE INDEX IF NOT EXISTS ix_budget_category_company_active ON budget_category(company_id, is_active);
CREATE INDEX IF NOT EXISTS ix_budget_plan_company_year ON budget_plan(company_id, fiscal_year);
CREATE INDEX IF NOT EXISTS ix_activity_company_active ON activity(company_id, is_active);
CREATE INDEX IF NOT EXISTS ix_counterparty_company_name ON counterparty(company_id, display_name);
CREATE INDEX IF NOT EXISTS ix_merchant_company_name ON merchant(company_id, name);
CREATE INDEX IF NOT EXISTS ix_accounting_period_company_dates ON accounting_period(company_id, start_date, end_date);
CREATE INDEX IF NOT EXISTS ix_audit_event_company_time ON audit_event(company_id, occurred_at);
CREATE INDEX IF NOT EXISTS ix_period_close_range_company_id_dates ON period_close_range(company_id, status, start_date, end_date);
CREATE INDEX IF NOT EXISTS ix_period_close_event_company_id_time ON period_close_event(company_id, event_at);

-- Replace formerly global business keys with company-scoped keys. Nullable
-- company_id deliberately permits unresolved historical rows to remain untouched.
EXECUTE IMMEDIATE (
    SELECT COALESCE(
        MAX('ALTER TABLE fund DROP CONSTRAINT "' || constraint_name || '"'),
        'SELECT 1')
    FROM information_schema.table_constraints
    WHERE lower(table_schema) = 'public'
      AND lower(table_name) = 'fund'
      AND constraint_type = 'UNIQUE'
);
ALTER TABLE fund DROP CONSTRAINT IF EXISTS uq_fund_code;
ALTER TABLE fund ADD CONSTRAINT IF NOT EXISTS uq_fund_company_code UNIQUE (company_id, code);
ALTER TABLE budget_category DROP CONSTRAINT IF EXISTS uq_budget_category_code;
ALTER TABLE budget_category ADD CONSTRAINT IF NOT EXISTS uq_budget_category_company_code UNIQUE (company_id, code);
ALTER TABLE budget_plan DROP CONSTRAINT IF EXISTS uq_budget_plan_fiscal_version;
ALTER TABLE budget_plan ADD CONSTRAINT IF NOT EXISTS uq_budget_plan_company_fiscal_version UNIQUE (company_id, fiscal_year, version_code);
EXECUTE IMMEDIATE (
    SELECT COALESCE(
        MAX('ALTER TABLE activity DROP CONSTRAINT "' || constraint_name || '"'),
        'SELECT 1')
    FROM information_schema.table_constraints
    WHERE lower(table_schema) = 'public'
      AND lower(table_name) = 'activity'
      AND constraint_type = 'UNIQUE'
);
ALTER TABLE activity DROP CONSTRAINT IF EXISTS uq_activity_code;
ALTER TABLE activity ADD CONSTRAINT IF NOT EXISTS uq_activity_company_code UNIQUE (company_id, code);
EXECUTE IMMEDIATE (
    SELECT COALESCE(
        MAX('ALTER TABLE merchant DROP CONSTRAINT "' || constraint_name || '"'),
        'SELECT 1')
    FROM information_schema.table_constraints
    WHERE lower(table_schema) = 'public'
      AND lower(table_name) = 'merchant'
      AND constraint_type = 'UNIQUE'
);
ALTER TABLE merchant DROP CONSTRAINT IF EXISTS uq_merchant_name;
ALTER TABLE merchant ADD CONSTRAINT IF NOT EXISTS uq_merchant_company_name UNIQUE (company_id, name);
ALTER TABLE accounting_period DROP CONSTRAINT IF EXISTS uq_accounting_period_year_number;
ALTER TABLE accounting_period ADD CONSTRAINT IF NOT EXISTS uq_accounting_period_company_year_number UNIQUE (company_id, fiscal_year, period_number);

-- Deterministic chart ownership: explicit active-chart reference first.
UPDATE chart_of_accounts chart
SET company_id = (
    SELECT MIN(company.id)
    FROM company
    WHERE company.active_chart_of_accounts_id = chart.id
)
WHERE chart.company_id IS NULL
  AND 1 = (
      SELECT COUNT(DISTINCT company.id)
      FROM company
      WHERE company.active_chart_of_accounts_id = chart.id
  );

-- A database containing exactly one company has a deterministic owner for legacy
-- rows. This is a database fact, not the currently selected UI company.
UPDATE chart_of_accounts SET company_id = (SELECT MIN(id) FROM company)
WHERE company_id IS NULL AND 1 = (SELECT COUNT(*) FROM company);
UPDATE fund SET company_id = (SELECT MIN(id) FROM company)
WHERE company_id IS NULL AND 1 = (SELECT COUNT(*) FROM company);
UPDATE budget_category SET company_id = (SELECT MIN(id) FROM company)
WHERE company_id IS NULL AND 1 = (SELECT COUNT(*) FROM company);
UPDATE budget_plan SET company_id = (SELECT MIN(id) FROM company)
WHERE company_id IS NULL AND 1 = (SELECT COUNT(*) FROM company);
UPDATE activity SET company_id = (SELECT MIN(id) FROM company)
WHERE company_id IS NULL AND 1 = (SELECT COUNT(*) FROM company);
UPDATE counterparty SET company_id = (SELECT MIN(id) FROM company)
WHERE company_id IS NULL AND 1 = (SELECT COUNT(*) FROM company);
UPDATE merchant SET company_id = (SELECT MIN(id) FROM company)
WHERE company_id IS NULL AND 1 = (SELECT COUNT(*) FROM company);
UPDATE accounting_period SET company_id = (SELECT MIN(id) FROM company)
WHERE company_id IS NULL AND 1 = (SELECT COUNT(*) FROM company);
UPDATE audit_event SET company_id = (SELECT MIN(id) FROM company)
WHERE company_id IS NULL AND 1 = (SELECT COUNT(*) FROM company);
UPDATE txn SET company_id = (SELECT MIN(id) FROM company)
WHERE company_id IS NULL AND 1 = (SELECT COUNT(*) FROM company);

-- Period-close company_code is retained for compatibility but company_id becomes
-- the stable authority when the code resolves uniquely.
UPDATE period_close_range close_range
SET company_id = (
    SELECT MIN(company.id) FROM company
    WHERE UPPER(company.code) = UPPER(close_range.company_code)
)
WHERE close_range.company_id IS NULL
  AND 1 = (
      SELECT COUNT(*) FROM company
      WHERE UPPER(company.code) = UPPER(close_range.company_code)
  );
UPDATE period_close_event close_event
SET company_id = (
    SELECT MIN(company.id) FROM company
    WHERE UPPER(company.code) = UPPER(close_event.company_code)
)
WHERE close_event.company_id IS NULL
  AND 1 = (
      SELECT COUNT(*) FROM company
      WHERE UPPER(company.code) = UPPER(close_event.company_code)
  );

-- In multi-company databases, infer ownership only when all available reference
-- evidence points to exactly one company. H2 correlated subqueries are used
-- directly; no outer update alias is referenced from a derived table.

-- Prefer the configured bank account when every owned split dimension agrees.
UPDATE txn transaction_header
SET company_id = (
    SELECT chart.company_id
    FROM account account_row
    JOIN chart_of_accounts chart ON chart.id = account_row.chart_id
    WHERE account_row.id = transaction_header.bank_account_id
)
WHERE transaction_header.company_id IS NULL
  AND transaction_header.bank_account_id IS NOT NULL
  AND (SELECT chart.company_id
       FROM account account_row
       JOIN chart_of_accounts chart ON chart.id = account_row.chart_id
       WHERE account_row.id = transaction_header.bank_account_id) IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM txn_split split_row
      JOIN account account_row ON account_row.id = split_row.account_id
      JOIN chart_of_accounts chart ON chart.id = account_row.chart_id
      WHERE split_row.txn_id = transaction_header.id
        AND chart.company_id IS NOT NULL
        AND chart.company_id <> (
            SELECT bank_chart.company_id
            FROM account bank_account
            JOIN chart_of_accounts bank_chart ON bank_chart.id = bank_account.chart_id
            WHERE bank_account.id = transaction_header.bank_account_id
        )
  )
  AND NOT EXISTS (
      SELECT 1
      FROM txn_split split_row
      JOIN fund ON fund.id = split_row.fund_id
      WHERE split_row.txn_id = transaction_header.id
        AND fund.company_id IS NOT NULL
        AND fund.company_id <> (
            SELECT bank_chart.company_id
            FROM account bank_account
            JOIN chart_of_accounts bank_chart ON bank_chart.id = bank_account.chart_id
            WHERE bank_account.id = transaction_header.bank_account_id
        )
  );

-- Otherwise use one distinct split-account owner when every fund agrees.
UPDATE txn transaction_header
SET company_id = (
    SELECT MIN(chart.company_id)
    FROM txn_split split_row
    JOIN account account_row ON account_row.id = split_row.account_id
    JOIN chart_of_accounts chart ON chart.id = account_row.chart_id
    WHERE split_row.txn_id = transaction_header.id
      AND chart.company_id IS NOT NULL
)
WHERE transaction_header.company_id IS NULL
  AND 1 = (
      SELECT COUNT(DISTINCT chart.company_id)
      FROM txn_split split_row
      JOIN account account_row ON account_row.id = split_row.account_id
      JOIN chart_of_accounts chart ON chart.id = account_row.chart_id
      WHERE split_row.txn_id = transaction_header.id
        AND chart.company_id IS NOT NULL
  )
  AND NOT EXISTS (
      SELECT 1
      FROM txn_split fund_split
      JOIN fund ON fund.id = fund_split.fund_id
      WHERE fund_split.txn_id = transaction_header.id
        AND fund.company_id IS NOT NULL
        AND fund.company_id <> (
            SELECT MIN(account_chart.company_id)
            FROM txn_split account_split
            JOIN account account_row ON account_row.id = account_split.account_id
            JOIN chart_of_accounts account_chart ON account_chart.id = account_row.chart_id
            WHERE account_split.txn_id = transaction_header.id
              AND account_chart.company_id IS NOT NULL
        )
  );

-- A fund-only transaction can still be resolved when all funds agree.
UPDATE txn transaction_header
SET company_id = (
    SELECT MIN(fund.company_id)
    FROM txn_split split_row
    JOIN fund ON fund.id = split_row.fund_id
    WHERE split_row.txn_id = transaction_header.id
      AND fund.company_id IS NOT NULL
)
WHERE transaction_header.company_id IS NULL
  AND 1 = (
      SELECT COUNT(DISTINCT fund.company_id)
      FROM txn_split split_row
      JOIN fund ON fund.id = split_row.fund_id
      WHERE split_row.txn_id = transaction_header.id
        AND fund.company_id IS NOT NULL
  );

UPDATE fund master
SET company_id = (
    SELECT MIN(transaction_header.company_id)
    FROM txn_split split_row
    JOIN txn transaction_header ON transaction_header.id = split_row.txn_id
    WHERE split_row.fund_id = master.id
      AND transaction_header.company_id IS NOT NULL
)
WHERE master.company_id IS NULL
  AND 1 = (
      SELECT COUNT(DISTINCT transaction_header.company_id)
      FROM txn_split split_row
      JOIN txn transaction_header ON transaction_header.id = split_row.txn_id
      WHERE split_row.fund_id = master.id
        AND transaction_header.company_id IS NOT NULL
  );

UPDATE budget_category master
SET company_id = (
    SELECT MIN(transaction_header.company_id)
    FROM txn_split split_row
    JOIN txn transaction_header ON transaction_header.id = split_row.txn_id
    WHERE split_row.budget_category_id = master.id
      AND transaction_header.company_id IS NOT NULL
)
WHERE master.company_id IS NULL
  AND 1 = (
      SELECT COUNT(DISTINCT transaction_header.company_id)
      FROM txn_split split_row
      JOIN txn transaction_header ON transaction_header.id = split_row.txn_id
      WHERE split_row.budget_category_id = master.id
        AND transaction_header.company_id IS NOT NULL
  );

UPDATE activity master
SET company_id = (
    SELECT MIN(transaction_header.company_id)
    FROM txn_split split_row
    JOIN txn transaction_header ON transaction_header.id = split_row.txn_id
    WHERE split_row.activity_id = master.id
      AND transaction_header.company_id IS NOT NULL
)
WHERE master.company_id IS NULL
  AND 1 = (
      SELECT COUNT(DISTINCT transaction_header.company_id)
      FROM txn_split split_row
      JOIN txn transaction_header ON transaction_header.id = split_row.txn_id
      WHERE split_row.activity_id = master.id
        AND transaction_header.company_id IS NOT NULL
  );

UPDATE merchant master
SET company_id = (
    SELECT MIN(transaction_header.company_id)
    FROM txn_split split_row
    JOIN txn transaction_header ON transaction_header.id = split_row.txn_id
    WHERE split_row.merchant_id = master.id
      AND transaction_header.company_id IS NOT NULL
)
WHERE master.company_id IS NULL
  AND 1 = (
      SELECT COUNT(DISTINCT transaction_header.company_id)
      FROM txn_split split_row
      JOIN txn transaction_header ON transaction_header.id = split_row.txn_id
      WHERE split_row.merchant_id = master.id
        AND transaction_header.company_id IS NOT NULL
  );

UPDATE counterparty master
SET company_id = (
    SELECT MIN(transaction_header.company_id)
    FROM txn transaction_header
    WHERE transaction_header.payee_id = master.id
      AND transaction_header.company_id IS NOT NULL
)
WHERE master.company_id IS NULL
  AND 1 = (
      SELECT COUNT(DISTINCT transaction_header.company_id)
      FROM txn transaction_header
      WHERE transaction_header.payee_id = master.id
        AND transaction_header.company_id IS NOT NULL
  );

-- Resolve a plan through categories first when all funds agree.
UPDATE budget_plan plan
SET company_id = (
    SELECT MIN(category.company_id)
    FROM budget_line line_row
    JOIN budget_category category ON category.id = line_row.budget_category_id
    WHERE line_row.budget_plan_id = plan.id
      AND category.company_id IS NOT NULL
)
WHERE plan.company_id IS NULL
  AND 1 = (
      SELECT COUNT(DISTINCT category.company_id)
      FROM budget_line line_row
      JOIN budget_category category ON category.id = line_row.budget_category_id
      WHERE line_row.budget_plan_id = plan.id
        AND category.company_id IS NOT NULL
  )
  AND NOT EXISTS (
      SELECT 1
      FROM budget_line fund_line
      JOIN fund ON fund.id = fund_line.fund_id
      WHERE fund_line.budget_plan_id = plan.id
        AND fund.company_id IS NOT NULL
        AND fund.company_id <> (
            SELECT MIN(category.company_id)
            FROM budget_line category_line
            JOIN budget_category category ON category.id = category_line.budget_category_id
            WHERE category_line.budget_plan_id = plan.id
              AND category.company_id IS NOT NULL
        )
  );

UPDATE budget_plan plan
SET company_id = (
    SELECT MIN(fund.company_id)
    FROM budget_line line_row
    JOIN fund ON fund.id = line_row.fund_id
    WHERE line_row.budget_plan_id = plan.id
      AND fund.company_id IS NOT NULL
)
WHERE plan.company_id IS NULL
  AND 1 = (
      SELECT COUNT(DISTINCT fund.company_id)
      FROM budget_line line_row
      JOIN fund ON fund.id = line_row.fund_id
      WHERE line_row.budget_plan_id = plan.id
        AND fund.company_id IS NOT NULL
  );

CREATE TABLE IF NOT EXISTS company_ownership_issue (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(120) NOT NULL,
    issue_code VARCHAR(80) NOT NULL,
    candidate_company_count INTEGER NOT NULL DEFAULT 0,
    details VARCHAR(1000) NOT NULL,
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    CONSTRAINT uq_company_ownership_issue UNIQUE (entity_type, entity_id, issue_code),
    CONSTRAINT ck_company_ownership_issue_candidates CHECK (candidate_company_count >= 0)
);
CREATE INDEX IF NOT EXISTS ix_company_ownership_issue_open ON company_ownership_issue(resolved_at, entity_type);

INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'CHART_OF_ACCOUNTS', CAST(id AS VARCHAR), 'UNRESOLVED_OWNER', 0, 'Chart has no deterministic company owner.'
FROM chart_of_accounts WHERE company_id IS NULL;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'TXN', CAST(id AS VARCHAR), 'UNRESOLVED_OWNER', 0, 'Transaction has no deterministic company owner.'
FROM txn WHERE company_id IS NULL;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'FUND', CAST(id AS VARCHAR), 'UNRESOLVED_OWNER', 0, 'Fund has no deterministic company owner.'
FROM fund WHERE company_id IS NULL;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'BUDGET_CATEGORY', CAST(id AS VARCHAR), 'UNRESOLVED_OWNER', 0, 'Budget category has no deterministic company owner.'
FROM budget_category WHERE company_id IS NULL;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'BUDGET_PLAN', CAST(id AS VARCHAR), 'UNRESOLVED_OWNER', 0, 'Budget plan has no deterministic company owner.'
FROM budget_plan WHERE company_id IS NULL;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'ACTIVITY', CAST(id AS VARCHAR), 'UNRESOLVED_OWNER', 0, 'Activity has no deterministic company owner.'
FROM activity WHERE company_id IS NULL;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'COUNTERPARTY', CAST(id AS VARCHAR), 'UNRESOLVED_OWNER', 0, 'Counterparty has no deterministic company owner.'
FROM counterparty WHERE company_id IS NULL;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'MERCHANT', CAST(id AS VARCHAR), 'UNRESOLVED_OWNER', 0, 'Merchant has no deterministic company owner.'
FROM merchant WHERE company_id IS NULL;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'ACCOUNTING_PERIOD', CAST(id AS VARCHAR), 'UNRESOLVED_OWNER', 0, 'Compatibility accounting period has no deterministic company owner.'
FROM accounting_period WHERE company_id IS NULL;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'AUDIT_EVENT', CAST(id AS VARCHAR), 'UNRESOLVED_OWNER', 0, 'Audit event has no deterministic company owner.'
FROM audit_event WHERE company_id IS NULL;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'PERIOD_CLOSE_RANGE', CAST(id AS VARCHAR), 'UNRESOLVED_OWNER', 0, 'Close range company_code does not resolve uniquely.'
FROM period_close_range WHERE company_id IS NULL;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'PERIOD_CLOSE_EVENT', CAST(id AS VARCHAR), 'UNRESOLVED_OWNER', 0, 'Close event company_code does not resolve uniquely.'
FROM period_close_event WHERE company_id IS NULL;

-- Cross-company facts are retained and diagnosed; this migration never rewrites
-- an accounting reference to force it into one company.
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'TXN_SPLIT', CAST(split_row.id AS VARCHAR), 'CROSS_COMPANY_REFERENCE', 2,
       'Transaction split account chart is owned by a different company than its transaction.'
FROM txn_split split_row
JOIN txn transaction_header ON transaction_header.id = split_row.txn_id
JOIN account account_row ON account_row.id = split_row.account_id
JOIN chart_of_accounts chart ON chart.id = account_row.chart_id
WHERE transaction_header.company_id IS NOT NULL AND chart.company_id IS NOT NULL
  AND transaction_header.company_id <> chart.company_id;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'TXN_SPLIT', CAST(split_row.id AS VARCHAR), 'CROSS_COMPANY_FUND', 2,
       'Transaction split fund is owned by a different company than its transaction.'
FROM txn_split split_row
JOIN txn transaction_header ON transaction_header.id = split_row.txn_id
JOIN fund ON fund.id = split_row.fund_id
WHERE transaction_header.company_id IS NOT NULL AND fund.company_id IS NOT NULL
  AND transaction_header.company_id <> fund.company_id;
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'BUDGET_LINE', CAST(line_row.id AS VARCHAR), 'CROSS_COMPANY_REFERENCE', 2,
       'Budget line category or fund is owned by a different company than its budget plan.'
FROM budget_line line_row
JOIN budget_plan plan ON plan.id = line_row.budget_plan_id
JOIN budget_category category ON category.id = line_row.budget_category_id
LEFT JOIN fund ON fund.id = line_row.fund_id
WHERE plan.company_id IS NOT NULL
  AND ((category.company_id IS NOT NULL AND category.company_id <> plan.company_id)
       OR (fund.company_id IS NOT NULL AND fund.company_id <> plan.company_id));
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'FIXED_ASSET', CAST(asset.id AS VARCHAR), 'CROSS_COMPANY_REFERENCE', 2,
       'Fixed asset account or fund reference crosses company ownership.'
FROM fixed_asset asset
JOIN account asset_account ON asset_account.id = asset.asset_account_id
JOIN chart_of_accounts asset_chart ON asset_chart.id = asset_account.chart_id
JOIN fund ON fund.id = asset.fund_id
WHERE (asset_chart.company_id IS NOT NULL AND asset_chart.company_id <> asset.company_id)
   OR (fund.company_id IS NOT NULL AND fund.company_id <> asset.company_id);
INSERT INTO company_ownership_issue(entity_type, entity_id, issue_code, candidate_company_count, details)
SELECT 'INVENTORY_ITEM', CAST(item.id AS VARCHAR), 'CROSS_COMPANY_REFERENCE', 2,
       'Inventory account or fund reference crosses company ownership.'
FROM inventory_item item
JOIN account inventory_account ON inventory_account.id = item.inventory_account_id
JOIN chart_of_accounts chart ON chart.id = inventory_account.chart_id
JOIN fund ON fund.id = item.fund_id
WHERE (chart.company_id IS NOT NULL AND chart.company_id <> item.company_id)
   OR (fund.company_id IS NOT NULL AND fund.company_id <> item.company_id);

CREATE TABLE IF NOT EXISTS interchange_identity (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    company_id BIGINT NOT NULL,
    format_code VARCHAR(40) NOT NULL,
    source_system VARCHAR(160) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    external_id VARCHAR(160) NOT NULL,
    normalized_content_hash VARCHAR(64) NOT NULL,
    local_entity_id VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interchange_identity_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT uq_interchange_identity_external UNIQUE (company_id, format_code, source_system, entity_type, external_id),
    CONSTRAINT ck_interchange_identity_hash CHECK (CHAR_LENGTH(normalized_content_hash) = 64)
);
CREATE INDEX IF NOT EXISTS ix_interchange_identity_company_type ON interchange_identity(company_id, entity_type);
CREATE INDEX IF NOT EXISTS ix_interchange_identity_local ON interchange_identity(company_id, entity_type, local_entity_id);
