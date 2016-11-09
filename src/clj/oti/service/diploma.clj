(ns oti.service.diploma
  (:require [clojure.java.io :as io]
            [hiccup.core :as hiccup]
            [oti.service.user-data :as user-data]
            [oti.component.localisation :refer [t]]
            [clojure.string :as str]
            [ring.util.response :as resp])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter]))

(def formatter (DateTimeFormatter/ofPattern "d.M.yyyy"))

(def base-html (->> (io/resource "oti/html-templates/diploma.html")
                    slurp))

(defn- diploma [localisation {:keys [etunimet sukunimi hetu language]} signer]
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
    [:span.date (-> (LocalDate/now) (.format formatter))]]
   [:div.signature
    [:div.signer
     signer]
    [:div.signer-title
     (t localisation language "diploma-signer-title")]]])

(defn- diplomas [{:keys [localisation]} users signer]
  (-> [:div.diplomas
       (for [user users]
         (diploma localisation user signer))]
      hiccup/html))

(defn generate-diplomas [config ids signer]
  (let [users (->> (map #(user-data/participant-data config %) ids)
                   (remove nil?)
                   (filter #(not= (:filter %) :incomplete)))]
    (if (seq users)
      (-> (str/replace base-html "PAGE_CONTENT" (diplomas config users signer))
          (resp/response)
          (resp/header "Content-Type" "text/html; charset=utf-8"))
      (-> (resp/not-found "Käyttäjää ei löydy tai tutkintoa ei ole suoritettu.")
          (resp/header "Content-Type" "text/plain; charset=utf-8")))))
