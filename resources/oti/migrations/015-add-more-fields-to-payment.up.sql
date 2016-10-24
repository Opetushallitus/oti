ALTER TABLE payment
  ADD COLUMN amount NUMERIC NOT NULL,
  ADD COLUMN reference NUMERIC NOT NULL,
  ADD COLUMN order_number VARCHAR(32) NOT NULL UNIQUE,
  ADD COLUMN paym_call_id VARCHAR(25) NOT NULL UNIQUE,
  ADD COLUMN payment_method TEXT,
  ADD COLUMN ext_archiving_id TEXT,
  ALTER ext_reference_id DROP NOT NULL;

CREATE SEQUENCE IF NOT EXISTS payment_order_number_seq;
