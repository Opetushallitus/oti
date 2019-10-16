(ns oti.service.user-data
  (:require [clojure.core.cache :as cache]
            [oti.boundary.api-client-access :as api]
            [oti.boundary.db-access :as dba]
            [oti.spec :as os]))

(def cache-ttl
  ; 2 hours
  7200000)

(def vtj-address-opts {:type "yhteystietotyyppi4"
                       :origin "alkupera1"})

(def domestic-address-opts {:type "yhteystietotyyppi2"
                            :origin "alkupera2"})

(def address-mapping {"YHTEYSTIETO_KATUOSOITE" ::os/registration-street-address
                      "YHTEYSTIETO_POSTINUMERO" ::os/registration-zip
                      "YHTEYSTIETO_KAUPUNKI" ::os/registration-post-office})

(defonce C (atom (cache/ttl-cache-factory {} :ttl cache-ttl)))

(defn- api-fetch-required? [user-oid]
  (not (cache/has? @C user-oid)))

(defn- format-user-data [user]
  (-> (select-keys user [:etunimet :sukunimi :kutsumanimi :hetu :oidHenkilo])
      (assoc :asiointikieli (get-in user [:asiointiKieli :kieliKoodi]))))

(defn- fetch-oids! [api-client oids]
  (when (seq oids)
    (let [users (api/get-persons api-client oids)]
      (doseq [{:keys [oidHenkilo] :as user} users]
        (let [cache-user (format-user-data user)]
          (swap! C cache/miss oidHenkilo cache-user))))))

(defn api-user-data-by-oid [api-client user-oids]
  (let [must-fetch-oids (filter api-fetch-required? (distinct user-oids))]
    (fetch-oids! api-client must-fetch-oids)
    (select-keys @C user-oids)))

(defn- addresses-of-type [{:keys [yhteystiedotRyhma]} {:keys [type origin]}]
  (->> yhteystiedotRyhma
       (filter (fn [{:keys [ryhmaKuvaus ryhmaAlkuperaTieto]}]
                 (and (= ryhmaKuvaus type) (= ryhmaAlkuperaTieto origin))))))

(defn- api-address->map [{:keys [yhteystieto]}]
  (reduce (fn [address {:keys [yhteystietoTyyppi yhteystietoArvo]}]
            (if-let [key (address-mapping yhteystietoTyyppi)]
              (assoc address key yhteystietoArvo)
              address))
          {}
          yhteystieto))

