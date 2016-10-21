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
            [oti.spec :as os]))

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

(defn- update-participant-session [{:keys [localisation]} session reg-status msg-key lang]
  (let [data (cond-> {:registration-status reg-status
                      :registration-message (loc/t localisation lang msg-key)})]
    (meta-merge session {:participant data})))

(defn- session-participant [config {:keys [participant] :as session} lang]
  (let [{:keys [registration-status registration-id external-user-id]} participant]
    (if (and (= :pending registration-status) external-user-id)
      ; Delete pending payments / regs older than 25 minutes
      (if-let [pmt (-> (payment/process-unpaid-payments-of-participant! config external-user-id 25)
                       (get registration-id))]
        (condp = pmt
          :confirmed (update-participant-session config session :success "registration-complete" lang)
          :cancelled (update-participant-session config session :error "registration-payment-cancel" lang)
          :expired (update-participant-session config session :error "registration-payment-cancel" lang)
          :unpaid session)
        (update-participant-session config session :error "registration-payment-error" lang))
      ; Other or missing registration status, just return the session as is
      session)))

(defn participant-endpoint [{:keys [db api-client localisation] :as config}]
  (routes
    (GET "/oti/abort" [lang]
         (-> (redirect (if (= lang "fi")
                              "/oti/ilmoittaudu"
                              "/oti/anmala"))
             (assoc :session {})))
    (context routing/participant-api-root []
      ;; TODO: Maybe relocate translation endpoints to a localisation endpoint?
      (GET "/translations" [lang]
        (if (str/blank? lang)
          {:status 400 :body {:error "Missing lang parameter"}}
          (response (loc/translations-by-lang localisation lang))))
      (GET "/translations/refresh" []
        (if-let [new-translations (loc/refresh-translations localisation)]
          (response new-translations)
          {:status 500 :body {:error "Refreshing translations failed"}}))
      ;; FIXME: This is a dummy route
      (GET "/authenticate" [callback]
        (if-not (str/blank? callback)
          (let [hetu (random-hetu)
                {:keys [oidHenkilo etunimet sukunimi kutsumanimi]} (api/get-person-by-hetu api-client hetu)
                {:keys [email id]} (when oidHenkilo (first (dba/participant-by-ext-id db oidHenkilo)))]
            (when id
              ;; Remove all existing unpaid payments / registrations at this stage if the participant has re-authenticated
              (payment/verify-or-delete-payments-of-participant! config oidHenkilo))
            (-> (redirect callback)
                (assoc :session {:participant {:etunimet (if etunimet (str/split etunimet #" ") [(random-name) (random-name)])
                                               :sukunimi (or sukunimi (random-name))
                                               :kutsumanimi kutsumanimi
                                               :email email
                                               :hetu hetu
                                               :external-user-id oidHenkilo}})))
          {:status 400 :body {:error "Missing callback uri"}}))
      (GET "/exam-sessions" []
        (let [sessions (->> (dba/published-exam-sessions-with-space-left db)
                            (map c/convert-session-row))]
          (response sessions)))
      (-> (context "/authenticated" {session :session}
            (GET "/participant-data" [lang]
              (if-not (str/blank? lang)
                (let [updated-session (session-participant config session lang)]
                  {:status 200 :body (:participant updated-session) :session updated-session})
                {:status 400 :body {:error "Missing language parameter"}}))
            (GET "/registration-options" []
              (let [user-id (-> session :participant :external-user-id)] ; user-id is nil at this stage if the user is new
                (->> {:sections (dba/sections-and-modules-available-for-user db user-id)
                      :payments (registration/payment-amounts config user-id)}
                     (response))))
            (GET "/payment-form-data" {session :session {:keys [lang]} :params}
              (registration/payment-data-for-retry config session lang))
            (POST "/register" request
              (registration/register! config request)))
          (wrap-routes wrap-authentication)))))
