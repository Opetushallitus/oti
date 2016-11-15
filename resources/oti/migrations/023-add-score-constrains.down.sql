ALTER TABLE IF EXISTS section_score
DROP CONSTRAINT IF EXISTS section_once_per_exam_session;

ALTER TABLE IF EXISTS module_score
DROP CONSTRAINT IF EXISTS module_once_per_section_score;

ALTER TABLE IF EXISTS section_score
  DROP COLUMN updated;

ALTER TABLE IF EXISTS module_score
  DROP COLUMN updated;
