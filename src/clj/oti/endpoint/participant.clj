(ns oti.endpoint.participant
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [resource-response content-type redirect response]]
            [clojure.spec :as s]
            [oti.boundary.db-access :as dba]
            [oti.spec :as os]
            [clojure.string :as str]
            [oti.routing :as routing]))

(def translations
  {"Ilmoittautuminen" {:fi "Ilmoittautuminen" :sv "Anmälning"}
   "switch-language" {:fi "Pä svenska" :sv "Suomeksi"}
   "registration-title" {:fi "Opetushallinnon tutkintoon ilmoittautuminen"
                         :sv "Anmälning till examen i undervisningsförvaltning"}})

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

(defn participant-endpoint [{:keys [db]}]
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
                                           :hetu (random-hetu)}}))
        {:status 400 :body {:error "Missing callback uri"}}))
    (GET "/participant-data" {session :session}
      (response (:participant session)))))
