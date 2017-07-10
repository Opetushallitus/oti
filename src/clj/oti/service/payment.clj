(ns oti.service.payment
  (:require [oti.boundary.db-access :as dba]
            [oti.boundary.payment :as payment-util]
            [clojure.tools.logging :refer [error info]]
            [org.httpkit.client :as http]
            [clojure.string :as str]
            [oti.service.user-data :as user-data]
            [oti.service.registration :as registration]
            [oti.util.logging.audit :as audit]
            [oti.db-states :as states])
  (:import [java.time LocalDateTime]
           [java.net URLEncoder URLDecoder]))

(defn- blank->nil [s]
  (when-not (str/blank? s)
    s))

(defn- process-response! [{:keys [vetuma-payment db]} form-data db-fn]
  (let [db-params {:order-number (:ORDNR form-data)
                   :pay-id (blank->nil (:PAYID form-data))
                   :archive-id (blank->nil (:PAID form-data))
                   :payment-method (blank->nil (:SO form-data))}]
    (if (and (payment-util/authentic-response? vetuma-payment form-data) (:order-number db-params))
      (do (db-fn db db-params)
          true)
      (error "Could not verify payment response message:" form-data))))

(defn- send-confirmation-email! [config {:keys [ORDNR LG] :as payment-data}]
  (if (not-any? str/blank? [ORDNR LG])
    (some->> (user-data/participant-data config ORDNR LG)
             (registration/send-confirmation-email! config LG))
    (error "Can't send confirmation email because of missing data. Payment data:" payment-data)))

(defn confirm-payment! [config form-data]
  (when (process-response! config form-data dba/confirm-registration-and-payment!)
    (audit/log :app :admin
               :who "SYSTEM"
               :on :payment
               :op :update
               :before {:order-number (:ORDNR form-data)
                        :state states/pmt-unpaid}
               :after {:order-number (:ORDNR form-data)
                       :state states/pmt-ok}
               :msg "Payment has been confirmed.")
    (try
      (send-confirmation-email! config form-data)
      (catch Throwable t
        (error t "Could not send confirmation email. Payment data:" form-data)))
    :confirmed))

(defn cancel-payment! [config form-data]
  (when (process-response! config form-data dba/cancel-registration-and-payment!)
    (audit/log :app :admin
               :who "SYSTEM"
               :on :payment
               :op :update
               :before {:order-number (:ORDNR form-data)
                        :state states/pmt-unpaid}
               :after {:order-number (:ORDNR form-data)
                       :state states/pmt-error}
               :msg "Payment has been cancelled.")
    :cancelled))

(defn- cancel-payment-by-order-number! [db {:keys [state order_number]}]
  (audit/log :app :admin
             :who "SYSTEM"
             :on :payment
             :op :update
             :before {:order-number order_number
                      :state state}
             :after {:order-number order_number
                     :state states/pmt-error}
             :msg "Payment has been cancelled.")
  (dba/cancel-registration-and-payment! db {:order-number order_number})
  :cancelled)

