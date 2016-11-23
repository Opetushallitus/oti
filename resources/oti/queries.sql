-- name: select-exam-sessions
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
  LEFT JOIN registration r ON es.id = r.exam_session_id AND r.state IN ('INCOMPLETE'::registration_state, 'OK'::registration_state)
WHERE session_date >= :start-date AND session_date <= :end-date
GROUP BY es.id, es.session_date, es.start_time, es.end_time, es.max_participants, es.exam_id, es.published, est.city,
  est.street_address, est.language_code, est.other_location_info
ORDER BY es.session_date, es.start_time, id;

-- name: exam-session-by-id
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
  LEFT JOIN registration r ON es.id = r.exam_session_id AND r.state IN ('INCOMPLETE'::registration_state, 'OK'::registration_state)
WHERE es.id = :id
GROUP BY es.id, es.session_date, es.start_time, es.end_time, es.max_participants, es.exam_id, es.published, est.city,
  est.street_address, est.language_code, est.other_location_info;

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
FROM exam_session es
  JOIN exam_session_translation est ON es.id = est.exam_session_id
  LEFT JOIN registration r ON es.id = r.exam_session_id AND r.state IN ('INCOMPLETE'::registration_state, 'OK'::registration_state)
WHERE session_date > now() AND es.published = TRUE
GROUP BY es.id, es.session_date, es.start_time, es.end_time, es.max_participants, es.exam_id, es.published, est.city,
  est.street_address, est.language_code, est.other_location_info
HAVING (es.max_participants - COUNT(r.id)) > 0
ORDER BY es.session_date, es.start_time;

-- name: select-exam-session-access-token
SELECT access_token FROM exam_session WHERE id = :id;

-- name: select-exam-session-matching-token
SELECT id FROM exam_session
WHERE id = :id AND access_token IS NOT NULL AND access_token = :token
      AND session_date >= (SELECT current_date - interval '7 days');

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
  :street-address,
  :city,
  :other-location-info,
  :language-code,
  :exam-session-id
);

-- name: update-exam-session!
UPDATE exam_session SET
  session_date = :oti.spec/session-date,
  start_time = :oti.spec/start-time,
  end_time = :oti.spec/end-time,
  max_participants = :oti.spec/max-participants,
  exam_id = :oti.spec/exam-id,
  published = :oti.spec/published
WHERE id = :oti.spec/id;

-- name: update-exam-session-translation!
UPDATE exam_session_translation SET
  street_address = :street-address,
  city = :city,
  other_location_info = :other-location-info
WHERE language_code = :language-code AND exam_session_id = :exam-session-id;

-- name: update-exam-session-with-token!
UPDATE exam_session SET access_token = :token WHERE id = :id;

-- name: delete-exam-session!
DELETE FROM exam_session WHERE id = :exam-session-id;

-- name: delete-exam-session-translations!
DELETE FROM exam_session_translation WHERE exam_session_id = :exam-session-id;

-- name: exam-sessions-full
SELECT es.id AS exam_session_id,
       p.ext_reference_id AS participant_ext_reference,
       es.session_date AS exam_session_date,
       es.start_time AS exam_session_start_time,
       es.end_time AS exam_session_end_time,
       es.max_participants AS exam_session_max_participants,
       es.published AS exam_session_published,
       est.street_address AS exam_session_street_address,
       est.city AS exam_session_city,
       est.other_location_info AS exam_session_other_location_info
FROM exam_session es
JOIN exam_session_translation est ON es.id = est.exam_session_id
JOIN registration r ON es.id = r.exam_session_id
JOIN participant p ON r.participant_id = p.id
WHERE est.language_code = :lang
AND es.session_date <= current_date
ORDER BY es.id;

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
  LEFT JOIN registration r ON (r.exam_session_id = es.id AND r.participant_id = p.id AND r.state IN ('INCOMPLETE'::registration_state, 'OK'::registration_state))
  LEFT JOIN registration_exam_content_section recs ON (r.id = recs.registration_id AND recs.section_id = s.id)
  LEFT JOIN registration_exam_content_module recm ON (r.id = recm.registration_id AND recm.module_id = m.id)
  LEFT JOIN accredited_exam_module aem ON (m.id = aem.module_id AND aem.participant_id = p.id)
  LEFT JOIN accredited_exam_section aes ON (s.id = aes.section_id AND aes.participant_id = p.id)
WHERE (ss.accepted IS NULL OR ss.accepted = FALSE OR ms.accepted IS NULL OR ms.accepted = FALSE)
      AND e.id = 1
