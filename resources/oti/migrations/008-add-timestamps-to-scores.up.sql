ALTER TABLE section_score
    ADD COLUMN created TIMESTAMP NOT NULL DEFAULT current_timestamp;

ALTER TABLE module_score
  ADD COLUMN created TIMESTAMP NOT NULL DEFAULT current_timestamp;
