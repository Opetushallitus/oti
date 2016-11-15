(ns oti.endpoint.participant
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [resource-response content-type redirect response]]
            [clojure.string :as str]
            [oti.boundary.db-access :as dba]
            [oti.component.localisation :as loc]
            [oti.routing :as routing]
            [oti.util.coercion :as c]
            [taoensso.timbre :refer [error info]]
            [meta-merge.core :refer [meta-merge]]
            [oti.boundary.api-client-access :as api]
            [oti.service.registration :as registration]
            [oti.service.payment :as payment]
            [oti.spec :as os]
            [clojure.spec :as s]
            [oti.util.request :as req]
            [oti.component.url-helper :refer [url]]))

(def check-digits {0  0 16 "H"
                   1  1 17 "J"
                   2  2 18 "K"
                   3  3 19 "L"
                   4  4 20 "M"
                   5  5 21 "N"
                   6  6 22 "P"
                   7  7 23 "R"
                   8  8 24 "S"
                   9  9 25 "T"
                   10 "A" 26 "U"
                   11 "B" 27 "V"
                   12 "C" 28 "W"
                   13 "D" 29 "X"
                   14 "E" 30 "Y"
                   15 "F"})

(defn- random-hetu []
  (let [fmt "%1$02d"
        d (format fmt (inc (rand-int 30)))
        m (format fmt (inc (rand-int 12)))
        y (format fmt (+ (rand-int 30) 60))
        serial (+ 900 (rand-int 98))
        full (Integer/parseInt (str d m y serial))
        check-digit (get check-digits (mod full 31))]
    (str d m y "-" serial check-digit)))

(defn- random-name []
  (->> (repeatedly #(rand-nth "aefhijklmnoprstuvy"))
       (take 10)
       (apply str)
       (str/capitalize)))

(defn- wrap-authentication [handler]
  (fn [request]
    (if (-> request :session :participant :hetu)
      (handler request)
      {:status 401 :body {:error "Identification required"}})))

(defn- update-participant-session [session reg-status msg-key]
  (let [data (cond-> {:registration-status reg-status
                      :registration-message msg-key})]
    (meta-merge session {:participant data})))

(defn- session-participant [{:keys [db] :as config} {:keys [participant] :as session}]
  (let [{:keys [registration-status registration-id external-user-id]} participant]
    (if (and (= :pending registration-status) external-user-id registration-id)
      ; Delete pending payments / regs older than 25 minutes
      (if-let [pmt (-> (payment/process-unpaid-payments-of-participant! config external-user-id 25)
                       (get registration-id))]
        (condp = pmt
          :confirmed (update-participant-session session :success "registration-complete")
          :cancelled (update-participant-session session :error "registration-payment-cancel")
          :expired (update-participant-session session :error "registration-payment-cancel")
          :unpaid session)
        ; If the payment was not found, check the status from the registration
        (condp = (dba/registration-state-by-id db registration-id)
          "OK" (update-participant-session session :success "registration-complete")
          "ERROR" (update-participant-session session :error "registration-payment-cancel")
          (update-participant-session session :error "registration-payment-error")))
      ; Other or missing registration status, just return the session as is
      session)))

(defn authenticate [{:keys [db api-client] :as config} callback user-hetu automatic-address]
  (if-not (str/blank? callback)
    (let [hetu (if (s/valid? ::os/hetu user-hetu) user-hetu (random-hetu))
          {:keys [oidHenkilo etunimet sukunimi kutsumanimi]} (api/get-person-by-hetu api-client hetu)
          {:keys [email id]} (when oidHenkilo (first (dba/participant-by-ext-id db oidHenkilo)))
          address (when (= automatic-address "true")
                    #::os{:registration-post-office    "Helsinki"
                          :registration-zip            "00100"
                          :registration-street-address "Testikatu 1"})]
      (when id
        ;; Remove all existing unpaid payments / registrations at this stage if the participant has re-authenticated
        (payment/verify-or-delete-payments-of-participant! config oidHenkilo))
      (-> (redirect callback)
          (assoc :session {:participant (merge
                                          {:etunimet         (if etunimet
                                                               (str/split etunimet #" ")
                                                               [(random-name) (random-name)])
                                           :sukunimi         (or sukunimi (random-name))
                                           :kutsumanimi      kutsumanimi
                                           :hetu             hetu
                                           :external-user-id oidHenkilo
                                           ::os/email        email}
                                          address)})))
    {:status 400 :body {:error "Missing callback uri"}}))

(defn- redirect-response [lang]
  (redirect (if (= lang "sv")
              "/oti/anmala"
              "/oti/ilmoittaudu")))

(defn- header-authentication [{:keys [db api-client] :as config} {:keys [query-params headers] :as request}]
  (info "Authentication request received")
  (info headers)
  (info query-params)
  (response {:request-map request}))

(defn- init-authentication [{:keys [url-helper]} lang]
  (let [url-key (if (and lang (= "sv" lang))
                  "tunnistus.url.sv"
                  "tunnistus.url.fi")]
    (redirect (url url-helper url-key))))

(defn- abort [lang]
  (-> (redirect-response lang)
      (assoc :session {})))

(defn- translations [{:keys [localisation]} lang]
  (if (str/blank? lang)
    {:status 400 :body {:error "Missing lang parameter"}}
    (response (loc/translations-by-lang localisation lang))))

(defn- refresh-translations [{:keys [localisation]}]
  (if-let [new-translations (loc/refresh-translations localisation)]
    (response new-translations)
    {:status 500 :body {:error "Refreshing translations failed"}}))

(defn- exam-sessions [{:keys [db]}]
  (let [sessions (->> (dba/published-exam-sessions-with-space-left db)
                      (map c/convert-session-row))]
    (response sessions)))

(defn- participant-data [config session]
  (let [updated-session (session-participant config session)]
    {:status 200 :body (:participant updated-session) :session updated-session}))

(defn- registration-options [{:keys [db] :as config} session]
  (let [user-id (-> session :participant :external-user-id)] ; user-id is nil at this stage if the user is new
    (->> {:sections (dba/sections-and-modules-available-for-user db user-id)
          :payments (registration/payment-amounts config user-id)}
         (response))))

(defn participant-endpoint [config]
  (routes
    (GET "/oti/abort" [lang] (abort lang))
    (GET "/oti/initsession" request (header-authentication config request))
    (context routing/participant-api-root []
      (GET "/translations"         [lang] (translations config lang))
      (GET "/translations/refresh" []     (refresh-translations config))
      (GET "/authenticate"         [lang] (init-authentication config lang))
      ;; FIXME: This is a dummy route
      (GET "/dummy-authenticate"   [callback hetu automatic-address] (authenticate config callback hetu automatic-address))
      (GET "/exam-sessions"        []         (exam-sessions config))
      (-> (context "/authenticated" {session :session}
           (GET "/participant-data"     []      (participant-data config session))
           (GET "/registration-options" []      (registration-options config session))
           (GET "/payment-form-data"    request (registration/payment-data-for-retry config request))
           (POST "/register"            request (registration/register! config request)))
          (wrap-routes wrap-authentication)
          (wrap-routes req/wrap-disable-cache)))))
