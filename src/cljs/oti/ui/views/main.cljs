(ns oti.ui.views.main
  (:require [re-frame.core :as re-frame]
            [oti.ui.views.exam-sessions :refer [exam-sessions-panel]]
            [oti.ui.views.students :refer [students-panel]]
            [oti.ui.routes :refer [routes]]))

(defmulti panels identity)
(defmethod panels :exam-sessions-panel [] [exam-sessions-panel])
(defmethod panels :students-panel [] [students-panel])
(defmethod panels :default [] [:div])

(defn show-panel
  [panel-name]
  [panels panel-name])

(defn navigation-panel [active-page]
  [:nav.navbar
   [:ul.navbar-list
    (doall
      (for [{:keys [view url text]} routes]
        [:li.navbar-item {:key (name view)}
         (if (= active-page view)
           [:span text]
           [:a {:href url} text])]))]])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      [:div.container
       [:div.header
        [:div.logo
         [:img {:src "http://www.oph.fi/instancedata/prime_product_julkaisu/oph/pics/opetushallitus.gif"}]]
        [:div.text "Opetushallinnon tutkintorekisteri"]]
       [navigation-panel @active-panel]
       [:main
        [show-panel @active-panel]]])))
