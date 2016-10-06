(ns oti.endpoint.participant
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [resource-response content-type redirect response]]
            [clojure.spec :as s]
            [oti.boundary.db-access :as dba]
            [oti.spec :as os]
            [clojure.string :as str]
            [oti.routing :as routing]
            [oti.util.coercion :as c]
            [ring.util.response :as resp]
            [cognitect.transit :as transit]
            [clojure.java.io :as io]
            [taoensso.timbre :refer [error]]
            [meta-merge.core :refer [meta-merge]]))

(def translations
  {"Ilmoittautuminen" {:fi "Ilmoittautuminen" :sv "Anmälning"}
   "switch-language" {:fi "Pä svenska" :sv "Suomeksi"}
   "registration-title" {:fi "Opetushallinnon tutkintoon ilmoittautuminen"
                         :sv "Anmälning till examen i undervisningsförvaltning"}
   "fi" {:fi "Suomi" :sv "Finska"}
   "sv" {:fi "Ruotsi" :sv "Svenska"}})

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
    (if (-> request :session :participant :external-user-id)
      (handler request)
      {:status 401 :body {:error "Identification required"}})))

(defn- registration-response [status text lang session]
  (let [url (if (= lang :sv) "/oti/anmala" "/oti/ilmoittaudu")]
    (-> (redirect url :see-other)
        (assoc :session (meta-merge session {:participant {:registration-status status
                                                           :registration-message text}})))))

(defn- register [db {session :session params :params}]
  (if-let [transit-data (:registration-data params)]
    (with-open [is (io/input-stream (.getBytes transit-data "UTF-8"))]
      (let [parsed-data (-> is (transit/reader :json) (transit/read))
            conformed (s/conform ::os/registration parsed-data)
            user-id (-> session :participant :external-user-id)
            lang (::os/language-code conformed)]
        (if (s/invalid? conformed)
          {:error (s/explain-data ::os/registration parsed-data)}
          (try
            (dba/register! db parsed-data user-id)
            (registration-response :success "Ilmoittautumisesi on rekisteröity" lang session)
            (catch Throwable t
              (error "Error inserting registration")
              (error t)
              (registration-response :failure "Ilmoittautumisessa tapahtui odottamaton virhe" lang session))))))
    (registration-response :failure "Ilmoittautumistiedot olivat virheelliset" :fi session)))

(defn participant-endpoint [{:keys [db payments]}]
  (routes
    (GET "/oti/abort" []
      (-> (resp/redirect "/oti/ilmoittaudu")
          (assoc :session {})))
    (context routing/participant-api-root []
      (GET "/translations" [lang]
        (if (str/blank? lang)
          {:status 400 :body {:error "Missing lang parameter"}}
          (response (->> translations
                         (map (fn [[k v]] [k ((keyword lang) v)]))
                         (into {})))))
      ;; FIXME: This is a dummy route
      (GET "/authenticate" [callback]
        (if-not (str/blank? callback)
          (-> (redirect callback)
              (assoc :session {:participant {:first-name (random-name)
                                             :last-name (random-name)
                                             :hetu (random-hetu)
                                             :external-user-id "A"}}))
          {:status 400 :body {:error "Missing callback uri"}}))
      (GET "/exam-sessions" []
        (let [sessions (->> (dba/published-exam-sessions-with-space-left db)
                            (map c/convert-session-row))]
          (response sessions)))
      (-> (context "/authenticated" {session :session}
            (GET "/participant-data" []
              (response (:participant session)))
            (GET "/registration-options" []
              (let [user-id (-> session :participant :external-user-id)
                    paid? (pos? (dba/valid-full-payments-for-user db user-id))
                    payments {:full (if paid? 0 (-> payments :amounts :full))
                              :retry (-> payments :amounts :retry)}]
                (->> {:sections (dba/modules-available-for-user db user-id)
                      :payments payments}
                     (response))))
            (POST "/register" request
              (register db request)))
          (wrap-routes wrap-authentication)))))
