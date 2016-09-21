CREATE OR REPLACE FUNCTION error_if_no_section_for_module() RETURNS TRIGGER AS $$
DECLARE
  parent_section_exists BOOLEAN := (
    SELECT EXISTS (SELECT re.id FROM registration_exam_content re WHERE re.content_ref_id = (SELECT s.id FROM section s WHERE s.id = (SELECT m.section_id FROM module m WHERE m.id = NEW.content_ref_id)) AND
                                                                        re.type = 'SECTION' AND
                                                                        re.participant_id = NEW.participant_id)

  );
BEGIN
  IF NEW.type = 'MODULE' THEN
    IF parent_section_exists THEN
      RETURN NEW;
    ELSE
      RAISE EXCEPTION 'No parent content section yet for this module.';
    END IF;
  ELSE
    RETURN NEW;
  END IF;
END;
$$ LANGUAGE plpgsql;
--;;
CREATE TRIGGER section_must_exist_before_module_trigger
  BEFORE INSERT
  ON registration_exam_content
  FOR EACH ROW
  EXECUTE PROCEDURE error_if_no_section_for_module();