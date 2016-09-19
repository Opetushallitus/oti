(ns oti.endpoint.auth
  (:require [compojure.core :refer :all]
            [oti.boundary.ldap-access :as la]
            [clojure.pprint :as pp]))

(defn auth-endpoint [{:keys [ldap]}]
  (GET "/auth/cas" [username]
    {:status 200
     :body (with-out-str (pp/pprint (la/user-has-access? ldap username)))
     :headers {"Content-Type" "text/plain"}}))
