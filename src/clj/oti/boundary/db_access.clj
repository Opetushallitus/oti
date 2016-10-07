(ns oti.boundary.db-access
  (:require [yesql.core :refer [require-sql]]
            [clojure.java.jdbc :as jdbc]
            [duct.component.hikaricp]
            [oti.spec :as spec]
            [clojure.set :as cs]
            [taoensso.timbre :refer [error]]
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

(defn store-exam-session-translations [tx exam-session update?]
  (let [langs (set (mapcat keys-of-mapval (translatable-keys-from-exam-session exam-session)))]
    (if (::spec/id exam-session)
      (mapv #(store-exam-session-translation % tx exam-session update?) langs)
      (throw (Exception. "Error occured inserting translations. Missing exam session id.")))))

(defn insert-exam-session [tx exam-session]
  (or (q/insert-exam-session<! exam-session {:connection tx})
      (throw (Exception. "Could not create new exam session."))))

(defn store-registration! [tx {::spec/keys [session-id language-code sections]} external-user-id]
  (let [conn {:connection tx}]
    (let [registarable-sections (remove (fn [[_ options]] (::spec/accredit? options)) sections)]
      (when (pos? (count registarable-sections))
        (if-let [reg-id (:id (q/insert-registration<! {:session-id session-id
                                                     :external-user-id external-user-id
                                                     :language-code (name language-code)} conn))]
          (doseq [[section-id opts] registarable-sections]
            (let [params {:section-id section-id
                          :external-user-id external-user-id
                          :registration-id reg-id}]
              (q/insert-section-registration! params conn)
              (let [all-modules (->> (q/select-modules-for-section params conn) (map :id) set)
                    register-modules (or (seq (::spec/retry-modules opts))
                                         (cs/difference all-modules (::spec/accredit-modules opts)))]
                (doseq [module-id register-modules]
                  (q/insert-module-registration! (assoc params :module-id module-id) conn))
                (doseq [module-id (::spec/accredit-modules opts)]
                  (q/insert-module-accreditation! (assoc params :module-id module-id) conn)))))
          (throw (Exception. "No registeration id received.")))))
    (doseq [[section-id _] (filter (fn [[_ options]] (::spec/accredit? options)) sections)]
      (q/insert-section-accreditation! {:section-id section-id :external-user-id external-user-id} conn))))

(defprotocol DbAccess
  (upcoming-exam-sessions [db])
  (add-exam-session! [db exam-session])
  (save-exam-session! [db exam-session])
  (remove-exam-session! [db id])
  (published-exam-sessions-with-space-left [db])
  (exam-session [db id])
  (modules-available-for-user [db external-user-id])
  (valid-full-payments-for-user [db external-user-id])
  (register! [db registration-data external-user-id])
  (registrations-for-session [db exam-session])
  (section-and-module-names [db]))

(extend-type HikariCP
  DbAccess
  (upcoming-exam-sessions [{:keys [spec]}]
    (group-exam-session-translations (q/exam-sessions-in-future {} {:connection spec})))
  (published-exam-sessions-with-space-left [{:keys [spec]}]
    (group-exam-session-translations (q/published-exam-sessions-in-future-with-space-left {} {:connection spec})))
  (exam-session [{:keys [spec]} id]
    (-> (q/exam-session-by-id {:id id} {:connection spec}) group-exam-session-translations))
  (add-exam-session! [{:keys [spec]} exam-session]
    (jdbc/with-db-transaction [tx spec]
      (let [exam-session-id (:id (insert-exam-session tx exam-session))]
        (store-exam-session-translations tx (assoc exam-session ::spec/id exam-session-id) false))))
  (save-exam-session! [{:keys [spec]} exam-session]
    (jdbc/with-db-transaction [tx spec]
      (q/update-exam-session! exam-session {:connection tx})
      (store-exam-session-translations tx exam-session :update)))
  (remove-exam-session! [{:keys [spec]} id]
    (q/delete-exam-session-translations! {:exam-session-id id} {:connection spec})
    (q/delete-exam-session! {:exam-session-id id} {:connection spec}))
  (modules-available-for-user [{:keys [spec]} external-user-id]
    (->> (q/select-modules-available-for-user {:external-user-id external-user-id} {:connection spec})
         group-sections-and-modules))
  (valid-full-payments-for-user [{:keys [spec]} external-user-id]
    (-> (q/select-valid-payment-count-for-user {:external-user-id external-user-id} {:connection spec})
        first
        :count))
  (register! [{:keys [spec]} registration-data external-user-id]
    (jdbc/with-db-transaction [tx spec {:isolation :serializable}]
      (store-registration! tx registration-data external-user-id)))
  (registrations-for-session [{:keys [spec]} exam-session-id]
    (->> (q/select-registrations-for-exam-session {:exam-session-id exam-session-id} {:connection spec})
         (partition-by :id)
         (map (fn [registration-rows]
                (let [sections (->> (partition-by :section_id registration-rows)
                                    (map (fn [s-rows] {:id (:section_id (first s-rows))
                                                       :modules (map :module_id s-rows)})))
                      {:keys [id created language_code, participant_id ext_reference_id]} (first registration-rows)]
                  {:id id
                   :created created
                   :lang (keyword language_code)
                   :participant-id participant_id
                   :external-user-id ext_reference_id
                   :sections sections})))))
  (section-and-module-names [{:keys [spec]}]
    (->> (q/select-section-and-module-names {} {:connection spec})
         (reduce (fn [names {:keys [section_id section_name module_id module_name]}]
                   (meta-merge names {:sections {section_id section_name}
                                      :modules {module_id module_name}}))
                 {}))))
