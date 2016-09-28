(ns oti.spec
  (:require #?(:clj [clojure.spec :as s]
               :cljs [cljs.spec :as s])
                    [clojure.string :as str]))

(defn future-date? [candidate]
  #?(:clj (.after candidate (java.util.Date.))
     :cljs (= 1 (compare candidate (js/Date.)))))

(s/def ::non-blank-string (s/and string? #(not (str/blank? %))))

(def time-regexp #"\d?\d[:\.]\d\d")
(s/def ::time (s/and string? #(re-matches time-regexp %)))

(defn date-conformer [x]
  #?(:clj (cond
            (instance? java.time.LocalDate x)
            x
            (and (instance? java.util.Date x) (future-date? x))
            (-> x
                .toInstant
                (.atZone (java.time.ZoneId/of "Europe/Helsinki"))
                .toLocalDate)
            :else ::s/invalid)
     :cljs (if (and (inst? x) (future-date? x))
             (inst-ms x)
             ::s/invalid)))

(defn valid-time-string? [x]
  (and (string? x) (re-matches time-regexp x)))

(defn time-conformer [x]
  #?(:clj (cond
            (instance? java.time.LocalTime x) x
            (valid-time-string? x) (-> (str/replace x "." ":")
                                       (java.time.LocalTime/parse (java.time.format.DateTimeFormatter/ofPattern "H:mm")))
            :else ::s/invalid)
     :cljs (if (valid-time-string? x)
             x
             ::s/invalid)))

(s/def ::id pos-int?)

;; i18n
(s/def ::language-code (s/and ::non-blank-string #(= (count %) 2)))
(s/def ::i18n-string (s/map-of ::language-code ::non-blank-string))

;; exam-session
(s/def ::session-date        (s/conformer date-conformer))
(s/def ::start-time          (s/conformer time-conformer))
(s/def ::end-time            (s/conformer time-conformer))
(s/def ::street-address      ::i18n-string)
(s/def ::city                ::i18n-string)
(s/def ::other-location-info ::i18n-string)
(s/def ::max-participants    pos-int?)
(s/def ::exam-id             pos-int?)
(s/def ::exam-session-id     pos-int?)
(s/def ::published           boolean?)

(s/def ::exam-session (s/keys :req [::session-date
                                    ::start-time
                                    ::end-time
                                    ::street-address
                                    ::city
                                    ::other-location-info
                                    ::max-participants
                                    ::exam-id
                                    ::published]))
