(ns oti.routing)

(def app-root "/oti")

(def virkailija-root (str app-root "/virkailija"))

(def hakija-root (str app-root "/hakija"))

(def api-root (str app-root "/api"))

(defn v-route [path]
  (str virkailija-root path))

(defn h-route [path]
  (str hakija-root path))

(defn v-a-route [path]
  (str api-root "/virkailija" path))

(defn h-a-route [path]
  (str api-root "/hakija" path))

(defn img [file]
  (str app-root "/img/" file))

(defn auth-route [path]
  (str app-root "/auth" path))
