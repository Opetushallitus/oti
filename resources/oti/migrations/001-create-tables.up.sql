CREATE TABLE IF NOT EXISTS exam (
  id BIGSERIAL PRIMARY KEY
);
--;;
CREATE TABLE IF NOT EXISTS section (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  exam_id BIGINT REFERENCES exam (id) NOT NULL
);
--;;
CREATE TABLE IF NOT EXISTS module (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  section_id BIGINT REFERENCES section (id) NOT NULL
);
--;;
CREATE TABLE IF NOT EXISTS exam_session (
  id BIGSERIAL PRIMARY KEY,
  session_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  location_info TEXT NOT NULL,
  exam_id BIGINT REFERENCES exam (id) NOT NULL
);
--;;
CREATE TABLE IF NOT EXISTS participant (
  id BIGSERIAL PRIMARY KEY,
  ext_reference_id TEXT UNIQUE NOT NULL
);
--;;
CREATE TYPE registration_state AS ENUM ('OK', 'INCOMPLETE', 'ERROR');
--;;
CREATE TABLE IF NOT EXISTS registration (
  id BIGSERIAL PRIMARY KEY,
  state registration_state NOT NULL,
  exam_session_id BIGINT REFERENCES exam_session (id) NOT NULL,
  participant_id BIGINT REFERENCES participant (id) NOT NULL
);
--;;
CREATE TYPE payment_state AS ENUM ('OK', 'UNPAID', 'ERROR');
--;;
CREATE TYPE payment_type AS ENUM ('FULL', 'PARTIAL');
--;;
CREATE TABLE IF NOT EXISTS payment (
  id BIGSERIAL PRIMARY KEY,
  payment_date DATE NOT NULL,
  state payment_state NOT NULL,
  type payment_type NOT NULL,
  ext_reference_id TEXT UNIQUE NOT NULL,
  registration_id BIGINT REFERENCES registration (id) NOT NULL
);
--;;
CREATE TABLE IF NOT EXISTS section_score (
  id BIGSERIAL PRIMARY KEY,
  accepted BOOLEAN NOT NULL,
  section_id BIGINT REFERENCES section (id) NOT NULL,
  exam_session_id BIGINT REFERENCES exam_session (id) NOT NULL
);
--;;
CREATE TABLE IF NOT EXISTS module_score (
  id BIGSERIAL PRIMARY KEY,
  accepted BOOLEAN NOT NULL,
  points NUMERIC NOT NULL,
  module_id BIGINT REFERENCES module (id) NOT NULL,
  section_score_id BIGINT REFERENCES section_score (id) NOT NULL
);
