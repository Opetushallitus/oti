ALTER TABLE IF EXISTS module_score
  ADD CONSTRAINT module_once_per_section_score UNIQUE (module_id, section_score_id);

ALTER TABLE IF EXISTS section_score
  ADD CONSTRAINT section_once_per_exam_session UNIQUE (section_id, exam_session_id, participant_id);

ALTER TABLE IF EXISTS module_score
  ADD COLUMN updated TIMESTAMP DEFAULT NULL;

ALTER TABLE IF EXISTS section_score
  ADD COLUMN updated TIMESTAMP DEFAULT NULL;
