(ns oti.ui.views.virkailija-main
  (:require [re-frame.core :as re-frame]
            [oti.ui.exam-sessions.session-list :refer [exam-sessions-panel]]
            [oti.ui.exam-sessions.exam-session :refer [new-exam-session edit-exam-session]]
            [oti.ui.views.students :refer [students-panel]]
            [oti.ui.routes :refer [virkailija-routes]]
            [oti.routing :as routing]))

(defmulti panels identity)
(defmethod panels :exam-sessions-panel [] [exam-sessions-panel])
(defmethod panels :students-panel [] [students-panel])
(defmethod panels :new-exam-session-panel [] [new-exam-session])
(defmethod panels :edit-exam-session-panel [] [edit-exam-session])
(defmethod panels :default [] [:div])

(defn show-panel
  [panel-name]
  [panels panel-name])

(defn navigation-panel [active-page user]
  [:nav#nav-holder
   (->
        (reduce (fn [hiccup {:keys [view url text]}]
                  (concat hiccup [[:li.divider]
                                  [:li {:key (name view)
                                        :class (when (= active-page view) "active")}
                                   (if (= active-page view)
                                     [:span text]
                                     [:a {:href url} text])]]))
                [:ul#main-nav]
                (filter :text virkailija-routes))
        (concat [[:li.user
                  [:span (:username user)]
                  [:br]
                  [:a.logout {:href (routing/auth-route "/logoout")} "Kirjaudu ulos"]]])
        (vec))])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])
        user (re-frame/subscribe [:user])
        flash-message (re-frame/subscribe [:flash-message])]
    (fn []
      [:div
       [:div#header
        [:img {:src (routing/img "opetushallitus.gif")}]
        [:p "Opetushallinnon tutkintorekisteri"]
        [:a {} "På svenska"]]
       [navigation-panel @active-panel @user]
       [:div#content-area
        [:main.container
         [show-panel @active-panel]]]
       (when (seq @flash-message)
         (let [{:keys [type text]} @flash-message]
           [:div.flash-message
            [:span.icon {:class (if (= :success type) "success" "error")}
             (if (= :success type)
               "\u2713"
               "\u26A0")]
            [:span.text text]]))])))
