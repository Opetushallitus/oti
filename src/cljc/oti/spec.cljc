(ns oti.spec
  (:require #?(:clj [clojure.spec :as s]
               :cljs [cljs.spec :as s])
                    [clojure.string :as str]))

(defn future-date? [candidate]
  #?(:clj (.after candidate (java.util.Date.))
     :cljs (= 1 (compare candidate (js/Date.)))))

(s/def ::non-blank-string (s/and string? #(not (str/blank? %))))

(def time-regexp #"(0?[0-9]|1[0-9]|2[0-3])[:\.]([0-5][0-9])")

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

(defn start-before-end-time? [{::keys [start-time end-time]}]
  #?(:clj (.isBefore start-time end-time)
     :cljs (let [[s-h s-m] (->> (str/split start-time #"[:\.]")
                                (map js/parseInt))
                 [e-h e-m] (->> (str/split end-time #"[:\.]")
                                (map js/parseInt))]
             (or (< s-h e-h) (and (= s-h e-h) (< s-m e-m))))))

(s/def ::id pos-int?)

;; i18n
(def recognized-languages #{:fi :sv})
(s/def ::language-code recognized-languages)
(s/def ::i18n-string (s/map-of ::language-code ::non-blank-string :count 2))

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
(s/def ::registration-count  int?)

(s/def ::exam-session (s/and
                        (s/keys :req [::session-date
                                      ::start-time
                                      ::end-time
                                      ::street-address
                                      ::city
                                      ::other-location-info
                                      ::max-participants
                                      ::exam-id
                                      ::published]
                                :opt [::id])
                        start-before-end-time?))

;; registration
(s/def ::email (s/and string? #(re-matches #".+@.+\..+" %)))
(s/def ::retry any?)
(s/def ::accredit? any?)
(s/def ::retry-modules (s/* ::id))
(s/def ::accredit-modules (s/* ::id))
(s/def ::session-id pos-int?)

(defn retry-or-accredit? [{::keys [retry? accredit?]}]
  (not (and retry? accredit?)))

(s/def ::section (s/and (s/keys :opt [::retry?
                                      ::accredit?
                                      ::retry-modules
                                      ::accredit-modules])
                        retry-or-accredit?))

(s/def ::sections (s/and (s/map-of ::id ::section)
                         seq))

(s/def ::registration (s/keys :req [::email
                                    ::session-id
                                    ::language-code
                                    ::sections]))
