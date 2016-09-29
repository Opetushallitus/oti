(ns oti.routing)

(def app-root "/oti")

(def virkailija-root (str app-root "/virkailija"))

(def participant-root (str app-root "/ilmoittaudu"))

(def api-root (str app-root "/api"))

(def virkailija-api-root (str api-root "/virkailija"))

(def participant-api-root (str api-root "/participant"))

(defn v-route [path]
  (str virkailija-root path))

(defn p-route [path]
  (str participant-root path))

(defn v-a-route [path]
  (str virkailija-api-root path))

(defn p-a-route [& path-parts]
  (apply str participant-api-root path-parts))

(defn img [file]
  (str app-root "/img/" file))

(defn auth-route [path]
  (str app-root "/auth" path))