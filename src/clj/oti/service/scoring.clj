(ns oti.service.scoring
  (:require [oti.boundary.db-access :as dba]
            [oti.util.logging.audit :as audit]
            [ring.util.response :refer [response]]))

(defn- upsert-module-scores [db evaluator upserted-section [module-id {:keys [module-points
                                                                              module-accepted] :as module}]]
  (let [old-module (dba/module-score db {:module-id module-id
                                         :section-score-id (:section-score-id upserted-section)})
        upserted-module (dba/upsert-module-score db {:evaluator evaluator
                                                     :module-points module-points
                                                     :module-id module-id
                                                     :module-accepted (if (nil? module-accepted) true module-accepted) ;; if module-accepted is nil, assume the module isn't accepted separately and make it true
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

(defn- upsert-section-and-module-scores [db evaluator participant-id exam-session-id [section-id {:keys [section-accepted] :as section}]]
  (let [old-section (dba/section-score db {:section-id section-id
                                           :exam-session-id exam-session-id
                                           :participant-id participant-id})
        upserted-section (dba/upsert-section-score db {:evaluator evaluator
                                                       :section-accepted section-accepted
                                                       :section-id section-id
                                                       :participant-id participant-id
                                                       :exam-session-id exam-session-id})
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
    (when (and upserted-section (seq upserted-modules))
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