GROUP BY s.id, m.id, section_name, st.language_code, module_name, ss.id,  module_score_id, section_accepted, module_accepted
HAVING count(recm.id) = 0 AND count(recs.id) = 0 AND count(aem.id) = 0 AND count(aes.id) = 0
ORDER BY section_id, module_id, language_code;

-- name: select-registrations-by-participant-id
SELECT r.state, r.retry, p.type AS payment_type
  FROM registration r
LEFT JOIN payment p ON r.id = p.registration_id
WHERE r.participant_id = :id AND
      p.state = 'OK';

-- name: insert-participant!
INSERT INTO participant (ext_reference_id, email)
  SELECT :external-user-id, :email
  WHERE NOT EXISTS (SELECT id FROM participant WHERE ext_reference_id = :external-user-id);

-- name: select-participant
SELECT id,
       ext_reference_id,
       exam_session_id,
       email,
       section_id,
       section_score_created,
       section_score_updated,
       section_score_id,
       section_score_accepted,
       section_score_evaluator,
       module_id,
       module_score_created,
       module_score_updated,
       module_score_id,
       module_score_points,
       module_score_accepted,
       module_score_evaluator,
       section_accreditation_section_id,
       section_accreditation_date,
       module_accreditation_module_id,
       module_accreditation_date,
       diploma_date,
       diploma_signer,
       diploma_signer_title
FROM all_participant_data
WHERE ext_reference_id = :external-user-id AND lang = 'fi'
ORDER BY id;

-- name: select-participant-scores-by-ext-reference
SELECT es.id AS exam_session_id,
       s.id AS section_id,
       ss.id AS section_score_id,
       ss.accepted AS section_score_accepted,
       m.id AS module_id,
       ms.id AS module_score_id,
       ms.accepted AS module_score_accepted,
       ms.points AS module_score_points,
       p.ext_reference_id AS participant_ext_reference_id,
       ss.participant_id AS participant_id,
       ss.evaluator AS section_score_evaluator,
       ms.evaluator AS module_score_evaluator
FROM exam_session es
  JOIN section_score ss ON ss.exam_session_id = es.id
  JOIN participant p ON ss.participant_id = p.id
  JOIN section s ON s.id = ss.section_id
  JOIN section_translation st ON st.section_id = s.id
  LEFT JOIN module_score ms ON ms.section_score_id = ss.id
  LEFT JOIN module m ON m.id = ms.module_id
  LEFT JOIN module_translation mt ON mt.module_id = m.id
WHERE es.id = :exam-session-id AND st.language_code = 'fi' AND p.ext_reference_id IN (:ext-reference-ids)
ORDER BY ext_reference_id;

-- name: select-all-participants
SELECT id,
       ext_reference_id,
       exam_session_id,
       email,
       section_id,
       section_score_created,
       section_score_updated,
       section_score_id,
       section_score_accepted,
       section_score_evaluator,
       module_id,
       module_score_created,
       module_score_updated,
       module_score_id,
       module_score_points,
       module_score_accepted,
       module_score_evaluator,
       section_accreditation_section_id,
       section_accreditation_date,
       module_accreditation_module_id,
       module_accreditation_date,
       section_registration_section_id,
       section_registration_id,
       module_registration_module_id,
       module_registration_id,
       registration_id,
       registration_state,
       diploma_date,
       diploma_signer,
       diploma_signer_title
FROM all_participant_data
WHERE lang = 'fi'
ORDER BY id;

-- name: select-all-participants-by-ext-references
SELECT id,
       ext_reference_id,
       exam_session_id,
       email,
       section_id,
       section_score_created,
       section_score_updated,
       section_score_id,
       section_score_accepted,
       section_score_evaluator,
       module_id,
       module_score_created,
       module_score_updated,
       module_score_id,
       module_score_points,
       module_score_accepted,
       module_score_evaluator,
       section_accreditation_section_id,
       section_accreditation_date,
       module_accreditation_module_id,
       module_accreditation_date,
       section_registration_section_id,
       section_registration_id,
       module_registration_module_id,
       module_registration_id,
       registration_id,
       registration_state,
       diploma_date,
       diploma_signer,
       diploma_signer_title
FROM all_participant_data
WHERE lang = 'fi' AND ext_reference_id IN (:ext-reference-ids)
ORDER BY id;

-- name: select-participant-by-id
SELECT * FROM all_participant_data
WHERE id = :id AND lang = 'fi'
ORDER BY section_id, session_date, exam_session_id, module_id;

-- name: select-participant-by-payment-order-number
SELECT * FROM all_participant_data
WHERE order_number = :order-number AND lang = :lang;

