-- name: exam-sessions-in-future
SELECT * FROM exam_sessions WHERE session_date > now();
