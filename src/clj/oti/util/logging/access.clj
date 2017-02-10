(ns oti.util.logging.access
  (:require [ring.logger :as logger]
            [ring.logger.messages :refer [finished request-details starting request-params sending-response exception]]
            [ring.logger.protocols :as logger.protocols]
            [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [clojure.string :as str])
  (:import (java.time.format DateTimeFormatter)
           (java.util Locale TimeZone)
           (java.time LocalDateTime ZonedDateTime)))

(defn- str-key-value [[key value]]
  (str "\"" key "\"" ": " "\"" value "\""))

(defn- jsonfy [& args]
  (str "{" (str/join ", " (map str-key-value (partition-all 2 args))) "}"))

(defmethod starting :local [_ _])
(defmethod starting :dev [_ _])
(defmethod starting :qa [_ _])
(defmethod starting :prod [_ _])

(defmethod request-params :local [_ _])
(defmethod request-params :dev [_ _])
(defmethod request-params :qa [_ _])
(defmethod request-params :prod [_ _])

(defmethod sending-response :local [_ _])
(defmethod sending-response :dev [_ _])
(defmethod sending-response :qa [_ _])
(defmethod sending-response :prod [_ _])

(defmethod exception :local [_ _])
(defmethod exception :dev [_ _])
(defmethod exception :qa [_ _])
(defmethod exception :prod [_ _])

(defmethod request-details :local [_ _])
(defmethod request-details :dev [_ _])
(defmethod request-details :qa [_ _])
(defmethod request-details :prod [_ _])

(defn- info-log [{:keys [logger] :as options}
                 {:keys [logger-start-time logger-end-time
                         request-method uri remote-addr] req-headers :headers :as req}
                 {:keys [status] resp-headers :headers :as resp}
                 env]
  (logger.protocols/info logger
                         (jsonfy "timestamp" (.format (ZonedDateTime/now) (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss.SSSXX"))
                                 "responseCode" status
                                 "request" (str (clojure.string/upper-case (name request-method))
                                                " " (get req-headers "host") uri)
                                 "responseTime" (- logger-end-time logger-start-time)
                                 "request-method" (clojure.string/upper-case (name request-method))
                                 "service" "OTI"
                                 "environment" (or env "-")
                                 "customer" "OPH" ;; ?
                                 "user-agent" (get req-headers "user-agent" "-")
                                 "caller-id" "-" ;; ?
                                 "x-forwarded-for" (get req-headers "x-forwarded-for" "-")
                                 "remote-ip" remote-addr
                                 "session" "-"
                                 "response-size" (get resp-headers "Content-Length" 0)
                                 "referer" (get req-headers "referer" "-")
                                 "opintopolku-api-key" "-")))

(defmethod finished :local [o req resp] (info-log o req resp "local"))
(defmethod finished :dev [o req resp] (info-log o req resp "luokka"))
(defmethod finished :qa [o req resp] (info-log o req resp "qa"))
(defmethod finished :prod [o req resp] (info-log o req resp "prod"))

(defn make-logger []
  (reify logger.protocols/Logger
    (add-extra-middleware [_ handler] handler)
    (log [_ level ?throwable message]
      (when (#{:error :fatal :info} level)
        (if ?throwable
          (log/log level ?throwable message)
          (log/log level message))))))

(defn wrap-with-logger [handler & [opts]]
  (logger/wrap-with-logger
    handler
    {:logger       (make-logger)
     :printer      (case (:env opts)
                     "dev"        :dev
                     "qa"         :qa
                     "prod"       :prod
                     :local)
     :timing       true
     :redact-value "-"
     :exceptions   false}))