-- name: update-participant-diploma!
UPDATE participant SET diploma_date = current_date, diploma_signer = :signer, diploma_signer_title = :title
WHERE id = :id AND diploma_date IS NULL;

-- name: select-diploma-count
SELECT COUNT(id) AS count FROM participant
WHERE diploma_date >= :start-date AND diploma_date <= :end-date;

-- SCORING

-- name: upsert-participant-section-score<!
INSERT INTO section_score (evaluator, accepted, section_id, participant_id, exam_session_id) VALUES (
  :evaluator,
  :section-accepted,
  :section-id,
  :participant-id,
  :exam-session-id
) ON CONFLICT (section_id, exam_session_id, participant_id)
DO UPDATE SET accepted = :section-accepted, evaluator = :evaluator, updated = current_timestamp
RETURNING id AS section_score_id, accepted AS section_score_accepted, section_id, participant_id, exam_session_id;

-- name: upsert-participant-module-score<!
INSERT INTO module_score (evaluator, accepted, points, module_id, section_score_id) VALUES (
  :evaluator,
  :module-accepted,
  :module-points,
  :module-id,
  :section-score-id
) ON CONFLICT (module_id, section_score_id)
DO UPDATE SET accepted = :module-accepted, points = :module-points, evaluator = :evaluator, updated = current_timestamp
RETURNING id AS module_score_id, accepted AS module_score_accepted, points AS module_score_points, module_id, section_score_id;

-- name: select-section-score
SELECT id AS section_score_id, accepted AS section_score_accepted, section_id, participant_id, exam_session_id
FROM section_score
WHERE section_id = :section-id AND exam_session_id = :exam-session-id AND participant_id = :participant-id;

-- name: select-module-score
SELECT id AS module_score_id, accepted AS module_score_accepted, points AS module_score_points, module_id, section_score_id
FROM module_score
WHERE module_id = :module-id AND section_score_id = :section-score-id;

-- REGISTRATION

-- name: insert-registration<!
WITH pp AS (
    SELECT id FROM participant WHERE ext_reference_id = :external-user-id
), session AS (
    SELECT es.id FROM exam_session es LEFT JOIN registration r ON es.id = r.exam_session_id
      WHERE es.id = :session-id
    GROUP BY (es.id) HAVING (es.max_participants - COUNT(r.id)) > 0
)
INSERT INTO registration (state, retry, exam_session_id, participant_id, language_code)
  SELECT :state::registration_state, :retry, session.id, pp.id, :language-code FROM pp, session
RETURNING registration.id;

-- name: insert-section-registration!
INSERT INTO registration_exam_content_section (section_id, participant_id, registration_id)
SELECT :section-id, id, :registration-id FROM participant WHERE ext_reference_id = :external-user-id;

-- name: select-modules-for-section
SELECT id FROM module WHERE section_id = :section-id;

-- name: insert-module-registration!
INSERT INTO registration_exam_content_module (module_id, participant_id, registration_id)
SELECT :module-id, id, :registration-id FROM participant WHERE ext_reference_id = :external-user-id;

-- name: update-registration-state!
UPDATE registration SET state = :state::registration_state WHERE id = :id;

-- name: select-registrations-for-exam-session
SELECT
  r.id,
  r.created,
  r.language_code,
  p.id AS participant_id,
  p.ext_reference_id,
  recs.section_id,
  recm.module_id,
  pp.state AS payment_state
FROM registration r
  JOIN participant p ON r.participant_id = p.id
  LEFT JOIN payment pp ON r.id = pp.registration_id
  LEFT JOIN registration_exam_content_section recs ON r.id = recs.registration_id
  LEFT JOIN module m ON recs.section_id = m.section_id
  LEFT JOIN registration_exam_content_module recm ON r.id = recm.registration_id AND m.id = recm.module_id
WHERE r.exam_session_id = :exam-session-id AND r.state IN ('INCOMPLETE'::registration_state, 'OK'::registration_state)
ORDER BY r.id, recs.section_id, recm.module_id;

-- name: select-registration-state-by-id
SELECT state FROM registration WHERE id = :id;

-- name: select-existing-registration-id
SELECT r.id FROM registration r
JOIN participant p ON r.participant_id = p.id
WHERE p.ext_reference_id = :external-user-id AND r.exam_session_id = :exam-session-id;

-- name: select-section-and-module-names
SELECT st.section_id, st.name AS section_name, mt.module_id, mt.name AS module_name
FROM section s
  LEFT JOIN section_translation st ON st.section_id = s.id
  LEFT JOIN module m ON st.section_id = m.section_id
  LEFT JOIN module_translation mt ON mt.module_id = m.id AND mt.language_code = 'fi'
