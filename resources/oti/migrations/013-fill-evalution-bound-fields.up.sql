UPDATE section SET is_executed_as_whole = true  WHERE id = 1;
UPDATE section SET is_executed_as_whole = false WHERE id = 2;
UPDATE module SET has_points = true, is_accepted_separately = false WHERE section_id = 1;
UPDATE module SET has_points = true, is_accepted_separately = true WHERE id = 5;
UPDATE module SET has_points = false, is_accepted_separately = true WHERE id != 5 AND section_id = 2;
