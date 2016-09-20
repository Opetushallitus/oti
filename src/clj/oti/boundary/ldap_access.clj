(ns oti.boundary.ldap-access
  (:require [clj-ldap.client :as ldap]
            [clojure.string :as str]
            [cheshire.core :as json]
            [oti.component.ldap])
  (:import [oti.component.ldap Ldap]))

(def people-path-base "ou=People,dc=opintopolku,dc=fi")
(def user-right-name "APP_OTI_CRUD")

(defprotocol LdapAccess
  (user-has-access? [ldap username]))

(extend-type Ldap
  LdapAccess
  (user-has-access? [{:keys [pool]} username]
    (when-let [user (first (ldap/search pool people-path-base {:filter (str "(uid=" username ")")}))]
      (->> (json/parse-string (:description user))
           (filter #(str/includes? % user-right-name))
           first))))
