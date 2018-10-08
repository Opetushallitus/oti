(ns oti.util.logging.exception
  (:require [compojure.response :as compojure]
            [ring.util.response :as response]
            [clojure.tools.logging :as log]))

(defn wrap-hide-errors [handler error-response]
  (fn [request]
    (try
      (handler request)
      (catch Throwable _
        (log/error _)
        (-> (compojure/render error-response request)
            (response/content-type "text/html")
            (response/status 500))))))
