(ns oti.service.registration
  (:require [clojure.spec :as s]
            [clojure.string :as str]
            [oti.boundary.db-access :as dba]
            [oti.component.localisation :as loc]
            [oti.spec :as os]
            [taoensso.timbre :refer [error info]]
            [meta-merge.core :refer [meta-merge]]
            [oti.boundary.api-client-access :as api]
            [oti.exam-rules :as rules]
            [oti.boundary.payment :as payment-util]
            [oti.util.logging.audit :as audit]
            [oti.component.email-service :as email])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]
           [java.util Locale]))

(defn- store-person-to-service! [api-client {:keys [etunimet sukunimi hetu]} preferred-name lang]
  (api/add-person! api-client
                   {:sukunimi sukunimi
                    :etunimet (str/join " " etunimet)
                    :kutsumanimi preferred-name
                    :hetu hetu
                    :henkiloTyyppi "OPPIJA"
                    :asiointiKieli {:kieliKoodi (name lang)}}))

(defn- registration-response
  ([status-code status text session]
    (registration-response status-code status text session nil nil))
  ([status-code status text session registration-id payment-data]
   (let [session-data {:registration-message text
                       :registration-status status
                       :registration-id registration-id}
         body (cond-> session-data
                      payment-data (assoc ::os/payment-form-data payment-data))]
     {:status status-code :body body :session (meta-merge session {:participant session-data})})))

(defn- valid-module-registration? [{:keys [modules]} reg-modules reg-type]
  (every?
    (fn [registered-to-module-id]
      (some #(and (= registered-to-module-id (:id %))
                  (not (:accepted %))
                  (if (= :retry reg-type)
                    (:previously-attempted? %)
                    (not (:previously-attempted? %))))
            modules))
    reg-modules))

