(ns oti.ui.exam-sessions.session-list
  (:require [oti.ui.exam-sessions.handlers]
            [oti.ui.exam-sessions.subs]
            [oti.ui.exam-sessions.utils :refer [unparse-date]]
            [oti.ui.views.common :refer [small-loader]]
            [oti.spec :as spec]
            [re-frame.core :as re-frame]
            [oti.routing :as routing]
            [oti.ui.i18n :as i18n]
            [cljs-pikaday.reagent :as pikaday]
            [reagent.core :as r]
            [oti.ui.exam-sessions.reporting :as reporting]))

(defn- exam-session-table [exam-sessions]
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
                          other-location-info max-participants registration-count]} exam-sessions]
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
                [:span registration-count])]]))]])

(def pikaday-opts
  {:pikaday-attrs {:format   "D.M.YYYY"
                   :i18n     i18n/pikaday-i18n
                   :firstDay 1}
   :input-attrs   {:type "text"
                   :id   "pikaday-input"}})

(defn exam-sessions-panel []
  (let [exam-sessions (re-frame/subscribe [:exam-sessions])
        loading?      (re-frame/subscribe [:loading?])
        default-start (-> (js/moment.) (.subtract 2 "days") (.startOf "day") .toDate)
        start-date    (r/atom default-start)
        end-date      (r/atom nil)]
    (re-frame/dispatch [:load-exam-sessions @start-date @end-date])
    (fn []
      [:div
       [:h2 "Tutkintotapahtumat"]
       [:div.exam-session-dates
        [:div.label "Tapahtuma-aika"]
        [:div.pikaday-input
         [pikaday/date-selector (assoc pikaday-opts :date-atom start-date)]
         [:i.icon-calendar.date-picker-icon]]
        [:div.dash "\u2014"]
        [:div.pikaday-input
         [pikaday/date-selector (assoc pikaday-opts :date-atom end-date)]
         [:i.icon-calendar.date-picker-icon]]]
       [:div.buttons
        [:div.left
         [:button {:on-click (fn [_]
                               (reset! start-date nil)
                               (reset! end-date nil)
                               true)}
          "Tyhjennä"]
         [:button.button-primary {:on-click (fn [_]
                                              (re-frame/dispatch [:load-exam-sessions @start-date @end-date]))}
          "Hae"]]
        [:div.right
         [:a.button {:href (routing/v-route "/tutkintotapahtuma")} "Lisää uusi tapahtuma"]]]
       [:div.exam-sessions
        (cond
          (or @loading? (nil? @exam-sessions)) [small-loader]
          (seq @exam-sessions) [exam-session-table @exam-sessions]
          :else [:div.no-results "Ei tutkintotapahtumia aikavälillä"])]
       [reporting/reporting]])))
