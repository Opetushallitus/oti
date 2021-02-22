(ns oti.endpoint.participant
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [resource-response content-type redirect response not-found]]
            [clojure.string :as str]
            [oti.boundary.db-access :as dba]
            [oti.component.localisation :as loc]
            [oti.routing :as routing]
            [oti.util.coercion :as c]
            [clojure.java.io :as io]
            [org.httpkit.client :as http]
            [oti.util.http :refer [http-default-headers]]
            [clojure.tools.logging :refer [error info]]
            [meta-merge.core :refer [meta-merge]]
            [oti.boundary.api-client-access :as api]
            [oti.service.registration :as registration]
            [oti.service.payment :as payment]
            [oti.spec :as os]
            [clojure.spec.alpha :as s]
            [oti.util.request :as req]
            [oti.component.url-helper :refer [url]]
            [oti.db-states :as states]
            [clojure.data.xml :refer :all]))

(def check-digits {0  0 16 "H"
                   1  1 17 "J"
                   2  2 18 "K"
                   3  3 19 "L"
                   4  4 20 "M"
                   5  5 21 "N"
                   6  6 22 "P"
                   7  7 23 "R"
                   8  8 24 "S"
                   9  9 25 "T"
                   10 "A" 26 "U"
                   11 "B" 27 "V"
                   12 "C" 28 "W"
                   13 "D" 29 "X"
                   14 "E" 30 "Y"
                   15 "F"})

(defn- random-hetu []
  (let [fmt "%1$02d"
        d (format fmt (inc (rand-int 30)))
        m (format fmt (inc (rand-int 12)))
        y (format fmt (+ (rand-int 30) 60))
        serial (+ 900 (rand-int 98))
        full (Integer/parseInt (str d m y serial))
        check-digit (get check-digits (mod full 31))]
    (str d m y "-" serial check-digit)))

(defn- random-name []
  (->> (repeatedly #(rand-nth "aefhijklmnoprstuvy"))
       (take 10)
       (apply str)
       (str/capitalize)))

(defn xml->map [x]
  (hash-map
   (:tag x)
   (map
    #(if (= (type %) clojure.data.xml.Element)
      (xml->map %)
      %)
    (:content x))))

