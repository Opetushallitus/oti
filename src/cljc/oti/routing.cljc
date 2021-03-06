(ns oti.routing)

(def app-root "/oti")

(def virkailija-root (str app-root "/virkailija"))

(def participant-root (str app-root "/ilmoittaudu"))

(def participant-sv-root (str app-root "/anmala"))

(def api-root (str app-root "/api"))

(def virkailija-api-root (str api-root "/virkailija"))

(def participant-api-root (str api-root "/participant"))

(defn v-route [& path-parts]
  (apply str virkailija-root path-parts))

(defn p-route [& path-parts]
  (apply str participant-root path-parts))

(defn p-sv-route [& path-parts]
  (apply str participant-sv-root path-parts))

(defn v-a-route [& path-parts]
  (apply str virkailija-api-root path-parts))

(defn p-a-route [& path-parts]
  (apply str participant-api-root path-parts))

(defn ext-route [& path-parts]
  (apply str app-root "/ext" path-parts))

(defn img [file]
  (str app-root "/img/" file))

(defn pdf [file]
  (str app-root "/pdf/" file))

(defn auth-route [path]
  (str app-root "/auth" path))
