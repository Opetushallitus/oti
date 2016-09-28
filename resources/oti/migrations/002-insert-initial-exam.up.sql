INSERT INTO language (code, name) VALUES
  ('FI', 'Finnish'),
  ('SV', 'Swedish');
--;;
INSERT INTO exam VALUES (default);
--;;
INSERT INTO section (exam_id) VALUES (1);
--;;
INSERT INTO section_translation (language_code, name, section_id) VALUES
  ('FI', 'A', 1),
  ('SV', 'A', 1);
--;;
INSERT INTO section (exam_id) VALUES (1);
--;;
INSERT INTO section_translation (language_code, name, section_id) VALUES
  ('FI', 'B', 1),
  ('SV', 'B', 1);
--;;
INSERT INTO module (section_id) VALUES (1);
--;;
INSERT INTO module_translation (language_code, name, module_id) VALUES
  ('FI', 'Yleishallinto-oikeus', 1),
  ('SV', 'Allmänna administrations lag', 1);
--;;
INSERT INTO module (section_id) VALUES (1);
--;;
INSERT INTO module_translation (language_code, name, module_id) VALUES
  ('FI', 'Kunnallishallinto', 2),
  ('SV', 'Kommunal förvaltning', 2);
--;;
INSERT INTO module (section_id) VALUES (1);
--;;
INSERT INTO module_translation (language_code, name, module_id) VALUES
  ('FI', 'Viranomaisen asiakirjat ja henkilötietojen julkisuus', 3),
  ('SV', 'Behörighetshandlingar och publicitet av personuppgifter', 3);
--;;
INSERT INTO module (section_id) VALUES (1);
--;;
INSERT INTO module_translation (language_code, name, module_id) VALUES
  ('FI', 'Oikeudellinen vastuu', 4),
  ('SV', 'Rättsligt ansvar', 4);
--;;
INSERT INTO module (section_id) VALUES (2);
--;;
INSERT INTO module_translation (language_code, name, module_id) VALUES
  ('FI', 'Henkilöstöhallinto', 5),
  ('SV', 'Human Resources', 5);
--;;
INSERT INTO module (section_id) VALUES (2);
--;;
INSERT INTO module_translation (language_code, name, module_id) VALUES
  ('FI', 'Talous', 6),
  ('SV', 'Ekonomi', 6);
--;;
INSERT INTO module (section_id) VALUES (2);
--;;
INSERT INTO module_translation (language_code, name, module_id) VALUES
  ('FI', 'Opetushallinto', 7),
  ('SV', 'Utbildningsadministration', 7);
