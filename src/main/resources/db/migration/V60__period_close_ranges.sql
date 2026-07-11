CREATE TABLE IF NOT EXISTS period_close_range (
    id UUID PRIMARY KEY,
    company_code VARCHAR(80) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    range_kind VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CLOSED',
    closed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_by VARCHAR(200) NOT NULL,
    close_reason VARCHAR(1000),
    reopened_at TIMESTAMP,
    reopened_by VARCHAR(200),
    reopen_reason VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_period_close_range_company_status_dates
    ON period_close_range(company_code, status, start_date, end_date);

CREATE TABLE IF NOT EXISTS period_close_event (
    id UUID PRIMARY KEY,
    close_range_id UUID NOT NULL,
    company_code VARCHAR(80) NOT NULL,
    event_type VARCHAR(20) NOT NULL,
    actor VARCHAR(200) NOT NULL,
    reason VARCHAR(1000),
    event_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_period_close_event_company_time
    ON period_close_event(company_code, event_at);
CREATE INDEX IF NOT EXISTS ix_period_close_event_range
    ON period_close_event(close_range_id);
