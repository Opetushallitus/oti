(ns oti.service.search
  (:require [oti.boundary.db-access :as dba]
            [oti.service.user-data :as user-data]
            [clojure.string :as str]
            [clojure.spec :as s]
            [oti.boundary.api-client-access :as api]))

(defn- score-map [id-kw ts-kw acc-kw rows]
  (->> (filter id-kw rows)
       (reduce (fn [result row]
                 (assoc result (id-kw row) (cond-> {:ts (ts-kw row)}
                                                   acc-kw (assoc :accepted (acc-kw row))))) {})))

(defn- process-db-participants [db participants filter-kw]
  (let [{:keys [sections]} (dba/section-and-module-names db)]
    (->> (partition-by :id participants)
         (map
           (fn [participant-rows]
             (let [scored-sections (score-map :scored_section_id :section_score_ts :section_accepted participant-rows)
                   scored-modules (score-map :scored_module_id :module_score_ts :module_accepted participant-rows)
                   accredited-sections (score-map :accredited_section_id :accredited_section_date nil participant-rows)
                   accredited-modules (score-map :accredited_module_id :accredited_module_date nil participant-rows)
                   completed-sections (->> (merge scored-sections accredited-sections)
                                           (filter (fn [[_ props]] (or (:section_accepted props) (:accredited_section_date props))))
                                           (map first)
                                           set)
                   required-sections (set (keys sections))
                   assigned-filter (cond
                                     (= completed-sections required-sections) :complete
                                     :else :incomplete)
                   {:keys [id ext_reference_id email]} (first participant-rows)]
               {:id id
                :external-user-id ext_reference_id
                :email email
                :filter assigned-filter
                :scored-sections scored-sections
                :scored-modules scored-modules
                :accredited-sections accredited-sections
                :accredited-modules accredited-modules})))
         (filter #(or (= filter-kw :all) (= (:filter %) filter-kw))))))

(defn- query-matches? [query user-data]
  (when user-data
    (let [{:keys [etunimet sukunimi]} user-data]
      (or (str/includes? (str/lower-case sukunimi) query)
          (str/includes? (str/lower-case etunimet) query)))))

(defn- hetu-query [{:keys [db api-client]} hetu filter-kw]
  (when-let [user-data (api/get-person-by-hetu api-client hetu)]
    (when-let [db-data (dba/participant db (:oidHenkilo user-data))]
      (when-let [processed-db-data (first (process-db-participants db [db-data] filter-kw))]
        (-> (select-keys user-data [:etunimet :sukunimi :kutsumanimi :hetu])
            (merge processed-db-data)
            (vector))))))

(defn search-participants [{:keys [db api-client] :as config} query filter-kw]
  (if (s/valid? :oti.spec/hetu query)
    (or (hetu-query config query filter-kw) [])
    (let [filtered-participants (process-db-participants db (dba/all-participants db) filter-kw)
          oids (map :external-user-id filtered-participants)
          users-by-oid (user-data/api-user-data-by-oid api-client oids)
          lc-query (when-not (str/blank? query) (str/lower-case query))]
      (cond->> filtered-participants
               lc-query (filter (fn [{:keys [external-user-id]}]
                                  (->> (get users-by-oid external-user-id)
                                       (query-matches? lc-query))))
               :always (map (fn [{:keys [external-user-id] :as db-data}]
                              (let [user-data (get users-by-oid external-user-id)]
                                (-> (dissoc user-data :oidHenkilo)
                                    (merge db-data)))))))))
