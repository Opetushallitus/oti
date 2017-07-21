(ns oti.service.registration
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.set :as set]
            [oti.boundary.db-access :as dba]
            [oti.component.localisation :as loc]
            [oti.spec :as os]
            [clojure.tools.logging :refer [error info]]
            [meta-merge.core :refer [meta-merge]]
            [oti.boundary.api-client-access :as api]
            [oti.exam-rules :as rules]
            [oti.boundary.payment :as payment-util]
            [oti.util.logging.audit :as audit]
            [oti.component.email-service :as email]
            [clojure.set :as set]
            [oti.service.user-data :as user-data]
            [oti.db-states :as states])
  (:import [java.time LocalDateTime]
           [java.time.format DateTimeFormatter]
           [java.util Locale]))

(defn- valid-address [data]
  (let [addr (select-keys data [::os/registration-city ::os/registration-post-office ::os/registration-zip
                                ::os/registration-street-address])]
    (when (s/valid? ::os/postal-address addr)
      addr)))

(defn- api-address-list [registration-data]
  (mapv (fn [[api-type key]]
          (when-let [data (key registration-data)]
            {:yhteystietoTyyppi api-type
             :yhteystietoArvo data}))
        user-data/address-mapping))

(defn- existing-address [existing-person address-origin address-list]
  (let [addr-set (set address-list)]
    (->> (:yhteystiedotRyhma existing-person)
         (some (fn [{:keys [ryhmaAlkuperaTieto yhteystiedot id]}]
                 (when (and (= ryhmaAlkuperaTieto address-origin)
                            (->> (map #(dissoc % :id) yhteystiedot)
                                 set
                                 (set/intersection addr-set)
                                 (= addr-set)))
                   id))))))

(defn store-address-to-service! [api-client external-user-id participant-data reg-data]
  (if-let [existing-person (api/get-person-by-id api-client external-user-id)]
    (let [vtj-address (valid-address participant-data)
          address (or vtj-address (valid-address reg-data))
          {:keys [type origin]} (if vtj-address user-data/vtj-address-opts user-data/domestic-address-opts)
          address-list (api-address-list address)
          existing-address-id (existing-address existing-person origin address-list)]
      (when-not (or vtj-address address)
        (throw (Exception. (str "No valid postal address provided for user " external-user-id))))
      (if-not existing-address-id
        (api/update-person! api-client
                            external-user-id
                            (update existing-person
                                    :yhteystiedotRyhma
                                    conj {:ryhmaAlkuperaTieto origin
                                          :ryhmaKuvaus type
                                          :yhteystiedot address-list}))
        (info "Not storing address for user" external-user-id "because address already exists as id" existing-address-id)))
    (do (error "Could not retrieve user" external-user-id "from API for address update, cannot proceed")
        (throw (Exception. "Could not store address for user")))))

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

(defn- gen-reference-number [external-id]
  (let [with-prefix (str "900" external-id)
        reference-number-with-checknum (s/conform ::os/reference-number-conformer with-prefix)]
    (when-not (s/invalid? reference-number-with-checknum)
      reference-number-with-checknum)))

(defn- payment-param-map [localisation lang amount ref-number order-number]
  #::os{:timestamp        (LocalDateTime/now)
        :language-code    (keyword lang)
        :amount           amount
        :reference-number ref-number
        :order-number     order-number
        :app-name         (loc/t localisation lang "vetuma-app-name")
        :msg              (loc/t localisation lang "payment-name")
        :payment-id       order-number})

