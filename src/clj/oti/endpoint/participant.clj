(ns oti.endpoint.participant
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [resource-response content-type redirect response]]
            [clojure.spec :as s]
            [clojure.string :as str]
            [oti.boundary.db-access :as dba]
            [oti.component.localisation :as localisation]
            [oti.spec :as os]
            [oti.routing :as routing]
            [oti.util.coercion :as c]
            [ring.util.response :as resp]
            [cognitect.transit :as transit]
            [clojure.java.io :as io]
            [taoensso.timbre :refer [error info]]
            [meta-merge.core :refer [meta-merge]]
            [oti.boundary.api-client-access :as api])) 

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

(defn- store-person-to-service! [api-client {:keys [etunimet sukunimi hetu]} preferred-name lang]
  (api/add-person! api-client
                   {:sukunimi sukunimi
                    :etunimet (str/join " " etunimet)
                    :kutsumanimi preferred-name
                    :hetu hetu
                    :henkiloTyyppi "OPPIJA"
                    :asiointiKieli {:kieliKoodi (name lang)}}))

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

(defn- registration-response [status text lang session]
  (let [url (if (= lang :sv) "/oti/anmala" "/oti/ilmoittaudu")]
    (-> (redirect url :see-other)
        (assoc :session (meta-merge session {:participant {:registration-status status
                                                           :registration-message text}})))))

(defn- register [{:keys [db api-client]} {session :session params :params}]
  (if-let [transit-data (:registration-data params)]
    (with-open [is (io/input-stream (.getBytes transit-data "UTF-8"))]
      (let [parsed-data (-> is (transit/reader :json) (transit/read))
            conformed (s/conform ::os/registration parsed-data)
            lang (::os/language-code conformed)
            participant-data (-> session :participant)
            external-user-id (or (:external-user-id participant-data)
                                 (store-person-to-service! api-client participant-data (::os/preferred-name conformed) lang))]
        (if (or (s/invalid? conformed) (nil? external-user-id))
          (registration-response :failure "Ilmoittautumistiedot olivat virheelliset" :fi session)
          (try
            (dba/register! db conformed external-user-id)
            (registration-response :success "Ilmoittautumisesi on rekisteröity" lang session)
            (catch Throwable t
              (error "Error inserting registration")
              (error t)
              (registration-response :failure "Ilmoittautumisessa tapahtui odottamaton virhe" lang session))))))
    (registration-response :failure "Ilmoittautumistiedot olivat virheelliset" :fi session)))

(defn participant-endpoint [{:keys [db payments api-client localisatation] :as config}]
  (routes
    (GET "/oti/abort" []
      (-> (resp/redirect "/oti/ilmoittaudu")
          (assoc :session {})))
    (context routing/participant-api-root []
      ;; TODO: Maybe relocate translation endpoints to a localisation endpoint?
      (GET "/translations" [lang]
        (if (str/blank? lang)
          {:status 400 :body {:error "Missing lang parameter"}}
          (response (localisation/by-lang localisation lang))))
      (GET "/translations/refresh" []
        (if (localisation/refresh localisation)
          (response {:message "ok"})
          {:status 500 :body {:error "Refreshing translations failed"}}))
      ;; FIXME: This is a dummy route
      (GET "/authenticate" [callback]
        (if-not (str/blank? callback)
          (let [hetu (random-hetu)
                {:keys [oidHenkilo etunimet sukunimi kutsumanimi]} (api/get-person-by-hetu api-client hetu)
                existing-email (when oidHenkilo (dba/participant-email db oidHenkilo))]
            (-> (redirect callback)
                (assoc :session {:participant {:etunimet (if etunimet (str/split etunimet #" ") [(random-name) (random-name)])
                                               :sukunimi (or sukunimi (random-name))
                                               :kutsumanimi kutsumanimi
                                               :email existing-email
                                               :hetu hetu
                                               :external-user-id oidHenkilo}})))
          {:status 400 :body {:error "Missing callback uri"}}))
      (GET "/exam-sessions" []
        (let [sessions (->> (dba/published-exam-sessions-with-space-left db)
                            (map c/convert-session-row))]
          (response sessions)))
      (-> (context "/authenticated" {session :session}
            (GET "/participant-data" []
              (response (:participant session)))
            (GET "/registration-options" []
              (let [user-id (-> session :participant :external-user-id) ; user-id is nil at this stage if the user is new
                    paid? (and user-id (pos? (dba/valid-full-payments-for-user db user-id)))
                    payments {:full (if paid? 0 (-> payments :amounts :full))
                              :retry (-> payments :amounts :retry)}]
                (->> {:sections (dba/modules-available-for-user db user-id)
                      :payments payments}
                     (response))))
            (POST "/register" request
              (register config request)))
          (wrap-routes wrap-authentication)))))
