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
  (if-let [user (api/get-person-by-id api-client ext-reference-id)]
    (:kieliKoodi (:asiointiKieli user) )
      "fi" ))

(defn get-participant-language-by-order-number [{:keys [api-client] :as config} order-number]
  (let [participants (user-data/participant-data config order-number "fi")
        ext-participant-id (:oidHenkilo participants)]
    (if (string? ext-participant-id) (get-participant api-client ext-participant-id) "fi")))

(defn confirm-payment! [config form-data lang]
  (when (process-response! config form-data dba/confirm-registration-and-payment!)
    (audit/log :app :admin
               :on :payment
               :op :update
               :before {:order-number (:ORDER_NUMBER form-data)
                        :state states/pmt-unpaid}
               :after {:order-number (:ORDER_NUMBER form-data)
                       :state states/pmt-ok}
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
               :before {:order-number (:ORDER_NUMBER form-data)
                        :state states/pmt-unpaid}
               :after {:order-number (:ORDER_NUMBER form-data)
                       :state states/pmt-error}
               :msg "Payment has been cancelled.")
    :cancelled))

(defn- cancel-payment-by-order-number! [db {:keys [state order_number]}]
  (audit/log :app :admin
             :on :payment
             :op :update
             :before {:order-number order_number
                      :state state}
             :after {:order-number order_number
                     :state states/pmt-error}
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
             :before {:order-number order-number
                      :state states/pmt-error}
             :after {:order-number order-number
                     :state states/pmt-ok}
             :msg "Payment and related registration has been approved.")
  (when (= 1 (dba/confirm-registration-and-payment! db {:order-number order-number}))
    (some->> (user-data/participant-data config order-number user-lang)
             (registration/send-confirmation-email! config user-lang))
    true))