(defn- payment-params [{:keys [db localisation]} external-user-id amount lang]
  (let [ref-number (-> (str/split external-user-id #"\.") last gen-reference-number)
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

(defn- reg-state-of-last-session-rec [sections state top-registration-id]
  (if (empty? sections)
    state
    (if (empty? (:sessions (first sections)))
      (reg-state-of-last-session-rec (rest sections) state top-registration-id)
      (let [session (last (:sessions (first sections)))
            registration-id (:registration-id session)
            registration-state (:registration-state session)
            new-state (if (>= registration-id top-registration-id) registration-state state)]
        (reg-state-of-last-session-rec (rest sections) new-state (max top-registration-id registration-id))))))

(defn- reg-state-of-last-session [sections]
  (reg-state-of-last-session-rec sections nil -1))


(defn- participant-has-valid-full-payment?
  "Checks if the participant's full payment is still valid for registration:
   1) Participant's first accredited or accepted section has been completed less than 2 years ago, or the participant
      has not completed anything.
   2) Participant has made a successful full payment and has not been absent without approval from the original registration
   3) There's still a section that the participant has not registered to or requested approval for (approved absence counts as no),
      as the original full payment allows only one try per section, and possible non-approved absence will lead to a new full payment."
  [{:keys [db]} {:keys [payments sections]}]
  (let [ref-ts (-> (LocalDateTime/now) (.minusYears 2))
        still-valid? (not-any? (fn [{:keys [accepted score-ts accreditation-date]}]
                                 (or (and accepted (-> (.toLocalDateTime score-ts) (.isBefore ref-ts)))
                                     (and accreditation-date (-> (.toLocalDate accreditation-date) (.atStartOfDay) (.isBefore ref-ts)))))
                               sections)
        required-sections (->> (dba/section-and-module-names db) :sections keys set)
        valid-full-payment? (some (fn [{:keys [registration-state registration-id state type]}]
                                    (and (or (nil? registration-id) (#{states/reg-ok states/reg-absent-approved} registration-state))
                                         (= states/pmt-ok state)
                                         (= "FULL" type)))
                                  payments)
        attempted-sections (->> sections
                                (filter (fn [{:keys [sessions accreditation-requested?]}]
                                          (or accreditation-requested?
                                              (->> sessions
                                                   (filter #(#{states/reg-ok} (:registration-state %)))
                                                   seq))))
                                (map :id)
                                set)
        something-not-attempted? (seq (set/difference required-sections attempted-sections))
        last-reg-not-absent? (not (= states/reg-absent (reg-state-of-last-session sections)))]
    (and still-valid? valid-full-payment? something-not-attempted? last-reg-not-absent?)))

(defn- participant-has-valid-retry-payment?
  "Checks if the participant has a valid retry payment. This is only possible if the
   participant has been absent from a retry exam with approval."
  [{:keys [db]} {:keys [payments id]}]
  (let [refund-count (->> payments
                          (filter (fn [{:keys [state type registration-state]}]
                                    (and (= states/pmt-ok state)
                                         (= "PARTIAL" type)
                                         (= states/reg-absent-approved registration-state))))
                          count)
        unpaid-retry-count (->> (dba/registration-by-participant-id db id)
                                (filter #(and (:retry %) (nil? (:payment_type %)) (#{states/reg-ok states/reg-absent} (:state %))))
                                count)]
    (> refund-count unpaid-retry-count)))

(defn payment-amounts [{{{:keys [full retry]} :amounts} :payments :as config} participant-id]
  (if-let [user-data (when participant-id (user-data/participant-data config participant-id))]
    {:full (if (participant-has-valid-full-payment? config user-data) 0 full)
     :retry (if (participant-has-valid-retry-payment? config user-data) 0 retry)}
    {:full full
     :retry retry}))

(defn register! [{:keys [db api-client vetuma-payment] :as config}
                 {old-session :session {:keys [registration-data ui-lang]} :body-params}]
  (let [conformed (s/conform ::os/registration registration-data)]
    (if-not (s/invalid? conformed)
      (let [lang (::os/language-code conformed)
            participant-data (-> old-session :participant)
            external-user-id (or (:external-user-id participant-data)
                                 (:oidHenkilo (api/get-person-by-hetu api-client (:hetu participant-data)))
                                 (store-person-to-service! api-client participant-data (::os/preferred-name conformed) lang))
            session (assoc-in old-session [:participant :external-user-id] external-user-id)
            valid?  (and external-user-id
                         (valid-registration? config external-user-id conformed))
            price-type (rules/price-type-for-registration conformed)
            amount (price-type (payment-amounts config (:id participant-data)))
            reg-state (if (zero? amount) states/reg-ok states/reg-incomplete)]
        (if valid?
          (try
            (store-address-to-service! api-client external-user-id participant-data conformed)
            (let [pmt (when (pos? amount) (payment-params config external-user-id amount (keyword ui-lang)))
                  db-pmt (payment-params->db-payment pmt price-type external-user-id)
                  payment-form-data (when pmt (payment-util/form-data-for-payment vetuma-payment pmt))
                  msg-key (if (pos? amount) "registration-payment-pending" "registration-complete")
                  status (if (pos? amount) :pending :success)
                  ; If the participant has registered to this session before, we fetch the existing reg id and add the
                  ; additional sections / modules to it
                  existing-registration-id (dba/existing-registration-id db (::os/session-id conformed) external-user-id)
                  registration-id (dba/register! db conformed external-user-id reg-state db-pmt existing-registration-id (= price-type :retry))]
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
                    :email-type "REGISTRATION_CONFIRMATION"
                    :lang lang
                    :template-id :registration-success
                    :template-values values}]
    (email/send-email-to-participant! email-service db email-data)))

(defn fetch-registrations [{:keys [db api-client]} session-id]
  (if-let [regs (seq (dba/registrations-for-session db session-id))]
    (let [oids (map :external-user-id regs)
          user-data-by-oid (user-data/api-user-data-by-oid api-client oids)]
      (map (fn [{:keys [external-user-id] :as registration}]
             (-> (get user-data-by-oid external-user-id)
                 (select-keys [:etunimet :sukunimi])
                 (merge registration)))
           regs))
    []))

(defn cancel-registration! [{:keys [db]} registration-id state {{authority :username} :identity}]
  {:pre [(pos-int? registration-id) (#{states/reg-cancelled states/reg-absent states/reg-absent-approved} state)]}
  (audit/log :app :admin
             :who authority
             :on :registration
             :op :update
             :before {:id registration-id
                      :state (dba/registration-state-by-id db registration-id)}
             :after {:id    registration-id
                     :state state}
             :msg "Registration cancelled.")
  (= 1 (dba/update-registration-state! db registration-id state)))
