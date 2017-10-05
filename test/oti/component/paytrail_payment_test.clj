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
                     :reference-number (bigdec 43963156058)
                     :msg              "Tutkintomaksu"}]
    (is (= #::os{:uri "https://payment.paytrail.com/e2",
                 :pt-payment-form-params
                      #::os{:MERCHANT_ID  13466,
                            :LOCALE       "fi_FI",
                            :URL_SUCCESS  "https://oti.local/oti/paytrail/success",
                            :URL_CANCEL   "https://oti.local/oti/paytrail/cancel",
                            :AMOUNT       "212.00",
                            :ORDER_NUMBER "OTI439631560581",
                            :REFERENCE_NUMBER 43963156058M,
                            :MSG_SETTLEMENT_PAYER "Tutkintomaksu",
                            :MSG_UI_MERCHANT_PANEL "Tutkintomaksu",
                            :PARAMS_IN    "MERCHANT_ID,LOCALE,URL_SUCCESS,URL_CANCEL,AMOUNT,ORDER_NUMBER,REFERENCE_NUMBER,MSG_SETTLEMENT_PAYER,MSG_UI_MERCHANT_PANEL,PARAMS_IN,PARAMS_OUT",
                            :PARAMS_OUT   "ORDER_NUMBER,PAYMENT_ID,AMOUNT,TIMESTAMP,STATUS",
                            ;; If you change anything above (add params, modify values ets.), then the following authcode must
                            ;; be updated too. Let the test fail first and pick up the new calculated authcode from fail message.
                            ;; This is not the most convenient way to do this, but this logic follows the same logic as the one made
                            ;; for depracated VETUMA payment.
                            :AUTHCODE     "278457B7DE228A136A6C7645B5FECDDEF269F9963F45C3DEFBAC5A813BCB77A3"}}
           (form-data-for-payment component params)))))

