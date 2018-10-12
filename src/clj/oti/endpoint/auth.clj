(ns oti.endpoint.auth
  (:require [compojure.core :refer :all]
            [oti.component.url-helper :refer [url]]
            [oti.boundary.api-client-access :as api]
            [environ.core :refer [env]]
            [oti.component.cas :as cas]
            [ring.util.response :as resp]
            [clojure.tools.logging :refer [info error]]
            [clojure.string :as str]
            [oti.util.auth :as auth])
  (:import [java.net URLEncoder]))

(def oti-cas-path "/oti/auth/cas")
(def user-right-name "ROLE_APP_OTI_CRUD")

(defn- redirect-to-cas-login-page [opintopolku-login-uri login-callback]
  (resp/redirect (str opintopolku-login-uri (URLEncoder/encode login-callback "UTF-8"))))

(defn- fetch-authorized-user [api-client username]
  (when-let [user (api/get-user-details api-client username)]
    (when (->> (map :authority (:authorities user))
               (some #(str/includes? % user-right-name)))
      (let [person (api/get-person-by-id api-client (:username user))]
        {:username username
         :oid (:oidHenkilo person)
         :given-name (:etunimet person)
         :surname (:sukunimi person)}))))

(defn- cas-login [cas-config login-callback api-client ticket path]
  (info "validating ticket" ticket)
  (if-let [username (cas/username-from-valid-service-ticket cas-config login-callback ticket)]
    (if-let [user (fetch-authorized-user api-client username)]
      (do
        (auth/login ticket)
        (info "user" user "logged in")
        (-> (resp/redirect (or path "/oti/virkailija"))
            (assoc :session {:identity (assoc user :ticket ticket)})))
      (do
        (info "username" username "tried to log in but does not have the correct role in kayttooikeus-service")
        {:status 403 :body "Ei käyttöoikeuksia palveluun" :headers {"Content-Type" "text/plain; charset=utf-8"}}))
    (do
      (error "CAS did not validate our service ticket" ticket)
      ; This might be because the user provided an invalid ticket, but also because of some other error, so we don't
      ; want to cause an infinite redirect loop by redirecting the user back to the CAS from here.
      {:status 500 :body "Pääsyoikeuksien tarkistus epäonnistui" :headers {"Content-Type" "text/plain; charset=utf-8"}})))

(defn- login [cas-config url-helper api-client ticket path]
  (let [login-callback (str (url url-helper "oti.cas-auth") (when path (str "?path=" path)))]
    (try
      (if ticket
        (cas-login cas-config login-callback api-client ticket path)
        (redirect-to-cas-login-page (url url-helper "cas.login-uri") login-callback))
      (catch Exception e
        (error "Error in login ticket handling" e)
        (redirect-to-cas-login-page (url url-helper "cas.login-uri") login-callback)))))

(defn- logout [url-helper session]
  (info "username" (-> session :identity :username) "logged out")
  (auth/logout (-> session :identity :ticket))
  (-> (resp/redirect (url url-helper "cas.logout-uri" (url url-helper "oti.cas-auth")))
      (assoc :session {:identity nil})))

(defn cas-initiated-logout [logout-request]
  (info "cas-initiated logout")
  (let [ticket (cas/parse-ticket-from-logout-request logout-request)]
    (info "logging out ticket" ticket)
    (if (str/blank? ticket)
      (error "Could not parse ticket from CAS request" logout-request)
      (auth/logout ticket))
    {:status 200 :body ""}))

(defn auth-endpoint [{:keys [api-client cas url-helper]}]
  (context "/oti/auth" []
    (GET "/cas" [ticket path]
      (login cas url-helper api-client ticket path))
    (POST "/cas" [logoutRequest]
      (cas-initiated-logout logoutRequest))
    (GET "/logout" {session :session}
      (logout url-helper session))))
