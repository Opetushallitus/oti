(ns oti.service.search
  (:require [oti.boundary.db-access :as dba]
            [oti.service.user-data :as user-data]
            [clojure.string :as str]
            [clojure.spec :as s]
            [oti.boundary.api-client-access :as api]))


(defn- query-matches? [query user-data]
  (or
    (str/blank? query)
    (when user-data
      (let [{:keys [etunimet sukunimi]} user-data
            lc-query (str/lower-case query)]
        (or (str/includes? (str/lower-case etunimet) lc-query)
            (str/includes? (str/lower-case sukunimi) lc-query))))))
(defn- hetu-query [{:keys [db api-client]} hetu]
  (when-let [user-data (api/get-person-by-hetu api-client hetu)]
    (when-let [db-data (dba/participant db (:oidHenkilo user-data))]
      (-> (select-keys user-data [:etunimet :sukunimi :kutsumanimi :hetu])
          (assoc :external-user-id (:oidHenkilo user-data))
          (merge db-data)))))

(defn search-participants [{:keys [db api-client] :as config} query]
  (if (s/valid? :oti.spec/hetu query)
    (hetu-query config query)
    (let [all-participants (dba/all-participants db)
          oids (map :ext_reference_id all-participants)
          users-by-oid (user-data/api-user-data-by-oid api-client oids)
          matching-participants (->> all-participants
                                     (filter (fn [{:keys [ext_reference_id]}]
                                               (->> (get users-by-oid ext_reference_id)
                                                    (query-matches? query)))))]
      (map (fn [{:keys [id ext_reference_id email]}]
             (let [user-data (get users-by-oid ext_reference_id)]
               (-> (assoc user-data :external-user-id ext_reference_id :id id :email email)
                   (dissoc :oidHenkilo))))
           matching-participants))))
