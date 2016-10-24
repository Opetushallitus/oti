(ns oti.component.vetuma-payment-test
  (:require [clojure.test :refer :all]
            [oti.boundary.payment :refer :all]
            [oti.component.vetuma-payment :refer :all]
            [clojure.java.io :as io]
            [oti.spec :as os])
  (:import [java.time LocalDateTime]))

(def config
  (->> (io/resource "dev.edn")
       slurp
       (clojure.edn/read-string)
       :config
       :vetuma-payment))

(def component (vetuma-payment config))

(deftest payment-form-data-is-generated-correctly
  (let [params #::os{:timestamp        (LocalDateTime/of 2016 10 24 16 00 05)
                     :language-code    :fi
                     :amount           (bigdec 212.00)
                     :reference-number (bigdec 43963156058)
                     :order-number     "OTI439631560581"
                     :app-name         "Opetush. tutkintoon ilmoittautuminen"
                     :msg              "Tutkintomaksu"
                     :payment-id       "OTI439631560581"}]
    (is (= #::os{:uri "https://testitunnistus.suomi.fi/VETUMAPayment/",
                 :payment-form-params
                      #::os{:RETURL       "https://oti.local/oti/vetuma/success",
                            :LG           "fi",
                            :APPNAME      "Opetush. tutkintoon ilmoittautuminen",
                            :AP           "TESTIASIAKAS1",
                            :APPID        "oph-oti",
                            :RCVID        "TESTIASIAKAS11",
                            :MSGBUYER     "Tutkintomaksu",
                            :REF          43963156058M,
                            :MSGFORM      "Tutkintomaksu",
                            :AU           "PAY",
                            :SO           "",
                            :ERRURL       "https://oti.local/oti/vetuma/error",
                            :MAC          "F2392F76651C522E088A5EDC36CDA8DC1413BFD1BA1E8650696775EB108E2141",
                            :TIMESTMP     "20161024160005000",
                            :PAYM_CALL_ID "OTI439631560581",
                            :TYPE         "PAYMENT",
                            :CANURL       "https://oti.local/oti/vetuma/cancel",
                            :ORDNR        "OTI439631560581",
                            :SOLIST       "P,L",
                            :AM           "212,00"}}
           (form-data-for-payment component params)))))
