(ns oti.endpoint.status
  (:require [compojure.core :refer :all]))

(defn status-endpoint [{{db :spec} :db}]
  (GET "/oti/status" []
    "Health OK"))
