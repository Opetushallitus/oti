ALTER TABLE payment
  DROP amount,
  DROP reference,
  DROP paym_call_id,
  DROP order_number;

DROP SEQUENCE payment_order_number_seq;
