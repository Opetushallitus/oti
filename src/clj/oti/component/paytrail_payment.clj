(ns oti.component.paytrail-payment
  (:require [com.stuartsierra.component :as component]
            [oti.boundary.payment :as payment-util]
            [clojure.spec.alpha :as s]
            [oti.spec :as os]
            [clojure.core.match :refer [match]]
            [clojure.string :as str]
            [clojure.walk :refer [stringify-keys]]
            [clojure.tools.logging :refer [error info]]
            [clj-http.client :as client]
            [buddy.core.mac :as mac]
            [buddy.core.codecs :as codecs]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log])
  (:import [java.util UUID]
           [java.util Locale]
           [org.apache.commons.codec.digest DigestUtils]
           [java.time ZoneOffset ZonedDateTime]
           [java.time.format DateTimeFormatter]))

(defn- generate-json-data [{:keys [oti-paytrail-uri]}
                           {::os/keys [email first-name last-name language-code amount order-number] :as params}]
  {:pre  [(s/valid? ::os/pt-payment-params params)]}
  (let [callback-urls {"success" (str oti-paytrail-uri "/success")
                       "cancel"  (str oti-paytrail-uri "/cancel")}]
    {"stamp"        (str (UUID/randomUUID))
     ; Order reference
     "reference"    order-number
     ; Total amount in EUR cents
     "amount"       amount
     "currency"     "EUR"
     "language"     (case (or language-code :fi) :fi "FI" :sv "SV" :en "EN")
     "customer"     {"email"     email
                     "firstName" (or first-name "testi")
                     "lastName"  (or last-name "tester")}
     "redirectUrls" callback-urls
     "callbackUrls" callback-urls
     }))

(defn- authentication-headers [method merchant-id transaction-id]
  (cond-> {"checkout-account"   merchant-id
           "checkout-algorithm" "sha512"
           "checkout-method"    method
           "checkout-nonce"     (str (UUID/randomUUID))
           "checkout-timestamp" (.format (ZonedDateTime/now)
                                         (-> (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss.SSS'Z'")
                                             (.withZone (ZoneOffset/UTC))))}
          (some? transaction-id)
          (assoc "checkout-transaction-id" transaction-id)))

(defn- headers->signature-keys-order [headers]
  (->> (keys headers)
       (filter #(str/starts-with? % "checkout-"))
       (sort)))

(defn- sign-request [merchant-secret headers body]
  (let [sb (StringBuilder.)]
    (doseq [header (headers->signature-keys-order headers)]
      (when-let [data (headers header)]
        (.append sb header)
        (.append sb ":")
        (.append sb data)
        (.append sb "\n")))
    (when body
      (.append sb body))
    (-> (.toString sb)
        (mac/hash {:key merchant-secret
                   :alg :hmac+sha512})
        (codecs/bytes->hex))))

(defn- generate-link-for-payment [{:keys [paytrail-host merchant-id merchant-secret] :as payment-component}
                                  params]
  {:pre  [(s/valid? ::os/pt-payment-params params)]
   :post [(s/valid? ::os/pt-payment-form-data %)]}
  (let [debug2 (info (str "merchant-id: " merchant-id ", " merchant-secret))
        debug33 (info "host: " paytrail-host)
        authentication-headers (authentication-headers "POST" merchant-id nil)
        data (generate-json-data payment-component params)
        debug1 (info (str "data: " data))
        body (json/write-str data)
        response (-> {:method           :post
                      :url              paytrail-host
                      :content-type     "application/json; charset=utf-8"
                      :throw-exceptions true
                      :as               :json
                      :headers          (-> authentication-headers
                                            (assoc "signature" (sign-request merchant-secret authentication-headers body)))
                      :body             body}
                     client/request)]
    (info (str "got href: " (-> response :body :href)))
    #:oti.spec{:uri (-> response :body :href)}))

(comment OLD STUFF BEGINS)

(defn- format-number-us-locale [n]
  (String/format (Locale. "us"), "%.2f", (to-array [(double n)])))

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
                               :AMOUNT       (format-number-us-locale amount)
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
  (let [signed-headers (sign-request merchant-secret (stringify-keys form-data) nil)
        auth-ok (= signed-headers (:signature form-data))
        status-ok (= (:checkout-status form-data) "ok")]
    (and auth-ok status-ok)
    (match [auth-ok status-ok]
           [false _] (log/error "Tried to authenticate message, but the signature didnt match:" form-data)
           [_ false] (log/error "Tried to authenticate message, but checkout status was not ok:" form-data)
           [true true] true)))

(defrecord PaytrailPayment []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  payment-util/Payment
  (link-for-payment [payment-component params]
    (generate-link-for-payment payment-component params))
  (authentic-response? [payment-component form-data]
    (return-authcode-valid? payment-component form-data)))

(defn paytrail-payment [config]
  (map->PaytrailPayment config))
