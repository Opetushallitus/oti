-- name: exam-sessions-in-future
SELECT es.id,
       es.session_date,
       es.start_time,
       es.end_time,
       es.max_participants,
       es.exam_id,
       es.published,
       est.city,
       est.street_address,
       est.language_code,
       est.other_location_info,
       count(r.id) AS registration_count
FROM exam_session es
  JOIN exam_session_translation est ON es.id = est.exam_session_id
  LEFT JOIN registration r ON es.id = r.exam_session_id
WHERE session_date > now()
GROUP BY es.id, es.session_date, es.start_time, es.end_time, es.max_participants, es.exam_id, es.published, est.city,
  est.street_address, est.language_code, est.other_location_info
ORDER BY es.session_date, es.start_time, id;

-- name: published-exam-sessions-in-future-with-space-left
SELECT es.id,
  es.session_date,
  es.start_time,
  es.end_time,
  es.max_participants,
  es.exam_id,
  es.published,
  est.city,
  est.street_address,
  est.language_code,
  est.other_location_info,
  (es.max_participants - COUNT(r.id)) AS available
FROM exam_session es JOIN exam_session_translation est ON es.id = est.exam_session_id
  LEFT JOIN registration r ON es.id = r.exam_session_id
WHERE session_date > now() AND es.published = TRUE
GROUP BY es.id, es.session_date, es.start_time, es.end_time, es.max_participants, es.exam_id, es.published, est.city,
  est.street_address, est.language_code, est.other_location_info
HAVING (es.max_participants - COUNT(r.id)) > 0
ORDER BY es.session_date, es.start_time;

-- name: insert-exam-session<!
INSERT INTO exam_session (
  session_date,
  start_time,
  end_time,
  max_participants,
  exam_id,
  published
) VALUES (
  :oti.spec/session-date,
  :oti.spec/start-time,
  :oti.spec/end-time,
  :oti.spec/max-participants,
  :oti.spec/exam-id,
  :oti.spec/published
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

-- name: select-modules-available-for-user
SELECT
  s.id AS section_id, m.id AS module_id, st.name AS section_name, st.language_code, mt.name AS module_name,
  ss.id AS section_score_id, ms.id AS module_score_id, ss.accepted AS section_accepted, ms.accepted AS module_accepted
FROM exam e
  JOIN section s ON e.id = s.exam_id
  JOIN module m ON m.section_id = s.id
  JOIN section_translation st ON s.id = st.section_id
  JOIN module_translation mt ON (m.id = mt.module_id AND mt.language_code = st.language_code)
  JOIN exam_session es ON (e.id = es.exam_id AND es.session_date > current_date AND es.published = TRUE)
  LEFT JOIN participant p ON p.ext_reference_id = :external-user-id
  LEFT JOIN section_score ss ON (ss.section_id = s.id AND ss.participant_id = p.id)
  LEFT JOIN module_score ms ON (ms.module_id = m.id AND ms.section_score_id = ss.id)
  LEFT JOIN registration r ON (r.exam_session_id = es.id AND r.participant_id = p.id)
  LEFT JOIN registration_exam_content_section recs ON (r.id = recs.registration_id AND recs.section_id = s.id)
  LEFT JOIN registration_exam_content_module recm ON (r.id = recm.registration_id AND recm.module_id = m.id)
  LEFT JOIN accredited_exam_module aem ON (m.id = aem.module_id AND aem.participant_id = p.id)
  LEFT JOIN accredited_exam_section aes ON (s.id = aes.section_id AND aes.participant_id = p.id)
WHERE (ss.accepted IS NULL OR ss.accepted = FALSE OR ms.accepted IS NULL OR ms.accepted = FALSE)
      AND e.id = 1
GROUP BY s.id, m.id, section_name, st.language_code, module_name, ss.id,  module_score_id, section_accepted, module_accepted
HAVING count(recm.id) = 0 AND count(recs.id) = 0 AND count(aem.id) = 0 AND count(aes.id) = 0
ORDER BY section_id, module_id, language_code;

-- name: modules
SELECT m.id, json_object_agg(mt.language_code, mt.name) AS NAMES
FROM (SELECT language_code, name FROM module_translation WHERE module_id = 1) AS mt, module m
GROUP BY m.id;

-- name: select-valid-payment-count-for-user
SELECT
  COUNT(*)
FROM payment p
JOIN registration r ON p.registration_id = r.id
JOIN participant pp ON r.participant_id = pp.id
JOIN exam_session e ON r.exam_session_id = e.id
LEFT JOIN section_score ss ON (ss.exam_session_id = e.id AND ss.participant_id = p.id)
LEFT JOIN module_score ms ON ms.section_score_id = ss.id
WHERE pp.ext_reference_id = :external-user-id AND p.state = 'OK' AND p.type = 'FULL' AND
      (((ss.accepted IS NULL OR ss.accepted = FALSE ) AND (ms.accepted IS NULL OR ms.accepted = FALSE))
        OR (ss.created >= (SELECT current_date - interval '2 years'))) ;

-- name: insert-registration<!
WITH pp AS (
    SELECT id FROM participant WHERE ext_reference_id = :external-user-id
), session AS (
    SELECT es.id FROM exam_session es LEFT JOIN registration r ON es.id = r.exam_session_id
      WHERE es.id = :session-id
    GROUP BY (es.id) HAVING (es.max_participants - COUNT(r.id)) > 0
)
INSERT INTO registration (state, exam_session_id, participant_id, language_code)
  SELECT 'OK', session.id, pp.id, :language-code FROM pp, session
RETURNING registration.id;


-- name: insert-section-registration!
INSERT INTO registration_exam_content_section (section_id, participant_id, registration_id)
SELECT :section-id, id, :registration-id FROM participant WHERE ext_reference_id = :external-user-id;

-- name: select-modules-for-section
SELECT id FROM module WHERE section_id = :section-id;

-- name: insert-module-registration!
INSERT INTO registration_exam_content_module (module_id, participant_id, registration_id)
SELECT :module-id, id, :registration-id FROM participant WHERE ext_reference_id = :external-user-id;

-- name: insert-section-accreditation!
INSERT INTO accredited_exam_section (section_id, participant_id)
SELECT :section-id, id FROM participant WHERE ext_reference_id = :external-user-id;

-- name: insert-module-accreditation!
INSERT INTO accredited_exam_module (module_id, participant_id)
SELECT :module-id, id FROM participant WHERE ext_reference_id = :external-user-id;