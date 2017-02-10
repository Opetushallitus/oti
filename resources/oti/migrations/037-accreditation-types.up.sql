UPDATE accredited_exam_module SET accreditation_type_id = (SELECT id FROM accreditation_type WHERE description = 'Muu korvaavuus') WHERE accreditation_type_id = (SELECT id FROM accreditation_type WHERE description = 'Kurssi');
UPDATE accredited_exam_section SET accreditation_type_id = (SELECT id FROM accreditation_type WHERE description = 'Muu korvaavuus') WHERE accreditation_type_id = (SELECT id FROM accreditation_type WHERE description = 'Kurssi');
DELETE FROM accreditation_type WHERE description = 'Kurssi';
