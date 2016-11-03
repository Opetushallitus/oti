(ns oti.spec
  (:require
    #?(:clj  [clojure.spec :as s]
       :cljs [cljs.spec :as s])
             [clojure.string :as str]
             [oti.utils :refer [parse-int]]))

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

(defn date-time? [x]
  #?(:clj (instance? java.time.LocalDateTime x)
     :cljs (inst? x)))

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
(s/def ::session-id integer?)
(s/def ::preferred-name ::non-blank-string)
(s/def ::registration-city ::non-blank-string)
(s/def ::registration-post-office ::non-blank-string)
(s/def ::registration-street-address ::non-blank-string)
(s/def ::registration-zip (s/and string? #(re-matches #"\d{5}" %)))

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
                                    ::preferred-name
                                    ::sections
                                    ::registration-street-address
                                    ::registration-zip
                                    ::registration-post-office
                                    ::registration-city]))

(s/def ::postal-address (s/keys :req [::registration-street-address
                                      ::registration-zip
                                      ::registration-post-office
                                      ::registration-city]))

(def hetu-regexp #"[\d]{6}[+\-A-Za-z][\d]{3}[\dA-Za-z]")
(s/def ::hetu (s/and string? #(re-matches hetu-regexp %)))

(defn- valid-reference-number? [x]
  (let [factors [7 3 1]
        ref-str (str x)
        check-num (-> (last ref-str) str parse-int)
        ref (drop-last ref-str)]
    (-> (->> (reverse ref)
           (partition 3 3 [])
           (map (fn [three]
                  (->> (map #(parse-int (str %)) three)
                       (map * factors))))
           (flatten)
           (reduce +)
           str
           last
           str
           parse-int
           (- 10))
        (mod 10)
        (= check-num))))

;; payment

(s/def ::timestamp date-time?)
(s/def ::amount (s/and number? pos?))
(s/def ::reference-number (s/and number? #(valid-reference-number? %)))
(s/def ::order-number (s/and ::non-blank-string #(< (count %) 33)))
(s/def ::app-name ::non-blank-string)
(s/def ::msg ::non-blank-string)
(s/def ::payment-id (s/and ::non-blank-string #(< (count %) 26)))

(s/def ::payment-params (s/keys :req [::timestamp
                                      ::language-code
                                      ::amount
                                      ::reference-number
                                      ::order-number
                                      ::app-name
                                      ::msg
                                      ::payment-id]))

(def amount-regexp #"\d{0,3},\d{2}")

(s/def ::RCVID ::non-blank-string)
(s/def ::APPID ::non-blank-string)
(s/def ::TIMESTMP ::non-blank-string)
(s/def ::SO string?)
(s/def ::SOLIST ::non-blank-string)
(s/def ::TYPE ::non-blank-string)
(s/def ::AU ::non-blank-string)
(s/def ::LG ::non-blank-string)
(s/def ::RETURL ::non-blank-string)
(s/def ::CANURL ::non-blank-string)
(s/def ::ERRURL ::non-blank-string)
(s/def ::AP ::non-blank-string)
(s/def ::MAC ::non-blank-string)
(s/def ::APPNAME ::non-blank-string)
(s/def ::AM (s/and string? #(re-matches amount-regexp %)))
(s/def ::REF ::reference-number)
(s/def ::ORDNR ::order-number)
(s/def ::MSGBUYER ::msg)
(s/def ::MSGFORM ::msg)
(s/def ::PAYM_CALL_ID ::payment-id)

(s/def ::payment-form-params (s/keys :req [::RCVID
                                           ::APPID
                                           ::TIMESTMP
                                           ::SO
                                           ::SOLIST
                                           ::TYPE
                                           ::AU
                                           ::LG
                                           ::RETURL
                                           ::CANURL
                                           ::ERRURL
                                           ::AP
                                           ::MAC
                                           ::APPNAME
                                           ::AM
                                           ::REF
                                           ::ORDNR
                                           ::MSGBUYER
                                           ::MSGFORM
                                           ::PAYM_CALL_ID]))

(s/def ::uri ::non-blank-string)

(s/def ::payment-form-data (s/keys :req [::uri
                                         ::payment-form-params]))

(s/def ::payment-query-params (s/keys :req [::RCVID
                                            ::APPID
                                            ::TIMESTMP
                                            ::SO
                                            ::SOLIST
                                            ::TYPE
                                            ::AU
                                            ::LG
                                            ::RETURL
                                            ::CANURL
                                            ::ERRURL
                                            ::AP
                                            ::MAC
                                            ::PAYM_CALL_ID]))

(s/def ::payment-query-data (s/keys :req [::uri
                                          ::payment-query-params]))
