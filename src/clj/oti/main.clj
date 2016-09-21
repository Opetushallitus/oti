(ns oti.main
    (:gen-class)
    (:require [com.stuartsierra.component :as component]
              [duct.util.runtime :refer [add-shutdown-hook]]
              [duct.util.system :refer [load-system]]
              [environ.core :refer [env]]
              [clojure.java.io :as io]
              [taoensso.timbre :refer [info error]]))

(defn -main [& args]
  (let [prod-config-path (or (:config env) "./oph-configuration/config.edn")
        _ (info "Loading configuration from" prod-config-path)
        system (load-system [(io/resource "oti/system.edn") (io/file prod-config-path)])]
    (info "Starting HTTP server on port" (-> system :http :port))
    (add-shutdown-hook ::stop-system #(component/stop system))
    (component/start system)))
