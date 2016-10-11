(ns oti.endpoint.virkailija
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [response not-found]]
            [oti.util.auth :as auth]
            [clojure.spec :as s]
            [oti.boundary.db-access :as dba]
            [oti.spec :as os]
            [oti.util.coercion :as c]
            [oti.boundary.api-client-access :as api]))

(defn- as-int [x]
  (try (Integer/parseInt x)
       (catch Throwable _)))

(defn- fetch-registrations [{:keys [db api-client]} session-id]
  (if-let [regs (seq (dba/registrations-for-session db session-id))]
    (let [oids (map :external-user-id regs)
          user-data-by-oid (->> (api/get-persons api-client oids)
                                (reduce (fn [users {:keys [oidHenkilo] :as user}]
                                          (assoc users oidHenkilo user))
                                        {}))]
      (map (fn [{:keys [external-user-id] :as registration}]
             (-> (get user-data-by-oid external-user-id)
                 (select-keys [:etunimet :sukunimi])
                 (merge registration)))
           regs))
    []))

(defn virkailija-endpoint [{:keys [db] :as config}]
  (-> (context "/oti/api/virkailija" []
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
              (let [regs (fetch-registrations config id)
                    names (dba/section-and-module-names db)]
                (response {:registrations regs
                           :translations names}))))))
      (wrap-routes auth/wrap-authorization)))
