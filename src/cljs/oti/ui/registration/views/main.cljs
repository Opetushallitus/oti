(ns oti.ui.registration.views.main
  (:require [oti.ui.registration.handlers]
            [oti.ui.registration.subs]
            [oti.ui.registration.views.authentication :as av]
            [oti.ui.registration.views.registration :as rv]
            [oti.ui.registration.views.registration-result :as rrv]
            [oti.ui.exam-sessions.utils :as utils]
            [oti.ui.views.common :refer [loader]]
            [re-frame.core :as re-frame]
            [oti.routing :as routing]
            [oti.ui.i18n :refer [t]]))

(defn navigation-panel []
  [:nav#nav-holder
   [:ul#main-nav
    [:li.divider]
    [:li.active
     [:span (t "enrollment" "Ilmoittautuminen")]]
    [:li.divider]]])

(defn parse-session-id []
  (let [path (-> js/window .-location .-pathname)]
    (->> (re-matches (re-pattern (str "(" routing/participant-root "|" routing/participant-sv-root ")/(\\d+)")) path)
         (last)
         (utils/parse-int))))

(defn main-panel []
  (let [flash-message (re-frame/subscribe [:flash-message])
        current-language (re-frame/subscribe [:language])
        participant-data (re-frame/subscribe [:participant-data])
        loading? (re-frame/subscribe [:loading?])]
    (fn []
      [:div
       [loader @loading?]
       [:div#header
        [:img {:src (routing/img "opetushallitus.gif")}]
        [:p (t "registration-title")]
        [:a {:href "#" :on-click (fn [e]
                                   (.preventDefault e)
                                   (re-frame/dispatch [:set-language (if (= @current-language :fi) :sv :fi)]))}
         (t "switch-language")]]
       [navigation-panel]
       [:div#content-area
        [:main.container
         (cond
           (empty? @participant-data) [av/authentication-panel]
           (:registration-status @participant-data) [rrv/result-panel @participant-data]
           (seq @participant-data) [rv/registration-panel @participant-data (parse-session-id)])]]
       (when (seq @flash-message)
         (let [{:keys [type text]} @flash-message]
           [:div.flash-message
            [:span.icon {:class (if (= :success type) "success" "error")}
             (if (= :success type)
               "\u2713"
               "\u26A0")]
            [:span.text text]]))])))
