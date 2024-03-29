(ns oti.component.paytrail-payment
  (:require [com.stuartsierra.component :as component]
            [oti.boundary.payment :as payment-util]
            [clojure.spec.alpha :as s]
            [oti.spec :as os]
            [clojure.core.match :refer [match]]
            [clojure.string :as str]
            [clojure.walk :refer [stringify-keys]]
            [clj-http.client :as client]
            [buddy.core.mac :as mac]
            [buddy.core.codecs :as codecs]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log])
  (:import [java.util UUID]
           [java.time ZoneOffset ZonedDateTime]
           [java.time.format DateTimeFormatter]))

(defn- generate-json-data [{:keys [oti-paytrail-uri]}
                           {::os/keys [email first-name last-name language-code amount order-number] :as params}]
  {:pre  [(s/valid? ::os/pt-payment-params params)]}
  (let [callback-urls {"success" (str oti-paytrail-uri "/success")
                       "cancel"  (str oti-paytrail-uri "/cancel")}
        paytrail-amount-in-euro-cents (* amount 100)]
    {"stamp"        (str (UUID/randomUUID))
     ; Order reference
     "reference"    order-number
     ; Total amount in EUR cents
     "amount"       paytrail-amount-in-euro-cents
     "currency"     "EUR"
     "language"     (case (or language-code :fi) :fi "FI" :sv "SV" :en "EN")
     "customer"     {"email"     email
                     "firstName" first-name
                     "lastName"  last-name}
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
  (let [authentication-headers (authentication-headers "POST" merchant-id nil)
        data (generate-json-data payment-component params)
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
    #:oti.spec{:uri (-> response :body :href)}))

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
