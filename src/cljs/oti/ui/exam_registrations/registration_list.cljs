(ns oti.ui.exam-registrations.registration-list
  (:require [re-frame.core :as re-frame]
            [reagent.core :as r]
            [oti.spec :as os]
            [oti.ui.exam-sessions.utils :refer [unparse-date]]))

(defn- panel [exam-sessions pre-selected-session-id]
  (let [session-id (r/atom pre-selected-session-id)]
    (fn [exam-sessions pre-selected-session-id]
      [:div [:h2 "Ilmoittautumiset"]
       [:div.exam-session-selection
        [:label {:for "exam-session-select"} "Tapahtuma:"]
        [:select#exam-session-select
         {:value @session-id
          :on-change (fn [e]
                       (let [new-id (-> e .-target .-value)]
                         (reset! session-id new-id)
                         ))}
         (doall
           (for [{::os/keys [id street-address city other-location-info session-date start-time end-time]} exam-sessions]
             [:option {:value id :key id}
              (str (unparse-date session-date) " " start-time " - " end-time " "
                   (:fi city) ", " (:fi street-address) ", " (:fi other-location-info))]))]]])))

(defn reg-list-panel [pre-selected-session-id]
  (re-frame/dispatch [:load-exam-sessions])
  (let [exam-sessions (re-frame/subscribe [:exam-sessions])]
    (when (seq @exam-sessions)
      [panel @exam-sessions (or pre-selected-session-id (-> @exam-sessions first ::os/id))])))
