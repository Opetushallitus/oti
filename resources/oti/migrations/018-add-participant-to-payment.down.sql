DROP VIEW all_participant_data;

ALTER TABLE payment
  DROP COLUMN participant_id,
  ALTER registration_id SET NOT NULL;
