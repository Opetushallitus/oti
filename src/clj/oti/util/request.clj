(ns oti.util.request
  (:require [ring.util.response :refer [header]]))

(defn wrap-disable-cache [handler]
  (fn [req]
    (-> (handler req)
        (header "Cache-Control" "no-store, must-revalidate"))))
