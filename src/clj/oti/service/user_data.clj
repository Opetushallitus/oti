(ns oti.service.user-data
  (:require [clojure.core.cache :as cache]
            [oti.boundary.api-client-access :as api]))

(def cache-ttl
  ; 2 hours
  7200000)

(defonce C (atom (cache/ttl-cache-factory {} :ttl cache-ttl)))

(defn- api-fetch-required? [user-oid]
  (not (cache/has? @C user-oid)))

(defn- fetch-oids! [api-client oids]
  (when (seq oids)
    (let [users (api/get-persons api-client oids)]
      (doseq [{:keys [oidHenkilo] :as user} users]
        (let [cache-user (select-keys user [:etunimet :sukunimi :kutsumanimi :hetu :oidHenkilo])]
          (swap! C cache/miss oidHenkilo cache-user))))))

(defn api-user-data-by-oid [api-client user-oids]
  (let [must-fetch-oids (filter api-fetch-required? user-oids)]
    (fetch-oids! api-client must-fetch-oids)
    (select-keys @C user-oids)))
