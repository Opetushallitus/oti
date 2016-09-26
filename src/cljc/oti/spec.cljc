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

(defn date-conformer [x]
  #?(:clj (cond
            (instance? java.time.LocalDate x) x
            (instance? java.util.Date x) (-> x
                                             .toInstant
                                             (.atZone (java.time.ZoneId/of "Europe/Helsinki"))
                                             .toLocalDate)
            :else ::s/invalid)
     :cljs (if (inst? x)
             (inst-ms x)
             ::s/invalid)))

(defn valid-time-string? [x]
  (and (string? x) (re-matches time-regexp x)))

(defn time-conformer [x]
  #?(:clj (cond
            (instance? java.time.LocalTime x) x
            (valid-time-string? x) (-> (str/replace x "." ":")
                                       (java.time.LocalTime/parse))
            :else ::s/invalid)
     :cljs (if (valid-time-string? x)
             x
             ::s/invalid)))

(s/def ::session-date (s/conformer date-conformer))

(s/def ::start-time (s/conformer time-conformer))

(s/def ::end-time (s/conformer time-conformer))

(s/def ::street-address ::non-blank-string)

(s/def ::city ::non-blank-string)

(s/def ::other-location-info ::non-blank-string)

(s/def ::max-participants pos-int?)

(s/def ::exam-id pos-int?)

(s/def ::id pos-int?)

(s/def ::exam-session (s/keys :req [::session-date
                                    ::start-time
                                    ::end-time
                                    ::street-address
                                    ::city
                                    ::other-location-info
                                    ::max-participants
                                    ::exam-id]))
