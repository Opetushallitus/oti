(ns oti.service.payment
  (:require [oti.boundary.db-access :as dba]
            [oti.boundary.payment :as payment-util]
            [taoensso.timbre :refer [error]]))

(defn confirm-payment! [{:keys [vetuma-payment db]} form-data]
  (let [db-params {:order-number (:ORDNR form-data)
                   :pay-id (:PAYID form-data)
                   :archive-id (:PAID form-data)
                   :payment-method (:SO form-data)}]
    (if (and (payment-util/authentic-response? vetuma-payment form-data) (:order-number db-params))
      (do (dba/confirm-registration-and-payment! db db-params)
          true)
      (do
        (error "Could not verify payment response message:" form-data)
        false))))

(defn cancel-payment [{:keys [vetuma-payment db]} form-data]
  (let [{order-number :ORDNR} form-data]
    (if (and (payment-util/authentic-response? vetuma-payment form-data) order-number)
      true
      (do
        (error "Could not verify payment response message:" form-data)
        false))))
