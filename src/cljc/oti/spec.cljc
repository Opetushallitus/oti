(ns oti.spec
  (:require #?(:clj [clojure.spec :as s]
               :cljs [cljs.spec :as s])
                    [clojure.string :as str]))

;(defn future-date? [candidate]
;  #?(:clj ()
;     ))

(s/def ::non-blank-string (s/and string? #(not (str/blank? %))))

(def time-regexp #"\d?\d[:\.]\d\d")
(s/def ::time (s/and string? #(re-matches time-regexp %)))

(s/def ::session-date inst?)

(s/def ::start-time ::time)

(s/def ::end-time ::time)

(s/def ::street-address ::non-blank-string)

(s/def ::city ::non-blank-string)

(s/def ::other-location-info ::non-blank-string)

(s/def ::max-participants pos-int?)

(s/def ::exam-id pos-int?)

(s/def ::exam-session (s/keys :req [::session-date
                                    ::start-time
                                    ::end-time
                                    ::street-address
                                    ::city
                                    ::other-location-info
                                    ::max-participants
                                    ::exam-id]))
