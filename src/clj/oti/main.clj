(ns oti.main
    (:gen-class)
    (:require [com.stuartsierra.component :as component]
              [duct.util.runtime :refer [add-shutdown-hook]]
              [duct.util.system :refer [load-system]]
              [duct.component.ragtime :as ragtime]
              [environ.core :refer [env]]
              [clojure.java.io :as io]
              [taoensso.timbre :as timbre :refer [info error]]
              [oti.util.logging.core :refer [logging-config]]))

(defn -main [& args]
  (let [bindings {'http-port (Integer/parseInt (:oti-http-port env "3000"))}
        prod-config-path (or (:config env) "./oph-configuration/config.edn")
        _ (info "Loading configuration from" prod-config-path)
        system (load-system [(io/resource "oti/system.edn") (io/file prod-config-path)] bindings)]
    (timbre/set-config! (logging-config))
    (info "Starting HTTP server on port" (-> system :http :port))
    (add-shutdown-hook ::stop-system (fn []
                                       (info "Shutting down system")
                                       (component/stop system)))
    (let [running-system (component/start system)]
      (info "Running migrations")
      (ragtime/migrate (:ragtime running-system)))))
