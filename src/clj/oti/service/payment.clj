(ns oti.service.payment
  (:require [oti.boundary.db-access :as dba]
            [oti.boundary.payment :as payment-util]
            [taoensso.timbre :refer [error info]]
            [org.httpkit.client :as http]
            [clojure.string :as str])
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

(defn confirm-payment! [config form-data]
  (when (process-response! config form-data dba/confirm-registration-and-payment!)
    :confirmed))

(defn cancel-payment! [config form-data]
  (when (process-response! config form-data dba/cancel-registration-and-payment!)
    :cancelled))

(defn- check-and-process-unpaid-payment! [{:keys [db vetuma-payment] :as config} {:keys [paym_call_id created order_number]} delete-limit-minutes]
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
                                                               (into {}))
            deletion-limit (-> (LocalDateTime/now) (.minusMinutes delete-limit-minutes))
            delete? (-> (.toLocalDateTime created) (.isBefore deletion-limit))]
        (cond
          (not= "SUCCESSFUL" STATUS)
          (error "Payment query response has error status. Response data:" payment-data)

          (not (payment-util/authentic-response? vetuma-payment payment-data))
          (error "Could not verify MAC for payment query response:" form-data)

          (= PAYM_STATUS "OK_VERIFIED")
          (confirm-payment! config payment-data)

          (= PAYM_STATUS "CANCELLED_OR_REJECTED")
          (do (dba/cancel-registration-and-unknown-payment! db order_number)
              :cancelled)

          delete?
          (do (dba/cancel-registration-and-unknown-payment! db order_number)
              :expired)

          :else
          (do (info "Payment" order_number "remains unverified, will check again")
              :unpaid)))
      (error "Querying for payment" paym_call_id "resulted in HTTP error status" status))))

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
