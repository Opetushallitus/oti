(ns oti.endpoint.auth
  (:require [compojure.core :refer :all]
            [oti.boundary.ldap-access :as la]
            [clojure.pprint :as pp]
            [environ.core :refer [env]]
            [oti.component.cas :as cas]
            [ring.util.response :as resp]
            [taoensso.timbre :refer [info error]]
            [clojure.string :as str]
            [oti.util.auth :as auth]))

(defn- redirect-to-cas-login-page [{:keys [opintopolku-login-uri oti-login-success-uri]}]
  (resp/redirect (str opintopolku-login-uri oti-login-success-uri)))

(defn- cas-login [cas-config {:keys [oti-login-success-uri] :as auth-config} ldap ticket]
  (info "validating ticket" ticket)
  (if-let [username (cas/username-from-valid-service-ticket cas-config oti-login-success-uri ticket)]
    (if (la/user-has-access? ldap username)
      (do
        (auth/login ticket)
        (info "username" username "logged in")
        (-> (resp/redirect "/oti/virkailija")
            (assoc :session {:identity {:username username :ticket ticket}})))
      (do
        (info "username" username "tried to log in but does not have the correct role in LDAP")
        {:status 403 :body "Ei käyttöoikeuksia palveluun" :headers {"Content-Type" "text/plain; charset=utf-8"}}))
    (redirect-to-cas-login-page auth-config)))

(defn- login [cas-config auth-config ldap ticket]
  (try
    (if ticket
      (cas-login cas-config auth-config ldap ticket)
      (redirect-to-cas-login-page auth-config))
    (catch Exception e
      (error "Error in login ticket handling" e)
      (redirect-to-cas-login-page auth-config))))

(defn- logout [{:keys [opintopolku-logout-uri oti-login-success-uri]} session]
  (info "username" (-> session :identity :username) "logged out")
  (println (-> session :identity :ticket))
  (auth/logout (-> session :identity :ticket))
  (-> (resp/redirect (str opintopolku-logout-uri oti-login-success-uri))
      (assoc :session {:identity nil})))

(defn cas-initiated-logout [logout-request]
  (info "cas-initiated logout")
  (let [ticket (cas/parse-ticket-from-logout-request logout-request)]
    (info "logging out ticket" ticket)
    (if (str/blank? ticket)
      (error "Could not parse ticket from CAS request" logout-request)
      (auth/logout ticket))
    {:status 200 :body ""}))

(defn auth-endpoint [{:keys [ldap authentication cas]}]
  (context "/oti/auth" []
    (GET "/cas" [ticket]
      (login cas authentication ldap ticket))
    (POST "/cas" [logoutRequest]
      (cas-initiated-logout logoutRequest))
    (GET "/logout" {session :session}
      (logout authentication session))))
