(ns oti.component.url-helper
  (:require [com.stuartsierra.component :as component])
  (:import [fi.vm.sade.properties OphProperties]))

(defprotocol UrlResolver
  (url [this key] [this key params]))

(defn- oph-properties [{:keys [virkailija-host oti-host tunnistus-host alb-host oppija-host]}]
  (doto (OphProperties. (into-array String ["/oti/oti_url.properties"]))
    (.addDefault "host-virkailija" virkailija-host)
    (.addDefault "host-tunnistus" tunnistus-host)
    (.addDefault "alb-host" alb-host)
    (.addDefault "oti-host" oti-host)
    (.addDefault "host-oppija" oppija-host)))

(defrecord UrlHelper []
  component/Lifecycle
  (start [this]
    (assoc this :oph-properties (oph-properties this)))
  (stop [this] this)
  UrlResolver
  (url [this key]
    (url this key []))
  (url [{:keys [oph-properties]} key params]
    (.url oph-properties (name key) (to-array (if (sequential? params) params [params])))))

(defn url-helper [config]
  (map->UrlHelper config))
