(ns oti.ui.exam-registrations.registration-list
  (:require [re-frame.core :as re-frame]
            [reagent.core :as r]
            [oti.spec :as os]
            [oti.ui.exam-sessions.utils :refer [unparse-date]]
            [oti.ui.exam-registrations.handlers]
            [oti.ui.exam-registrations.subs]
            [oti.exam-rules :as rules]
            [clojure.string :as str]))

(defn- panel [exam-sessions pre-selected-session-id]
  (let [session-id (r/atom pre-selected-session-id)
        registration-data (re-frame/subscribe [:registrations])]
    (fn [exam-sessions pre-selected-session-id]
      [:div [:h2 "Ilmoittautumiset"]
       [:div.exam-session-selection
        [:label {:for "exam-session-select"} "Tapahtuma:"]
        [:select#exam-session-select
         {:value @session-id
          :on-change (fn [e]
                       (let [new-id (-> e .-target .-value)]
                         (reset! session-id new-id)
                         (re-frame/dispatch [:load-registrations new-id])))}
         (doall
           (for [{::os/keys [id street-address city other-location-info session-date start-time end-time]} exam-sessions]
             [:option {:value id :key id}
              (str (unparse-date session-date) " " start-time " - " end-time " "
                   (:fi city) ", " (:fi street-address) ", " (:fi other-location-info))]))]]

       [:div.registrations
        (let [{:keys [registrations translations]} (get @registration-data @session-id)]
          (if (seq registrations)
            [:table
             [:thead
              [:tr
               [:th "Nimi"]
               (doall
                 (for [[_ name] (:sections translations)]
                   [:th {:key name} (str "Koe " name)]))
               [:th "Kokeen kieli"]]]
             [:tbody
              (for [reg registrations]
                [:tr {:key (:id reg)}
                 [:td (:external-user-id reg)]
                 (let [sections (:sections reg)]
                   (doall
                     (for [[id _] (:sections translations)]
                       [:td {:key id}
                        (if-let [modules (get sections id)]
                          (let [rules (rules/rules-by-section-id id)]
                            (if (or (:can-retry-partially? rules) (:can-accredit-partially? rules))
                              [:div
                               "Suorittaa osiot:"
                               [:br]
                               (->> modules (map #(get (:modules translations) %)) (str/join ", "))]
                              [:div "Suorittaa"]))
                          [:div "Ei suorita"])])))
                 [:td (if (= :sv (:lang reg))
                        "Ruotsi"
                        "Suomi")]])]]))]])))

(defn reg-list-panel [pre-selected-session-id]
  (re-frame/dispatch [:load-exam-sessions])
  (let [exam-sessions (re-frame/subscribe [:exam-sessions])]
    (when (seq @exam-sessions)
      (let [exam-session-id (or pre-selected-session-id (-> @exam-sessions first ::os/id))]
        (re-frame/dispatch [:load-registrations exam-session-id])
        [panel @exam-sessions exam-session-id]))))