(defn find-value
  "Find value in map"
  [m init-ks]
  (loop [c (get m (first init-ks)) ks (rest init-ks)]
    (if (empty? ks)
      c
      (let [k (first ks)]
        (recur
          (if (map? c)
            (get c k)
            (some #(get % k) c))
          (rest ks))))))

;; TODO this needs a better implementation? The xml parsing could be handled better...
(defn process-attributes [attributes]
  (into {} (for [m attributes
                 [k v] m]
             [k (clojure.string/join " " v)])))

(defn- using-valtuudet? [response]
  (boolean (or (find-value
                response
                [:serviceResponse :authenticationSuccess
                                  :attributes :impersonatorNationalIdentificationNumber])
               (find-value
                response
                [:serviceResponse :authenticationSuccess
                                  :attributes :impersonatorDisplayName]))))

(defn- wrap-authentication [handler]
  (fn [request]
    (if (-> request :session :participant :hetu)
      (handler request)
      {:status 401 :body {:error "Identification required"}})))

(defn- update-participant-session [session reg-status msg-key]
  (let [data (cond-> {:registration-status reg-status
                      :registration-message msg-key})]
    (meta-merge session {:participant data})))

(defn- session-participant [{:keys [db] :as config} {:keys [participant] :as session}]
  (let [{:keys [registration-status registration-id external-user-id]} participant]
    (if (and (= :pending registration-status) external-user-id registration-id)
      (condp = (dba/registration-state-by-id db registration-id)
        states/reg-ok (update-participant-session session :success "registration-complete")
        states/reg-cancelled (update-participant-session session :error "registration-payment-cancel")
        (update-participant-session session :error "registration-payment-error"))
      ; Other or missing registration status, just return the session as is
      session)))

(defn authenticate [{:keys [db api-client url-helper global-config] :as config} callback user-hetu automatic-address]
  (if (not= (:env global-config) "prod")
    (if (and (not (str/blank? callback)) (or
        (= (:env global-config) "dev")
        (str/starts-with? callback (str (:oti-host url-helper) "/"))))
      (let [hetu (if (s/valid? ::os/hetu user-hetu) user-hetu (random-hetu))
            {:keys [oidHenkilo etunimet sukunimi kutsumanimi]} (api/get-person-by-hetu api-client hetu)
            {:keys [email id]} (when oidHenkilo (first (dba/participant-by-ext-id db oidHenkilo)))
            address (when (= automatic-address "true")
                      #::os{:registration-post-office    "Helsinki"
                            :registration-zip            "00100"
                            :registration-street-address "Testikatu 1"})]
        (-> (redirect callback)
            (assoc :session {:participant (merge
                                           {:etunimet         (if etunimet
                                                                (str/split etunimet #" ")
                                                                [(random-name) (random-name)])
                                            :sukunimi         (or sukunimi (random-name))
                                            :kutsumanimi      kutsumanimi
                                            :hetu             hetu
                                            :external-user-id oidHenkilo
                                            :id               id
                                            ::os/email        email}
                                           address)})))
      {:status 400 :body {:error "Missing or invalid callback uri"}})
    {:status 403 :body {:error "Forbidden"}}))

(defn- participant-base-url [{:keys [url-helper]} lang]
  (let [url-key (if (and lang (= "sv" lang))
                  "oti.participant.sv"
                  "oti.participant.fi")]
    (info "participant base url:" (url url-helper url-key))
    (url url-helper url-key)))

(defn- respond-with-failed-authentication [cas-ticket-validation-result]
  (do (info "Ticket validation failed: %s"
                 (:error cas-ticket-validation-result))
    ({:status 403 :body {:error "Ticket validation failed"}})))


(defn- populate-user-data-and-redirect-to-service [{:keys [db api-client] :as config} session {:keys [user-data]}]
  (let [{:keys [VakinainenKotimainenLahiosoiteS
                VakinainenKotimainenLahiosoitePostitoimipaikkaS
                vakinainenkotimainenlahiosoitepostinumero
                sn
                firstName
                nationalIdentificationNumber
                ]} user-data
        {:keys [oidHenkilo etunimet sukunimi kutsumanimi]} (api/get-person-by-hetu api-client nationalIdentificationNumber)
        {:keys [email id]} (when oidHenkilo (first (dba/participant-by-ext-id db oidHenkilo)))
        address #::os{:registration-post-office    VakinainenKotimainenLahiosoitePostitoimipaikkaS
                      :registration-zip            vakinainenkotimainenlahiosoitepostinumero
                      :registration-street-address VakinainenKotimainenLahiosoiteS}
        redirect-url (participant-base-url config (:original-lang session))]
    (if (and sn firstName (s/valid? ::os/hetu nationalIdentificationNumber))
      (-> (redirect redirect-url :see-other)
          (assoc :session {:participant (merge
                                          {:etunimet         (if etunimet
                                                               (str/split etunimet #" ")
                                                               (str/split firstName #" "))
                                           :sukunimi         (or sukunimi sn)
                                           :kutsumanimi      kutsumanimi
                                           :hetu             nationalIdentificationNumber
                                           :external-user-id oidHenkilo
                                           :id               id
                                           ::os/email        email}
                                          address)}))
      {:status 400 :body {:error "Missing critical authentication data"}})))

(defn- cas-oppija-ticket-validation  [{:keys [url-helper]} ticket session]
  (let [uri (url url-helper "cas-oppija.validate-service")
        {:keys [status body]} @(http/get uri {:query-params {:ticket ticket :service (url url-helper "oti.participant.authenticate.baseurl")}
        :headers (http-default-headers)})]
    (when (= status 200)
      body
    )))

(defn- redirect-to-cas-oppija-login  [{:keys [url-helper]} lang]
  (-> (redirect (str (url url-helper "cas-oppija.login") "?service=" (url url-helper "oti.participant.authenticate.baseurl")) :see-other)
      (assoc :session {:original-lang lang})))

(defn- convert-oppija-cas-response-data [xml-data]
  (let [response (xml->map xml-data)
        success (some?
                 (find-value response
                             [:serviceResponse :authenticationSuccess]))
        using-valtuudet (using-valtuudet? response)]
    (info "Response: %s" response)
    {:success? success
     :error (when-not success
              (first
               (find-value
                response
                [:serviceResponse :authenticationFailure])))
     :user-oid (if-not using-valtuudet
                 (first
                  (find-value
                   response
                   [:serviceResponse :authenticationSuccess
                                     :attributes :personOid]))
                 (first
                  (find-value
                   response
                   [:serviceResponse :authenticationSuccess
                                     :attributes :impersonatorPersonOid])))
     :usingValtuudet using-valtuudet
     :user-data (process-attributes (find-value
                                                response
                                                [:serviceResponse :authenticationSuccess :attributes]))
     }))

(defn validate-oppija-ticket [config ticket lang session]
  (info "validating cas-oppija ticket" ticket)
  (let [responsebody (cas-oppija-ticket-validation config ticket session)]
    (info "validation response" responsebody)
    (let [xml-data (parse-str responsebody)]
      (convert-oppija-cas-response-data xml-data))))

(defn- init-authentication [config session ticket lang]
  (if ticket
    (let [cas-ticket-validation-result (validate-oppija-ticket config ticket lang session)]
      (if (:success? cas-ticket-validation-result)
        (populate-user-data-and-redirect-to-service config session cas-ticket-validation-result)
        (respond-with-failed-authentication cas-ticket-validation-result)))
    (redirect-to-cas-oppija-login config lang)))

(defn- abort [{:keys [url-helper] :as config} lang]
      (-> (redirect (str (url url-helper "cas-oppija.logout") "?service=" (participant-base-url config lang)) :see-other)
          (assoc :session nil)))

(defn- translations [{:keys [localisation]} lang]
  (if (str/blank? lang)
    {:status 400 :body {:error "Missing lang parameter"}}
    (response (loc/translations-by-lang localisation lang))))

(defn- refresh-translations [{:keys [localisation]}]
  (if-let [new-translations (loc/refresh-translations localisation)]
    (response new-translations)
    {:status 500 :body {:error "Refreshing translations failed"}}))

(defn- exam-sessions [{:keys [db]}]
  (let [sessions (->> (dba/published-exam-sessions-with-space-left db)
                      (map c/convert-session-row))]
    (response sessions)))

(defn- participant-data [config session]
  (let [updated-session (session-participant config session)]
    {:status 200 :body (:participant updated-session) :session updated-session}))

(defn- registration-options [{:keys [db] :as config} {{:keys [id external-user-id]} :participant}]
  ; user ids are nil at this stage if the user is new
  (->> {:sections (dba/sections-and-modules-available-for-user db external-user-id)
        :payments (registration/payment-amounts config id)}
       (response)))

(defn participant-endpoint [config]
  (routes
    (GET "/oti/abort" [lang] (abort config lang))
    (context routing/participant-api-root {session :session}
      (GET "/translations"         [lang] (translations config lang))
      (GET "/translations/refresh" []     (refresh-translations config))
      (GET "/authenticate"         [lang ticket] (init-authentication config session ticket lang))
      ;; FIXME: This is a dummy route
      (GET "/dummy-authenticate"   [callback hetu automatic-address] (authenticate config callback hetu automatic-address))
      (GET "/exam-sessions"        []         (exam-sessions config))
      (-> (context "/authenticated" {session :session}
           (GET "/participant-data"     []      (participant-data config session))
           (GET "/registration-options" []      (registration-options config session))
           (GET "/payment-form-data"    request (registration/payment-data-for-retry config request))
           (POST "/register"            request (registration/register! config request)))
          (wrap-routes wrap-authentication)
          (wrap-routes req/wrap-disable-cache)))))
