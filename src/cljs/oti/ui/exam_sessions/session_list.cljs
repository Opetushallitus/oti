(ns oti.ui.exam-sessions.session-list
  (:require [oti.ui.exam-sessions.handlers]
            [oti.ui.exam-sessions.subs]
            [oti.ui.exam-sessions.utils :refer [unparse-date]]
            [oti.spec :as spec]
            [re-frame.core :as re-frame]
            [oti.routing :as routing]))

(defn exam-sessions-panel []
  (re-frame/dispatch [:load-exam-sessions])
  (let [exam-sessions (re-frame/subscribe [:exam-sessions])]
    (fn []
      [:div
       [:h2 "Tutkintotapahtumat"]
       [:div.exam-sessions
        (when (seq @exam-sessions)
          [:table
           [:thead
            [:tr
             [:td "Päivämäärä ja aika"]
             [:td "Osoite"]
             [:td "Tilatieto"]
             [:td "Enimmäismäärä"]
             [:td "Ilmoittautuneet"]]]
           [:tbody
            (doall
              (for [{::spec/keys [id city start-time end-time session-date street-address
                                  other-location-info max-participants registration-count]} @exam-sessions]
                ^{:key id}
                [:tr
                 [:td
                  [:a {:href (routing/v-route "/tutkintotapahtuma/" id)}
                   (str (unparse-date session-date) " " start-time " - " end-time)]]
                 [:td
                  (str (:fi city) ", " (:fi street-address))
                  [:br]
                  (str (:sv city) ", " (:sv street-address))]
                 [:td
                  (:fi other-location-info)
                  [:br]
                  (:sv other-location-info)]
                 [:td max-participants]
                 [:td (if (pos? registration-count)
                        [:a {:href (routing/v-route "/ilmoittautumiset/" id)} registration-count]
                        [:span registration-count])]]))]])]
       [:div.buttons
        [:div.right
         [:a.button {:href (routing/v-route "/tutkintotapahtuma")} "Lisää uusi"]]]])))
