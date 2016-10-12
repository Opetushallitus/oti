(ns oti.ui.registration.views.authentication
  (:require [oti.ui.i18n :refer [t]]
            [oti.routing :as routing]))

(defn cb-uri []
  (-> js/window .-location .-href js/encodeURI))

(defn authentication-panel []
  [:div
   [:h1 (t "authentication" "Tunnistautuminen")]
   [:div (t "authenticate-bank-mobile"
            "Tunnistaudu pankkitunnuksillasi tai mobiilivarmenteella.")]
   [:div.buttons
    [:a.button.button-primary {:href (routing/p-a-route "/authenticate?callback=" (cb-uri))} (t "authenticate" "Tunnistaudu")]]])
