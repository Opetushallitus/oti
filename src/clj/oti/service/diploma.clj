(ns oti.service.diploma
  (:require [clojure.java.io :as io]
            [hiccup.core :as hiccup]
            [oti.service.user-data :as user-data]
            [oti.component.localisation :refer [t]]
            [clojure.string :as str]
            [ring.util.response :as resp]
            [oti.boundary.db-access :as dba]
            [oti.util.logging.audit :as audit]
            [oti.spec :as os])
  (:import [java.time.format DateTimeFormatter]))

(def formatter (DateTimeFormatter/ofPattern "d.M.yyyy"))

(def base-html (->> (io/resource "oti/html-templates/diploma.html")
                    slurp))
(def now (java.time.LocalDateTime/now))

(defn- db-date->str [date]
  (-> date (.toLocalDate) (.format formatter)))

(defn- localdate [date]
       (-> date (.format formatter)))

(defn- diploma [localisation {:keys [etunimet sukunimi hetu language diploma]} signerinfo]
  [:div.diploma
   [:h1 (t localisation language "diploma")]
   [:h2 (t localisation language "of-ot")]
   [:div.participant
    [:span.name (str etunimet " " sukunimi)]
    [:span.hetu (str "(" hetu ")")]]
   [:div.description (t localisation language "diploma-description")]
   [:div.content
    [:div.title (t localisation language "diploma-contains")]
    [:div.module-title (t localisation language "diploma-module-administration")]
    [:ol.sections
     (for [id (range 1 4)]
       [:li (t localisation language (str "diploma-module-administration-" id))])]
    [:div.module-title (t localisation language "diploma-module-lead")]
    [:ol.sections
     (for [id (range 1 4)]
       [:li (t localisation language (str "diploma-module-lead-" id))])]]
   [:div.place-and-date
    [:span.place (t localisation language "in-helsinki")]
    [:span.date (localdate now)]]
   [:div.signature
    [:div.signer
     (:signer signerinfo)]
    [:div.signer-title
     [:span.title (get (get signerinfo :title ) language)]]]])

(defn- diplomas [{:keys [localisation]} users signerinfo]
  (-> [:div.diplomas
       (for [user users]
         (diploma localisation user signerinfo))]
      hiccup/html))

(defn- write-audit-log! [old-user session new-users-by-id]
  (let [old-data (select-keys old-user [:id :diploma])
        new-data (-> (get new-users-by-id (:id old-user))
                     (select-keys [:id :diploma]))]
    (audit/log :app :admin
               :who (get-in session [:identity :oid])
               :ip (get-in session [:identity :ip])
               :user-agent (get-in session [:identity :user-agent])
               :op :update
               :on :diploma
               :before old-data
               :after new-data
               :msg "Generating participant diploma.")))

(defn generate-diplomas [{:keys [db] :as config} {::os/keys [participant-ids signer signer-title]} session]
      (let [signerinfo {:signer signer, :title signer-title}]
      (let [users (->> (map #(user-data/participant-data config %) participant-ids)
                   (remove nil?)
                   (filter #(not= (:filter %) :incomplete)))
        valid-ids (map :id users)]
    (if (seq users)
      (do (->> users
               (map (fn [{:keys [id language]}] {:id id :signer signer :title (language signer-title)}))
               (dba/update-participant-diploma-data! db))
          (let [updated-users (map #(user-data/participant-data config %) valid-ids)
                users-by-id (reduce #(assoc %1 (:id %2) %2) {} updated-users)]
            (dorun (map #(write-audit-log! % session users-by-id) users))
            (-> (str/replace base-html "PAGE_CONTENT" (diplomas config updated-users signerinfo))
                (resp/response)
                (resp/header "Content-Type" "text/html; charset=utf-8"))))
      (-> (resp/not-found "Käyttäjää ei löydy tai tutkintoa ei ole suoritettu.")
          (resp/header "Content-Type" "text/plain; charset=utf-8"))))))

(defn default-signer-title [{:keys [localisation]}]
  {:fi (t localisation :fi "diploma-signer-title")
   :sv (t localisation :sv "diploma-signer-title")})
