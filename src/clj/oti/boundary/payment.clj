(ns oti.boundary.payment
  (:require [clojure.spec.alpha :as s]))

(defprotocol Payment
  (link-for-payment [payment-component params])
  (authentic-response? [payment-component form-data]))
