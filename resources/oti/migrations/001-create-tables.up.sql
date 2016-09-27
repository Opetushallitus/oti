CREATE TABLE IF NOT EXISTS language (
  code CHAR(2) PRIMARY KEY,
  name TEXT NOT NULL
);
--;;
CREATE TABLE IF NOT EXISTS exam (
  id BIGSERIAL PRIMARY KEY
);
--;;
CREATE TABLE IF NOT EXISTS section (
  id BIGSERIAL PRIMARY KEY,
  exam_id BIGINT REFERENCES exam (id) NOT NULL
);
--;;
CREATE TABLE IF NOT EXISTS section_translation (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  language_code CHAR(2) REFERENCES language (code) NOT NULL,
  section_id BIGINT NOT NULL REFERENCES section (id) ON DELETE CASCADE
);
--;;
CREATE TABLE IF NOT EXISTS module (
  id BIGSERIAL PRIMARY KEY,
  section_id BIGINT REFERENCES section (id) NOT NULL
);
--;;
CREATE TABLE IF NOT EXISTS module_translation (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  language_code CHAR(2) REFERENCES language (code) NOT NULL,
  module_id BIGINT NOT NULL REFERENCES module (id) ON DELETE CASCADE
);
--;;
CREATE TABLE IF NOT EXISTS exam_session (
  id BIGSERIAL PRIMARY KEY,
  session_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  max_participants INTEGER NOT NULL,
  exam_id BIGINT REFERENCES exam (id) NOT NULL
);
--;;
CREATE TABLE IF NOT EXISTS exam_session_translation (
  id BIGSERIAL PRIMARY KEY,
  street_address TEXT NOT NULL,
  city TEXT NOT NULL,
  other_location_info TEXT NOT NULL,
  language_code CHAR(2) REFERENCES language (code) NOT NULL,
  exam_session_id BIGINT NOT NULL REFERENCES exam_session (id) ON DELETE CASCADE
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
  created TIMESTAMP DEFAULT now(),
  exam_session_id BIGINT REFERENCES exam_session (id) NOT NULL,
  participant_id BIGINT REFERENCES participant (id) NOT NULL,
  CONSTRAINT one_participation_per_session_constraint UNIQUE (exam_session_id, participant_id)
);
--;;
CREATE TABLE IF NOT EXISTS accreditation_type (
  id BIGSERIAL PRIMARY KEY
);
--;;
CREATE TABLE IF NOT EXISTS accreditation_type_translation (
  id BIGSERIAL PRIMARY KEY,
  description TEXT NOT NULL,
  language_code CHAR(2) REFERENCES language (code) NOT NULL,
  accreditation_type_id BIGINT NOT NULL REFERENCES accreditation_type (id) ON DELETE CASCADE
);
--;;
CREATE TABLE IF NOT EXISTS accredited_exam_section (
  id BIGSERIAL PRIMARY KEY,
  accreditor TEXT NOT NULL,
  accreditation_date DATE NOT NULL DEFAULT current_date,
  accreditation_type_id BIGINT REFERENCES accreditation_type (id) NOT NULL,
  section_id BIGINT REFERENCES section (id) NOT NULL,
  participant_id BIGINT REFERENCES participant (id) NOT NULL,
  CONSTRAINT section_accredited_only_once UNIQUE (section_id, participant_id)
);
--;;
CREATE TABLE IF NOT EXISTS accredited_exam_module (
  id BIGSERIAL PRIMARY KEY,
  accreditor TEXT NOT NULL,
  accreditation_date DATE NOT NULL DEFAULT current_date,
  accreditation_type_id BIGINT REFERENCES accreditation_type (id) NOT NULL,
  module_id BIGINT REFERENCES module (id) NOT NULL,
  participant_id BIGINT REFERENCES participant (id) NOT NULL,
  CONSTRAINT module_accredited_only_once UNIQUE (module_id, participant_id)
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
  created TIMESTAMP DEFAULT now(),
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
