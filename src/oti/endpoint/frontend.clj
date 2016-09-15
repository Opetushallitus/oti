(ns oti.endpoint.frontend
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :refer [resource-response content-type]]))

(defn frontend-endpoint [{{db :spec} :db}]
  (routes
    (GET "/" []
      (-> (resource-response "/public/index.html")
          (content-type "text/html; charset=utf-8")))
    (route/resources "/" {:root "/oti/public"
                          :mime-types {"js" "text/javascript; charset=utf-8"}})))
