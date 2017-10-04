(ns oti.component.paytrail-payment-test
  (:require [clojure.test :refer :all]
            [oti.component.paytrail-payment :refer :all]
            [oti.boundary.payment :refer :all]
            [clojure.java.io :as io]
            [oti.spec :as os])
  (:import [java.time LocalDateTime]))

(def config
  (->> (io/resource "dev.edn")
       slurp
       (clojure.edn/read-string)
       :config
       :paytrail-payment))

(def component (paytrail-payment config))

(deftest payment-form-data-is-generated-correctly
  (let [params #::os{:language-code    :fi
                     :amount           (bigdec 212.00)
                     :order-number     "OTI439631560581"
                     :reference-number (bigdec 43963156058)}]
    (is (= #::os{:uri "https://payment.paytrail.com/e2",
                 :pt-payment-form-params
                      #::os{:MERCHANT_ID  13466,
                            :LOCALE       "fi_FI",
                            :URL_SUCCESS  "https://oti.local/oti/paytrail/success",
                            :URL_CANCEL   "https://oti.local/oti/paytrail/cancel",
                            :AMOUNT       "212.00",
                            :ORDER_NUMBER "OTI439631560581",
                            :REFERENCE_NUMBER 43963156058M,
                            :PARAMS_IN    "MERCHANT_ID,LOCALE,URL_SUCCESS,URL_CANCEL,AMOUNT,ORDER_NUMBER,REFERENCE_NUMBER,PARAMS_IN,PARAMS_OUT",
                            :PARAMS_OUT   "PAYMENT_ID,TIMESTAMP,STATUS",
                            ;; If you change anything above (add params, modify values ets.), then the following authcode must
                            ;; be updated too. Let the test fail first and pick up the new calculated authcode from fail message.
                            :AUTHCODE     "1A0C24B9806E96491939187D74EABE570554C2F9D9885D090E3543D77F2BC895"}}
           (form-data-for-payment component params)))))

