CREATE TABLE email (
  id BIGSERIAL PRIMARY KEY,
  participant_id BIGINT NULL REFERENCES participant(id),
  recipient TEXT NOT NULL,
  subject TEXT NOT NULL,
  body TEXT NOT NULL,
  created TIMESTAMP NOT NULL DEFAULT current_timestamp,
  sent TIMESTAMP
);
