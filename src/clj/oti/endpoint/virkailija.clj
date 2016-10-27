(ns oti.endpoint.virkailija
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [response not-found header]]
            [oti.util.auth :as auth]
            [oti.util.logging.audit :as audit]
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

(defn- disable-cache [handler]
  (fn [req]
    (-> (handler req)
        (header "Cache-Control" "no-store, must-revalidate"))))

(defn- user-info [{{:keys [identity]} :session}]
  (response (select-keys identity [:username])))

(defn- new-exam-session [{:keys [db]} {params :params session :session}]
  (let [conformed (s/conform ::os/exam-session params)
        new-exam-session (when-not (s/invalid? conformed)
                           (dba/add-exam-session! db conformed))]
    (if new-exam-session
      (audit/auditable-response
       (response {:success true})
       :app :admin
       :who (get-in session [:identity :username])
       :op :create
       :on :exam-session
       :after new-exam-session)
      {:status 400
       :body {:errors (s/explain ::os/exam-session params)}})))

(defn- exam-sessions [{:keys [db]}]
  (let [sessions (->> (dba/upcoming-exam-sessions db)
                      (map c/convert-session-row))]
    (response sessions)))

(defn- exam-session [{:keys [db]} id]
  (if-let [exam-session (->> (dba/exam-session db id)
                             (map c/convert-session-row)
                             first)]
    (response exam-session)
    (not-found {})))

(defn- update-exam-session [{:keys [db]} {params :params} id]
  (let [conformed (s/conform ::os/exam-session (assoc params ::os/id id))]
    (if (or (s/invalid? conformed) (not (seq (dba/save-exam-session! db conformed))))
      {:status 400
       :body {:errors (s/explain ::os/exam-session params)}}
      (response {:success true}))))

(defn- delete-exam-session [{:keys [db]} id]
  (if (pos? (dba/remove-exam-session! db id))
    (response {:success true})
    (not-found {})))

(defn- exam-session-registrations [config id]
  (response (fetch-registrations config id)))

(defn- sections-and-modules [{:keys [db]}]
  (response (dba/section-and-module-names db)))

(defn- search-participant [config q filter]
  (let [filter-kw (if (str/blank? filter) :all (keyword filter))
        query (when q (str/trim q))
        results (search/search-participants config query filter-kw)]
    (response results)))

(defn- participant-by-id [config id]
  (if-let [data (user-data/participant-data config id)]
    (response data)
    (not-found {})))

(defn- exam-session-routes [config]
  (context "/exam-sessions" []
    (POST "/" request (audit/log-if-status-200 (new-exam-session config request)))
    (GET "/"  []      (exam-sessions config))
    (context "/:id{[0-9]+}" [id :<< as-int]
      (GET "/"              []      (exam-session config id))
      (PUT "/"              request (update-exam-session config request id))
      (DELETE "/"           []      (delete-exam-session config id))
      (GET "/registrations" []      (exam-session-registrations config id)))))

(defn- participant-routes [config]
  (routes
   (GET "/participant-search"   [q filter] (search-participant config q filter))
   (context "/participant/:id{[0-9]+}" [id :<< as-int]
     (GET "/" [] (participant-by-id config id)))))

(defn virkailija-endpoint [config]
  (-> (context routing/virkailija-api-root []
        (GET "/user-info" [] user-info)
        (GET "/sections-and-modules" [] (sections-and-modules config))
        (exam-session-routes config)
        (participant-routes config))
      (wrap-routes auth/wrap-authorization)
      (wrap-routes disable-cache)))
