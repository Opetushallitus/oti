(ns oti.util.logging.core
  (:require [ring.logger :as logger]
            [ring.logger.protocols :as logger.protocols]
            [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rolling :refer [rolling-appender]]
            [environ.core :refer [env]]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.util Locale TimeZone)))

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

(defn logging-config []
  {:level :info
   :appenders {:rolling-audit-log-appender (assoc (rolling-appender {:path (str (logs-path) "/auditlog_oti.log")})
                                                  :ns-whitelist ["fi.vm.sade.auditlog.*"]
                                                  :output-fn (fn [{:keys [msg_]}]
                                                               (str (force msg_))))
               :rolling-application-log-appender (assoc (rolling-appender {:path (str (logs-path) "/oph-oti.log")})
                                                        :ns-blacklist ["fi.vm.sade.auditlog.*" "oti.util.logging.access"]
                                                        :timestamp-opts {:pattern "yyyy-MM-dd'T'HH:mm:ss.SSSX"
                                                                         :locale (Locale. "fi")
                                                                         :timezone (TimeZone/getTimeZone "Europe/Helsinki")}

                                                        ;; Example output:
                                                        ;; (taoensso.timbre/info "test")
                                                        ;; => nil
                                                        ;; 2016-11-01T11:27:12.759+02 INFO {} [nREPL-worker-7] INFO user: test

                                                        :output-fn (fn [{:keys [level ?err #_vargs msg_ ?ns-str hostname_
                                                                                timestamp_ ?line]}]
                                                                     (str
                                                                      (force timestamp_) " "
                                                                      (str/upper-case (name level)) " "
                                                                      "{" "} " ; FIXME: %X{user} is used in log4j pattern here
                                                                      "[" (.getName (Thread/currentThread)) "] "
                                                                      (str/upper-case (name level)) " "
                                                                      (or ?ns-str "?") ": "
                                                                      (force msg_)
                                                                      (when-let [err ?err]
                                                                        (str "\n" (timbre/stacktrace err {:stacktrace-fonts {}}))))
                                                                     ))
               :rolling-access-log-appender (assoc (rolling-appender {:path (str (logs-path) "/localhost_access_log")})
                                                   :ns-whitelist ["oti.util.logging.access"]
                                                   :output-fn (fn [{:keys [msg_]}]
                                                                (str (force msg_))))}})
