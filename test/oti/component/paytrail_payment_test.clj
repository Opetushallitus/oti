(ns oti.component.paytrail-payment-test
  (:require [clojure.test :refer :all]
            [oti.component.paytrail-payment :refer :all]
            [oti.boundary.payment :refer :all]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [oti.spec :as os])
  (:import [java.time LocalDateTime])
  (:use clj-http.fake))

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
                     :email            "tero.testaa@test.com"
                     :first-name       "tero"
                     :last-name        "testaa"
                     :order-number     "OTI439631560581"
                     :reference-number (bigdec 43963156058)
                     :msg              "terve tero"}
        paytrail-mock {:post (fn [_]
                               {:status  200
                                :headers {}
                                :body    "{\"href\":\"http://esimerkkilinkki\"}"})}]
    (with-global-fake-routes {"https://services.paytrail.com/payments" paytrail-mock}
                             (is (= #::os{:uri "http://esimerkkilinkki"}
                                    (link-for-payment component params))))))

