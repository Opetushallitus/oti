(ns oti.service.registration
  (:require [clojure.spec :as s]
            [clojure.string :as str]
            [oti.boundary.db-access :as dba]
            [oti.component.localisation :as loc]
            [oti.spec :as os]
            [taoensso.timbre :refer [error info]]
            [meta-merge.core :refer [meta-merge]]
            [oti.boundary.api-client-access :as api]
            [oti.exam-rules :as rules]
            [oti.boundary.payment :as payment-util]
            [oti.component.localisation :refer [t]])
  (:import [java.time LocalDateTime]))

(defn- store-person-to-service! [api-client {:keys [etunimet sukunimi hetu]} preferred-name lang]
  (api/add-person! api-client
                   {:sukunimi sukunimi
                    :etunimet (str/join " " etunimet)
                    :kutsumanimi preferred-name
                    :hetu hetu
                    :henkiloTyyppi "OPPIJA"
                    :asiointiKieli {:kieliKoodi (name lang)}}))

(defn- registration-response
  ([status-code status text session]
    (registration-response status-code status text session nil nil))
  ([status-code status text session registration-id payment-data]
   (let [session-data {:registration-message text
                       :registration-status status
                       :registration-id registration-id}
         body (cond-> session-data
                      payment-data (assoc ::os/payment-form-data payment-data))]
     {:status status-code :body body :session (meta-merge session {:participant session-data})})))

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

(defn- payment-params [{:keys [db localisation]} external-user-id amount lang]
  (let [ref-number (-> (str/split external-user-id #"\.") last)
        order-suffix (dba/next-order-number! db)
        order-number (str "OTI" ref-number order-suffix)]
    #::os{:timestamp (LocalDateTime/now)
          :language-code lang
          :amount amount
          :reference-number (bigdec ref-number)
          :order-number order-number
          :app-name (loc/t localisation lang "vetuma-app-name")
          :msg (loc/t localisation lang "payment-name")
          :payment-id order-number}))

(defn- payment-params->db-payment [{::os/keys [timestamp amount reference-number order-number payment-id] :as params} type]
  (when params
    {:created timestamp,
     :type (if (= type :full) "FULL" "PARTIAL")
     :amount amount
     :reference reference-number
     :order-number order-number
     :payment-id payment-id}))

(defn- db-payment->payment-params [localisation {:keys [created amount reference order_number paym_call_id]} ui-lang]
  #::os{:timestamp (.toLocalDateTime created)
        :language-code (keyword ui-lang)
        :amount amount
        :reference-number reference
        :order-number order_number
        :app-name (loc/t localisation ui-lang "vetuma-app-name")
        :msg (loc/t localisation ui-lang "payment-name")
        :payment-id paym_call_id})

(defn payment-amounts [{:keys [payments db]} external-user-id]
  (let [paid? (and external-user-id (pos? (dba/valid-full-payments-for-user db external-user-id)))]
    {:full (if paid? 0 (-> payments :amounts :full))
     :retry (-> payments :amounts :retry)}))

(defn register! [{:keys [db api-client vetuma-payment localisation] :as config}
                 {old-session :session {:keys [registration-data ui-lang]} :params}]
  (let [conformed (s/conform ::os/registration registration-data)
        lang (::os/language-code conformed)
        participant-data (-> old-session :participant)
        external-user-id (or (:external-user-id participant-data)
                             (:oidHenkilo (api/get-person-by-hetu api-client (:hetu participant-data)))
                             (store-person-to-service! api-client participant-data (::os/preferred-name conformed) lang))
        session (assoc-in old-session [:participant :external-user-id] external-user-id)
        valid?  (and (not (s/invalid? conformed))
                     external-user-id
                     (valid-registration? config external-user-id conformed))
        price-type (rules/price-type-for-registration conformed)
        amount (price-type (payment-amounts config external-user-id))
        reg-state (if (zero? amount) "OK" "INCOMPLETE")]
    (if valid?
      (try
        (let [pmt (when (pos? amount) (payment-params config external-user-id amount (keyword ui-lang)))
              db-pmt (payment-params->db-payment pmt price-type)
              payment-form-data (when pmt (payment-util/form-data-for-payment vetuma-payment pmt))
              msg-key (if (pos? amount) "registration-payment-pending" "registration-complete")
              status (if (pos? amount) :pending :success)
              registration-id (dba/register! db conformed external-user-id reg-state db-pmt)]
          (registration-response 200 status (t localisation ui-lang msg-key) session registration-id payment-form-data))
        (catch Throwable t
          (error "Error inserting registration")
          (error t)
          (registration-response 500 :error (t localisation ui-lang "registration-unknown-error") session)))
      (do
        (error "Invalid registration data. Valid selection:" (valid-registration? config external-user-id conformed)
               "| Spec errors:" (s/explain-data ::os/registration registration-data))
        (registration-response 400 :error (t localisation (or ui-lang :fi) "registration-invalid-data") session)))))

(defn payment-data-for-retry [{:keys [db localisation vetuma-payment]}
                              {{:keys [registration-id registration-status]} :participant :as session}
                              ui-lang]
  (if (= :pending registration-status)
    (if-let [db-pmt (dba/unpaid-payment-by-registration db registration-id)]
      (->> (db-payment->payment-params localisation db-pmt ui-lang)
           (payment-util/form-data-for-payment vetuma-payment)
           (registration-response 200 :pending (t localisation ui-lang "registration-payment-pending") session registration-id))
      (do
        (error "Tried to retry payment for registration" registration-id "but matching payment was not found")
        (registration-response 404 :error (t localisation ui-lang "registration-payment-expired") session)))
    (do
      (error "Tried to retry payment for registration" registration-id "but it's status is not pending")
      (registration-response 400 :error (t localisation ui-lang "registration-unknown-error") session))))