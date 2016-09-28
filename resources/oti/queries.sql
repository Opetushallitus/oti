-- name: exam-sessions-in-future
SELECT es.id,
       es.session_date,
       es.start_time,
       es.end_time,
       es.max_participants,
       es.exam_id,
       est.city,
       est.street_address,
       est.language_code,
       est.other_location_info FROM exam_session es JOIN exam_session_translation est ON es.id = est.exam_session_id
WHERE session_date > now();

-- name: exams
SELECT * FROM exam;

-- name: insert-exam-session<!
INSERT INTO exam_session (
  session_date,
  start_time,
  end_time,
  max_participants,
  exam_id
) VALUES (
  :oti.spec/session-date,
  :oti.spec/start-time,
  :oti.spec/end-time,
  :oti.spec/max-participants,
  :oti.spec/exam-id
);

-- name: insert-exam-session-translation!
INSERT INTO exam_session_translation (
  street_address,
  city,
  other_location_info,
  language_code,
  exam_session_id
) VALUES (
  :oti.spec/street-address,
  :oti.spec/city,
  :oti.spec/other-location-info,
  :oti.spec/language-code,
  :oti.spec/exam-session-id
);
