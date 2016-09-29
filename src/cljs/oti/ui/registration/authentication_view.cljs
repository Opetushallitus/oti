(ns oti.ui.registration.authentication-view
  (:require [oti.ui.i18n :refer [t]]))

(defn authentication-panel []
  [:div
   [:h1 (t "Tunnistautuminen")]
   [:div "Tunnistaudu pankkitunnuksillasi tai mobiilivarmenteella."]
   [:div.buttons
    [:button.button-primary "Tunnistaudu"]]])
