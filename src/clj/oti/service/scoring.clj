(ns oti.service.scoring
  (:require [oti.boundary.db-access :as dba]
            [oti.util.logging.audit :as audit]
            [clojure.spec :as s]
            [taoensso.timbre :as log]
            [oti.spec :as spec]
            [ring.util.response :refer [response]]
            [oti.component.email-service :as email]
            [compojure.coercions :refer [as-int]]))


(defn send-scores-email [{:keys [db email-service]} {{:keys [exam-session-id]} :params} participant-id])

(defn scores-email-sent?  [{:keys [db email-service]} {{:keys [exam-session-id]} :params} participant-id]
  (response (if-let [exam-session-id (as-int exam-session-id)]
              (email/email-sent? email-service db {:exam-session-id exam-session-id
                                                   :participant-id participant-id
                                                   :email-type "SCORES"})
              false)))

(defn- upsert-module-scores [db evaluator upserted-section [module-id {::spec/keys [module-score-points
                                                                                    module-score-accepted] :as module}]]

  (let [old-module (dba/module-score db {:module-id module-id
                                         :section-score-id (:section-score-id upserted-section)})
        upserted-module (dba/upsert-module-score db {:evaluator evaluator
                                                     :module-points module-score-points
                                                     :module-id module-id
                                                     :module-accepted (if (nil? module-score-accepted) true module-score-accepted) ;; if module-accepted is nil, assume the module isn't accepted separately and make it true
                                                     :section-score-id (:section-score-id upserted-section)})]
    (when (not= old-module upserted-module)
      (audit/log :app :admin
                 :who evaluator
                 :op :create
                 :on :module-score
                 :before old-module
                 :after upserted-module
                 :msg "Inserted or updated a module score."))
    (when upserted-module
      [module-id upserted-module])))

(defn- upsert-section-and-module-scores [db evaluator participant-id exam-session-id [section-id {::spec/keys [section-score-accepted] :as section}]]
  (let [old-section (dba/section-score db {:section-id section-id
                                           :exam-session-id exam-session-id
                                           :participant-id participant-id})
        upserted-section (if (:changed? section)
                           (dba/upsert-section-score db {:evaluator evaluator
                                                         :section-accepted section-score-accepted
                                                         :section-id section-id
                                                         :participant-id participant-id
                                                         :exam-session-id exam-session-id})
                           (dba/section-score db {:section-id section-id
                                                  :exam-session-id exam-session-id
                                                  :participant-id participant-id}))
        upserted-modules (into {} (mapv (partial upsert-module-scores
                                                 db
                                                 evaluator
                                                 upserted-section) (:modules section)))]
    (when (not= old-section upserted-section)
      (audit/log :app :admin
                 :who evaluator
                 :op :create
                 :on :section-score
                 :before old-section
                 :after upserted-section
                 :msg "Inserted or updated a section score."))
    (when (and upserted-section
               (if (seq (:modules section))
                 (seq upserted-modules)
                 true))
      [section-id (assoc upserted-section :modules upserted-modules)])))

(defn upsert-scores [{:keys [db]} request participant-id]
  (let [scores (get-in request [:params :scores])
        exam-session-id (get-in request [:params :exam-session-id])
        evaluator (get-in request [:session :identity :username])
        upserted-scores (into {} (mapv (partial upsert-section-and-module-scores
                                                db
                                                evaluator
                                                participant-id
                                                exam-session-id) scores))]
    (if (seq upserted-scores)
      (response {:scores upserted-scores})
      {:status 400
       :body {:errors [:upsert-failed]}})))

(defn delete-scores [{:keys [db]} {{:keys [scores]} :params :as request} participant-id]
  (let [who (get-in request [:session :identity :username])
        section-score-ids (remove nil? (map ::spec/section-score-id (vals scores)))
        module-score-ids (remove nil? (mapv ::spec/module-score-id (mapcat (fn [s]
                                                                             (vals (:modules s))) (vals scores))))
        deletions (when (or (seq section-score-ids)
                            (seq module-score-ids))
                    (dba/delete-scores-by-score-ids db {:section-score-ids section-score-ids
                                                        :module-score-ids module-score-ids}))]

    (if (seq deletions)
      (do (doall (for [id module-score-ids]
                   (audit/log :app :admin
                              :who who
                              :op :delete
                              :on :module-score
                              :before {:module-score-id id}
                              :after nil
                              :msg "Deleting a module score.")))
          (doall (for [id section-score-ids]
                   (audit/log :app :admin
                              :who who
                              :op :delete
                              :on :section-score
                              :before {:section-score-id id}
                              :after nil
                              :msg "Deleting a section score.")))
          (response {:success true}))
      {:status 400
       :body {:errors [:score-deletion-failed]}})))

