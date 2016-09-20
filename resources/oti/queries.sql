-- name: exam-sessions-in-future
SELECT * FROM exam_session WHERE session_date > now();

-- name: exams
SELECT * FROM exam;
