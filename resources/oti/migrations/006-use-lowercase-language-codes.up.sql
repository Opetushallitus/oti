INSERT INTO language (code, name) VALUES ('fi', 'Finnish'), ('sv', 'Swedish');
UPDATE accreditation_type_translation SET language_code = lower(language_code);
UPDATE exam_session_translation SET language_code = lower(language_code);
UPDATE module_translation SET language_code = lower(language_code);
UPDATE section_translation SET language_code = lower(language_code);
DELETE FROM language WHERE code IN ('FI', 'SV');
