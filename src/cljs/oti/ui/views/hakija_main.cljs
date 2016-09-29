(ns oti.ui.views.hakija-main
  (:require [re-frame.core :as re-frame]))

(defn navigation-panel []
  [:nav#nav-holder
   [:ul#main-nav
    [:li.divider]
    [:li.active
     [:span "Ilmoittautuminen"]]
    [:li.divider]]])

(defn main-panel []
  (let [user (re-frame/subscribe [:user])
        flash-message (re-frame/subscribe [:flash-message])]
    (fn []
      [:div
       [:div#header
        [:img {:src "/oti/img/opetushallitus.gif"}]
        [:p "Opetushallinnon tutkintoon ilmoittautuminen"]
        [:a {} "På svenska"]]
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
