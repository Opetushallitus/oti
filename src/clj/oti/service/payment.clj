(ns oti.service.payment
  (:require [oti.boundary.db-access :as dba]
            [oti.boundary.payment :as payment-util]
            [clojure.tools.logging :refer [error info]]
            [org.httpkit.client :as http]
            [clojure.string :as str]
            [oti.service.user-data :as user-data]
            [oti.service.registration :as registration]
            [oti.util.logging.audit :as audit]
            [oti.db-states :as states]
            [oti.boundary.api-client-access :as api])
  (:import [java.time LocalDateTime]
           [java.net URLEncoder URLDecoder]))

(defn- blank->nil [s]
  (when-not (str/blank? s)
    s))


(defn- process-response! [{:keys [paytrail-payment db]} form-data db-fn]
  (let [db-params {:order-number (:ORDER_NUMBER form-data)
                   :pay-id (blank->nil (:PAYMENT_ID form-data))
                   :payment-method (blank->nil (:PAYMENT_METHOD form-data))}]
    (if (and (payment-util/authentic-response? paytrail-payment form-data) (:order-number db-params))
      (do (db-fn db db-params)
          true)
      (error "Could not verify payment response message:" form-data))))

(defn- send-confirmation-email! [config {:keys [ORDER_NUMBER] :as payment-data} lang]
  (if (not-any? str/blank? [ORDER_NUMBER])
    (some->> (user-data/participant-data config ORDER_NUMBER lang)
             (registration/send-confirmation-email! config lang))
    (error "Can't send confirmation email because of missing data. Payment data:" payment-data)))

(defn- get-participant [api-client ext-reference-id]
  (get-in (api/get-person-by-id api-client ext-reference-id) [:asiointiKieli :kieliKoodi]))

(defn get-participant-language-by-order-number [{:keys [db api-client]} order-number]
  (or
    (dba/language-code-by-order-number db order-number)
    (some->> (dba/participant-ext-reference-by-order-number db order-number)
             (get-participant api-client))
    "fi"))

(defn get-paid-payments [{:keys [db api-client]} start-date end-date query]
  (when-let [payments (seq (dba/paid-payments db start-date end-date))]
    (let [oids (map :ext_reference_id payments)
          users-by-oid (user-data/api-user-data-by-oid api-client oids)]
      (->> payments
           (map (fn [{:keys [ext_reference_id] :as payment}]
                  (-> (or
                        (get users-by-oid ext_reference_id)
                        {:sukunimi "" :etunimet "" :kutsumanimi "" :hetu ""})
                  (merge payment)
                  (update :created #(.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss") %))
                  (select-keys [:sukunimi :etunimet :kutsumanimi :hetu :amount :created]))))
           (filter (fn [payment-and-user]
                     (or
                       (str/blank? query)
                       (str/includes? (get payment-and-user :etunimet) query)
                       (str/includes? (get payment-and-user :sukunimi) query))))))))

(defn confirm-payment! [config form-data lang]
  (when (process-response! config form-data dba/confirm-registration-and-payment!)
    (audit/log :app :admin
               :on :payment
               :op :update
               :id (:ORDER_NUMBER form-data)
               :before {:state states/pmt-unpaid}
               :after {:state states/pmt-ok}
               :msg "Payment has been confirmed.")
    (try
      (send-confirmation-email! config form-data lang)
      (catch Throwable t
        (error t "Could not send confirmation email. Payment data:" form-data)))
    :confirmed))

(defn cancel-payment! [config form-data]
  (when (process-response! config form-data dba/cancel-registration-and-payment!)
    (audit/log :app :admin
               :on :payment
               :op :update
               :id (:ORDER_NUMBER form-data)
               :before {:state states/pmt-unpaid}
               :after {:state states/pmt-error}
               :msg "Payment has been cancelled.")
    :cancelled))

(defn- cancel-payment-by-order-number! [db {:keys [state order_number]}]
  (audit/log :app :admin
             :on :payment
             :op :update
             :id order_number
             :before {:state state}
             :after {:state states/pmt-error}
             :msg "Payment has been cancelled.")
  (dba/cancel-registration-and-payment! db {:order-number order_number})
  :cancelled)

(defn confirm-payment-manually! [{:keys [db] :as config} order-number user-lang session]
  {:pre [order-number user-lang]}
  (audit/log :app :admin
             :who (get-in session [:identity :oid])
             :ip (get-in session [:identity :ip])
             :user-agent (get-in session [:identity :user-agent])
             :on :payment
             :op :update
             :id order-number
             :before {:state states/pmt-error}
             :after {:state states/pmt-ok}
             :msg "Payment and related registration has been approved.")
  (when (= 1 (dba/confirm-registration-and-payment! db {:order-number order-number}))
    (some->> (user-data/participant-data config order-number user-lang)
             (registration/send-confirmation-email! config user-lang))
    true))

(defn cancel-obsolete-payments! [db]
  (info "Cancelled obsolete payments" (dba/cancel-obsolete-registrations-and-payments! db)))
