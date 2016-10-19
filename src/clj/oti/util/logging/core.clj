(ns oti.util.logging.core
  (:require [ring.logger :as logger]
            [ring.logger.protocols :as logger.protocols]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rolling :refer [rolling-appender]]
            [environ.core :refer [env]]
            [clojure.java.io :as io]))

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

(defn logs-path
  "Assumes that in production logs folder is in user.home/logs
  and in development uses project root (or where the jar is run)."
  []
  (let [app-home (:user-home env)
        dev? (:dev? env)]
    (if-not dev?
      (if (.isDirectory (io/file (str app-home "/logs")))
        (str app-home "/logs")
        (throw (IllegalStateException. "Could not determine logs directory")))
      (if (.isDirectory (io/file "./logs")) ; In dev-mode use project-root/where-jar-is-run, make dir if necessary
        "./logs"
        (if (.mkdir (io/file "./logs"))
          "./logs"
          (throw (IllegalStateException. "Could not create local logs directory")))))))

(def logging-config
  {:level :info
   :appenders {:rolling-audit-log-appender (assoc (rolling-appender {:path (str (logs-path) "/auditlog_oti.log")})
                                                  :ns-whitelist ["fi.vm.sade.auditlog.*"]
                                                  :output-fn (fn [{:keys [msg_]}]
                                                               (str (force msg_))))
               :rolling-application-log-appender (assoc (rolling-appender {:path (str (logs-path) "/applicationlog_oti.log")})
                                                        :ns-blacklist ["fi.vm.sade.auditlog.*"])}})
