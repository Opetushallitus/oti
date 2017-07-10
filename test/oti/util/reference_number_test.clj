(ns oti.util.reference-number-test
  (:require [oti.spec :as os]
            [clojure.test :refer :all]
            [clojure.spec.alpha :as s]))

(defn- gen-external-id-str []
  (apply str "900" (take 11 (repeatedly #(rand-int 10)))))

(deftest conformer-generates-proper-references
  (doseq [id-with-prefix (take 1000 (repeatedly gen-external-id-str))]
    (is (s/valid? ::os/reference-number (s/conform ::os/reference-number-conformer id-with-prefix)))))
