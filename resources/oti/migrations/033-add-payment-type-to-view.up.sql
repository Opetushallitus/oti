DROP VIEW all_participant_data;

CREATE VIEW all_participant_data AS
  SELECT
    p.id,
    p.ext_reference_id,
    email,
    s.id                      AS section_id,
    st.name                   AS section_name,
    m.id                      AS module_id,
    mt.name                   AS module_name,
    ss.id                     AS section_score_id,
    ss.created                AS section_score_created,
    ss.updated                AS section_score_updated,
    ss.accepted               AS section_score_accepted,
    ss.evaluator              AS section_score_evaluator,
    ms.id                     AS module_score_id,
    ms.created                AS module_score_created,
    ms.updated                AS module_score_updated,
    ms.accepted               AS module_score_accepted,
    ms.points                 AS module_score_points,
    ms.evaluator              AS module_score_evaluator,
    aes.section_id            AS section_accreditation_section_id,
    aes.accreditation_date    AS section_accreditation_date,
    aes.accreditation_type_id AS section_accreditation_type,
    aem.module_id             AS module_accreditation_module_id,
    aem.accreditation_date    AS module_accreditation_date,
    aem.accreditation_type_id AS module_accreditation_type,
    es.id                     AS exam_session_id,
    es.session_date,
    es.start_time,
    es.end_time,
    recs.id                   AS section_registration_id,
    recs.section_id           AS section_registration_section_id,
    recm.id                   AS module_registration_id,
    recm.module_id            AS module_registration_module_id,
    r.state                   AS registration_state,
    est.city,
    est.street_address,
    est.other_location_info,
    st.language_code          AS lang,
    pm.created                AS payment_created,
    pm.id                     AS payment_id,
    pm.amount,
    pm.state                  AS payment_state,
    pm.order_number,
    pm.type                   AS payment_type,
    r.id                      AS registration_id,
    r.language_code           AS registration_language,
    p.diploma_date,
    p.diploma_signer,
    p.diploma_signer_title
  FROM participant p
    LEFT JOIN section s ON s.exam_id = 1
    LEFT JOIN section_translation st ON s.id = st.section_id
    LEFT JOIN module m ON m.section_id = s.id
    LEFT JOIN module_translation mt ON mt.module_id = m.id AND mt.language_code = st.language_code
    LEFT JOIN accredited_exam_section aes ON aes.participant_id = p.id AND aes.section_id = s.id
    LEFT JOIN accredited_exam_module aem ON aem.participant_id = p.id AND aem.module_id = m.id
    LEFT JOIN registration_exam_content_section recs ON recs.section_id = s.id AND recs.participant_id = p.id
    LEFT JOIN registration_exam_content_module recm
      ON recm.module_id = m.id AND recm.participant_id = p.id AND recm.registration_id = recs.registration_id
    LEFT JOIN registration r ON recs.registration_id = r.id
    LEFT JOIN exam_session es ON es.id = r.exam_session_id
    LEFT JOIN section_score ss ON ss.participant_id = p.id AND ss.section_id = s.id AND ss.exam_session_id = es.id
    LEFT JOIN module_score ms ON ms.section_score_id = ss.id AND ms.module_id = m.id
    LEFT JOIN payment pm
      ON p.id = pm.participant_id AND (pm.registration_id = r.id OR (pm.registration_id IS NULL AND r.id IS NULL))
    LEFT JOIN exam_session_translation est ON es.id = est.exam_session_id AND est.language_code = st.language_code
  ORDER BY section_id, module_id, es.session_date, es.id, lang;
