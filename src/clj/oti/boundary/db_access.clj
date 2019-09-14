(ns oti.boundary.db-access
  (:require [jeesql.core :refer [require-sql]]
            [clojure.java.jdbc :as jdbc]
            [duct.component.hikaricp]
            [oti.spec :as spec]
            [clojure.spec.alpha :as s]
            [clojure.set :as cs]
            [clojure.string :as str]
            [clojure.tools.logging :as log :refer [error]]
            [meta-merge.core :refer [meta-merge]]
            [oti.db-states :as states])
  (:import [duct.component.hikaricp HikariCP]))

(require-sql ["oti/queries.sql" :as q])

(defn- translation-by-key-fn [key]
  (fn [ts e]
    (assoc ts (keyword (or (:language_code e) (:lang e))) (key e))))

(defn- group-exam-session-translations [exam-sessions]
  (->> (partition-by :id exam-sessions)
       (map (fn [sessions]
              (let [session (apply merge sessions)
                    street-addresses (reduce (translation-by-key-fn :street_address) {} sessions)
                    cities (reduce (translation-by-key-fn :city) {} sessions)
                    other-location-infos (reduce (translation-by-key-fn :other_location_info) {} sessions)]
                (merge session {:street_address street-addresses
                                :city cities
                                :other_location_info other-location-infos}))))))

(defn- group-modules [module-groups]
  (map (fn [modules]
         (let [{:keys [module_id module_score_id]} (apply merge modules)
               name (reduce (translation-by-key-fn :module_name) {} modules)]
           {:id module_id
            :previously-attempted? module_score_id
            :accepted? false
            :name name}))
       module-groups))

(defn- group-sections-and-modules [resultset]
  (->> (partition-by :section_id resultset)
       (map (fn [sections]
              (let [{:keys [section_id section_score_id]} (apply merge sections)
                    name (reduce (translation-by-key-fn :section_name) {} sections)
                    modules (->> (partition-by :module_id sections)
                                 group-modules)]
                {:id section_id
                 :previously-attempted? section_score_id
                 :accepted? false
                 :name name
                 :modules modules})))))

(defn- translatable-keys-from-exam-session [es]
  (select-keys es [::spec/city
                   ::spec/street-address
                   ::spec/other-location-info]))

(defn- keys-of-mapval [m] (-> m second keys))

(defn- exam-session-translation [street-address city other-location-info lang exam-session-id]
  (if (and street-address city other-location-info lang exam-session-id)
    {:street-address      street-address
     :city                city
     :other-location-info other-location-info
     :language-code       (name lang)
     :exam-session-id     exam-session-id}
    (throw (Exception. "Error occured creating exam-session translation. Missing or invalid params."))))

(defn- store-exam-session-translation [lang tx exam-session update?]
  (let [street-address      (get-in exam-session [::spec/street-address lang])
        city                (get-in exam-session [::spec/city lang])
        other-location-info (get-in exam-session [::spec/other-location-info lang])
        exam-session-id     (get exam-session ::spec/id)
        translation         (exam-session-translation street-address
                                                      city
                                                      other-location-info
                                                      lang exam-session-id)
        db-fn               (if update? q/update-exam-session-translation! q/insert-exam-session-translation!)]
    (db-fn tx translation)))

