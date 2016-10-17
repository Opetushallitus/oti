(ns oti.endpoint.virkailija
  (:require [compojure.core :refer :all]
            [ring.util.response :refer [response not-found]]
            [oti.util.auth :as auth]
            [clojure.spec :as s]
            [oti.boundary.db-access :as dba]
            [oti.spec :as os]
            [oti.util.coercion :as c]
            [oti.routing :as routing]
            [oti.service.user-data :as user-data]
            [oti.service.search :as search]
            [clojure.string :as str]))

(defn- as-int [x]
  (try (Integer/parseInt x)
       (catch Throwable _)))

(defn- fetch-registrations [{:keys [db api-client]} session-id]
  (if-let [regs (seq (dba/registrations-for-session db session-id))]
    (let [oids (map :external-user-id regs)
          user-data-by-oid (user-data/api-user-data-by-oid api-client oids)]
      (map (fn [{:keys [external-user-id] :as registration}]
             (-> (get user-data-by-oid external-user-id)
                 (select-keys [:etunimet :sukunimi])
                 (merge registration)))
           regs))
    []))

(defn- make-kw [prefix suffix]
  (keyword (str prefix "_" suffix)))

(defn- make-get-fn [kw-prefix]
  (fn [row suffix]
    (let [key (make-kw kw-prefix suffix)]
      (key row))))

(defn- sec-or-mod-props [kw-prefix rows]
  (let [get-fn (make-get-fn kw-prefix)
        first-row (first rows)]
    {:id (get-fn first-row "id")
     :name (get-fn first-row "name")
     :score-ts (some #(get-fn % "score_ts") rows)
     :accepted (some #(get-fn % "accepted") rows)
     :points (some #(get-fn % "points") rows)
     :accreditation-requested? (some #(get-fn % "accreditation") rows)
     :accreditation-date (some #(get-fn % "accreditation_date") rows)
     :registered-to? (some #(get-fn % "registration") rows)}))

(defn- group-by-session [rows]
  (->> (partition-by :exam_session_id rows)
       (map
         (fn [session-rows]
           (let [modules (->> (partition-by :module_id session-rows)
                              (map #(sec-or-mod-props "module" %))
                              (remove :accreditation-requested?))
                 {:keys [session_date start_time end_time city street_address other_location_info exam_session_id]} (first session-rows)]
             (when session_date
               (-> (select-keys (sec-or-mod-props "section" session-rows) [:score-ts :accepted])
                   (merge
                     {:modules (reduce #(assoc %1 (:id %2) %2) {} modules)
                      :session-date session_date
                      :start-time (str (.toLocalTime start_time))
                      :end-time (str (.toLocalTime end_time))
                      :session-id exam_session_id
                      :street-address street_address
                      :city city
                      :other-location-info other_location_info}))))))
       (remove nil?)))

(defn- group-by-section [participant-rows]
  (->> (partition-by :section_id participant-rows)
       (map
         (fn [section-rows]
           (let [accreditated-modules (->> (partition-by :module_id section-rows)
                                           (map #(sec-or-mod-props "module" %))
                                           (filter :accreditation-requested?))
                 sessions (group-by-session section-rows)
                 module-titles (->> (reduce (fn [mods {:keys [modules]}]
                                              (->> (map #(select-keys (second %) [:id :name]) modules)
                                                   (concat mods)))
                                            []
                                            sessions)
                                    (sort-by :id))]
             (-> (sec-or-mod-props "section" section-rows)
                 (select-keys [:id :name :accreditation-requested? :accreditation-date])
                 (assoc :sessions sessions
                        :accredited-modules accreditated-modules
                        :module-titles module-titles)))))))

(defn- participant-data [{:keys [db api-client]} id]
  (when-let [db-data (seq (dba/participant-by-id db id))]
    (let [external-user-id (:ext_reference_id (first db-data))
          api-data (-> (user-data/api-user-data-by-oid api-client [external-user-id])
                       (get external-user-id))]
      (merge
        api-data
        (select-keys (first db-data) [:id :email])
        {:sections (group-by-section db-data)}))))

(defn virkailija-endpoint [{:keys [db] :as config}]
  (-> (context routing/virkailija-api-root []
        (GET "/user-info" {session :session}
          {:status 200
           :body (select-keys (:identity session) [:username])})
        (context "/exam-sessions" []
          (POST "/" {params :params}
            (let [conformed (s/conform ::os/exam-session params)]
              (if (or (s/invalid? conformed) (not (seq (dba/add-exam-session! db conformed))))
                {:status 400
                 :body {:errors (s/explain ::os/exam-session params)}}
                (response {:success true}))))
          (GET "/" []
            (let [sessions (->> (dba/upcoming-exam-sessions db)
                                (map c/convert-session-row))]
              (response sessions)))
          (context "/:id{[0-9]+}" [id :<< as-int]
            (GET "/" []
              (if-let [exam-session (->> (dba/exam-session db id)
                                         (map c/convert-session-row)
                                         first)]
                (response exam-session)
                (not-found {})))
            (PUT "/" {params :params}
              (let [conformed (s/conform ::os/exam-session (assoc params ::os/id id))]
                (if (or (s/invalid? conformed) (not (seq (dba/save-exam-session! db conformed))))
                  {:status 400
                   :body {:errors (s/explain ::os/exam-session params)}}
                  (response {:success true}))))
            (DELETE "/" []
              (if (pos? (dba/remove-exam-session! db id))
                (response {:success true})
                (not-found {})))
            (GET "/registrations" []
              (response (fetch-registrations config id)))))
        (GET "/sections-and-modules" []
          (response (dba/section-and-module-names db)))
        (GET "/participant-search" [q filter]
          (let [filter-kw (if (str/blank? filter) :all (keyword filter))
                query (when q (str/trim q))
                results (search/search-participants config query filter-kw)]
            (response results)))
        (context "/participant/:id{[0-9]+}" [id :<< as-int]
          (GET "/" []
            (if-let [data (participant-data config id)]
              (response data)
              (not-found {})))))
      (wrap-routes auth/wrap-authorization)))
