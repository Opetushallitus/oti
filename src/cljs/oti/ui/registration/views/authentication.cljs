(ns oti.ui.registration.views.authentication
  (:require [oti.ui.i18n :refer [t]]
            [oti.routing :as routing]
            [re-frame.core :as rf]))

(defn cb-uri [lang]
  (let [uri (-> js/window .-location .-href js/encodeURI)]
    (if (= lang :fi)
      (clojure.string/replace uri "anmala" "ilmoittaudu")
      (clojure.string/replace uri "ilmoittaudu" "anmala"))))

(defn authentication-panel []
  (let [lang (rf/subscribe [:language])]
    (fn []
      [:div
       [:h1 (t "authentication" "Tunnistautuminen")]
       [:div (t "authenticate-bank-mobile"
                "Tunnistaudu pankkitunnuksillasi tai mobiilivarmenteella.")]
       [:div.buttons
        [:a.button.button-primary {:href (routing/p-a-route "/authenticate?callback=" (cb-uri @lang))}
         (t "authenticate" "Tunnistaudu")]]])))
