-- V6: workflow run records for reconciliation and period close auditing

CREATE TABLE reconciliation_run (
  id UUID PRIMARY KEY,
  group_code VARCHAR(64) NOT NULL,
  statement_ending_on DATE NOT NULL,
  bank_format VARCHAR(20) NOT NULL,
  imported_transaction_count INT NOT NULL,
  status VARCHAR(20) NOT NULL,
  notes VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_reconciliation_run_group_end ON reconciliation_run(group_code, statement_ending_on);

CREATE TABLE period_close_run (
  id UUID PRIMARY KEY,
  group_code VARCHAR(64) NOT NULL,
  close_date DATE NOT NULL,
  status VARCHAR(20) NOT NULL,
  produced_transaction_id UUID NULL,
  notes VARCHAR(500) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_period_close_run_txn FOREIGN KEY (produced_transaction_id) REFERENCES journal_transaction(id)
);

CREATE INDEX ix_period_close_run_group_date ON period_close_run(group_code, close_date);
