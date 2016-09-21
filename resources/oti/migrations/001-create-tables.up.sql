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
  max_participants NUMERIC DEFAULT 40,
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
  participant_id BIGINT REFERENCES participant (id) NOT NULL,
  CONSTRAINT one_participation_per_session_constraint UNIQUE (exam_session_id, participant_id)
);
--;;
CREATE TABLE IF NOT EXISTS registration_exam_content_section (
  id BIGSERIAL PRIMARY KEY,
  section_id BIGINT REFERENCES section (id) NOT NULL,
  participant_id BIGINT REFERENCES participant (id) NOT NULL,
  registration_id BIGINT REFERENCES registration (id) NOT NULL,
  CONSTRAINT section_only_once_for_single_registration_constraint UNIQUE (section_id, participant_id, registration_id)
);
--;;
CREATE TABLE IF NOT EXISTS registration_exam_content_module (
  id BIGSERIAL PRIMARY KEY,
  module_id BIGINT REFERENCES module (id) NOT NULL,
  participant_id BIGINT REFERENCES participant (id) NOT NULL,
  registration_id BIGINT REFERENCES registration (id) NOT NULL,
  CONSTRAINT module_only_once_for_single_registration_constraint UNIQUE (module_id, participant_id, registration_id)
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
  participant_id BIGINT REFERENCES participant (id) NOT NULL,
  exam_session_id BIGINT REFERENCES exam_session (id)
);
--;;
CREATE TABLE IF NOT EXISTS module_score (
  id BIGSERIAL PRIMARY KEY,
  accepted BOOLEAN NOT NULL,
  points NUMERIC NOT NULL,
  module_id BIGINT REFERENCES module (id) NOT NULL,
  section_score_id BIGINT REFERENCES section_score (id) NOT NULL
);
