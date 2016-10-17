(ns oti.component.vetuma-payment
  (:require [com.stuartsierra.component :as component]
            [oti.boundary.payment :as pmt]
            [clojure.spec :as s]
            [oti.spec :as os]))

(defn generate-form-data [params]
  {:pre [(s/valid? ::os/payment-params params)]
   :post [(s/valid? ::os/payment-form-params %)]}
  {})

(defrecord VetumaPayment []
  component/Lifecycle
  (start [this] this)
  (stop [this] this)
  pmt/Payment
  (form-data-for-payment [payment-component params]
    (generate-form-data params))
  (validate-response [payment-component form-data]))

(defn vetuma-payment [config]
  (map->VetumaPayment config))
