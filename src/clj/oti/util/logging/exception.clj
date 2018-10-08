(ns oti.util.logging.exception
  (:require [clojure.tools.logging :as log]))

;; This has to be done before wrap-hide-errors is executed because it eats the exception
(defn log-exceptions [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable throwable
        (log/error throwable)
        (throw throwable)))))
