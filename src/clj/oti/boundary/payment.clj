(ns oti.boundary.payment
  (:require [clojure.spec :as s]))

(defprotocol Payment
  (form-data-for-payment [payment-component params])
  (validate-response [payment-component form-data]))