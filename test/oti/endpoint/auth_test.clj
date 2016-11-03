(ns oti.endpoint.auth-test
  (:require [clojure.test :refer :all]
            [oti.endpoint.auth :refer :all]
            [shrubbery.core :refer :all]
            [oti.component.cas]
            [oti.boundary.ldap-access]
            [oti.util.auth :as auth-util]
            [ring.middleware.defaults :refer [wrap-defaults]])
  (:import [oti.boundary.ldap_access LdapAccess]
           [oti.component.cas CasAccess]))

(def valid-user "virkailija")

(def invalid-user "guest")

(def valid-ticket "niceticket")

(def ticket-for-wrong-user "suspicious")

(def ldap-stub
  (reify LdapAccess
    (fetch-authorized-user [t username]
      (when (= username valid-user) {:username username
                                     :given-name "Testi"
                                     :surname "Testaaja"}))))

(def cas-stub
  (reify CasAccess
    (username-from-valid-service-ticket [t service-uri ticket]
      (cond (= ticket valid-ticket) valid-user
            (= ticket ticket-for-wrong-user) invalid-user))))

(def authentication
  {:opintopolku-login-uri "login-uri?service="
   :opintopolku-logout-uri  "logout-uri?service="
   :oti-login-success-uri "/auth/cas"})

(def path
  "/oti/virkailija/henkilot")

(deftest login-works-with-correct-user
  (is (= {:status 302
          :headers {"Location" "/oti/virkailija/henkilot"}
          :body ""
          :session {:identity {:username "virkailija"
                               :given-name "Testi"
                               :surname "Testaaja"
                               :ticket "niceticket"}}}
         (#'oti.endpoint.auth/login cas-stub authentication ldap-stub valid-ticket path))))

(deftest login-is-denied-for-invalid-ticket
  (is (= {:status 500, :body "Pääsyoikeuksien tarkistus epäonnistui", :headers {"Content-Type" "text/plain; charset=utf-8"}}
        (#'oti.endpoint.auth/login cas-stub authentication ldap-stub "invalid" path))))

(deftest login-is-denied-for-user-without-correct-role
  (is (= 403
         (:status (#'oti.endpoint.auth/login cas-stub authentication ldap-stub ticket-for-wrong-user path)))))

(deftest logout-works
  (let [request {:session {:identity {:ticket valid-ticket :username valid-user}}}]
    (#'oti.endpoint.auth/login cas-stub authentication ldap-stub valid-ticket path)
    (is (auth-util/logged-in? request))
    (is (= {:status 302, :headers {"Location" "logout-uri?service=/auth/cas"}, :body "", :session {:identity nil}}
           (#'oti.endpoint.auth/logout authentication (:session request))))
    (is (not (auth-util/logged-in? request)))))

(deftest cas-initiated-logout-works
  (let [logout-request "<samlp:LogoutRequest><saml:NameID>virkailija</saml:NameID><samlp:SessionIndex>niceticket</samlp:SessionIndex></samlp:LogoutRequest>"
        request {:session {:identity {:ticket valid-ticket}}}]
    (auth-util/login valid-ticket)
    (is (auth-util/logged-in? request))
    (is (= {:status 200 :body ""}
           (#'oti.endpoint.auth/cas-initiated-logout logout-request)))
    (is (not (auth-util/logged-in? request)))))
