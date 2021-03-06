(ns oti.endpoint.virkailija
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [response not-found header]]
            [oti.util.auth :as auth]
            [oti.util.logging.audit :as audit]
            [clojure.spec.alpha :as s]
            [oti.boundary.db-access :as dba]
            [oti.spec :as os]
            [oti.util.coercion :as c]
            [oti.routing :as routing]
            [oti.service.user-data :as user-data]
            [oti.service.search :as search]
            [oti.service.accreditation :as accreditation]
            [oti.service.registration :as registration]
            [clojure.string :as str]
            [oti.util.request :as req]
            [oti.util.csv :as csv]
            [compojure.coercions :refer [as-int]]
            [oti.utils :as utils]
            [oti.service.diploma :as diploma]
            [oti.service.payment :as payment]
            [oti.service.scoring :as scoring]
            [clojure.tools.logging :as log])
  (:import [java.time LocalDate Instant LocalDateTime ZoneId]
           [java.security SecureRandom]
           [org.apache.commons.codec.digest DigestUtils]))

(defn- fetch-exam-session [db id]
  (->> (dba/exam-session db id)
       (map c/convert-session-row)
       first))

(defn- user-info [{{:keys [identity]} :session}]
  (response (select-keys identity [:username])))

(defn- new-exam-session [{:keys [db]} {params :body-params session :session}]
  (let [conformed (s/conform ::os/exam-session params)
        new-exam-session (when-not (s/invalid? conformed)
                           (fetch-exam-session db (:id (dba/add-exam-session! db conformed))))]
    (if (seq new-exam-session)
      (do (audit/log :app :admin
                     :who (get-in session [:identity :oid])
                     :ip (get-in session [:identity :ip])
                     :user-agent (get-in session [:identity :user-agent])
                     :op :create
                     :on :exam-session
                     :id (get new-exam-session ::os/id)
                     :after new-exam-session
                     :msg "Creating a new exam session.")
          (response {:success true}))
      {:status 400
       :body {:errors (s/explain ::os/exam-session params)}})))

(def timezone (ZoneId/of "Europe/Helsinki"))

(defn- ts->local-date [ts]
  (-> (Instant/ofEpochMilli ts)
      (LocalDateTime/ofInstant timezone)
      .toLocalDate))

(defn- ts->local-date-time [ts]
  (-> (Instant/ofEpochMilli ts)
      (LocalDateTime/ofInstant timezone)))

(defn- exam-sessions [{:keys [db]} start-date end-date]
  (let [sts (as-int start-date)
        ets (as-int end-date)
        start-date (if sts
                     (ts->local-date sts)
                     (LocalDate/of 2016 1 1))
        end-date (if ets
                   (ts->local-date ets)
                   (LocalDate/of 9999 12 31))
        sessions (->> (dba/exam-sessions db start-date end-date)
                      (map c/convert-session-row))]
    (response sessions)))

(defn- exam-session [{:keys [db]} id]
  (if-let [exam-session (fetch-exam-session db id)]
    (response exam-session)
    (not-found {})))

(defn- diploma-count [{:keys [db]} start-ts end-ts]
  (let [sts (as-int start-ts)
        ets (as-int end-ts)]
    (if (and sts ets)
      (-> (dba/diploma-count db (ts->local-date sts) (ts->local-date ets))
          (response))
      {:status 400 :body {:error "Invalid start or end time"}})))

(defn- update-exam-session [{:keys [db]} {params :params session :session} id]
  (let [conformed (s/conform ::os/exam-session (assoc params ::os/id id))
        exam-session (fetch-exam-session db id)
        updated-exam-session (when-not (s/invalid? conformed)
                               (dba/save-exam-session! db conformed)
                               (fetch-exam-session db id))]
    (if updated-exam-session
      (do (audit/log :app :admin
                     :who (get-in session [:identity :oid])
                     :ip (get-in session [:identity :ip])
                     :user-agent (get-in session [:identity :user-agent])
                     :op :update
                     :on :exam-session
                     :id (get exam-session ::os/id)
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
                     :who (get-in session [:identity :oid])
                     :ip (get-in session [:identity :ip])
                     :user-agent (get-in session [:identity :user-agent])
                     :op :delete
                     :on :exam-session
                     :id (get exam-session ::os/id)
                     :before exam-session
                     :msg "Deleting an exam session.")
          (response {:success true}))
      (not-found {}))))

(defn- exam-session-registrations [{:keys [db] :as config} id]
  (response {:registrations (registration/fetch-registrations config id)
             :access-token (dba/access-token-for-exam-session db id)}))

(defn- frontend-config [{:keys [db]} {{:keys [identity]} :session}]
  (response {:section-and-module-names (dba/section-and-module-names db)
             :accreditation-types (dba/accreditation-types db)
             :user (select-keys identity [:given-name :surname])}))

(defn- search-participant [config q filter]
  (let [filter-kw (if (str/blank? filter) :all (keyword filter))
        query (when q (str/trim q))
        results (search/search-participants config query filter-kw)]
    (response results)))

(defn- participant-by-id [config id]
  (if-let [data (user-data/participant-data config id)]
    (response data)
    (not-found {})))

(defn- update-participant-email [{:keys [db]} email id]
  (dba/update-participant-email db email id)
  {:status 200
   :body {:message "OK"}})

(def secure-random (SecureRandom.))

(defn- generate-access-token-for-registrations! [{:keys [db]} session-id]
  (let [bytes (byte-array 20)
        _ (.nextBytes secure-random bytes)
        token (DigestUtils/sha256Hex bytes)]
    (dba/add-token-to-exam-session! db session-id token)
    (response {:access-token token})))

