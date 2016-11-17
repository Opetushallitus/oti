(ns oti.service.diploma
  (:require [clojure.java.io :as io]
            [hiccup.core :as hiccup]
            [oti.service.user-data :as user-data]
            [oti.component.localisation :refer [t]]
            [clojure.string :as str]
            [ring.util.response :as resp]
            [oti.boundary.db-access :as dba]
            [oti.util.logging.audit :as audit])
  (:import [java.time.format DateTimeFormatter]))

(def formatter (DateTimeFormatter/ofPattern "d.M.yyyy"))

(def base-html (->> (io/resource "oti/html-templates/diploma.html")
                    slurp))

(defn- db-date->str [date]
  (-> date (.toLocalDate) (.format formatter)))

(defn- diploma [localisation {:keys [etunimet sukunimi hetu language diploma]}]
  [:div.diploma
   [:h1 (t localisation language "diploma")]
   [:h2 (t localisation language "of-ot")]
   [:div.participant
    [:span.name (str etunimet " " sukunimi)]
    [:span.hetu (str "(" hetu ")")]]
   [:div.description (t localisation language "diploma-description")]
   [:div.content
    [:div.title (t localisation language "diploma-contains")]
    [:ol.sections
     (for [id (range 1 6)]
       [:li (t localisation language (str "diploma-module-" id))])]]
   [:div.place-and-date
    [:span.place (t localisation language "in-helsinki")]
    [:span.date (db-date->str (:date diploma))]]
   [:div.signature
    [:div.signer
     (:signer diploma)]
    [:div.signer-title
     (t localisation language "diploma-signer-title")]]])

(defn- diplomas [{:keys [localisation]} users]
  (-> [:div.diplomas
       (for [user users]
         (diploma localisation user))]
      hiccup/html))

(defn- write-audit-log! [old-user authority new-users-by-id]
  (let [old-data (select-keys old-user [:id :diploma])
        new-data (-> (get new-users-by-id (:id old-user))
                     (select-keys [:id :diploma]))]
    (audit/log :app :admin
               :who authority
               :op :update
               :on :diploma
               :before old-data
               :after new-data
               :msg "Generating participant diploma.")))

(defn generate-diplomas [{:keys [db] :as config} ids signer title {{authority :username} :identity}]
  (let [users (->> (map #(user-data/participant-data config %) ids)
                   (remove nil?)
                   (filter #(not= (:filter %) :incomplete)))
        valid-ids (map :id users)]
    (if (seq users)
      (do (dba/update-participant-diploma-data! db valid-ids signer title)
          (let [updated-users (map #(user-data/participant-data config %) valid-ids)
                users-by-id (reduce #(assoc %1 (:id %2) %2) {} updated-users)]
            (dorun (map #(write-audit-log! % authority users-by-id) users))
            (-> (str/replace base-html "PAGE_CONTENT" (diplomas config updated-users))
                (resp/response)
                (resp/header "Content-Type" "text/html; charset=utf-8"))))
      (-> (resp/not-found "Käyttäjää ei löydy tai tutkintoa ei ole suoritettu.")
          (resp/header "Content-Type" "text/plain; charset=utf-8")))))
