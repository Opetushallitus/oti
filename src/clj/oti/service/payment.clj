(ns oti.service.payment
  (:require [oti.boundary.db-access :as dba]
            [oti.boundary.payment :as payment-util]
            [taoensso.timbre :refer [error]]))

(defn- process-response! [{:keys [vetuma-payment db]} form-data db-fn]
  (let [db-params {:order-number (:ORDNR form-data)
                   :pay-id (:PAYID form-data)
                   :archive-id (:PAID form-data)
                   :payment-method (:SO form-data)}]
    (if (and (payment-util/authentic-response? vetuma-payment form-data) (:order-number db-params))
      (do (db-fn db db-params)
          true)
      (do
        (error "Could not verify payment response message:" form-data)
        false))))

(defn confirm-payment! [config form-data]
  (process-response! config form-data dba/confirm-registration-and-payment!))

(defn cancel-payment! [config form-data]
  (process-response! config form-data dba/cancel-registration-and-payment!))

(defn poll-status-for-incomplete-payments []
  )
