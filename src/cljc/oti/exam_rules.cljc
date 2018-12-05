(ns oti.exam-rules
  (:require [oti.spec :as spec]))

(def rules-by-section-id
  {
    1 {:can-retry-partially? true
       :can-accredit-partially? true},
    2 {:can-retry-partially? true
       :can-accredit-partially? true}
  })

(defn price-type-for-registration [registration]
  (let [retrying? (->> registration
                       ::spec/sections
                       (some (fn [[_ {::spec/keys [retry? retry-modules]}]]
                               (or retry? (seq retry-modules)))))]
    (if retrying? :retry :full)))
