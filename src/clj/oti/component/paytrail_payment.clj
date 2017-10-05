(ns oti.component.paytrail-payment
  (:require [com.stuartsierra.component :as component]
            [oti.boundary.payment :as pmt]
            [clojure.spec.alpha :as s]
            [oti.spec :as os]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import [java.time.format DateTimeFormatter]
           [java.util Locale]
           [org.apache.commons.codec.digest DigestUtils]))

(defn- format-number [n]
  (String/format (Locale. "fi"), "%.2f", (to-array [(double n)])))

(defn- calculate-authcode [{::os/keys [MERCHANT_ID LOCALE URL_SUCCESS URL_CANCEL
                                       AMOUNT ORDER_NUMBER REFERENCE_NUMBER MSG_SETTLEMENT_PAYER
                                       MSG_UI_MERCHANT_PANEL PARAMS_IN PARAMS_OUT]} secret]
  (let [plaintext (str/join "|" (->> [secret MERCHANT_ID LOCALE URL_SUCCESS URL_CANCEL
                                      AMOUNT ORDER_NUMBER REFERENCE_NUMBER  MSG_SETTLEMENT_PAYER
                                      MSG_UI_MERCHANT_PANEL PARAMS_IN PARAMS_OUT]
                                     (remove nil?)))]
    (-> plaintext (.getBytes "ISO-8859-1") DigestUtils/sha256Hex str/upper-case)))

(defn- generate-form-data [{:keys [paytrail-host oti-paytrail-uri merchant-id merchant-secret]}
                           {::os/keys [language-code amount order-number reference-number msg] :as params}]
  {:pre  [(s/valid? ::os/pt-payment-params params)]
   :post [(s/valid? ::os/pt-payment-form-data %)]}
  (let [params-in "MERCHANT_ID,LOCALE,URL_SUCCESS,URL_CANCEL,AMOUNT,ORDER_NUMBER,REFERENCE_NUMBER,MSG_SETTLEMENT_PAYER,MSG_UI_MERCHANT_PANEL,PARAMS_IN,PARAMS_OUT"
        params-out "ORDER_NUMBER,PAYMENT_ID,AMOUNT,TIMESTAMP,STATUS"
        form-params #:oti.spec{:MERCHANT_ID  merchant-id
                               :LOCALE       (case language-code :fi "fi_FI" :sv "sv_SE")
                               :URL_SUCCESS  (str oti-paytrail-uri "/success")
                               :URL_CANCEL   (str oti-paytrail-uri "/cancel")
                               :AMOUNT       (format  "%.2f" (double amount))
                               :ORDER_NUMBER order-number
                               :REFERENCE_NUMBER reference-number
                               :MSG_SETTLEMENT_PAYER msg
                               :MSG_UI_MERCHANT_PANEL msg
                               :PARAMS_IN params-in
                               :PARAMS_OUT params-out}
        authcode (calculate-authcode form-params merchant-secret)]
    #:oti.spec{:uri                 paytrail-host
               :pt-payment-form-params (assoc form-params ::os/AUTHCODE authcode)}))

(def response-keys [:ORDER_NUMBER :PAYMENT_ID :AMOUNT :TIMESTAMP :STATUS])

(defn- return-authcode-valid? [{:keys [merchant-secret]} form-data]
  (if-let [return-authcode (:RETURN_AUTHCODE form-data)]
    (let [plaintext (-> (->> response-keys
                             (map #(% form-data))
                             (remove nil?)
                             (str/join "|"))
                        (str "|" merchant-secret))
          calculated-authcode (-> plaintext DigestUtils/sha256Hex str/upper-case)]
      (log/info "********************************************************************************")
      (log/info "form-data: " form-data)
      (log/info "plaintext: " plaintext)
      (log/info "return-authcode: " return-authcode)
      (log/info "calculated-authcode: " calculated-authcode)
      (log/info "********************************************************************************")
      (= return-authcode calculated-authcode))
    (log/error "Tried to authenticate message, but the map contained no :RETURN_AUTHCODE key. Data:" form-data)))

(defrecord PaytrailPayment []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  pmt/Payment
  (form-data-for-payment [payment-component params]
    (generate-form-data payment-component params))
  (authentic-response? [payment-component form-data]
    (return-authcode-valid? payment-component form-data))
  (payment-query-data [payment-component params]
    nil))

(defn paytrail-payment [config]
  (map->PaytrailPayment config))