(defn- valid-registration? [{:keys [db]} external-user-id {::os/keys [sections]}]
  (let [avail-sections (dba/sections-and-modules-available-for-user db external-user-id)]
    (every? (fn [[id {::os/keys [retry? retry-modules accredit-modules]}]]
              (when-let [sect (first (filter #(= id (:id %)) avail-sections))]
                (and
                  (not (:accepted? sect))
                  (or (not retry?) (:previously-attempted? sect))
                  (valid-module-registration? sect retry-modules :retry)
                  (valid-module-registration? sect accredit-modules :accredit))))
            sections)))

(defn- gen-order-number [db reference]
  (let [order-suffix (dba/next-order-number! db)]
    (str "OTI" reference order-suffix)))

(defn- payment-param-map [localisation lang amount ref-number order-number]
  #::os{:timestamp        (LocalDateTime/now)
        :language-code    (keyword lang)
        :amount           amount
        :reference-number (bigdec ref-number)
        :order-number     order-number
        :app-name         (loc/t localisation lang "vetuma-app-name")
        :msg              (loc/t localisation lang "payment-name")
        :payment-id       order-number})

(defn- payment-params [{:keys [db localisation]} external-user-id amount lang]
  (let [ref-number (-> (str/split external-user-id #"\.") last)
        order-number (gen-order-number db ref-number)]
    (payment-param-map localisation lang amount ref-number order-number)))

(defn- payment-params->db-payment [{::os/keys [timestamp amount reference-number order-number payment-id] :as params}
                                   type external-user-id]
  (when params
    {:created timestamp,
     :type (if (= type :full) "FULL" "PARTIAL")
     :amount amount
     :reference reference-number
     :order-number order-number
     :payment-id payment-id
     :external-user-id external-user-id}))

(defn- db-payment->payment-params [{:keys [db localisation]} {:keys [amount reference]} ui-lang]
  (let [new-order-number (gen-order-number db reference)]
    (payment-param-map localisation ui-lang amount reference new-order-number)))

(defn- update-db-payment! [db payment-id {::os/keys [order-number timestamp] :as payment-params}]
  (dba/update-payment-order-number-and-ts! db {:id payment-id
                                               :order-number order-number
                                               :created timestamp})
  payment-params)

(defn payment-amounts [{:keys [payments db]} external-user-id]
  (let [paid? (and external-user-id (pos? (dba/valid-full-payments-for-user db external-user-id)))]
    {:full (if paid? 0 (-> payments :amounts :full))
     :retry (-> payments :amounts :retry)}))

(defn register! [{:keys [db api-client vetuma-payment] :as config}
                 {old-session :session {:keys [registration-data ui-lang]} :params}]
  (let [conformed (s/conform ::os/registration registration-data)]
    (if-not (s/invalid? conformed)
      (let [lang (::os/language-code conformed)
            participant-data (-> old-session :participant)
            external-user-id (or (:external-user-id participant-data)
                                 (:oidHenkilo (api/get-person-by-hetu api-client (:hetu participant-data)))
                                 (store-person-to-service! api-client participant-data (::os/preferred-name conformed) lang))
            session (assoc-in old-session [:participant :external-user-id] external-user-id)
            valid?  (and (not (s/invalid? conformed))
                         external-user-id
                         (valid-registration? config external-user-id conformed))
            price-type (rules/price-type-for-registration conformed)
            amount (price-type (payment-amounts config external-user-id))
            reg-state (if (zero? amount) "OK" "INCOMPLETE")]
        (if valid?
          (try
            (let [pmt (when (pos? amount) (payment-params config external-user-id amount (keyword ui-lang)))
                  db-pmt (payment-params->db-payment pmt price-type external-user-id)
                  payment-form-data (when pmt (payment-util/form-data-for-payment vetuma-payment pmt))
                  msg-key (if (pos? amount) "registration-payment-pending" "registration-complete")
                  status (if (pos? amount) :pending :success)
                  registration-id (dba/register! db conformed external-user-id reg-state db-pmt)]
              (audit/log :app :participant
                         :who external-user-id
                         :op :create
                         :on :registration
                         :after {:id registration-id}
                         :msg "New registration")
              (registration-response 200 status msg-key session registration-id payment-form-data))
            (catch Throwable t
              (error t "Error inserting registration")
              (registration-response 500 :error "registration-unknown-error" session)))
          (do
            (error "Invalid registration data. Valid selection:" (valid-registration? config external-user-id conformed)
                   "External user id:" external-user-id)
            (registration-response 400 :error "registration-invalid-data" session))))
      (do
        (error "Invalid registration data. Spec errors: " (s/explain-data ::os/registration registration-data))
        (registration-response 400 :error "registration-invalid-data" old-session)))))

(defn payment-data-for-retry [{:keys [db vetuma-payment] :as config}
                              {{{:keys [registration-id registration-status]} :participant :as session} :session {:keys [lang]} :params}]
  (if (= :pending registration-status)
    (if-let [{payment-id :id :as db-pmt} (dba/unpaid-payment-by-registration db registration-id)]
      (->> (db-payment->payment-params config db-pmt lang)
           (update-db-payment! db payment-id)
           (payment-util/form-data-for-payment vetuma-payment)
           (registration-response 200 :pending "registration-payment-pending" session registration-id))
      (do
        (error "Tried to retry payment for registration" registration-id "but matching payment was not found")
        (registration-response 404 :error "registration-payment-expired" session)))
    (do
      (error "Tried to retry payment for registration" registration-id "but it's status is not pending")
      (registration-response 400 :error "registration-unknown-error" session))))

(def formatter (DateTimeFormatter/ofPattern "d.M.yyyy"))

(defn- format-date-and-time [{:keys [session-date start-time end-time]}]
  (if session-date
    (str
      (-> session-date .toLocalDate (.format formatter))
      " " start-time " - " end-time)
    "-"))

(defn- format-registration-selections [sections]
  (->> sections
       (map
         (fn [{:keys [name sessions]}]
           (let [modules (->> (first sessions)
                              :modules
                              vals
                              (filter :registered-to?)
                              (map :name))]
             (str name " (" (str/join ", " modules) ")"))))
       (str/join ", ")))

(defn- format-amount [n]
  (String/format (Locale. "fi"), "%.2f", (to-array [(double n)])))

(defn send-confirmation-email! [{:keys [db email-service]} lang {:keys [sections payments id]}]
  "Note that participant data is expected to contain data about one exam session only"
  (let [values {:date-and-time (-> sections first :sessions first format-date-and-time)
                :sections (format-registration-selections sections)
                :amount (-> payments first :amount format-amount)}
        email-data {:participant-id id
                    :lang lang
                    :template-id :registration-success
                    :template-values values}]
    (email/send-email-to-participant! email-service db email-data)))
