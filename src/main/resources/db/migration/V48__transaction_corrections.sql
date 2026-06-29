ALTER TABLE txn ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ENTERED';
ALTER TABLE txn ADD COLUMN reversal_of_txn_id BIGINT NULL;
ALTER TABLE txn ADD COLUMN replacement_for_txn_id BIGINT NULL;
ALTER TABLE txn ADD COLUMN correction_note VARCHAR(1000) NULL;

ALTER TABLE txn ADD CONSTRAINT fk_txn_reversal_of FOREIGN KEY (reversal_of_txn_id) REFERENCES txn(id);
ALTER TABLE txn ADD CONSTRAINT fk_txn_replacement_for FOREIGN KEY (replacement_for_txn_id) REFERENCES txn(id);
ALTER TABLE txn ADD CONSTRAINT uq_txn_reversal_of UNIQUE (reversal_of_txn_id);
ALTER TABLE txn ADD CONSTRAINT ck_txn_status CHECK (status IN ('ENTERED', 'REVERSED'));

CREATE INDEX ix_txn_status ON txn(status);
CREATE INDEX ix_txn_replacement_for ON txn(replacement_for_txn_id);
