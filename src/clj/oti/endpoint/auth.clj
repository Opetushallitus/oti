(ns oti.endpoint.auth
  (:require [compojure.core :refer :all]
            [oti.boundary.ldap-access :as la]
            [clojure.pprint :as pp]
            [environ.core :refer [env]]
            [oti.component.cas :as cas]
            [ring.util.response :as resp]
            [taoensso.timbre :refer [info error]]
            [clojure.string :as str]
            [oti.util.auth :as auth])
  (:import [java.net URLEncoder]))

(defn- redirect-to-cas-login-page [opintopolku-login-uri login-callback]
  (resp/redirect (str opintopolku-login-uri (URLEncoder/encode login-callback "UTF-8"))))

(defn- cas-login [cas-config login-callback ldap ticket path]
  (info "validating ticket" ticket)
  (println login-callback)
  (if-let [username (cas/username-from-valid-service-ticket cas-config login-callback ticket)]
    (if (la/user-has-access? ldap username)
      (do
        (auth/login ticket)
        (info "username" username "logged in")
        (-> (resp/redirect (or path "/oti/virkailija"))
            (assoc :session {:identity {:username username :ticket ticket}})))
      (do
        (info "username" username "tried to log in but does not have the correct role in LDAP")
        {:status 403 :body "Ei käyttöoikeuksia palveluun" :headers {"Content-Type" "text/plain; charset=utf-8"}}))
    (do
      (error "CAS did not validate our service ticket" ticket)
      ; This might be because the user provided an invalid ticket, but also because of some other error, so we don't
      ; want to cause an infinite redirect loop by redirecting the user back to the CAS from here.
      {:status 500 :body "Pääsyoikeuksien tarkistus epäonnistui" :headers {"Content-Type" "text/plain; charset=utf-8"}})))

(defn- login [cas-config {:keys [oti-login-success-uri opintopolku-login-uri]} ldap ticket path]
  (let [login-callback (str oti-login-success-uri (when path (str "?path=" path)))]
    (try
      (if ticket
        (cas-login cas-config login-callback ldap ticket path)
        (redirect-to-cas-login-page opintopolku-login-uri login-callback))
      (catch Exception e
        (error "Error in login ticket handling" e)
        (redirect-to-cas-login-page opintopolku-login-uri login-callback)))))

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
  (context "/oti/auth" []
    (GET "/cas" [ticket path]
      (login cas authentication ldap ticket path))
    (POST "/cas" [logoutRequest]
      (cas-initiated-logout logoutRequest))
    (GET "/logout" {session :session}
      (logout authentication session))))
