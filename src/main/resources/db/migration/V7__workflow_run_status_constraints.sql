-- V7: normalize workflow run status/bank format tokens with DB constraints

ALTER TABLE reconciliation_run
  ADD CONSTRAINT ck_reconciliation_run_status
  CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED'));

ALTER TABLE reconciliation_run
  ADD CONSTRAINT ck_reconciliation_run_bank_format
  CHECK (bank_format IN ('OFX', 'QFX'));

ALTER TABLE period_close_run
  ADD CONSTRAINT ck_period_close_run_status
  CHECK (status IN ('STARTED', 'COMPLETED', 'FAILED'));