(defn- store-exam-session-translations [tx exam-session update?]
  (let [langs (set (mapcat keys-of-mapval (translatable-keys-from-exam-session exam-session)))]
    (if (::spec/id exam-session)
      (mapv #(store-exam-session-translation % tx exam-session update?) langs)
      (throw (Exception. "Error occured inserting translations. Missing exam session id.")))))

(defn- insert-exam-session [tx exam-session]
  (or (q/insert-exam-session<! tx exam-session)
      (throw (Exception. "Could not create new exam session."))))

(defn- store-registration! [tx {::spec/keys [session-id language-code email sections]} external-user-id state existing-reg-id retry?]
  (let [conn tx]
    (q/insert-participant! conn {:external-user-id external-user-id :email email :language-code (name language-code)})
    (doseq [[section-id {::spec/keys [accreditation-type-id]} _] (filter (fn [[_ options]] (::spec/accredit? options)) sections)]
      (q/insert-section-accreditation! conn {:section-id section-id :accreditation-type-id accreditation-type-id :external-user-id external-user-id}))
    (let [registarable-sections (remove (fn [[_ options]] (::spec/accredit? options)) sections)]
      (when (pos? (count registarable-sections))
        (if-let [reg-id (or existing-reg-id
                            (:id (q/insert-registration<! conn {:session-id session-id
                                                                :external-user-id external-user-id
                                                                :retry retry?
                                                                :state state})))]
          (do
            (doseq [[section-id opts] registarable-sections]
              (let [params {:section-id       section-id
                            :external-user-id external-user-id
                            :registration-id  reg-id}]
                (q/insert-section-registration! conn params)
                (let [all-modules (->> (q/select-modules-for-section conn params) (map :id) set)
                      register-modules (or (seq (::spec/retry-modules opts))
                                           (cs/difference all-modules (::spec/accredit-modules opts)))]
                  (doseq [module-id register-modules]
                    (q/insert-module-registration! conn (assoc params :module-id module-id)))
                  (doseq [module-id (::spec/accredit-modules opts)]
                    (q/insert-module-accreditation! conn (assoc params :module-id module-id))))))
            reg-id)
          (throw (Exception. "No registeration id received.")))))))

(defn- update-payment-and-registration-state! [spec params payment-state registration-state]
  (jdbc/with-db-transaction [tx spec {:isolation :serializable}]
    (let [q-fn (if (:pay-id params) q/update-payment! q/update-payment-state!)]
      (q-fn tx (assoc params :state payment-state))
      (q/update-registration-state-by-payment-order! tx (assoc params :state registration-state)))))

(defn- cancel-obsolete-payments-and-registrations! [spec]
  (jdbc/with-db-transaction [tx spec {:isolation :serializable}]
    (let [payment-ids (-> (q/cancel-obsolete-payments<! tx) :id)
          registration-ids (-> (q/cancel-obsolete-registrations<! tx) :id)]
      {:payment-ids payment-ids :registration-ids registration-ids})))

(defn- snake-keys [p]
  (let [vals (vals p)
        keys (keys p)
        new-keys (map #(-> (str/replace (name %) "_" "-") keyword) keys)]
    (when (and (seq vals) (seq keys))
      (zipmap new-keys vals))))

(defprotocol DbAccess
  (exam-sessions [db start-date end-date])
  (add-exam-session! [db exam-session])
  (save-exam-session! [db exam-session])
  (remove-exam-session! [db id])
  (published-exam-sessions-with-space-left [db])
  (exam-session [db id])
  (exam-sessions-full [db lang])
  (sections-and-modules-available-for-user [db external-user-id])
  (register! [db registration-data external-user-id state payment-data existing-reg-id retry?])
  (registrations-for-session [db exam-session])
  (existing-registration-id [db exam-session-id external-user-id])
  (section-and-module-names [db])
  (participant-by-ext-id [db external-user-id])
  (participant-by-id [db id])
  (participant-by-order-number [db order-number lang])
  (all-participants [db])
  (all-participants-by-ext-references [db ext-references])
  (confirm-registration-and-payment! [db params])
  (cancel-registration-and-payment! [db params])
  (cancel-obsolete-registrations-and-payments! [db])
  (cancel-payment-set-reg-incomplete! [db params])
  (update-registration-state! [db id state])
  (next-order-number! [db])
  (unpaid-payments [db])
  (unpaid-payments-by-participant [db external-user-id])
  (unpaid-payment-by-registration [db registration-id])
  (paid-payments [db start-date end-date])
  (update-payment-order-number-and-ts! [db params])
  (paid-credit-card-payments [db])
  (language-code-by-order-number [db order-number])
  (participant-ext-reference-by-order-number [db order-number])
  (registration-state-by-id [db id])
  (add-email-by-participant-id! [db params])
  (unsent-emails-for-update [db tx])
  (set-email-sent! [db tx email-id])
  (scores-email [db params])
  (health-check [db])
  (accreditation-types [db])
  (update-accreditations! [db params])
  (delete-section-accreditation! [db participant-id section-id])
  (add-token-to-exam-session! [db id token])
  (access-token-for-exam-session [db id])
  (access-token-matches-session? [db id token])
  (update-participant-diploma-data! [db update-params])
  (diploma-count [db start-date end-date])
  (exam-by-lang [db lang])
  (upsert-section-score [db section-score])
  (upsert-module-score [db module-score])
  (module-score [db params])
  (section-score [db params])
  (delete-scores-by-score-ids [db ids])
  (registration-by-participant-id [db id]))

(extend-type HikariCP
  DbAccess
  (exam-sessions [{:keys [spec]} start-date end-date]
    (-> (q/select-exam-sessions spec {:start-date start-date :end-date end-date})
        group-exam-session-translations))
  (published-exam-sessions-with-space-left [{:keys [spec]}]
    (group-exam-session-translations (q/published-exam-sessions-in-future-with-space-left spec)))
  (exam-session [{:keys [spec]} id]
    (-> (q/exam-session-by-id spec {:id id}) group-exam-session-translations))
  (add-exam-session! [{:keys [spec]} exam-session]
    (jdbc/with-db-transaction [tx spec]
      (let [new-exam-session (insert-exam-session tx exam-session)]
        (store-exam-session-translations tx (assoc exam-session ::spec/id (:id new-exam-session)) false)
        new-exam-session)))
  (save-exam-session! [{:keys [spec]} exam-session]
    (jdbc/with-db-transaction [tx spec]
      (q/update-exam-session! tx exam-session)
      (store-exam-session-translations tx exam-session :update)))
  (remove-exam-session! [{:keys [spec]} id]
    (q/delete-exam-session-translations! spec {:exam-session-id id})
    (q/delete-exam-session! spec {:exam-session-id id}))

  (exam-sessions-full [{:keys [spec] :as db} lang]
    (q/exam-sessions-full spec {:lang lang}))

  (sections-and-modules-available-for-user [{:keys [spec]} external-user-id]
    (->> (q/select-modules-available-for-user spec {:external-user-id external-user-id})
         group-sections-and-modules))
  (register! [{:keys [spec]} registration-data external-user-id state payment-data existing-reg-id retry?]
    (jdbc/with-db-transaction [tx spec {:isolation :serializable}]
      (let [reg-id (store-registration! tx registration-data external-user-id state existing-reg-id retry?)]
        (when payment-data
          (->> (assoc payment-data :registration-id reg-id)
               (q/insert-payment! tx)))
        reg-id)))
  (registrations-for-session [{:keys [spec]} exam-session-id]
    (->> (q/select-registrations-for-exam-session spec {:exam-session-id exam-session-id})
         (partition-by :id)
         (map (fn [registration-rows]
                (let [sections (->> (partition-by :section_id registration-rows)
                                    (map (fn [s-rows] [(:section_id (first s-rows)) (remove nil? (map :module_id s-rows))]))
                                    (into {}))
                      {:keys [id created language_code, participant_id ext_reference_id payment_state]} (first registration-rows)]
                  {:id id
                   :created created
                   :lang (keyword language_code)
                   :participant-id participant_id
                   :external-user-id ext_reference_id
                   :sections sections
                   :payment-state payment_state})))))
  (existing-registration-id [{:keys [spec]} exam-session-id external-user-id]
    (-> (q/select-existing-registration-id spec {:external-user-id external-user-id :exam-session-id exam-session-id})
        first
        :id))
  (section-and-module-names [{:keys [spec]}]
    (->> (q/select-section-and-module-names spec)
         (reduce (fn [names {:keys [section_id section_name module_id module_name]}]
                   (meta-merge names {:sections {section_id section_name}
                                      :modules {module_id module_name}}))
                 {})))
  (participant-by-ext-id [{:keys [spec]} external-user-id]
    (q/select-participant spec {:external-user-id external-user-id}))
  (participant-by-id [{:keys [spec]} id]
    (q/select-participant-by-id spec {:id id}))
  (participant-by-order-number [{:keys [spec]} order-number lang]
    (q/select-participant-by-payment-order-number spec {:order-number order-number :lang lang}))
  (all-participants [{:keys [spec]}]
    (q/select-all-participants spec))
  (all-participants-by-ext-references [{:keys [spec]} ext-ids]
    (q/select-all-participants-by-ext-references spec {:ext-reference-ids ext-ids}))
  (confirm-registration-and-payment! [{:keys [spec]} params]
    (update-payment-and-registration-state! spec params states/pmt-ok states/reg-ok))
  (cancel-registration-and-payment! [{:keys [spec]} params]
    (update-payment-and-registration-state! spec params states/pmt-error states/reg-cancelled))
  (cancel-obsolete-registrations-and-payments! [{:keys [spec]}]
    (cancel-obsolete-payments-and-registrations! spec))
  (cancel-payment-set-reg-incomplete! [{:keys [spec]} params]
    (update-payment-and-registration-state! spec params states/pmt-error states/reg-cancelled))
  (update-registration-state! [{:keys [spec]} id state]
    (q/update-registration-state! spec {:state state :id id}))
  (next-order-number! [{:keys [spec]}]
    (-> (q/select-next-order-number-suffix spec)
        first
        :nextval))
  (unpaid-payments [{:keys [spec]}]
    (q/select-unpaid-payments spec))
  (unpaid-payments-by-participant [{:keys [spec]} external-user-id]
    (q/select-unpaid-payments-by-participant spec {:external-user-id external-user-id}))
  (unpaid-payment-by-registration [{:keys [spec]} registration-id]
    (-> (q/select-unpaid-payment-by-registration-id spec {:registration-id registration-id})
        first))
  (paid-payments [{:keys [spec]} start-date end-date]
    (q/select-paid-payments spec {:start-date start-date :end-date end-date}))
  (update-payment-order-number-and-ts! [{:keys [spec]} params]
    (q/update-payment-order-and-timestamp! spec params))
  (paid-credit-card-payments [{:keys [spec]}]
    (q/select-credit-card-payments spec))
  (language-code-by-order-number [{:keys [spec]} order-number]
    (-> (q/select-language-code-by-order-number spec {:order-number order-number})
        first
        :language_code))
  (participant-ext-reference-by-order-number [{:keys [spec]} order-number]
    (-> (q/select-participant-ext-reference-by-order-number spec {:order-number order-number})
        first
        :ext_reference_id))
  (registration-state-by-id [{:keys [spec]} id]
    (-> (q/select-registration-state-by-id spec {:id id})
        first
        :state))
  (add-email-by-participant-id! [{:keys [spec]} params]
    (q/insert-email-by-participant-id! spec params))
  (unsent-emails-for-update [db tx]
    (q/select-unsent-email-for-update tx))
  (set-email-sent! [db tx email-id]
    (q/mark-email-sent! tx {:id email-id}))
  (scores-email [{:keys [spec]} {:keys [participant-id exam-session-id email-type]}]
    (first (q/select-email spec {:participant-id participant-id
                            :exam-session-id exam-session-id
                            :email-type email-type})))
  (health-check [{:keys [spec]}]
    (-> (q/select-exam-count spec)
        first
        :exam_count
        pos?))
  (accreditation-types [{:keys [spec]}]
    (q/select-accreditation-types spec))
  (update-accreditations! [{:keys [spec]} params]
    (jdbc/with-db-transaction [tx spec {:isolation :serializable}]
      (doseq [section (:sections params)]
        (q/update-section-accreditation! tx section))
      (doseq [module (:modules params)]
        (q/update-module-accreditation! tx module))))
  (delete-section-accreditation! [{:keys [spec]} participant-id section-id]
    (q/delete-section-accreditation! spec {:participant-id participant-id :section-id section-id}))
  (add-token-to-exam-session! [{:keys [spec]} id token]
    (q/update-exam-session-with-token! spec {:id id :token token}))
  (access-token-for-exam-session [{:keys [spec]} id]
    (-> (q/select-exam-session-access-token spec {:id id})
        first
        :access_token))
  (access-token-matches-session? [{:keys [spec]} id token]
    (-> (q/select-exam-session-matching-token spec {:id id :token token})
        first
        :id
        (= id)))
  (update-participant-diploma-data! [{:keys [spec]} update-params]
    (jdbc/with-db-transaction [tx spec {:isolation :serializable}]
      (doseq [participant-update update-params]
        (q/update-participant-diploma! tx participant-update))))
  (diploma-count [{:keys [spec]} start-date end-date]
    (-> (q/select-diploma-count spec {:start-date start-date :end-date end-date})
        first
        :count))
  (exam-by-lang [{:keys [spec]} lang]
    (when-let [section-module-sequence (q/exam-by-lang spec {:lang lang})]
      (->> (group-by :section_id section-module-sequence)
           vals
           (mapv (fn [section-seq]
                  (let [section {:id (:section_id (first section-seq))
                                 :name (:section_name (first section-seq))
                                 :executed-as-whole? (:section_executed_as_whole (first section-seq))
                                 :modules []}]
                    (reduce (fn [ss m]
                              (update ss :modules conj {:id (:module_id m)
                                                        :name (:module_name m)
                                                        :points? (:module_points m)
                                                        :section-id (:section_id m)
                                                        :accepted-separately? (:module_accepted_separately m)}))
                            section
                            section-seq)))))))
  (upsert-section-score [{:keys [spec]} section-score]
    (snake-keys (q/upsert-participant-section-score<! spec section-score)))
  (upsert-module-score [{:keys [spec]} module-score]
    (snake-keys (q/upsert-participant-module-score<! spec module-score)))
  (section-score [{:keys [spec]} params]
    (snake-keys (first (q/select-section-score spec params))))
  (module-score [{:keys [spec]} params]
    (snake-keys (first (q/select-module-score spec params))))
  (delete-scores-by-score-ids [{:keys [spec]} {:keys [section-score-ids module-score-ids]}]
    (jdbc/with-db-transaction [tx spec {:isolation :serializable}]
      (let [deleted-module-scores (doall (for [module-score-id module-score-ids]
                                           (q/delete-module-score! tx {:id module-score-id})))
            deleted-section-scores (doall (for [section-score-id section-score-ids]
                                            (q/delete-section-score! tx {:id section-score-id})))]
        (into deleted-module-scores deleted-section-scores))))
  (registration-by-participant-id [{:keys [spec]} id]
    (q/select-registrations-by-participant-id spec {:id id})))
