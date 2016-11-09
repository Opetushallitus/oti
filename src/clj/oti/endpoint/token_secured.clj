(ns oti.endpoint.token-secured
  (:require [compojure.core :refer :all]
            [ring.util.response :as resp]
            [clojure.java.io :as io]
            [oti.service.registration :as registration]
            [hiccup.core :as hiccup]
            [oti.boundary.db-access :as dba]
            [oti.spec :as os]
            [oti.exam-rules :as rules]
            [clojure.string :as str]
            [oti.utils :as utils]
            [oti.util.coercion :as c])
  (:import [java.time.format DateTimeFormatter]))

(defn- fetch-exam-session [db id]
  (->> (dba/exam-session db id)
       (map c/convert-session-row)
       first))

(def formatter (DateTimeFormatter/ofPattern "d.M.yyyy"))

(def base-html (->> (io/resource "oti/html-templates/registrations.html")
                    slurp))

(defn- registrations-table [{:keys [db]} registrations]
  (let [sm-names (dba/section-and-module-names db)]
    [:table
     [:thead
      [:tr
       [:th "Nimi"]
       (doall
         (for [[_ name] (:sections sm-names)]
           [:th {:key name} (str "Osa " name)]))
       [:th "Kokeen kieli"]]]
     [:tbody
      (doall
        (for [{:keys [sections etunimet sukunimi lang]} registrations]
          [:tr
           [:td (str etunimet " " sukunimi)]
           (doall
             (for [[id _] (:sections sm-names)]
               [:td
                (if-let [modules (get sections id)]
                  (let [rules (rules/rules-by-section-id id)]
                    (if (or (:can-retry-partially? rules) (:can-accredit-partially? rules))
                      [:div
                       "Suorittaa osiot:"
                       [:br]
                       (->> modules (map #(get (:modules sm-names) %)) (str/join ", "))]
                      [:div "Suorittaa"]))
                  [:div "Ei suorita"])]))
           [:td (if (= :sv lang)
                  "Ruotsi"
                  "Suomi")]]))]]))

(defn- registrations-markup [{:keys [db] :as config} id]
  (-> (let [registrations (registration/fetch-registrations config id)
            {::os/keys [street-address city other-location-info session-date start-time end-time]} (fetch-exam-session db id)]
        [:div [:h2 "Ilmoittautumiset"]
         [:div.exam-session-selection
          (str (-> session-date .toLocalDate (.format formatter)) " " start-time " - " end-time " "
               (:fi city) ", " (:fi street-address) ", " (:fi other-location-info))]
         [:div.registrations
          (if (seq registrations)
            (registrations-table config registrations)
            [:span "Ei ilmoittautumisia"])]])
      (hiccup/html)))

(defn- registration-page [{:keys [db] :as config} str-id token]
  (when-let [id (utils/parse-int str-id)]
    (if (and (not (str/blank? token)) (dba/access-token-matches-session? db id token))
      (-> (str/replace base-html "PAGE_CONTENT" (registrations-markup config id))
          (resp/response)
          (resp/header "Content-Type" "text/html; charset=utf-8"))
      {:status 404 :body "Sivua ei löydy" :headers {"Content-Type" "text/plain; charset=utf-8"}})))

(defn token-secured-endpoint [config]
  (GET "/oti/ext/ilmoittautumiset/:id" [id token]
    (registration-page config id token)))
