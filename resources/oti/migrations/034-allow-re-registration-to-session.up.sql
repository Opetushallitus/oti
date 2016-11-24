ALTER TABLE registration
    DROP CONSTRAINT one_participation_per_session_constraint;

CREATE UNIQUE INDEX one_participation_per_session_idx
  ON registration (exam_session_id, participant_id)
  WHERE state IN ('OK'::registration_state, 'INCOMPLETE'::registration_state);
