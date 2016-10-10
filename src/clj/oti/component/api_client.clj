(ns oti.component.api-client
  (:require [com.stuartsierra.component :as component]))

(defrecord ApiClient []
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn api-client [config]
  (map->ApiClient config))
