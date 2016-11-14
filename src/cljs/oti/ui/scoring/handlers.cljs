(ns oti.ui.scoring.handlers
  (:require [re-frame.core :as rf :refer [trim-v debug]]
            [cognitect.transit :as transit]
            [clojure.string :as str]
            [oti.routing :as routing]
            [ajax.core :as ajax]))

;; HELPER FUNCTIONS

(defn bigdec->str [bigdec]
  (if (transit/bigdec? bigdec)
    (str (-> bigdec .-rep))
    bigdec))

(defn- str->boolean [str]
  (if (not (boolean? str))
    (if (#{"true"} str) true false)
    str))

(defn- filter-chars [s]
  (let [s (str/replace s "," ".")]
    (if (re-matches #"[0-9]+\.?[0-9]*" s)
      s
      (apply str (butlast s)))))

(defn- prepare-modules [rows]
  (->> (group-by :module-id rows)
       (reduce (fn [modules [module-id module-rows]]
                 (if module-id
                   (assoc modules module-id {:module-id module-id
                                             :module-accepted (:module-score-accepted (first module-rows))
                                             :module-points (bigdec->str (:module-score-points (first module-rows)))})
                   modules)) {})))

(defn- prepare-existing-scores [existing-scores]
  (when existing-scores
    (->> (group-by :section-id existing-scores)
         (reduce (fn [sections [section-id section-rows]]
                   (assoc sections section-id {:section-id section-id
                                               :section-accepted (:section-score-accepted (first section-rows))
                                               :section-score-id (:section-score-id (first section-rows))
                                               :modules (prepare-modules section-rows)})) {}))))

(defn- prepare-updated-modules [modules]
  (into {} (mapv (fn [[module-id {:keys [module-id module-score-accepted module-score-points]}]]
                   [module-id {:module-id module-id
                               :module-accepted module-score-accepted
                               :module-points (bigdec->str module-score-points)}]) modules)))

(defn- prepare-updated-scores [updated-scores]
  (when updated-scores
    (into {} (mapv (fn [[section-id {:keys [section-id section-score-accepted section-score-id modules]}]]
                     [section-id
                      (update {:section-id section-id
                               :section-accepted section-score-accepted
                               :section-score-id section-score-id
                               :modules modules} :modules prepare-updated-modules)]) updated-scores))))

(defn- prepare-participant [participants participant]
  (let [scores (prepare-existing-scores (:scores participant))
        accreditations (into [] (:accreditations participant))
        data {:scores scores
              :accreditations accreditations}]
    (if (seq data)
      (assoc participants (:id participant) data)
      (assoc participants (:id participant) {:scores []
                                             :accreditations []}))))

(defn- prepare-exam-session [exam-sessions exam-session]
  (assoc exam-sessions
         (:id exam-session)
         (reduce prepare-participant {} (:participants exam-session))))

(defn- prepare-form-data [db existing-data]
  (assoc-in db [:scoring :form-data] (reduce prepare-exam-session {} existing-data)))

(defn- module-points-to-bigdecs [scores]
  (into {} (map (fn [[section-id section-score]]
                  (let [modules (into {} (map (fn [[module-id module-score]]
                                                [module-id (update module-score :module-points (fn [points]
                                                                                                 (when points
                                                                                                   (transit/bigdec points))))]) (:modules section-score)))]
                    [section-id (assoc section-score :modules modules)])) scores)))


;; HANDLERS

(rf/reg-event-fx
 :load-exam-sessions-full
 (fn [_ _]
   {:http-xhrio {:method          :get
                 :uri             (routing/v-a-route "/exam-sessions/full")
                 :response-format (ajax/transit-response-format)
                 :on-success      [:handle-exam-sessions-full-success]
                 :on-failure      [:bad-response]}
    :loader     true}))


(rf/reg-event-fx
 :handle-exam-sessions-full-success
 [trim-v]
 (fn [{:keys [db]} [result]]
   {:db (assoc-in db [:scoring :exam-sessions] result)
    :dispatch [:init-scoring-form-data result]}))


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
   (assoc-in db [:scoring :selected-participant] id)))


(rf/reg-event-db
 :init-scoring-form-data
 [trim-v]
 (fn [db [existing-data]]
   (let [form-data (get-in db [:scoring :form-data])]
     (if (seq form-data) ;; Already inited
       db
       (if (seq existing-data)
         (let [first-exam-session (-> (get-in db [:scoring :exam-sessions]) first :id)
               first-participant (-> (get-in db [:scoring :exam-sessions]) first :participants first :id)
               prepared-db (-> (prepare-form-data db existing-data)
                               (assoc-in [:scoring :selected-exam-session] first-exam-session)
                               (assoc-in [:scoring :selected-participant] first-participant))]
           (assoc-in prepared-db [:scoring :initial-form-data] (get-in prepared-db [:scoring :form-data])))
         db)))))

(rf/reg-event-db
 :set-initial-form-data
 [trim-v]
 (fn [db [data]]
   (assoc-in db [:scoring :initial-form-data] data)))


(rf/reg-event-db
 :set-radio-value
 [trim-v]
 (fn [db [type {:keys [id section-id]} value]]
   (let [exam-session-id (get-in db [:scoring :selected-exam-session])
         participant-id (get-in db [:scoring :selected-participant])]
     (condp = type
       :section (update-in db [:scoring :form-data exam-session-id participant-id :scores]
                           (fn [scores]
                             (assoc-in scores [id :section-accepted] (str->boolean value))))
       :module (update-in db [:scoring :form-data exam-session-id participant-id :scores]
                          (fn [scores]
                            (assoc-in scores [section-id :modules id :module-accepted] (str->boolean value))))))))


(rf/reg-event-db
 :set-input-value
 [trim-v]
 (fn [db [type {:keys [id section-id]} value]]
   (let [exam-session-id (get-in db [:scoring :selected-exam-session])
         participant-id (get-in db [:scoring :selected-participant])]
     (condp = type
       ;; Sections aren't scored as whole as of now
       :section #_(update-in db [:scoring :form-data exam-session-id participant-id :scores]
                             (fn [scores]
                               (assoc-in scores [id :section-score] value)))
               (throw (js/Error. "Sections aren't scored as whole. Check your dispatch type."))
       :module (update-in db [:scoring :form-data exam-session-id participant-id :scores]
                          (fn [scores]
                            (assoc-in scores [section-id :modules id :module-points] (filter-chars value))))))))

