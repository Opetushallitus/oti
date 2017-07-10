(ns oti.boundary.payment
  (:require [clojure.spec.alpha :as s]))

(defprotocol Payment
  (form-data-for-payment [payment-component params])
  (authentic-response? [payment-component form-data])
  (payment-query-data [payment-component params]))