WHERE st.language_code = 'fi' AND s.exam_id = 1
ORDER BY section_id, module_id;

-- PAYMENT

-- name: insert-payment!
INSERT INTO payment (created, state, type, participant_id, registration_id, amount, reference, order_number, paym_call_id)
SELECT :created, 'UNPAID'::payment_state, :type::payment_type, id, :registration-id, :amount,
  :reference, :order-number, :payment-id
  FROM participant WHERE ext_reference_id = :external-user-id;

-- name: update-payment!
UPDATE payment
SET state = :state::payment_state, ext_reference_id = :pay-id, ext_archiving_id = :archive-id, payment_method = :payment-method
WHERE order_number = :order-number;

-- name: update-payment-state!
UPDATE payment
SET state = :state::payment_state
WHERE order_number = :order-number;

-- name: update-payment-order-and-timestamp!
UPDATE payment SET
  created = :created, order_number = :order-number, paym_call_id = :order-number
WHERE id = :id;

-- name: update-registration-state-by-payment-order!
UPDATE registration
SET state = :state::registration_state
FROM payment p
WHERE registration.id = p.registration_id  AND p.order_number = :order-number;

-- name: select-next-order-number-suffix
SELECT nextval('payment_order_number_seq');

-- name: select-unpaid-payments
SELECT id, paym_call_id, order_number, created, state FROM payment WHERE state = 'UNPAID'::payment_state;

-- name: select-unpaid-payments-by-participant
SELECT p.id, p.paym_call_id, p.order_number, p.created, r.id as registration_id, p.state
FROM payment p
  JOIN registration r ON p.registration_id = r.id
  JOIN participant pp ON r.participant_id = pp.id
WHERE p.state = 'UNPAID'::payment_state AND pp.ext_reference_id = :external-user-id;

-- name: select-unpaid-payment-by-registration-id
SELECT p.id, p.paym_call_id, p.order_number, p.created, p.amount, p.reference, p.state
FROM payment p
WHERE p.state = 'UNPAID'::payment_state AND p.registration_id = :registration-id;

-- name: select-credit-card-payments
SELECT id, paym_call_id, order_number, created, state FROM payment
WHERE state = 'OK'::payment_state
      AND created >= (SELECT current_date - interval '60 days')
      AND payment_method LIKE 'L%';

-- EMAIL

-- name: insert-email-by-participant-id!
INSERT INTO email (participant_id, recipient, subject, body)
  SELECT p.id, p.email, :subject, :body FROM participant p
  WHERE p.id = :participant-id;

-- name: select-unsent-email-for-update
SELECT e.id, e.subject, e.body, e.recipient
FROM email e
JOIN participant p ON e.participant_id = p.id
WHERE e.sent IS NULL
ORDER BY e.created
FOR UPDATE OF e SKIP LOCKED;

-- name: mark-email-sent!
UPDATE email SET sent = current_timestamp WHERE id = :id;

-- name: select-exam-count
SELECT count(id) AS exam_count FROM exam;

-- name: select-accreditation-types
SELECT id, description FROM accreditation_type;

-- ACCREDITATION

-- name: insert-section-accreditation!
INSERT INTO accredited_exam_section (section_id, participant_id)
  SELECT :section-id, id FROM participant WHERE ext_reference_id = :external-user-id;

-- name: insert-module-accreditation!
INSERT INTO accredited_exam_module (module_id, participant_id)
  SELECT :module-id, id FROM participant WHERE ext_reference_id = :external-user-id;

-- name: update-section-accreditation!
UPDATE accredited_exam_section
SET accreditor = :accreditor, accreditation_date = :date, accreditation_type_id = :type
WHERE participant_id = :participant-id AND section_id = :id;

-- name: update-module-accreditation!
UPDATE accredited_exam_module
SET accreditor = :accreditor, accreditation_date = :date, accreditation_type_id = :type
WHERE participant_id = :participant-id AND module_id = :id;

--name: exam-by-lang
SELECT s.id AS section_id,
       s.executed_as_whole AS section_executed_as_whole,
       st.name AS section_name,
       m.id AS module_id,
       m.points AS module_points,
       m.accepted_separately AS module_accepted_separately,
       mt.name AS module_name
FROM exam e
JOIN section s ON e.id = s.exam_id
JOIN section_translation st ON s.id = st.section_id
JOIN module m ON s.id = m.section_id
JOIN module_translation mt ON m.id = mt.module_id
WHERE st.language_code = :lang AND mt.language_code = :lang
