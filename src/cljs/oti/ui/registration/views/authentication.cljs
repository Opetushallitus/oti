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
        hetu (r/atom nil)
        automatic-address (r/atom true)]
    (fn []
      [:div.authentication
       [:h1 (t "authentication" "Tunnistautuminen")]
       [:div (t "authenticate-bank-mobile"
                "Tunnistaudu pankkitunnuksillasi tai mobiilivarmenteella.")]
       [:div.buttons
        [:a.button.button-primary {:href (routing/p-a-route "/authenticate?lang=" (if @lang (name @lang) "fi"))}
         (t "authenticate")]]
       [:div.dummy-auth
        [:h3 "Testitunnistautuminen"]
        [:div [:input {:type "text"
                       :placeholder "Henkilötunnus (jätä tyhjäksi satunnaista varten)"
                       :style {"width" "50%" "margin" "15px 0"}
                       :on-change #(reset! hetu (-> % .-target .-value))}]]
        [:div.automatic-address
         [:label
          [:input {:type "checkbox"
                   :checked @automatic-address
                   :on-change (fn [e]
                                (let [value (-> e .-target .-checked)]
                                  (reset! automatic-address value)
                                  true))}]
          [:span.label "Simuloi käyttäjän osoitteen saaminen tunnistautumisesta"]]]
        [:div.buttons
         [:a.button.button-primary {:href (routing/p-a-route "/dummy-authenticate?callback="
                                                             (cb-uri @lang)
                                                             "&hetu=" @hetu
                                                             "&automatic-address=" @automatic-address)}
          "Testitunnistaudu"]]]])))
