UPDATE section_translation
SET name = 'A (Hallinnon perusteet)'
WHERE section_id = 1 AND language_code = 'fi';
UPDATE section_translation
SET name = 'A (Förvaltningens grunder)'
WHERE section_id = 1 AND language_code = 'sv';

UPDATE module_translation
SET name = 'Yleishallinto, julkisuus, tietosuoja sekä kunnallishallinto'
WHERE module_id = 1 AND language_code = 'fi';
UPDATE module_translation
SET name = 'Allmän förvaltning, offentlighet, dataskydd samt kommunalförvaltning'
WHERE module_id = 1 AND language_code = 'sv';

UPDATE module_translation
SET name = 'Opetustoimen hallinto'
WHERE module_id = 2 AND language_code = 'fi';
UPDATE module_translation
SET name = 'Utbildningsväsendets förvaltning'
WHERE module_id = 2 AND language_code = 'sv';

UPDATE module_translation
SET name = 'Opetustoimen rahoitusjärjestelmä'
WHERE module_id = 3 AND language_code = 'fi';
UPDATE module_translation
SET name = 'Utbildningsväsendets finansieringssystem'
WHERE module_id = 3 AND language_code = 'sv';

DELETE FROM accredited_exam_module
WHERE module_id = 4;
DELETE FROM module_score
WHERE module_id = 4;
DELETE FROM registration_exam_content_module
WHERE module_id = 4;
DELETE FROM module
WHERE id = 4;

UPDATE section_translation
SET name = 'B (Rehtorin johtamisroolit)'
WHERE section_id = 2 AND language_code = 'fi';
UPDATE section_translation
SET name = 'B (Rektorns ledarroller)'
WHERE section_id = 2 AND language_code = 'sv';

UPDATE module_translation
SET name = 'Rehtori koulun toiminnasta vastaavana johtajana'
WHERE module_id = 5 AND language_code = 'fi';
UPDATE module_translation
SET name = 'Rektorn som ansvarig för skolans verksamhet'
WHERE module_id = 5 AND language_code = 'sv';

UPDATE module_translation
SET name = 'Rehtori pedagogisena johtajana'
WHERE module_id = 6 AND language_code = 'fi';
UPDATE module_translation
SET name = 'Rektorn som pedagogisk ledare'
WHERE module_id = 6 AND language_code = 'sv';

UPDATE module_translation
SET name = 'Rehtori henkilöstöjohtajana (palvelussuhde ja lakimääräiset velvoitteet)'
WHERE module_id = 7 AND language_code = 'fi';
UPDATE module_translation
SET name = 'Rektorn som personalchef (anställningsförhållanden)'
WHERE module_id = 7 AND language_code = 'sv';
