(ns oti.component.vetuma-payment
  (:require [com.stuartsierra.component :as component]
            [oti.boundary.payment :as pmt]
            [clojure.spec :as s]
            [oti.spec :as os]
            [clojure.string :as str]
            [taoensso.timbre :as timbre])
  (:import [java.time.format DateTimeFormatter]
           [java.util Locale]
           [org.apache.commons.codec.digest DigestUtils]))

(def date-formatter (DateTimeFormatter/ofPattern "yyyyMMddHHmmssSSS"))

(defn- format-number [n]
  (String/format (Locale. "fi"), "%.2f", (to-array [(double n)])))

(defn- calculate-mac [{::os/keys [RCVID APPID TIMESTMP SO SOLIST TYPE AU LG RETURL CANURL ERRURL AP APPNAME AM REF
                                  ORDNR MSGBUYER MSGFORM PAYM_CALL_ID]} secret]
  (let [plaintext (str/join "&" (->> [RCVID APPID TIMESTMP SO SOLIST TYPE AU LG RETURL CANURL ERRURL AP
                                      APPNAME AM REF ORDNR MSGBUYER MSGFORM PAYM_CALL_ID secret ""]
                                     (remove nil?)))]
    (-> plaintext (.getBytes "ISO-8859-1") DigestUtils/sha256Hex str/upper-case)))

(defn- generate-form-data [{:keys [vetuma-host rcvid app-id oti-vetuma-uri secret ap]}
                           {::os/keys [timestamp language-code amount reference-number order-number
                                       app-name msg payment-id] :as params}]
  {:pre  [(s/valid? ::os/payment-params params)]
   :post [(s/valid? ::os/payment-form-data %)]}
  (let [form-params #:oti.spec{:RCVID        rcvid
                               :APPID        app-id
                               :TIMESTMP     (.format timestamp date-formatter)
                               :SO           ""
                               :SOLIST       "P,L"
                               :TYPE         "PAYMENT"
                               :AU           "PAY"
                               :LG           (name language-code)
                               :RETURL       (str oti-vetuma-uri "/success")
                               :CANURL       (str oti-vetuma-uri "/cancel")
                               :ERRURL       (str oti-vetuma-uri "/error")
                               :AP           ap
                               :APPNAME      app-name
                               :AM           (format-number amount)
                               :REF          reference-number
                               :ORDNR        order-number
                               :MSGBUYER     msg
                               :MSGFORM      msg
                               :PAYM_CALL_ID payment-id}
        mac (calculate-mac form-params secret)]
    #:oti.spec{:uri                 vetuma-host
               :payment-form-params (assoc form-params ::os/MAC mac)}))

(defn- generate-payment-query-params [{:keys [vetuma-host rcvid app-id oti-vetuma-uri secret ap]}
                                      {:keys [timestamp payment-id]}]
  {:post [(s/valid? ::os/payment-query-data %)]}
  (let [form-params #:oti.spec{:RCVID        rcvid
                               :APPID        app-id
                               :TIMESTMP     (.format timestamp date-formatter)
                               :SO           ""
                               :SOLIST       "P,L"
                               :TYPE         "PAYMENT"
                               :AU           "CHECK"
                               :LG           "fi"
                               :RETURL       (str oti-vetuma-uri "/success")
                               :CANURL       (str oti-vetuma-uri "/cancel")
                               :ERRURL       (str oti-vetuma-uri "/error")
                               :AP           ap
                               :PAYM_CALL_ID payment-id}
        mac (calculate-mac form-params secret)]
    #:oti.spec{:uri                  (str vetuma-host "Query")
               :payment-query-params (assoc form-params ::os/MAC mac)}))

(def response-keys [:RCVID :TIMESTMP :SO :LG :RETURL :CANURL :ERRURL :PAYID :REF :ORDNR :PAID :STATUS :TRID
                    :PAYM_STATUS :PAYM_AMOUNT :PAYM_CURRENCY])

(defn- response-mac-valid? [{:keys [secret]} form-data]
  (if-let [candidate-mac (:MAC form-data)]
    (let [plaintext (-> (->> response-keys
                             (map #(% form-data))
                             (remove nil?)
                             (str/join "&"))
                        (str "&" secret "&"))
          calculated-mac (-> plaintext DigestUtils/sha256Hex str/upper-case)]
      (= candidate-mac calculated-mac))
    (timbre/error "Tried to authenticate message, but the map contained no :MAC key. Data:" form-data)))

(defrecord VetumaPayment []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  pmt/Payment
  (form-data-for-payment [payment-component params]
    (generate-form-data payment-component params))
  (authentic-response? [payment-component form-data]
    (response-mac-valid? payment-component form-data))
  (payment-query-data [payment-component params]
    (generate-payment-query-params payment-component params)))

(defn vetuma-payment [config]
  (map->VetumaPayment config))
