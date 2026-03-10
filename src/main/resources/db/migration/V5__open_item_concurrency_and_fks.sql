-- V5: optimistic concurrency and transaction foreign keys for open-item tables

ALTER TABLE open_item_snapshot
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE open_item_snapshot
  ADD CONSTRAINT fk_open_item_snapshot_last_txn
  FOREIGN KEY (last_transaction_id) REFERENCES journal_transaction(id);

ALTER TABLE open_item_transition
  ADD CONSTRAINT fk_open_item_transition_trigger_txn
  FOREIGN KEY (trigger_transaction_id) REFERENCES journal_transaction(id);