(defn- check-payment-from-vetuma! [{:keys [vetuma-payment]} paym_call_id]
  "Check payment from VETUMA.

  Returns status and data if VETUMA response STATUS is SUCCESSFULL.

  The returned status key can have the following values:
    :confirmed
      when VETUMA response PAYM_STATUS is OK_VERIFIED
    :cancelled
      when VETUMA response PAYM_STATUS = CANCELLED_OR_REJECTED
    :unknown
      when VETUMA response is neither of the above"
  (let [params {:timestamp (LocalDateTime/now)
                :payment-id paym_call_id}
        {:oti.spec/keys [uri payment-query-params]} (payment-util/payment-query-data vetuma-payment params)
        form-data (->> payment-query-params
                       (map (fn [[key value]]
                              (str (name key) "=" (URLEncoder/encode value "ISO-8859-1"))))
                       (str/join "&"))
        headers {"Content-Type" "application/x-www-form-urlencoded"}
        {:keys [status body]} @(http/request {:url uri
                                              :method :post
                                              :headers headers
                                              :body form-data
                                              :as :text})]
    (if (= 200 status)
      (let [{:keys [PAYM_STATUS STATUS] :as payment-data} (->> (str/split body #"\&")
                                                               (map (fn [kv]
                                                                      (let [[k v] (str/split kv #"=")]
                                                                        [(keyword k)
                                                                         (URLDecoder/decode (or v "") "ISO-8859-1")])))
                                                               (into {}))]
        (cond
          (not= "SUCCESSFUL" STATUS)
          (error "Payment query response has error status. Response data:" payment-data)

          (not (payment-util/authentic-response? vetuma-payment payment-data))
          (error "Could not verify MAC for payment query response:" form-data)

          (= PAYM_STATUS "OK_VERIFIED")
          {:status :confirmed :data payment-data}

          (= PAYM_STATUS "CANCELLED_OR_REJECTED")
          {:status :cancelled :data payment-data}

          :else
          {:status :unknown :data payment-data}))
      (error "Querying for payment" paym_call_id "resulted in HTTP error status" status))))

(defn- check-and-process-unpaid-payment! [{:keys [db] :as config} {:keys [paym_call_id created order_number] :as pmt} delete-limit-minutes]
  "Check unpaid payment status from VETUMA. Three kinds of statuses are handled differently:
  1. :confirmed - the payment is confirmed
  2. :cancelled - the payment is cancelled
  3. :unknown - the payment is cancelled if it's creation time is before deletion limit"
  (let [{:keys [status data]} (check-payment-from-vetuma! config paym_call_id)
        deletion-limit (-> (LocalDateTime/now) (.minusMinutes delete-limit-minutes))
        delete? (-> (.toLocalDateTime created) (.isBefore deletion-limit))]
    (condp = status
      :confirmed (confirm-payment! config data)
      :cancelled (cancel-payment-by-order-number! db pmt)
      :unknown   (if delete?
                   (do (cancel-payment-by-order-number! db pmt) :expired)
                   (do (info "Payment" order_number "remains unverified, will check again") :unpaid))
      (error "Payment" order_number "could not be checked"))))

(defn process-unpaid-payments-of-participant!
  "Returns a map of results of processing the participant's unpaid payments
  (which there really should only be max one). The map keys are registration id's and
  values the applied operation."
  [{:keys [db] :as config} external-user-id delete-limit-minutes]
  {:pre [external-user-id delete-limit-minutes]}
  (->> (dba/unpaid-payments-by-participant db external-user-id)
       (reduce
         (fn [results {:keys [registration_id] :as pmt}]
           (let [status (check-and-process-unpaid-payment! config pmt delete-limit-minutes)]
             (assoc results registration_id status)))
         {})
       doall))

(defn verify-or-delete-payments-of-participant! [config external-user-id]
  (process-unpaid-payments-of-participant! config external-user-id 0))

(defn check-and-process-unpaid-payments! [{:keys [db] :as config}]
  (let [payments (dba/unpaid-payments db)]
    (when (pos? (count payments))
      (info "Checking" (count payments) "unpaid payments")
      (doseq [pmt payments]
        (check-and-process-unpaid-payment! config pmt 30))
      (info "Payments checked."))))

(defn check-and-process-cc-payments! [{:keys [db] :as config}]
  (let [payments (dba/paid-credit-card-payments db)]
    (when (pos? (count payments))
      (info "Checking" (count payments) "credit card payments")
      (doseq [{:keys [paym_call_id order_number state]} payments]
        (when (= (:status (check-payment-from-vetuma! config paym_call_id)) :cancelled)
          (audit/log :app :admin
                     :who "SYSTEM"
                     :on :payment
                     :op :update
                     :before {:order-number order_number
                              :state state}
                     :after {:order-number order_number
                             :state states/pmt-error}
                     :msg "Payment has been cancelled and related registration set as incomplete.")
          (dba/cancel-payment-set-reg-incomplete! db {:order-number order_number})))
      (info "Payments checked."))))

(defn confirm-payment-manually! [{:keys [db] :as config} order-number user-lang {{authority :username} :identity}]
  {:pre [order-number user-lang]}
  (audit/log :app :admin
             :who authority
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
