(ns oti.endpoint.auth-test
  (:require [clojure.test :refer :all]
            [oti.endpoint.auth :refer :all]
            [shrubbery.core :refer :all]
            [oti.component.cas]
            [oti.boundary.api-client-access]
            [oti.util.auth :as auth-util]
            [ring.middleware.defaults :refer [wrap-defaults]]
            [oti.component.url-helper :as uh]
            [com.stuartsierra.component :as component])
  (:import [oti.boundary.api_client_access ApiClientAccess]
           [oti.component.cas CasAccess]))

(def valid-user "virkailija")

(def invalid-user "guest")

(def valid-ticket "niceticket")

(def ticket-for-wrong-user "suspicious")

(def api-client-stub
  (reify ApiClientAccess
    (get-user-details [client username]
      (when (= username valid-user) {:username username
                                     :authorities [{:authority "ROLE_APP_OTI_CRUD"}]}))
    (get-person-by-id [client external-user-id]
      {:etunimet "Testi"
       :oidHenkilo "1.2.3.4"
       :kutsumanimi "Testi"
       :sukunimi "Testaaja"})))

(def cas-stub
  (reify CasAccess
    (username-from-valid-service-ticket [t service-uri ticket]
      (cond (= ticket valid-ticket) valid-user
            (= ticket ticket-for-wrong-user) invalid-user))))

(def url-helper (->> (uh/url-helper {:virkailija-host "itest-virkailija.oph.ware.fi"
                                     :oti-host "http://localhost:3000"
                                     :alb-host "https://itest-virkailija.oph.ware.fi"
                                     :tunnistus-host "tunnistus-testi.opintopolku.fi"
                                     :oppija-host "oppijatestipolku.fi"})
                     (component/start)))

(def path
  "/oti/virkailija/henkilot")

(def ip
  "127.0.0.1")

(def user-agent
  "IE6")

(deftest login-works-with-correct-user
  (is (= {:status 302
          :headers {"Location" "/oti/virkailija/henkilot"}
          :body ""
          :session {:identity {:username "virkailija"
                               :oid "1.2.3.4"
                               :given-name "Testi"
                               :surname "Testaaja"
                               :ip "127.0.0.1"
                               :user-agent "IE6"
                               :ticket "niceticket"}}}
         (#'oti.endpoint.auth/login cas-stub url-helper api-client-stub valid-ticket path ip user-agent))))

(deftest login-is-denied-for-invalid-ticket
  (is (= {:status 500, :body "Pääsyoikeuksien tarkistus epäonnistui", :headers {"Content-Type" "text/plain; charset=utf-8"}}
        (#'oti.endpoint.auth/login cas-stub url-helper api-client-stub "invalid" path ip user-agent))))

(deftest login-is-denied-for-user-without-correct-role
  (is (= 403
         (:status (#'oti.endpoint.auth/login cas-stub url-helper api-client-stub ticket-for-wrong-user path ip user-agent)))))

(deftest logout-works
  (let [request {:session {:identity {:ticket valid-ticket :username valid-user}}}]
    (#'oti.endpoint.auth/login cas-stub url-helper api-client-stub valid-ticket path ip user-agent)
    (is (auth-util/logged-in? request))
    (is (= {:status 302, :headers {"Location" "https://itest-virkailija.oph.ware.fi/cas/logout?service=http%3A%2F%2Flocalhost%3A3000%2Foti%2Fauth%2Fcas"}, :body "", :session {:identity nil}}
           (#'oti.endpoint.auth/logout url-helper (:session request))))
    (is (not (auth-util/logged-in? request)))))

(deftest cas-initiated-logout-works
  (let [logout-request "<samlp:LogoutRequest><saml:NameID>virkailija</saml:NameID><samlp:SessionIndex>niceticket</samlp:SessionIndex></samlp:LogoutRequest>"
        request {:session {:identity {:ticket valid-ticket}}}]
    (auth-util/login valid-ticket)
    (is (auth-util/logged-in? request))
    (is (= {:status 200 :body ""}
           (#'oti.endpoint.auth/cas-initiated-logout logout-request)))
    (is (not (auth-util/logged-in? request)))))
