DROP INDEX one_participation_per_session_idx;

CREATE UNIQUE INDEX one_participation_per_session_constraint
  ON registration (exam_session_id, participant_id);
