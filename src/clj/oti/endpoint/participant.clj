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


(defn participant-endpoint [{:keys [db]}]
  (context routing/participant-api-root []
    (GET "/translations" [lang]
      (if (str/blank? lang)
        {:status 400 :body {:error "Missing lang parameter"}}
        (response (->> translations
                       (map (fn [[k v]] [k ((keyword lang) v)]))
                       (into {})))))))