(rf/reg-event-fx
 :persist-scores
 [trim-v]
 (fn [{:keys [db]} [exam-session-id participant-id scores initial-scores]]
   {:http-xhrio {:method          :post
                 :uri             (routing/v-a-route "/participant/" participant-id "/scores")
                 :params          {:scores (module-points-to-bigdecs scores)
                                   :exam-session-id exam-session-id}
                 :format          (ajax/transit-request-format)
                 :response-format (ajax/transit-response-format)
                 :on-success      [:handle-persist-scores-success
                                   exam-session-id
                                   participant-id]
                 :on-failure      [:handle-persist-scores-failure
                                   exam-session-id
                                   participant-id
                                   initial-scores]}
    :loader     true}))

(rf/reg-event-db
 :handle-persist-scores-success
 [trim-v]
 (fn [db [exam-session-id participant-id response]]
   (-> (assoc-in db [:scoring :form-data exam-session-id participant-id :scores] (prepare-updated-scores (:scores response)))
       (assoc-in [:scoring :initial-form-data exam-session-id participant-id :scores] (prepare-updated-scores (:scores response))))))

(rf/reg-event-db
 :handle-persist-scores-failure
 [trim-v]
 (fn [db [exam-session-id participant-id initial-scores response]]
   (assoc-in db [:scoring :initial-form-data exam-session-id participant-id :scores] initial-scores)))

(rf/reg-event-fx
 :save-participant-scores
 [trim-v]
 (fn [{:keys [db]} _]
   (let [selected-exam-session (get-in db [:scoring :selected-exam-session])
         selected-participant (get-in db [:scoring :selected-participant])
         current-scores (get-in db [:scoring :form-data selected-exam-session selected-participant :scores])
         initial-scores (get-in db [:scoring :initial-form-data selected-exam-session selected-participant :scores])]
     {:db (assoc-in db [:scoring :initial-form-data selected-exam-session selected-participant :scores] current-scores)
      :dispatch [:persist-scores
                 selected-exam-session
                 selected-participant
                 current-scores
                 initial-scores]})))

(defn- positions [pred coll]
  (keep-indexed (fn [idx x]
                  (when (pred x)
                    idx))
                coll))

(defn- next-participant-id [db]
  (let [selected-exam-session-id (get-in db [:scoring :selected-exam-session])
        selected-participant-id (get-in db [:scoring :selected-participant])
        participants (->> (get-in db [:scoring :exam-sessions])
                          (filter #(= (:id %) selected-exam-session-id))
                          first
                          :participants
                          (sort-by :last-name))
        current-participant-index (first (positions #(= (:id %) selected-participant-id) participants))]
    (-> (nth participants (if (= current-participant-index (dec (count participants)))
                            0
                            (if (nil? current-participant-index)
                              0
                              (inc current-participant-index))))
        :id)))

(rf/reg-event-fx
 :save-participant-scores-and-select-next
 [trim-v]
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:scoring :selected-participant] (next-participant-id db))
    :dispatch [:save-participant-scores]}))

(rf/reg-event-db
 :select-next-participant
 [trim-v]
 (fn [db _]
   (assoc-in db [:scoring :selected-participant] (next-participant-id db))))
