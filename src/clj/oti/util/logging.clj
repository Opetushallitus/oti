(ns oti.util.logging
  (:require [ring.logger :as logger]
            [ring.logger.protocols :as logger.protocols]
            [taoensso.timbre :as timbre]))

(defn make-logger []
  (reify logger.protocols/Logger
    (add-extra-middleware [_ handler] handler)
    (log [_ level throwable message]
      (when (#{:error :fatal} level)
        (timbre/log level throwable message)))))

(defn wrap-with-logger [handler]
  (logger/wrap-with-logger
    handler
    {:logger (make-logger)}))
