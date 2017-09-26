(ns oti.component.vetuma-payment
  (:require [com.stuartsierra.component :as component]
            [oti.boundary.payment :as pmt]
            [clojure.spec.alpha :as s]
            [oti.spec :as os]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import [java.time.format DateTimeFormatter]
           [java.util Locale]
           [org.apache.commons.codec.digest DigestUtils]))


(defn- calculate-authcode [{::os/keys []} secret]
  (let [plaintext (str/join "|" (->> [secret MERCHANT_ID LOCALE URL_SUCCESS URL_CANCEL
                                      AMOUNT ORDER_NUMBER PARAMS_IN PARAMS_OUT]
                                     (remove nil?)))]
    (-> plaintext (.getBytes "ISO-8859-1") DigestUtils/sha256Hex str/upper-case)))


(defn- generate-form-data [{:keys [paytrail-host oti-paytrail-uri merchant-id merchant-secret]}
                           {::os/keys [language-code amount order-number] :as params}]
  {:pre  [(s/valid? ::os/payment-params params)]
   :post [(s/valid? ::os/payment-form-data %)]}
  (let [params-in "MERCHANT_ID,LOCALE,URL_SUCCESS,URL_CANCEL,AMOUNT,ORDER_NUMBER,PARAMS_IN,PARAMS_OUT"
        params-out "PAYMENT_ID,TIMESTAMP,STATUS"
        form-params #:oti.spec{:MERCHANT_ID  merchant-id
                               :LOCALE       (name language-code)
                               :URL_SUCCESS  (str oti-paytrail-uri "/success")
                               :URL_CANCEL   (str oti-paytrail-uri "/cancel")
                               :AMOUNT       (format-number amount)
                               :ORDER_NUMBER order-number
                               :PARAMS_IN params-in
                               :PARAMS_OUT params-out}
        authcode (calculate-authcode form-params merchant-secret)]
    #:oti.spec{:uri                 paytrail-host
               :payment-form-params (assoc form-params ::os/AUTHCODE authcode)}))


(defrecord PaytrailPayment []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  pmt/Payment
  (form-data-for-payment [payment-component params]
    (generate-form-data payment-component params))
  (authentic-response? [payment-component form-data]
    nil)
  (payment-query-data [payment-component params]
    nil))

(defn paytrail-payment [config]
  (map->PaytrailPayment config))
