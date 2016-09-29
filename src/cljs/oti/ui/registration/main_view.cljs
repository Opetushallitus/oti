(ns oti.ui.registration.main-view
  (:require [oti.ui.registration.handlers]
            [oti.ui.registration.subs]
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
  (let [user (re-frame/subscribe [:user])
        flash-message (re-frame/subscribe [:flash-message])
        current-language (re-frame/subscribe [:language])]
    (fn []
      [:div
       [:div#header
        [:img {:src (routing/img "opetushallitus.gif")}]
        [:p (t "registration-title")]
        [:a {:href "#" :on-click (fn [e]
                                   (.preventDefault e)
                                   (re-frame/dispatch [:set-language (if (= @current-language :fi) :sv :fi)]))}
         (t "switch-language")]]
       [navigation-panel @user]
       [:div#content-area
        [:main.container]]
       (when (seq @flash-message)
         (let [{:keys [type text]} @flash-message]
           [:div.flash-message
            [:span.icon {:class (if (= :success type) "success" "error")}
             (if (= :success type)
               "\u2713"
               "\u26A0")]
            [:span.text text]]))])))
