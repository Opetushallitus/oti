(ns oti.service.registration
  (:require [clojure.spec :as s]
            [clojure.string :as str]
            [oti.boundary.db-access :as dba]
            [oti.component.localisation :as localisation]
            [oti.spec :as os]
            [taoensso.timbre :refer [error info]]
            [meta-merge.core :refer [meta-merge]]
            [oti.boundary.api-client-access :as api]
            [oti.exam-rules :as rules]))

(defn- store-person-to-service! [api-client {:keys [etunimet sukunimi hetu]} preferred-name lang]
  (api/add-person! api-client
                   {:sukunimi sukunimi
                    :etunimet (str/join " " etunimet)
                    :kutsumanimi preferred-name
                    :hetu hetu
                    :henkiloTyyppi "OPPIJA"
                    :asiointiKieli {:kieliKoodi (name lang)}}))

(defn- registration-response [status-code text session & [payment-data]]
  (let [body (cond-> {:registration-message text
                      :registration-status (if (= 200 status-code) :success :error)}
                     payment-data (assoc ::os/payment-form-data payment-data))]
    {:status status-code :body body :session (meta-merge session {:participant body})}))

(defn- valid-module-registration? [{:keys [modules]} reg-modules reg-type]
  (every?
    (fn [reg-m]
      (some #(and (= (:id reg-m) (:id %))
                  (not (:accepted %))
                  (if (= :retry reg-type)
                    (:previously-attempted? %)
                    (not (:previously-attempted? %))))
            modules))
    reg-modules))

(defn- valid-registration? [{:keys [db]} external-user-id {::os/keys [sections]}]
  (let [avail-sections (dba/sections-and-modules-available-for-user db external-user-id)]
    (every? (fn [[id {::os/keys [retry? retry-modules accredit-modules]}]]
              (when-let [sect (first (filter #(= id (:id %)) avail-sections))]
                (and
                  (not (:accepted? sect))
                  (or (not retry?) (:previously-attempted? sect))
                  (valid-module-registration? sect retry-modules :retry)
                  (valid-module-registration? sect accredit-modules :accredit))))
            sections)))

(defn payment-amounts [{:keys [payments db]} external-user-id]
  (let [paid? (and external-user-id (pos? (dba/valid-full-payments-for-user db external-user-id)))]
    {:full (if paid? 0 (-> payments :amounts :full))
     :retry (-> payments :amounts :retry)}))

(defn register [{:keys [db api-client] :as config} {session :session registration-data :params}]
  (let [conformed (s/conform ::os/registration registration-data)
        lang (::os/language-code conformed)
        participant-data (-> session :participant)
        external-user-id (or (:external-user-id participant-data)
                             (:oidHenkilo (api/get-person-by-hetu api-client (:hetu participant-data)))
                             (store-person-to-service! api-client participant-data (::os/preferred-name conformed) lang))
        valid?  (and (not (s/invalid? conformed))
                     external-user-id
                     (valid-registration? config external-user-id conformed))
        price-type (rules/price-type-for-registration conformed)
        amount (price-type (payment-amounts config external-user-id))
        reg-state (if (zero? amount) "OK" "INCOMPLETE")]
    (if valid?
      (try
        (dba/register! db conformed external-user-id reg-state)
        (when (pos? amount)
          )
        (registration-response 200 "Ilmoittautumisesi on rekisteröity" session)
        (catch Throwable t
          (error "Error inserting registration")
          (error t)
          (registration-response 500 "Ilmoittautumisessa tapahtui odottamaton virhe" session)))
      (do
        (error "Invalid registration data. Valid selection:" (valid-registration? config external-user-id conformed)
               "| Spec errors:" (s/explain-data ::os/registration registration-data))
        (registration-response 400 "Ilmoittautumistiedot olivat virheelliset" session)))))