(defn- user-data-with-address [api-client oid]
  (when-let [user (api/get-person-by-id api-client oid)]
    (->> (map #(addresses-of-type user %) [vtj-address-opts domestic-address-opts])
         (sort-by :id)
         (map last)
         (remove nil?)
         first
         api-address->map
         (assoc (format-user-data user) :address))))

(defn- make-kw [prefix suffix]
  (keyword (str prefix "_" suffix)))

(defn- make-get-fn [kw-prefix]
  (fn [row suffix]
    (let [key (make-kw kw-prefix suffix)]
      (key row))))

(defn sec-or-mod-props [kw-prefix rows]
  (let [get-fn (make-get-fn kw-prefix)
        first-row (first rows)]
    {:id (get-fn first-row "id")
     :name (get-fn first-row "name")
     :score-ts (some #(get-fn % "score_created") rows)
     :accepted (some #(get-fn % "score_accepted") rows)
     :points (some #(get-fn % "score_points") rows)
     :accreditation-requested? (some #(get-fn % (str "accreditation_" kw-prefix "_id")) rows)
     :accreditation-date (some #(get-fn % "accreditation_date") rows)
     :accreditation-type (some #(get-fn % "accreditation_type") rows)
     :registered-to? (some #(get-fn % "registration_id") rows)}))

(defn- group-by-registration [rows]
  (->> (partition-by :registration_id rows)
       (map
         (fn [session-rows]
           (let [modules (->> (partition-by :module_id session-rows)
                              (map #(sec-or-mod-props "module" %))
                              (remove :accreditation-requested?))
                 {:keys [session_date start_time end_time city street_address
                         other_location_info exam_session_id
                         registration_state registration_id]} (first session-rows)]
             (when session_date
               (-> (select-keys (sec-or-mod-props "section" session-rows) [:score-ts :accepted])
                   (merge
                     {:modules (reduce #(assoc %1 (:id %2) %2) {} modules)
                      :session-date (str (.toLocalDate session_date))
                      :start-time (str (.toLocalTime start_time))
                      :end-time (str (.toLocalTime end_time))
                      :session-id exam_session_id
                      :street-address street_address
                      :city city
                      :other-location-info other_location_info
                      :registration-state registration_state
                      :registration-id registration_id}))))))
       (remove nil?)))

(defn- group-by-section [participant-rows]
  (->> (partition-by :section_id participant-rows)
       (map
         (fn [section-rows]
           (let [accredited-modules (->> (partition-by :module_id section-rows)
                                           (map #(sec-or-mod-props "module" %))
                                           (filter :accreditation-requested?))
                 sessions (group-by-registration section-rows)
                 module-titles (->> (reduce (fn [mods {:keys [modules]}]
                                              (->> (map #(select-keys (second %) [:id :name]) modules)
                                                   (concat mods)))
                                            []
                                            sessions)
                                    (sort-by :id)
                                    (distinct))]
             (-> (sec-or-mod-props "section" section-rows)
                 (select-keys [:id :name :accreditation-requested? :accreditation-date :accreditation-type :accepted :score-ts])
                 (assoc :sessions sessions
                        :accredited-modules accredited-modules
                        :module-titles module-titles)))))))

(defn- payments [participant-rows]
  (->> (group-by :payment_id participant-rows)
       (map
         (fn [[id payment-rows]]
           (let [{:keys [payment_id amount payment_state payment_created order_number payment_type registration_id]} (first payment-rows)]
             (when payment_id
               {:id payment_id
                :amount amount
                :state payment_state
                :order-number order_number
                :type payment_type
                :registration-id registration_id
                :registration-state (some :registration_state payment-rows)
                :created payment_created}))))
       (remove nil?)))

(defn user-status-filter [db sections diploma-delivered?]
  (let [completed-sections (->> sections
                                (filter #(or (:accepted %) (:accreditation-date %)))
                                (map :id)
                                set)
        required-sections (->> (dba/section-and-module-names db) :sections keys set)]
    (cond
      diploma-delivered? :diploma-delivered
      (= completed-sections required-sections) :complete
      :else :incomplete)))

(defn- merge-db-and-api-data [{:keys [db api-client]} db-data]
  (when-let [{:keys [ext_reference_id id email diploma_date diploma_signer diploma_signer_title]} (first db-data)]
    (let [api-data (user-data-with-address api-client ext_reference_id)
          sections (group-by-section db-data)]
      (merge
        api-data
        {:id id
         :email email
         :diploma {:date diploma_date :signer diploma_signer :title diploma_signer_title}
         :sections (group-by-section db-data)
         :filter (user-status-filter db sections diploma_date)
         :language (keyword (or (->> db-data (sort-by :registration_id) last :registration_language) (:asiointikieli api-data) :fi))
         :payments (payments db-data)}))))

(defn participant-data
  ([{:keys [db] :as config} participant-id]
   (->> (dba/participant-by-id db participant-id)
        (merge-db-and-api-data config)))
  ([{:keys [db] :as config} order-number lang]
   (->> (dba/participant-by-order-number db order-number lang)
        (merge-db-and-api-data config))))

(defn participant-data-by-ext-reference-id
  ([{:keys [db] :as config} ext-reference-id]
   (->> (dba/participant-by-ext-reference-id db ext-reference-id)
        (merge-db-and-api-data config))))
