(ns oti.component.global-config
  (:require [com.stuartsierra.component :as component])
  (:import [fi.vm.sade.properties OphProperties]))

(defrecord GlobalConfig []
  component/Lifecycle
  (start [this] this)
  (stop [this] this))

(defn global-config [config]
  (map->GlobalConfig config))
