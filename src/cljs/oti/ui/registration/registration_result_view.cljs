(ns oti.ui.registration.registration-result-view
  (:require [oti.ui.i18n :refer [t]]))

(defn result-panel [participant-data]
  [:div
   [:h3 (:registration-message participant-data)]
   [:div.buttons
    [:div.left
     [:a.button {:href "/oti/abort"} (t "Palaa alkuun")]]]])