(defn- generate-diplomas! [config {diploma-data :body-params session :session}]
  (if (s/valid? ::os/diploma-data diploma-data)
    (diploma/generate-diplomas config diploma-data session)
    {:status 400 :body {:error "Invalid parameters"}}))

(defn- paid-payments-as-csv [config start-date end-date query]
  (let [payments (payment/get-paid-payments config start-date end-date query)]
    {:status 200
     :headers {"content-type" "text/csv; charset=iso-8859-1"
               "content-disposition" "attachment; filename=\"payments.csv\""}
     :body (csv/csv-output payments)}))

(defn- exam-by-language [{:keys [db]} lang]
  (if-let [exam (dba/exam-by-lang db lang)]
    (response exam)
    (not-found {})))

(defn- enrich-participant-data [data p-data]
  (when (and (seq data) (seq p-data))
    (->> (map (fn [[exam-session-id exam-session]]
                [exam-session-id (update exam-session :participants
                                         (fn [ps]
                                           (->> (map (fn [[p-id p]]
                                                       (if-let [api-data (get p-data (:ext-reference-id p))]
                                                         [p-id (assoc p
                                                                      :first-names (:etunimet api-data)
                                                                      :last-name (:sukunimi api-data)
                                                                      :display-name (:kutsumanimi api-data)
                                                                      :ssn (:hetu api-data))]
                                                         [p-id p])) ps)
                                                (into {}))))]) data)
         (into {}))))

(defn- exam-sessions-full [{:keys [db api-client]} lang]
  (let [scoring-data (scoring/exam-sessions-full db lang)
        ext-references (map :ext-reference-id
                            (mapcat (fn [[_ es]]
                                      (-> (:participants es)
                                          vals)) scoring-data))
        external-data (when (seq ext-references)
                        (user-data/api-user-data-by-oid api-client ext-references))
        enriched (enrich-participant-data scoring-data external-data)]
    (if (seq enriched)
      (response enriched)
      (not-found {:error "No exam session data available"}))))

(defn- exam-session-routes [config]
  (context "/exam-sessions" []
    (POST "/"    request               (new-exam-session config request))
    (GET "/"     [start-date end-date] (exam-sessions config start-date end-date))
    (GET "/full" [lang]                (exam-sessions-full config (or lang "fi")))
    (context "/:id{[0-9]+}" [id :<< as-int]
      (GET "/"              []      (exam-session config id))
      (PUT "/"              request (update-exam-session config request id))
      (DELETE "/"           request (delete-exam-session config request id))
      (GET "/registrations" []      (exam-session-registrations config id))
      (PUT "/token"         []      (generate-access-token-for-registrations! config id)))))

(defn- participant-routes [config]
  (routes
   (GET "/participant-search" [q filter] (search-participant config q filter))
   (context "/diplomas" []
     (PUT "/" [] (partial generate-diplomas! config))
     (GET "/default-signer-title" [] (response (diploma/default-signer-title config)))
     (GET "/count" [start-date end-date] (diploma-count config start-date end-date)))
   (context "/participant/:id{[0-9]+}" [id :<< as-int]
     (GET "/" [] (participant-by-id config id))
     (PUT "/email" {{:keys [email]} :params}
       (update-participant-email config email id))
     (POST "/accreditations" {params :body-params session :session :as request}
       (try (accreditation/approve-accreditations! config id params session)
            (catch AssertionError _
              {:status 400 :body {:error "Invalid parameters"}})))
     (DELETE "/section/:section-id{[0-9]+}/accreditation" [section-id :<< as-int :as {session :session}]
       (try (accreditation/delete-section-accreditation! config id section-id session)
            (catch IllegalArgumentException _ {:status 400 :body {:error "Invalid parameters"}})))
     (POST "/scores" request
       (scoring/upsert-scores config request id))
     (DELETE "/scores" request
       (scoring/delete-scores config request id))
     (POST "/scores/email" request
       (scoring/send-scores-email config request id))
     (GET "/scores/email" request
       (scoring/scores-email-sent? config request id))
     (PUT "/registration" request
       (scoring/update-registration-state config request id)))
   (PUT "/payment/:order-number/approve" [order-number lang :as {session :session}]
     (if (payment/confirm-payment-manually! config order-number lang session)
       (response {:success true})
       (not-found {:error "Payment not found"})))
   (GET "/payments" [start-date :<< as-int end-date :<< as-int query]
     (paid-payments-as-csv config (ts->local-date-time start-date) (ts->local-date-time end-date) query))
   (DELETE "/registrations/:id{[0-9]+}/sections/:section-id{[0-9]+}" [id :<< as-int section-id :<< as-int :as {session :session {state :state} :body-params}]
     (if (registration/cancel-registration-by-section! config id section-id state session)
       (response {:success true})
       (not-found {:error "Registration not found"})))
   (DELETE "/registrations/:id{[0-9]+}" [id :<< as-int :as {session :session {state :state} :body-params}]
     (if (registration/cancel-registration! config id state session)
       (response {:success true})
       (not-found {:error "Registration not found"})))))

(defn- exam-routes [config]
  (routes
   (GET "/exam" [lang] (exam-by-language config lang))))

(defn virkailija-endpoint [config]
  (-> (context routing/virkailija-api-root []
        (GET "/frontend-config" [] (partial frontend-config config))
        (exam-session-routes config)
        (participant-routes config)
        (exam-routes config))
      (wrap-routes auth/wrap-authorization)
      (wrap-routes req/wrap-disable-cache)))
