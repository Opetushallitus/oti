(ns oti.ui.registration.views.authentication
  (:require [oti.ui.i18n :refer [t]]
            [oti.routing :as routing]
            [re-frame.core :as rf]
            [clojure.string :as str]
            [reagent.core :as r]))

(defn cb-uri [lang]
  (let [uri (-> js/window .-location .-href js/encodeURI)]
    (if (= lang :fi)
      (str/replace uri "anmala" "ilmoittaudu")
      (str/replace uri "ilmoittaudu" "anmala"))))

(defn authentication-panel []
  (let [lang (rf/subscribe [:language])
        hetu (r/atom nil)]
    (fn []
      [:div
       [:h1 (t "authentication" "Tunnistautuminen")]
       [:div (t "authenticate-bank-mobile"
                "Tunnistaudu pankkitunnuksillasi tai mobiilivarmenteella.")]
       [:div [:input {:type "text"
                      :placeholder "Henkilötunnus (jätä tyhjäksi satunnaista varten)"
                      :style {"width" "50%" "margin" "15px 0"}
                      :on-change #(reset! hetu (-> % .-target .-value))}]]
       [:div.buttons
        [:a.button.button-primary {:href (routing/p-a-route "/authenticate?callback=" (cb-uri @lang) "&hetu=" @hetu)}
         (t "authenticate" "Tunnistaudu")]]])))