(defn update-registration-state
  [{:keys [db]} {{:keys [exam-session-id registration-id registration-state]} :params} participant-id]
  (if-not (zero? (dba/update-registration-state! db registration-id registration-state))
    (response {:success true})
    {:status 400
     :body {:errors [:registration-state-update-failed]}}))

(defn- as-participants [[ext-reference-id participant-data]]
  {:ext-reference-id ext-reference-id
   :id (some :id participant-data)
   :exam-session-id (some :exam_session_id participant-data)
   :registration-id (some :registration_id participant-data)
   :registration-state (some :registration_state participant-data)
   :data participant-data})

(defn- conforms-filter
  "Returns distinct elements that conforms to spec."
  [spec data]
  (->> (map #(s/conform spec %) data)
       (remove #(= :clojure.spec/invalid %))
       (distinct)))

(defn- section-scores [participant-data]
  (conforms-filter ::spec/section-score-conformer participant-data))

(defn- module-scores [participant-data]
  (conforms-filter ::spec/module-score-conformer participant-data))

(defn- section-accreditations [participant-data]
  (conforms-filter ::spec/section-accreditation-conformer participant-data))

(defn- module-accreditations [participant-data]
  (conforms-filter ::spec/module-accreditation-conformer participant-data))

(defn- section-registrations [participant-data]
  (conforms-filter ::spec/section-registration-conformer participant-data))

(defn- module-registrations [participant-data]
  (conforms-filter ::spec/module-registration-conformer participant-data))

(defn- hierarchial-by
  "Returns a map where key points to the data in a semi relational manner.
  Assumes key is unique."
  [key data]
  (->> data
       (group-by key)
       (map (fn [[x y]]
              [x (first y)]))
       (into {})))

(defn- module-scores-under-section-scores [ss ms]
  (map (fn [s]
         (let [module-scores (filter #(= (::spec/section-score-id s)
                                         (::spec/section-score-id %)) ms)]
           (assoc s :modules (hierarchial-by ::spec/module-id module-scores)))) ss))

(defn- set-scores [participant]
  (let [section-scores (section-scores (:data participant))
        module-scores (module-scores (:data participant))
        merged-scores (module-scores-under-section-scores section-scores module-scores)]
    (assoc participant :scores (hierarchial-by ::spec/section-id merged-scores))))


(defn- set-accreditations [participant]
  (let [section-accreditations (section-accreditations (:data participant))
        module-accreditations (module-accreditations (:data participant))]
    (-> (assoc-in participant [:accreditations :sections] (hierarchial-by ::spec/section-id section-accreditations))
        (assoc-in [:accreditations :modules] (hierarchial-by ::spec/module-id module-accreditations)))))

(defn- set-exam-content [{:keys [data] :as participant}]
  (let [sections (section-registrations data)
        modules (module-registrations data)]
    (assoc participant :exam-content {:sections (hierarchial-by ::spec/section-registration-section-id sections)
                                      :modules (hierarchial-by ::spec/module-registration-module-id modules)})))

(defn- participants-by-exam-session [participant-data exam-session-id]
  (->> (filter #(or (= (:exam_session_id %) exam-session-id)
                    (nil? (:exam_session_id %))) participant-data) ;; Allow nil session ids, they should be accreditations.
       (group-by :ext_reference_id)
       (map as-participants)
       (map set-scores)
       (map set-accreditations)
       (map set-exam-content)
       (remove #(nil? (:exam-session-id %))) ;; Accreditations show up as 'participants' with nil exam_sessions
       (map #(dissoc % :data))
       (hierarchial-by :id)))

(defn exam-sessions-full [{:keys [spec] :as db} lang]
  (let [exam-session-data (dba/exam-sessions-full db lang)
        participant-ext-ids (into [] (distinct (map :participant_ext_reference exam-session-data)))
        participant-data (if (seq participant-ext-ids)
                           (dba/all-participants-by-ext-references db participant-ext-ids)
                           [])]
    (when (seq exam-session-data)
      (->> exam-session-data
           (group-by :exam_session_id)
           (mapv (fn [[es-id exam-sessions]]
                   (let [{:keys [exam_session_date
                                 exam_session_start_time
                                 exam_session_end_time
                                 exam_session_max_participants
                                 exam_session_published
                                 exam_session_street_address
                                 exam_session_city
                                 exam_session_other_location_info]} (first exam-sessions)]
                     {:id es-id
                      :date exam_session_date
                      :start-time (str (.toLocalTime exam_session_start_time))
                      :end-time (str (.toLocalTime exam_session_end_time))
                      :max-participants exam_session_max_participants
                      :published exam_session_published
                      :street-address exam_session_street_address
                      :city exam_session_city
                      :other-location-info exam_session_other_location_info
                      :participants (participants-by-exam-session participant-data es-id)})))
           (hierarchial-by :id)))))
