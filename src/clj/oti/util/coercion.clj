(ns oti.util.coercion
  (:require [oti.spec]))

(defn convert-session-row [{:keys [id exam_id session_date start_time end_time street_address city
                                   other_location_info max_participants published registration_count]}]
  #:oti.spec{:id id
             :exam-id exam_id
             :session-date (str (.toLocalDate session_date))
             :start-time (str (.toLocalTime start_time))
             :end-time (str (.toLocalTime end_time))
             :street-address street_address
             :city city
             :other-location-info other_location_info
             :max-participants max_participants
             :published published
             :registration-count registration_count})

(defn parse-int [number-string]
  (try (Integer/parseInt number-string)
       (catch Exception e nil)))

(defn convert-sections [sections]
  (let [section-fn (fn [{:keys [retry? accredit? retry-modules accredit-modules]}]
                     {:retry?           retry?
                      :accredit?        accredit?
                      :retry-modules    (set (map parse-int retry-modules))
                      :accredit-modules (map parse-int accredit-modules)})
        key-fn (fn [kw]
                 (parse-int (name kw)))]
    (into {} (for [[k v] sections] [(key-fn k) (section-fn v)]))))

(defn convert-registration-data [{:keys [email session-id language-code preferred-name sections
                                         registration-street-address registration-zip registration-post-office] :as data}]
  #:oti.spec{:email                       email
             :session-id                  (int session-id)
             :language-code               (keyword language-code)
             :preferred-name              preferred-name
             :sections                    (convert-sections sections)
             :registration-street-address registration-street-address
             :registration-zip            registration-zip
             :registration-post-office    registration-post-office})