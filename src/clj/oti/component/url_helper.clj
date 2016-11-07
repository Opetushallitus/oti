(ns oti.component.url-helper
  (:require [com.stuartsierra.component :as component])
  (:import [fi.vm.sade.properties OphProperties]))

(defprotocol UrlResolver
  (url [this key] [this key params]))

(defn- oph-properties [{:keys [virkailija-host oti-host]}]
  (doto (OphProperties. (into-array String ["./oph-configuration/oti_url.properties"]))
    (.addDefault "host-virkailija" virkailija-host)
    (.addDefault "oti-host" oti-host)))

(defrecord UrlHelper []
  component/Lifecycle
  (start [this]
    (assoc this :oph-properties (oph-properties this)))
  (stop [this] this)
  UrlResolver
  (url [this key]
    (url this key []))
  (url [{:keys [oph-properties]} key params]
    (.url oph-properties (name key) (to-array params))))

(defn url-helper [config]
  (map->UrlHelper config))
