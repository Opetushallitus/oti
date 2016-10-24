(ns oti.service.user-data
  (:require [clojure.core.cache :as cache]
            [oti.boundary.api-client-access :as api]
            [oti.boundary.db-access :as dba]))

(def cache-ttl
  ; 2 hours
  7200000)

(defonce C (atom (cache/ttl-cache-factory {} :ttl cache-ttl)))

(defn- api-fetch-required? [user-oid]
  (not (cache/has? @C user-oid)))

(defn- fetch-oids! [api-client oids]
  (when (seq oids)
    (let [users (api/get-persons api-client oids)]
      (doseq [{:keys [oidHenkilo] :as user} users]
        (let [cache-user (select-keys user [:etunimet :sukunimi :kutsumanimi :hetu :oidHenkilo])]
          (swap! C cache/miss oidHenkilo cache-user))))))

(defn api-user-data-by-oid [api-client user-oids]
  (let [must-fetch-oids (filter api-fetch-required? user-oids)]
    (fetch-oids! api-client must-fetch-oids)
    (select-keys @C user-oids)))

(defn- make-kw [prefix suffix]
  (keyword (str prefix "_" suffix)))

(defn- make-get-fn [kw-prefix]
  (fn [row suffix]
    (let [key (make-kw kw-prefix suffix)]
      (key row))))

(defn- sec-or-mod-props [kw-prefix rows]
  (let [get-fn (make-get-fn kw-prefix)
        first-row (first rows)]
    {:id (get-fn first-row "id")
     :name (get-fn first-row "name")
     :score-ts (some #(get-fn % "score_ts") rows)
     :accepted (some #(get-fn % "accepted") rows)
     :points (some #(get-fn % "points") rows)
     :accreditation-requested? (some #(get-fn % "accreditation") rows)
     :accreditation-date (some #(get-fn % "accreditation_date") rows)
     :registered-to? (some #(get-fn % "registration") rows)}))

(defn- group-by-session [rows]
  (->> (partition-by :exam_session_id rows)
       (map
         (fn [session-rows]
           (let [modules (->> (partition-by :module_id session-rows)
                              (map #(sec-or-mod-props "module" %))
                              (remove :accreditation-requested?))
                 {:keys [session_date start_time end_time city street_address
                         other_location_info exam_session_id registration_state]} (first session-rows)]
             (when session_date
               (-> (select-keys (sec-or-mod-props "section" session-rows) [:score-ts :accepted])
                   (merge
                     {:modules (reduce #(assoc %1 (:id %2) %2) {} modules)
                      :session-date session_date
                      :start-time (str (.toLocalTime start_time))
                      :end-time (str (.toLocalTime end_time))
                      :session-id exam_session_id
                      :street-address street_address
                      :city city
                      :other-location-info other_location_info
                      :registration-state registration_state}))))))
       (remove nil?)))

(defn- group-by-section [participant-rows]
  (->> (partition-by :section_id participant-rows)
       (map
         (fn [section-rows]
           (let [accredited-modules (->> (partition-by :module_id section-rows)
                                           (map #(sec-or-mod-props "module" %))
                                           (filter :accreditation-requested?))
                 sessions (group-by-session section-rows)
                 module-titles (->> (reduce (fn [mods {:keys [modules]}]
                                              (->> (map #(select-keys (second %) [:id :name]) modules)
                                                   (concat mods)))
                                            []
                                            sessions)
                                    (sort-by :id))]
             (-> (sec-or-mod-props "section" section-rows)
                 (select-keys [:id :name :accreditation-requested? :accreditation-date])
                 (assoc :sessions sessions
                        :accredited-modules accredited-modules
                        :module-titles module-titles)))))))

(defn- payments [participant-rows]
  (->> (partition-by :payment_id participant-rows)
       (map
         (fn [payment-rows]
           (let [{:keys [payment_id amount payment_state payment_created]} (first payment-rows)]
             (when payment_id
               {:id payment_id
                :amount amount
                :state payment_state
                :created payment_created}))))
       (remove nil?)))

(defn participant-data [{:keys [db api-client]} id]
  (when-let [db-data (seq (dba/participant-by-id db id))]
    (let [external-user-id (:ext_reference_id (first db-data))
          api-data (-> (api-user-data-by-oid api-client [external-user-id])
                       (get external-user-id))]
      (merge
        api-data
        (select-keys (first db-data) [:id :email])
        {:sections (group-by-section db-data)
         :payments (payments db-data)}))))
