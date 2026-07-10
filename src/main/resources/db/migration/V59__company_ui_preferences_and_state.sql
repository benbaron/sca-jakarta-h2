CREATE TABLE IF NOT EXISTS company_ui_preference (
    company_code VARCHAR(64) PRIMARY KEY,
    currency_symbol VARCHAR(8) NOT NULL DEFAULT '$',
    money_print_format VARCHAR(32) NOT NULL DEFAULT 'SYMBOL_PREFIX',
    date_display_format VARCHAR(32) NOT NULL DEFAULT 'MONTH_DAY_YEAR',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_company_ui_money_format CHECK (money_print_format IN ('SYMBOL_PREFIX', 'SYMBOL_SUFFIX', 'NUMBER_ONLY')),
    CONSTRAINT ck_company_ui_date_format CHECK (date_display_format IN ('MONTH_DAY_YEAR', 'DAY_MONTH_YEAR', 'YEAR_MONTH_DAY'))
);

CREATE TABLE IF NOT EXISTS company_ui_state (
    company_code VARCHAR(64) NOT NULL,
    state_key VARCHAR(240) NOT NULL,
    state_value CLOB NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (company_code, state_key)
);

CREATE INDEX IF NOT EXISTS ix_company_ui_state_company ON company_ui_state(company_code);
CREATE INDEX IF NOT EXISTS ix_company_ui_state_key ON company_ui_state(state_key);
