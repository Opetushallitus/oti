(ns oti.ui.views.main
  (:require [re-frame.core :as re-frame]
            [oti.ui.views.exam-sessions :refer [exam-sessions-panel]]
            [oti.ui.views.students :refer [students-panel]]))

(defmulti panels identity)
(defmethod panels :exam-sessions-panel [] [exam-sessions-panel])
(defmethod panels :students-panel [] [students-panel])
(defmethod panels :default [] [:div])

(defn show-panel
  [panel-name]
  [panels panel-name])

(defn main-panel []
  (let [active-panel (re-frame/subscribe [:active-panel])]
    (fn []
      [:div
       [:h1 "Opetushallinnon tutkintorekisteri"]
       [:main
        [show-panel @active-panel]]])))
