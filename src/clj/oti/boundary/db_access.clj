(ns oti.boundary.db-access
  (:require [yesql.core :refer [require-sql]]
            [clojure.java.jdbc :as jdbc]
            [duct.component.hikaricp]
            [oti.spec :as spec]
            [clojure.spec :as s]
            [clojure.set :as cs]
            [clojure.string :as str]
            [taoensso.timbre :as log :refer [error]]
            [meta-merge.core :refer [meta-merge]])
  (:import [duct.component.hikaricp HikariCP]))

(require-sql ["oti/queries.sql" :as q])

(defn- translation-by-key-fn [key]
  (fn [ts e]
    (assoc ts (keyword (:language_code e)) (key e))))

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
         (let [{:keys [module_id module_accepted module_score_id]} (apply merge modules)
               name (reduce (translation-by-key-fn :module_name) {} modules)]
           {:id module_id
            :previously-attempted? module_score_id
            :accepted module_accepted
            :name name}))
       module-groups))

(defn- group-sections-and-modules [resultset]
  (->> (partition-by :section_id resultset)
       (map (fn [sections]
              (let [{:keys [section_id section_accepted section_score_id]} (apply merge sections)
                    name (reduce (translation-by-key-fn :section_name) {} sections)
                    modules (->> (partition-by :module_id sections)
                                 group-modules)]
                {:id section_id
                 :previously-attempted? section_score_id
                 :accepted? section_accepted
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
    (db-fn translation {:connection tx})))

(defn- store-exam-session-translations [tx exam-session update?]
  (let [langs (set (mapcat keys-of-mapval (translatable-keys-from-exam-session exam-session)))]
    (if (::spec/id exam-session)
      (mapv #(store-exam-session-translation % tx exam-session update?) langs)
      (throw (Exception. "Error occured inserting translations. Missing exam session id.")))))

(defn- insert-exam-session [tx exam-session]
  (or (q/insert-exam-session<! exam-session {:connection tx})
      (throw (Exception. "Could not create new exam session."))))

(defn- store-registration! [tx {::spec/keys [session-id language-code email sections]} external-user-id state existing-reg-id]
  (let [conn {:connection tx}]
    (q/insert-participant! {:external-user-id external-user-id :email email} conn)
    (doseq [[section-id _] (filter (fn [[_ options]] (::spec/accredit? options)) sections)]
      (q/insert-section-accreditation! {:section-id section-id :external-user-id external-user-id} conn))
    (let [registarable-sections (remove (fn [[_ options]] (::spec/accredit? options)) sections)]
      (when (pos? (count registarable-sections))
        (if-let [reg-id (or existing-reg-id
                            (:id (q/insert-registration<! {:session-id session-id
                                                           :external-user-id external-user-id
                                                           :state state
                                                           :language-code (name language-code)} conn)))]
          (do
            (doseq [[section-id opts] registarable-sections]
              (let [params {:section-id       section-id
                            :external-user-id external-user-id
                            :registration-id  reg-id}]
                (q/insert-section-registration! params conn)
                (let [all-modules (->> (q/select-modules-for-section params conn) (map :id) set)
                      register-modules (or (seq (::spec/retry-modules opts))
                                           (cs/difference all-modules (::spec/accredit-modules opts)))]
                  (doseq [module-id register-modules]
                    (q/insert-module-registration! (assoc params :module-id module-id) conn))
                  (doseq [module-id (::spec/accredit-modules opts)]
                    (q/insert-module-accreditation! (assoc params :module-id module-id) conn)))))
            reg-id)
          (throw (Exception. "No registeration id received.")))))))

(defn- update-payment-and-registration-state! [spec params payment-state registration-state]
  (jdbc/with-db-transaction [tx spec {:isolation :serializable}]
    (let [q-fn (if (:pay-id params) q/update-payment! q/update-payment-state!)]
      (q-fn (assoc params :state payment-state) {:connection tx})
      (q/update-registration-state-by-payment-order! (assoc params :state registration-state) {:connection tx}))))

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
  (valid-full-payments-for-user [db external-user-id])
  (register! [db registration-data external-user-id state payment-data existing-reg-id])
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
  (cancel-payment-set-reg-incomplete! [db params])
  (cancel-registration! [db id])
  (next-order-number! [db])
  (unpaid-payments [db])
  (unpaid-payments-by-participant [db external-user-id])
  (unpaid-payment-by-registration [db registration-id])
  (update-payment-order-number-and-ts! [db params])
  (paid-credit-card-payments [db])
  (registration-state-by-id [db id])
  (add-email-by-participant-id! [db params])
  (unsent-emails-for-update [db tx])
  (set-email-sent! [db tx email-id])
  (health-check [db])
  (accreditation-types [db])
  (update-accreditations! [db params])
  (add-token-to-exam-session! [db id token])
  (access-token-for-exam-session [db id])
  (access-token-matches-session? [db id token])
  (update-participant-diploma-data! [db update-params])
  (diploma-count [db start-date end-date])
  (exam-by-lang [db lang])
  (upsert-section-score [db section-score])
  (upsert-module-score [db module-score])
  (module-score [db params])
  (section-score [db params]))

(extend-type HikariCP
  DbAccess
  (exam-sessions [{:keys [spec]} start-date end-date]
    (-> (q/select-exam-sessions {:start-date start-date :end-date end-date} {:connection spec})
        group-exam-session-translations))
  (published-exam-sessions-with-space-left [{:keys [spec]}]
    (group-exam-session-translations (q/published-exam-sessions-in-future-with-space-left {} {:connection spec})))
  (exam-session [{:keys [spec]} id]
    (-> (q/exam-session-by-id {:id id} {:connection spec}) group-exam-session-translations))
  (add-exam-session! [{:keys [spec]} exam-session]
    (jdbc/with-db-transaction [tx spec]
      (let [new-exam-session (insert-exam-session tx exam-session)]
        (store-exam-session-translations tx (assoc exam-session ::spec/id (:id new-exam-session)) false)
        new-exam-session)))
  (save-exam-session! [{:keys [spec]} exam-session]
    (jdbc/with-db-transaction [tx spec]
      (q/update-exam-session! exam-session {:connection tx})
      (store-exam-session-translations tx exam-session :update)))
  (remove-exam-session! [{:keys [spec]} id]
    (q/delete-exam-session-translations! {:exam-session-id id} {:connection spec})
    (q/delete-exam-session! {:exam-session-id id} {:connection spec}))

  (exam-sessions-full [{:keys [spec] :as db} lang]
    (q/exam-sessions-full {:lang lang} {:connection spec}))

  (sections-and-modules-available-for-user [{:keys [spec]} external-user-id]
    (->> (q/select-modules-available-for-user {:external-user-id external-user-id} {:connection spec})
         group-sections-and-modules))
  (valid-full-payments-for-user [{:keys [spec]} external-user-id]
    (-> (q/select-valid-payment-count-for-user {:external-user-id external-user-id} {:connection spec})
        first
        :count))
  (register! [{:keys [spec]} registration-data external-user-id state payment-data existing-reg-id]
    (jdbc/with-db-transaction [tx spec {:isolation :serializable}]
      (let [reg-id (store-registration! tx registration-data external-user-id state existing-reg-id)]
        (when payment-data
          (-> (assoc payment-data :registration-id reg-id)
              (q/insert-payment! {:connection tx})))
        reg-id)))
  (registrations-for-session [{:keys [spec]} exam-session-id]
    (->> (q/select-registrations-for-exam-session {:exam-session-id exam-session-id} {:connection spec})
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
    (-> (q/select-existing-registration-id {:external-user-id external-user-id :exam-session-id exam-session-id} {:connection spec})
        first
        :id))
  (section-and-module-names [{:keys [spec]}]
    (->> (q/select-section-and-module-names {} {:connection spec})
         (reduce (fn [names {:keys [section_id section_name module_id module_name]}]
                   (meta-merge names {:sections {section_id section_name}
                                      :modules {module_id module_name}}))
                 {})))
  (participant-by-ext-id [{:keys [spec]} external-user-id]
    (q/select-participant {:external-user-id external-user-id} {:connection spec}))
  (participant-by-id [{:keys [spec]} id]
    (q/select-participant-by-id {:id id} {:connection spec}))
  (participant-by-order-number [{:keys [spec]} order-number lang]
    (q/select-participant-by-payment-order-number {:order-number order-number :lang lang} {:connection spec}))
  (all-participants [{:keys [spec]}]
    (q/select-all-participants {} {:connection spec}))
  (all-participants-by-ext-references [{:keys [spec]} ext-ids]
    (q/select-all-participants-by-ext-references {:ext-reference-ids ext-ids}
                                                 {:connection spec}))
  (confirm-registration-and-payment! [{:keys [spec]} params]
    (update-payment-and-registration-state! spec params "OK" "OK"))
  (cancel-registration-and-payment! [{:keys [spec]} params]
    (update-payment-and-registration-state! spec params "ERROR" "ERROR"))
  (cancel-payment-set-reg-incomplete! [{:keys [spec]} params]
    (update-payment-and-registration-state! spec params "ERROR" "INCOMPLETE"))
  (cancel-registration! [{:keys [spec]} id]
    (q/update-registration-state! {:state "ERROR" :id id} {:connection spec}))
  (next-order-number! [{:keys [spec]}]
    (-> (q/select-next-order-number-suffix {} {:connection spec})
        first
        :nextval))
  (unpaid-payments [{:keys [spec]}]
    (q/select-unpaid-payments {} {:connection spec}))
  (unpaid-payments-by-participant [{:keys [spec]} external-user-id]
    (q/select-unpaid-payments-by-participant {:external-user-id external-user-id} {:connection spec}))
  (unpaid-payment-by-registration [{:keys [spec]} registration-id]
    (-> (q/select-unpaid-payment-by-registration-id {:registration-id registration-id} {:connection spec})
        first))
  (update-payment-order-number-and-ts! [{:keys [spec]} params]
    (q/update-payment-order-and-timestamp! params {:connection spec}))
  (paid-credit-card-payments [{:keys [spec]}]
    (q/select-credit-card-payments {} {:connection spec}))
  (registration-state-by-id [{:keys [spec]} id]
    (-> (q/select-registration-state-by-id {:id id} {:connection spec})
        first
        :state))
  (add-email-by-participant-id! [{:keys [spec]} params]
    (q/insert-email-by-participant-id! params {:connection spec}))
  (unsent-emails-for-update [db tx]
    (q/select-unsent-email-for-update {} {:connection tx}))
  (set-email-sent! [db tx email-id]
    (q/mark-email-sent! {:id email-id} {:connection tx}))
  (health-check [{:keys [spec]}]
    (-> (q/select-exam-count {} {:connection spec})
        first
        :exam_count
        pos?))
  (accreditation-types [{:keys [spec]}]
    (q/select-accreditation-types {} {:connection spec}))
  (update-accreditations! [{:keys [spec]} params]
    (jdbc/with-db-transaction [tx spec {:isolation :serializable}]
      (doseq [section (:sections params)]
        (q/update-section-accreditation! section {:connection tx}))
      (doseq [module (:modules params)]
        (q/update-module-accreditation! module {:connection tx}))))
  (add-token-to-exam-session! [{:keys [spec]} id token]
    (q/update-exam-session-with-token! {:id id :token token} {:connection spec}))
  (access-token-for-exam-session [{:keys [spec]} id]
    (-> (q/select-exam-session-access-token {:id id} {:connection spec})
        first
        :access_token))
  (access-token-matches-session? [{:keys [spec]} id token]
    (-> (q/select-exam-session-matching-token {:id id :token token} {:connection spec})
        first
        :id
        (= id)))
  (update-participant-diploma-data! [{:keys [spec]} update-params]
    (jdbc/with-db-transaction [tx spec {:isolation :serializable}]
      (doseq [participant-update update-params]
        (q/update-participant-diploma! participant-update {:connection tx}))))
  (diploma-count [{:keys [spec]} start-date end-date]
    (-> (q/select-diploma-count {:start-date start-date :end-date end-date} {:connection spec})
        first
        :count))
  (exam-by-lang [{:keys [spec]} lang]
    (when-let [section-module-sequence (q/exam-by-lang {:lang lang} {:connection spec})]
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
    (snake-keys (q/upsert-participant-section-score<! section-score {:connection spec})))
  (upsert-module-score [{:keys [spec]} module-score]
    (snake-keys (q/upsert-participant-module-score<! module-score {:connection spec})))
  (section-score [{:keys [spec]} params]
    (snake-keys (first (q/select-section-score params {:connection spec}))))
  (module-score [{:keys [spec]} params]
    (snake-keys (first (q/select-module-score params {:connection spec})))))
