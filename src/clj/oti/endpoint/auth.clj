(ns oti.endpoint.auth
  (:require [compojure.core :refer :all]
            [oti.boundary.ldap-access :as la]
            [clojure.pprint :as pp]
            [environ.core :refer [env]]
            [oti.util.cas-client :as cas]
            [ring.util.response :as resp]
            [taoensso.timbre :refer [info error]]
            [clojure.string :as str]
            [oti.util.auth :as auth]))

(defn- cas-login [cas-config {:keys [oti-login-success-uri]} ldap ticket]
  (if #_(:dev? env) false
    "DEVELOPER"
    (do
      (info "validating ticket" ticket)
      (when-let [username (cas/username-from-valid-service-ticket cas-config oti-login-success-uri ticket)]
        (when (la/user-has-access? ldap username)
          (auth/login ticket)
          username)))))

(defn- redirect-to-logged-out-page [{:keys [opintopolku-login-uri oti-login-success-uri]}]
  (resp/redirect (str opintopolku-login-uri oti-login-success-uri)))

(defn- login [cas-config auth-config ldap ticket]
  (try
    (if ticket
      (if-let [username (cas-login cas-config auth-config ldap ticket)]
        (do
          (info "username" username "logged in")
          (-> (resp/redirect "/test-auth")
              (assoc :session {:identity {:username username :ticket ticket}})))
        (redirect-to-logged-out-page auth-config))
      (redirect-to-logged-out-page auth-config))
    (catch Exception e
      (error "Error in login ticket handling" e)
      (redirect-to-logged-out-page auth-config))))

(defn- logout [{:keys [opintopolku-logout-uri oti-login-success-uri]} session]
  (info "username" (-> session :identity :username) "logged out")
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
  (routes
    (context "/auth" []
      (GET "/cas" [ticket]
        (login cas authentication ldap ticket))
      (POST "/cas" [logoutRequest]
        (cas-initiated-logout logoutRequest))
      (GET "/logout" {session :session}
        (logout authentication session)))
    (-> (GET "/test-auth" {session :session}
          {:status 200
           :body (with-out-str (pp/pprint session))
           :headers {"Content-Type" "text/plain"}})
        (auth/wrap-authorization))))
