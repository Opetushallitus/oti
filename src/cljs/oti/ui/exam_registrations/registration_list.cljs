(ns oti.ui.exam-registrations.registration-list
  (:require [re-frame.core :as re-frame]
            [reagent.core :as r]
            [oti.spec :as os]
            [oti.ui.exam-sessions.utils :refer [unparse-date]]
            [oti.ui.exam-registrations.handlers]
            [oti.ui.exam-registrations.subs]
            [oti.exam-rules :as rules]
            [clojure.string :as str]
            [oti.routing :as routing]
            [oti.ui.views.common :refer [small-loader]]))

(defn- registrations-table [registrations]
  (let [sm-names (re-frame/subscribe [:section-and-module-names])]
    (fn [registrations]
      [:table
       [:thead
        [:tr
         [:th "Nimi"]
         (doall
           (for [[_ name] (:sections @sm-names)]
             [:th {:key name} (str "Osa " name)]))
         [:th "Kokeen kieli"]]]
       [:tbody
        (doall
          (for [{:keys [sections id etunimet sukunimi lang participant-id]} registrations]
            [:tr {:key id}
             [:td [:a {:href (routing/v-route "/henkilot/" participant-id)} (str etunimet " " sukunimi)]]
             (doall
               (for [[id _] (:sections @sm-names)]
                 [:td {:key id}
                  (if-let [modules (get sections id)]
                    (let [rules (rules/rules-by-section-id id)]
                      (if (or (:can-retry-partially? rules) (:can-accredit-partially? rules))
                        [:div
                         "Suorittaa osiot:"
                         [:br]
                         (->> modules (map #(get (:modules @sm-names) %)) (str/join ", "))]
                        [:div "Suorittaa"]))
                    [:div "Ei suorita"])]))
             [:td (if (= :sv lang)
                    "Ruotsi"
                    "Suomi")]]))]])))

(defn- session-title [{::os/keys [street-address city other-location-info session-date start-time end-time]}]
  (str (unparse-date session-date) " " start-time " - " end-time " "
       (:fi city) ", " (:fi street-address) ", " (:fi other-location-info)))

(defn- ext-link [id token]
  (let [location (-> js/window .-location)
        host (.-host location)
        protocol (.-protocol location)]
    (str protocol "//" host (routing/ext-route "/ilmoittautumiset/" id "?token=" token))))

(defn- panel [exam-sessions pre-selected-session-id]
  (let [session-id (r/atom pre-selected-session-id)
        registration-data (re-frame/subscribe [:registrations])]
    (fn [exam-sessions pre-selected-session-id]
      [:div [:h2 "Ilmoittautumiset"]
       [:div.exam-session-selection
        [:label {:for "exam-session-select"}
         [:span.label "Tapahtuma:"]]
        [:select#exam-session-select
         {:value @session-id
          :on-change (fn [e]
                       (let [new-id (-> e .-target .-value)]
                         (reset! session-id new-id)
                         (re-frame/dispatch [:load-registrations new-id])))}
         (doall
           (for [{::os/keys [id] :as session} exam-sessions]
             [:option {:value id :key id}
              (session-title session)]))]
        [:span#exam-session-label (session-title (->> exam-sessions (filter #(= @session-id (::os/id %))) first))]]

       [:div.registrations
        (let [registrations (get-in @registration-data [@session-id :registrations])]
          (cond
            (nil? registrations) [small-loader]
            (seq registrations) [registrations-table registrations]
            :else [:span "Ei ilmoittautumisia"]))]
       [:div.buttons
        (if-let [token (get-in @registration-data [@session-id :access-token])]
          [:div.ext-link
           [:h4 "Linkki ilmoittautumislistan tarkastelua varten ilman kirjautumista"]
           [:a {:href (ext-link @session-id token)} (ext-link @session-id token)]
           [:p "Linkki on voimassa seitsemän päivää koetilaisuudesta."]]
          [:button.ext-link
           {:on-click #(re-frame/dispatch [:generate-registrations-access-token @session-id])}
           "Luo linkki ilmoittautumislistan tarkastelua varten"])]])))

(defn reg-list-panel [pre-selected-session-id]
  (re-frame/dispatch [:load-exam-sessions])
  (fn [pre-selected-session-id]
    (let [exam-sessions (re-frame/subscribe [:exam-sessions])]
      (when (seq @exam-sessions)
        (let [exam-session-id (or pre-selected-session-id (-> @exam-sessions first ::os/id))]
          (re-frame/dispatch [:load-registrations exam-session-id])
          [panel @exam-sessions exam-session-id])))))
