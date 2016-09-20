(ns oti.spec
  (:require #?(:clj [clojure.spec :as s]
               :cljs [cljs.spec :as s])))

(s/def ::even-number (s/and even? number?))