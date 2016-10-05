DROP TABLE accreditation_type_translation;

ALTER TABLE accreditation_type
    ADD COLUMN description TEXT NOT NULL DEFAULT '';

INSERT INTO accreditation_type (description) VALUES ('Kurssi'), ('Essee'), ('Muu korvaavuus');

ALTER TABLE accredited_exam_section
  ALTER COLUMN accreditor DROP NOT NULL,
  ALTER COLUMN accreditation_date DROP NOT NULL,
  ALTER COLUMN accreditation_type_id DROP NOT NULL;

ALTER TABLE accredited_exam_module
  ALTER COLUMN accreditor DROP NOT NULL,
  ALTER COLUMN accreditation_date DROP NOT NULL,
  ALTER COLUMN accreditation_type_id DROP NOT NULL;
