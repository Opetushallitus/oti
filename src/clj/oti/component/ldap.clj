(ns oti.component.ldap
  (:require [com.stuartsierra.component :as component]
            [clj-ldap.client :as ldap])
  (:import [java.net InetAddress]))

(defn create-ldap-connection-pool [ldap-config]
  (let [host        (InetAddress/getByName (:server ldap-config))
        host-name   (.getHostName host)]
    (ldap/connect {:host            [{:address host-name
                                      :port (:port ldap-config)}]
                   :bind-dn         (:userdn ldap-config)
                   :password        (:password ldap-config)
                   :ssl?            (:ssl ldap-config)
                   :num-connections 1})))

(defrecord Ldap [config]
  component/Lifecycle
  (start [this]
    (assoc this :pool (create-ldap-connection-pool config)))
  (stop [{:keys [pool] :as this}]
    (when pool
      (ldap/close pool))
    (assoc this :pool nil)))

(defn ldap [config]
  (->Ldap config))
