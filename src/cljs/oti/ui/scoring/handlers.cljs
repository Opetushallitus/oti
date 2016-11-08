(ns oti.ui.scoring.handlers
  (:require [re-frame.core :as rf :refer [trim-v]]
            [oti.routing :as routing]
            [ajax.core :as ajax]))

(rf/reg-event-fx
 :load-exam-sessions-full
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             (routing/v-a-route "/exam-sessions/full")
                 :response-format (ajax/transit-response-format)
                 :on-success      [:store-response-to-db [:scoring :exam-sessions]]
                 :on-failure      [:bad-response]}
    :loader     true}))

(rf/reg-event-db
 :select-exam-session
 [trim-v]
 (fn [db [id]]
   (rf/dispatch [:select-participant (->> (get-in db [:scoring :exam-sessions])
                                          (filter #(= (:id %) id))
                                          first
                                          :participants
                                          first
                                          :id)])
   (assoc-in db [:scoring :selected-exam-session] id)))

(rf/reg-event-db
 :select-participant
 [trim-v]
 (fn [db [id]]
   (rf/dispatch [:init-scoring-form-data (get-in db [:scoring :selected-exam-session]) id])
   (assoc-in db [:scoring :selected-participant] id)))

(defn- prepare-modules [rows]
  (->> (group-by :module-id rows)
       (reduce (fn [modules [module-id module-rows]]
                 (if module-id
                   (assoc modules module-id {:module-id module-id
                                             :module-accepted (:module-score-accepted (first module-rows))
                                             :module-points (:module-score-points (first module-rows))})
                   modules)) {})))

(defn- prepare-existing-scores [existing-scores]
  (when existing-scores
    (->> (group-by :section-id existing-scores)
         (reduce (fn [sections [section-id section-rows]]
                   (assoc sections section-id {:section-id section-id
                                               :section-accepted (:section-score-accepted (first section-rows))
                                               :modules (prepare-modules section-rows)})) {}))))

(rf/reg-event-db
 :init-scoring-form-data
 [trim-v]
 (fn [db [selected-exam-session selected-participant]]
   (let [form-data (get-in db [:scoring :form-data selected-exam-session selected-participant])
         existing-data (->> (get-in db [:scoring :exam-sessions])
                            (filter #(= (:id %) selected-exam-session))
                            first
                            :participants
                            (filter #(= (:id %) selected-participant))
                            first)]
     (if (seq form-data) ;; Already inited for this participant
       db
       (if (and selected-exam-session selected-participant)
         (let [scores (prepare-existing-scores (:scores existing-data))
               accreditations (into [] (:accreditations existing-data))
               data {:scores scores
                     :accreditations accreditations}]
           (assoc-in db [:scoring :form-data selected-exam-session selected-participant]
                     (if (seq data)
                       data
                       {:scores []
                        :accreditations []})))
         db)))))

(defn- str->boolean [str]
  (if (#{"true"} str) true false))

(rf/reg-event-db
 :set-radio-value
 [trim-v]
 (fn [db [type id value]]
   (let [exam-session-id (get-in db [:scoring :selected-exam-session])
         participant-id (get-in db [:scoring :selected-participant])]
     (condp = type
       :section (update-in db [:scoring :form-data exam-session-id participant-id :scores]
                           (fn [scores]
                             (assoc-in scores [id :section-accepted] (str->boolean value))))))))
