ALTER TABLE bank_statement_line DROP CONSTRAINT IF EXISTS ck_bank_statement_line_amount;
ALTER TABLE bank_statement_line ALTER COLUMN transaction_date DROP NOT NULL;
ALTER TABLE bank_statement_line ALTER COLUMN amount DROP NOT NULL;
