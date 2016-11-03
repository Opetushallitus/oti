(ns oti.component.ldap
  (:require [com.stuartsierra.component :as component]
            [clj-ldap.client :as ldap]
            [oti.boundary.ldap-access :as ldap-access]
            [cheshire.core :as json]
            [clojure.string :as str])
  (:import [java.net InetAddress]))

(def people-path-base "ou=People,dc=opintopolku,dc=fi")
(def user-right-name "APP_OTI_CRUD")

(defn- create-ldap-connection-pool [ldap-config]
  (let [host        (InetAddress/getByName (:server ldap-config))
        host-name   (.getHostName host)]
    (ldap/connect {:host            [{:address host-name
                                      :port (:port ldap-config)}]
                   :bind-dn         (:userdn ldap-config)
                   :password        (:password ldap-config)
                   :ssl?            (:ssl ldap-config)
                   :num-connections 1
                   :connect-timeout 10000
                   :timeout         15000})))

(defrecord Ldap [config]
  component/Lifecycle
  (start [this]
    (assoc this :pool (atom nil)))
  (stop [{:keys [pool] :as this}]
    (when (and pool @pool)
      (ldap/close @pool)
      (reset! pool nil))
    this)
  ldap-access/LdapAccess
  (fetch-authorized-user [{:keys [pool]} username]
    (when-not @pool
      (reset! pool (create-ldap-connection-pool config)))
    (when-let [user (first (ldap/search @pool people-path-base {:filter (str "(uid=" username ")")}))]
      (when (->> (json/parse-string (:description user))
                 (some #(str/includes? % user-right-name)))
        {:username username
         :given-name (:givenName user)
         :surname (:sn user)}))))

(defn ldap [config]
  (->Ldap config))
