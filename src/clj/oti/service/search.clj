(ns oti.service.search
  (:require [oti.boundary.db-access :as dba]
            [oti.service.user-data :as user-data]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [oti.boundary.api-client-access :as api]))

(defn- group-by-section [participant-rows]
  (->> (partition-by :section_id participant-rows)
       (map
         (fn [section-rows]
           (let [accredited-modules (->> (partition-by :module_id section-rows)
                                         (map #(user-data/sec-or-mod-props "module" %))
                                         (filter :accreditation-requested?))]
             (-> (user-data/sec-or-mod-props "section" section-rows)
                 (assoc :accredited-modules accredited-modules)))))))

(defn- process-db-participants [db participants filter-kw]
  (->> (partition-by :id participants)
       (map
         (fn [participant-rows]
           (let [sections (group-by-section participant-rows)
                 {:keys [id ext_reference_id email diploma_date]} (first participant-rows)]
             {:id id
              :external-user-id ext_reference_id
              :email email
              :filter (user-data/user-status-filter db sections diploma_date)
              :sections (->> sections (map (fn [{:keys [id] :as data}] [id data])) (into {}))})))
       (filter #(or (= filter-kw :all) (= (:filter %) filter-kw)))))

(defn- query-matches? [query user-data]
  (when user-data
    (let [{:keys [etunimet sukunimi]} user-data]
      (or (str/includes? (str/lower-case sukunimi) query)
          (str/includes? (str/lower-case etunimet) query)))))

(defn- hetu-query [{:keys [db api-client]} hetu filter-kw]
  (when-let [user-data (api/get-person-by-hetu api-client hetu)]
    (when-let [db-data (dba/participant-by-ext-id db (:oidHenkilo user-data))]
      (when-let [processed-db-data (first (process-db-participants db db-data filter-kw))]
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
