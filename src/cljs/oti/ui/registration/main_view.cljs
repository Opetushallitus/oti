(ns oti.ui.registration.main-view
  (:require [oti.ui.registration.handlers]
            [oti.ui.registration.subs]
            [oti.ui.registration.authentication-view :as av]
            [oti.ui.registration.registration-view :as rv]
            [re-frame.core :as re-frame]
            [oti.routing :as routing]
            [oti.ui.i18n :refer [t]]))

(defn navigation-panel []
  [:nav#nav-holder
   [:ul#main-nav
    [:li.divider]
    [:li.active
     [:span (t "Ilmoittautuminen")]]
    [:li.divider]]])

(defn main-panel []
  (re-frame/dispatch [:load-participant-data])
  (let [flash-message (re-frame/subscribe [:flash-message])
        current-language (re-frame/subscribe [:language])
        participant-data (re-frame/subscribe [:participant-data])]
    (fn []
      [:div
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
           (empty @participant-data) [av/authentication-panel]
           (seq @participant-data) [rv/registration-panel])]]
       (when (seq @flash-message)
         (let [{:keys [type text]} @flash-message]
           [:div.flash-message
            [:span.icon {:class (if (= :success type) "success" "error")}
             (if (= :success type)
               "\u2713"
               "\u26A0")]
            [:span.text text]]))])))
