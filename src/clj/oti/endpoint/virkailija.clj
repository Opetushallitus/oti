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
            [oti.service.accreditation :as accreditation]
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

(defn- fetch-exam-session [db id]
  (->> (dba/exam-session db id)
       (map c/convert-session-row)
       first))

(defn- user-info [{{:keys [identity]} :session}]
  (response (select-keys identity [:username])))

(defn- new-exam-session [{:keys [db]} {params :params session :session}]
  (let [conformed (s/conform ::os/exam-session params)
        new-exam-session (when-not (s/invalid? conformed)
                           (fetch-exam-session db (:id (dba/add-exam-session! db conformed))))]
    (if (seq new-exam-session)
      (do (audit/log :app :admin
                     :who (get-in session [:identity :username])
                     :op :create
                     :on :exam-session
                     :after new-exam-session
                     :msg "Creating a new exam session.")
          (response {:success true}))
      {:status 400
       :body {:errors (s/explain ::os/exam-session params)}})))

(defn- exam-sessions [{:keys [db]}]
  (let [sessions (->> (dba/upcoming-exam-sessions db)
                      (map c/convert-session-row))]
    (response sessions)))

(defn- exam-session [{:keys [db]} id]
  (if-let [exam-session (fetch-exam-session db id)]
    (response exam-session)
    (not-found {})))

(defn- update-exam-session [{:keys [db]} {params :params session :session} id]
  (let [conformed (s/conform ::os/exam-session (assoc params ::os/id id))
        exam-session (fetch-exam-session db id)
        updated-exam-session (when-not (s/invalid? conformed)
                               (dba/save-exam-session! db conformed)
                               (fetch-exam-session db id))]
    (if updated-exam-session
      (do (audit/log :app :admin
                     :who (get-in session [:identity :username])
                     :op :update
                     :on :exam-session
                     :before exam-session
                     :after updated-exam-session
                     :msg "Updating an existing exam session.")
          (response {:success true}))
      {:status 400
       :body {:errors (s/explain ::os/exam-session params)}})))

(defn- delete-exam-session [{:keys [db]} {session :session} id]
  (let [exam-session (fetch-exam-session db id)]
    (if (pos? (dba/remove-exam-session! db id))
      (do (audit/log :app :admin
                     :who (get-in session [:identity :username])
                     :op :delete
                     :on :exam-session
                     :before exam-session
                     :msg "Deleting an exam session.")
          (response {:success true}))
      (not-found {}))))

(defn- exam-session-registrations [config id]
  (response (fetch-registrations config id)))

(defn- frontend-config [{:keys [db]} {{:keys [identity]} :session}]
  (response {:section-and-module-names (dba/section-and-module-names db)
             :accreditation-types (dba/accreditation-types db)
             :user (select-keys identity [:username])}))

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
    (POST "/" request (new-exam-session config request))
    (GET "/"  []      (exam-sessions config))
    (context "/:id{[0-9]+}" [id :<< as-int]
      (GET "/"              []      (exam-session config id))
      (PUT "/"              request (update-exam-session config request id))
      (DELETE "/"           request (delete-exam-session config request id))
      (GET "/registrations" []      (exam-session-registrations config id)))))

(defn- participant-routes [config]
  (routes
   (GET "/participant-search"   [q filter] (search-participant config q filter))
   (context "/participant/:id{[0-9]+}" [id :<< as-int]
     (GET "/" [] (participant-by-id config id))
     (POST "/accreditations" {params :params session :session}
       (accreditation/approve-accreditations! config id params session)))))

(defn virkailija-endpoint [config]
  (-> (context routing/virkailija-api-root []
        (GET "/frontend-config" [] (partial frontend-config config))
        (exam-session-routes config)
        (participant-routes config))
      (wrap-routes auth/wrap-authorization)
      (wrap-routes disable-cache)))
