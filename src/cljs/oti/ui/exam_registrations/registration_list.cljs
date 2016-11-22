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
            [oti.ui.views.common :refer [small-loader]]
            [oti.ui.routes :as routes]
            [oti.db-states :as states]))

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
          (for [{:keys [sections id etunimet sukunimi lang participant-id payment-state]} registrations]
            [:tr {:key id}
             [:td
              (when (#{states/pmt-error states/pmt-unpaid} payment-state)
                [:i.icon-attention {:class "error"
                                    :title "Ilmoittautuminen maksamatta"}])
              [:a {:href (routing/v-route "/henkilot/" participant-id)} (str etunimet " " sukunimi)]]
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

(defn- copy-to-clipboard []
  (let [elem (.getElementById js/document "ext-list-link")]
    (.select elem)
    (.execCommand js/document "copy")))

(defn- redirect-to-session-path [id]
  (routes/redirect (routing/v-route "/ilmoittautumiset/" id)))

(defn- panel [exam-sessions session-id]
  (let [registration-data (re-frame/subscribe [:registrations])]
    (fn [exam-sessions session-id]
      [:div [:h2 "Ilmoittautumiset"]
       [:div.exam-session-selection
        [:label {:for "exam-session-select"}
         [:span.label "Tapahtuma:"]]
        [:select#exam-session-select
         {:value session-id
          :on-change (fn [e]
                       (let [new-id (-> e .-target .-value)]
                         (redirect-to-session-path new-id)))}
         (doall
           (for [{::os/keys [id] :as session} exam-sessions]
             [:option {:value id :key id}
              (session-title session)]))]
        [:span#exam-session-label (session-title (->> exam-sessions (filter #(= session-id (::os/id %))) first))]]

       [:div.registrations
        (let [registrations (get-in @registration-data [session-id :registrations])]
          (cond
            (nil? registrations) [small-loader]
            (seq registrations) [registrations-table registrations]
            :else [:span "Ei ilmoittautumisia"]))]
       [:div.buttons
        (if-let [token (get-in @registration-data [session-id :access-token])]
          [:div.ext-link
           [:h4 "Linkki ilmoittautumislistan tarkastelua varten ilman kirjautumista"]
           [:div.link-input
            [:div.copy-button
             [:button {:on-click copy-to-clipboard} "Kopioi leikepöydälle"]]
            [:div.link-text
             [:input {:id "ext-list-link" :type "text" :value (ext-link session-id token) :read-only true}]]]
           [:p "Linkki on voimassa seitsemän päivää koetilaisuudesta."]]
          [:button.ext-link
           {:on-click #(re-frame/dispatch [:generate-registrations-access-token session-id])}
           "Luo linkki ilmoittautumislistan tarkastelua varten"])]])))

(def default-start-date (-> (js/moment.) (.subtract 2 "months") (.startOf "day") .toDate))

(defn- sessions-contain-id? [exam-sessions id]
  (-> (map ::os/id exam-sessions)
      set
      (contains? id)))

(defn reg-list-panel [pre-selected-session-id]
  (let [exam-sessions (re-frame/subscribe [:exam-sessions])]
    (cond
      (empty? @exam-sessions) (re-frame/dispatch [:load-exam-sessions default-start-date])
      (nil? pre-selected-session-id) (redirect-to-session-path (-> @exam-sessions first ::os/id))
      ; We might have a link here with a session id that's not included in session from default start date,
      ; so as a last resort we load all sessions ever
      (not (sessions-contain-id? @exam-sessions pre-selected-session-id)) (re-frame/dispatch [:load-exam-sessions])
      :else (do (re-frame/dispatch [:load-registrations pre-selected-session-id])
                [panel @exam-sessions pre-selected-session-id]))))
