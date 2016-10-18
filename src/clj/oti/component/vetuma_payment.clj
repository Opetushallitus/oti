(ns oti.component.vetuma-payment
  (:require [com.stuartsierra.component :as component]
            [oti.boundary.payment :as pmt]
            [clojure.spec :as s]
            [oti.spec :as os]
            [clojure.string :as str])
  (:import [java.time.format DateTimeFormatter]
           [java.util Locale]
           [org.apache.commons.codec.digest DigestUtils]))

(def date-formatter (DateTimeFormatter/ofPattern "yyyyMMddHHmmssSSS"))

(defn- format-number [n]
  (String/format (Locale. "fi"), "%.2f", (to-array [(double n)])))

(defn- calculate-mac [{::os/keys [RCVID APPID TIMESTMP SO SOLIST TYPE AU LG RETURL CANURL ERRURL AP APPNAME AM REF
                                  ORDNR MSGBUYER MSGFORM PAYM_CALL_ID]} secret]
  (let [plaintext (str/join "&" [RCVID APPID TIMESTMP SO SOLIST TYPE AU LG RETURL CANURL ERRURL AP
                                 APPNAME AM REF ORDNR MSGBUYER MSGFORM PAYM_CALL_ID secret ""])]
    (-> plaintext DigestUtils/sha256Hex str/upper-case)))

(defn- generate-form-data [{:keys [vetuma-host rcvid app-id success-uri cancel-uri error-uri secret ap]}
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
                               :RETURL       success-uri
                               :CANURL       cancel-uri
                               :ERRURL       error-uri
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

(defrecord VetumaPayment []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  pmt/Payment
  (form-data-for-payment [payment-component params]
    (generate-form-data payment-component params))
  (validate-response [payment-component form-data]))

(defn vetuma-payment [config]
  (map->VetumaPayment config))