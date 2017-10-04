(ns oti.spec
  (:require
    #?(:clj  [clojure.spec.alpha :as s]
       :cljs [cljs.spec.alpha :as s])
             [clojure.string :as str]
             [oti.utils :refer [parse-int]]))

(defn future-date? [candidate]
  #?(:clj (.after candidate (java.util.Date.))
     :cljs (= 1 (compare candidate (js/Date.)))))

(s/def ::non-blank-string (s/and string? #(not (str/blank? %))))

(def time-regexp #"(0?[0-9]|1[0-9]|2[0-3])[:\.]([0-5][0-9])")

(s/def ::time (s/and string? #(re-matches time-regexp %)))

(s/def ::boolean #?(:clj #(instance? Boolean %)
                    :cljs #(= (type %) js/Boolean)))

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

(defn datetime-conformer [x]
  #?(:clj (if (instance? java.sql.Timestamp x)
            (.format (java.text.SimpleDateFormat. "MM.dd.yyyy HH:mm") x)
            ::s/invalid)
     :cljs x))

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
                                    ::registration-post-office]))

(s/def ::postal-address (s/keys :req [::registration-street-address
                                      ::registration-zip
                                      ::registration-post-office]))

(def hetu-regexp #"[\d]{6}[+\-A-Za-z][\d]{3}[\dA-Za-z]")
(s/def ::hetu (s/and string? #(re-matches hetu-regexp %)))

(defn- gen-ref-num [x]
  #?(:clj (let [factors [7 3 1]
                ref (str x)
                check-num (-> (->> (reverse ref)
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
                              (mod 10))]
            (bigdec (str x check-num)))
     :cljs (throw (js/Error. "Not implemented"))))

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

(s/def ::reference-number-conformer
  (s/conformer (fn [x]
                 (let [ref (gen-ref-num x)]
                   (if (s/valid? ::reference-number ref)
                     ref
                     ::s/invalid)))))

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

;; paytrail specific specs

(s/def ::pt-payment-params (s/keys :req [::language-code
                                         ::amount
                                         ::order-number
                                         ::reference-number]))

(def pt-amount-regexp #"\d{0,3}.\d{2}")
(def pt-locale-regexp #"^[a-z]{1,2}[_][A-Z]{1,2}$")

(s/def ::MERCHANT_ID number?)
(s/def ::LOCALE (s/and string? #(re-matches pt-locale-regexp %)))
(s/def ::URL_SUCCESS ::non-blank-string)
(s/def ::URL_CANCEL ::non-blank-string)
(s/def ::AMOUNT (s/and string? #(re-matches pt-amount-regexp %)))
(s/def ::ORDER_NUMBER ::order-number)
(s/def ::REFERENCE_NUMBER ::reference-number)
(s/def ::PARAMS_IN ::non-blank-string)
(s/def ::PARAMS_OUT ::non-blank-string)
(s/def ::AUTHCODE ::non-blank-string)

(s/def ::pt-payment-form-params (s/keys :req [::MERCHANT_ID
                                              ::LOCALE
                                              ::URL_SUCCESS
                                              ::URL_CANCEL
                                              ::AMOUNT
                                              ::ORDER_NUMBER
                                              ::REFERENCE_NUMBER
                                              ::PARAMS_IN
                                              ::PARAMS_OUT
                                              ::AUTHCODE]))

(s/def ::pt-payment-form-data (s/keys :req [::uri
                                            ::pt-payment-form-params]))

;; diploma

(s/def ::participant-ids (s/and set? (s/+ ::id)))
(s/def ::signer ::non-blank-string)
(s/def ::signer-title ::i18n-string)

(s/def ::diploma-data (s/keys :req [::participant-ids
                                    ::signer
                                    ::signer-title]))

(s/def ::module-score-id ::id)
(s/def ::module-id ::id)
(s/def ::module-score-points (s/nilable number?))
(s/def ::module-score-updated (s/nilable (s/conformer datetime-conformer)))
(s/def ::module-score-created (s/conformer datetime-conformer))
(s/def ::module-score-accepted (s/nilable boolean?))

(s/def ::module-score (s/keys :req [::module-score-id
                                    ::module-id
                                    ::module-score-created
                                    ::module-score-updated
                                    ::module-score-points
                                    ::module-score-accepted]))
(s/def ::section-score-id ::id)
(s/def ::section-id ::id)

(s/def ::section-score-updated (s/nilable (s/conformer datetime-conformer)))
(s/def ::section-score-created (s/conformer datetime-conformer))
(s/def ::section-score-accepted (s/nilable ::boolean))

(s/def ::exam-session-id ::id)
(s/def ::ext-reference-id ::non-blank-string)
(s/def ::participant-id ::id)
(s/def ::section-registration-id ::id)
(s/def ::section-registration-section-id ::id)
(s/def ::module-registration-id ::id)
(s/def ::module-registration-section-id ::id)
(s/def ::section-score (s/keys :req [::section-score-id
                                     ::section-id
                                     ::section-score-accepted
                                     ::section-score-created
                                     ::section-score-updated
                                     ::exam-session-id
                                     ::participant-id
                                     ::ext-reference-id]))

(s/def ::section-accreditation-date date-conformer)
(s/def ::module-accreditation-date date-conformer)
(s/def ::module-accreditation (s/keys :req [::module-id
                                            ::module-accreditation-date]))
(s/def ::section-accreditation (s/keys :req [::section-id
                                             ::section-accreditation-date]))
(s/def ::section-registration (s/keys :req [::section-registration-id
                                            ::section-registration-section-id
                                            ::exam-session-id]))
(s/def ::module-registration (s/keys :req [::module-registration-id
                                           ::module-registration-module-id
                                           ::exam-session-id]))


(s/def ::section-accreditation-conformer
  (s/conformer (fn [{:keys [section_accreditation_section_id
                            section_accreditation_date]}]
                 (let [section-accreditation {::section-id section_accreditation_section_id
                                              ::section-accreditation-date section_accreditation_date}]
                   (if (s/valid? ::section-accreditation section-accreditation)
                     section-accreditation
                     ::s/invalid)))))

(s/def ::module-accreditation-conformer
  (s/conformer (fn [{:keys [section_accreditation_section_id
                            section_accreditation_date
                            module_accreditation_module_id
                            module_accreditation_date]}]
                 (let [module-accreditation {::module-id module_accreditation_module_id
                                             ::module-accreditation-date module_accreditation_date}]
                   (if (s/valid? ::module-accreditation module-accreditation)
                     module-accreditation
                     ::s/invalid)))))

(s/def ::module-score-conformer
  (s/conformer (fn [{:keys [module_score_id module_id module_score_points
                            module_score_accepted section_score_id module_score_created
                            module_score_updated]}]
                 (let [module-score {::module-score-id module_score_id
                                     ::module-id module_id
                                     ::module-score-points module_score_points
                                     ::module-score-created  module_score_created
                                     ::module-score-updated  module_score_updated
                                     ::module-score-accepted module_score_accepted
                                     ::section-score-id section_score_id}]
                   (if (s/valid? ::module-score module-score)
                     module-score
                     ::s/invalid)))))

(s/def ::section-score-conformer
  (s/conformer (fn [{:keys [section_score_id
                            section_id
                            section_score_created
                            section_score_updated
                            section_score_accepted
                            exam_session_id
                            id
                            ext_reference_id]}]
                 (let [section-score {::section-score-id section_score_id
                                      ::section-id section_id
                                      ::section-score-created section_score_created
                                      ::section-score-updated section_score_updated
                                      ::section-score-accepted section_score_accepted
                                      ::exam-session-id exam_session_id
                                      ::participant-id id
                                      ::ext-reference-id ext_reference_id}]
                   (if (s/valid? ::section-score section-score)
                     section-score
                     ::s/invalid)))))

(s/def ::section-registration-conformer
  (s/conformer (fn [{:keys [section_registration_section_id
                            section_registration_id
                            exam_session_id]}]
                 (let [section-registration {::section-registration-id section_registration_id
                                             ::section-registration-section-id section_registration_section_id
                                             ::exam-session-id exam_session_id}]
                   (if (s/valid? ::section-registration section-registration)
                     section-registration
                     ::s/invalid)))))

(s/def ::module-registration-conformer
  (s/conformer (fn [{:keys [module_registration_module_id
                            module_registration_id
                            exam_session_id]}]
                 (let [module-registration {::module-registration-id module_registration_id
                                            ::module-registration-module-id module_registration_module_id
                                            ::exam-session-id exam_session_id}]
                   (if (s/valid? ::module-registration module-registration)
                     module-registration
                     ::s/invalid)))))
