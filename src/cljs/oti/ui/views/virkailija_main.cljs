(ns oti.ui.views.virkailija-main
  (:require [re-frame.core :as re-frame]
            [oti.ui.exam-sessions.session-list :refer [exam-sessions-panel]]
            [oti.ui.exam-sessions.exam-session :refer [new-exam-session edit-exam-session]]
            [oti.ui.exam-registrations.registration-list :as registration-list]
            [oti.ui.participants.views.search :as search]
            [oti.ui.participants.views.details :as details]
            [oti.ui.routes :refer [virkailija-routes]]
            [oti.ui.views.common :refer [flash-message]]
            [oti.routing :as routing]))

(defmulti panels identity)
(defmethod panels :exam-sessions-panel []  exam-sessions-panel)
(defmethod panels :registrations-panel [] registration-list/reg-list-panel)
(defmethod panels :new-exam-session-panel [] new-exam-session)
(defmethod panels :edit-exam-session-panel [] edit-exam-session)
(defmethod panels :participant-search-panel [] search/search-panel)
(defmethod panels :participant-details-panel [] details/participant-details-panel)
(defmethod panels :default [] :div)

(defn show-panel [panel-name data]
  [(panels panel-name) data])

(defn navigation-panel [active-page]
  [:nav#nav-holder
   (-> (reduce (fn [hiccup {:keys [view url text]}]
                  (concat hiccup [[:li.divider]
                                  [:li {:key (name view)
                                        :class (when (= active-page view) "active")}
                                   (if (= active-page view)
                                     [:span text]
                                     [:a {:href url} text])]]))
                [:ul#main-nav]
                (filter :text virkailija-routes))
        (concat [[:li.divider]])
        (vec))])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])
        active-panel-data (re-frame/subscribe [:active-panel-data])
        user (re-frame/subscribe [:user])
        flash-opts (re-frame/subscribe [:flash-message])]
    (fn []
      [:div
       [:div#header
        [:img {:src (routing/img "opetushallitus.gif")}]
        [:p "Opetushallinnon tutkintorekisteri"]
        [:div.user
         [:div (str (:given-name @user) " " (:surname @user))]
         [:div [:a.logout {:href (routing/auth-route "/logout")} "Kirjaudu ulos"]]]]
       [navigation-panel @active-panel]
       [:div#content-area
        [:main.container
         [show-panel @active-panel @active-panel-data]]]
       [flash-message @flash-opts]])))
