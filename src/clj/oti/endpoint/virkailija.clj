(ns oti.endpoint.virkailija
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [response not-found]]
            [oti.util.auth :as auth]
            [clojure.spec :as s]
            [oti.boundary.db-access :as dba]
            [oti.spec :as os]
            [oti.util.coercion :as c]
            [oti.routing :as routing]
            [oti.service.user-data :as user-data]
            [oti.service.search :as search]
            [clojure.string :as str]))

(defn- as-int [x]
  (try (Integer/parseInt x)
       (catch Throwable _)))

(defn- fetch-registrations [{:keys [db api-client]} session-id]
  (if-let [regs (seq (dba/registrations-for-session db session-id))]
    (let [oids (map :external-user-id regs)
          user-data-by-oid (user-data/api-user-data-by-oid api-client oids)]
      (map (fn [{:keys [external-user-id] :as registration}]
             (-> (get user-data-by-oid external-user-id)
                 (select-keys [:etunimet :sukunimi])
                 (merge registration)))
           regs))
    []))

(defn- make-kw [prefix suffix]
  (keyword (str prefix "_" suffix)))

(defn- sec-or-mod-props [kw-prefix row]
  (let [id (make-kw kw-prefix "id")
        score-ts (make-kw kw-prefix "score_ts")
        accepted (make-kw kw-prefix "accepted")
        accreditation (make-kw kw-prefix "accreditation")
        accreditation-date (make-kw kw-prefix "accreditation_date")
        registration (make-kw kw-prefix "registration")]
    {:id (id row)
     :score-ts (score-ts row)
     :accepted (accepted row)
     :accreditation-requested? (accreditation row)
     :accreditation-date (accreditation-date row)
     :registered-to? (registration row)}))

(defn- group-by-section [participant-rows]
  (->> (partition-by :section_id participant-rows)
       (map
         (fn [section-rows]
           (let [modules ()])))))

(defn- participant-data [{:keys [db api-client]} id]
  (if-let [db-data (seq (dba/participant-by-id db id))]
    (let [external-user-id (:ext_reference_id (first db-data))
          api-data (-> (user-data/api-user-data-by-oid api-client [external-user-id])
                       (get external-user-id))])
    {}))

(defn virkailija-endpoint [{:keys [db] :as config}]
  (-> (context routing/virkailija-api-root []
        (GET "/user-info" {session :session}
          {:status 200
           :body (select-keys (:identity session) [:username])})
        (context "/exam-sessions" []
          (POST "/" {params :params}
            (let [conformed (s/conform ::os/exam-session params)]
              (if (or (s/invalid? conformed) (not (seq (dba/add-exam-session! db conformed))))
                {:status 400
                 :body {:errors (s/explain ::os/exam-session params)}}
                (response {:success true}))))
          (GET "/" []
            (let [sessions (->> (dba/upcoming-exam-sessions db)
                                (map c/convert-session-row))]
              (response sessions)))
          (context "/:id{[0-9]+}" [id :<< as-int]
            (GET "/" []
              (if-let [exam-session (->> (dba/exam-session db id)
                                         (map c/convert-session-row)
                                         first)]
                (response exam-session)
                (not-found {})))
            (PUT "/" {params :params}
              (let [conformed (s/conform ::os/exam-session (assoc params ::os/id id))]
                (if (or (s/invalid? conformed) (not (seq (dba/save-exam-session! db conformed))))
                  {:status 400
                   :body {:errors (s/explain ::os/exam-session params)}}
                  (response {:success true}))))
            (DELETE "/" []
              (if (pos? (dba/remove-exam-session! db id))
                (response {:success true})
                (not-found {})))
            (GET "/registrations" []
              (response (fetch-registrations config id)))))
        (GET "/sections-and-modules" []
          (response (dba/section-and-module-names db)))
        (GET "/participant-search" [q filter]
          (let [filter-kw (if (str/blank? filter) :all (keyword filter))
                query (when q (str/trim q))
                results (search/search-participants config query filter-kw)]
            (response results)))
        (context "/participant/:id{[0-9]+}" [id :<< as-int]
          (GET "/" []
            )))
      (wrap-routes auth/wrap-authorization)))
