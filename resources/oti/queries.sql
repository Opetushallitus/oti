-- name: exam-sessions-in-future
SELECT * FROM exam_session WHERE session_date > now();

-- name: exams
SELECT * FROM exam;

-- name: insert-exam-session!

INSERT INTO exam_session (session_date, start_time, end_time, street_address, city, other_location_info, max_participants, exam_id)
    VALUES (:oti.spec/session-date, :oti.spec/start-time, :oti.spec/end-time, :oti.spec/street-address, :oti.spec/city,
            :oti.spec/other-location-info, :oti.spec/max-participants, :oti.spec/exam-id);
