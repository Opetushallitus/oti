(ns oti.ui.registration.views.registration-result
  (:require [oti.ui.i18n :refer [t]]
            [re-frame.core :as rf]))

(defn result-panel [participant-data]
  (let [lang (rf/subscribe [:language])]
    (fn [participant-data]
      [:div
       [:h3 (:registration-message participant-data)]
       [:div.buttons
        [:div.left
         [:a.button {:href (str "/oti/abort?lang=" (name @lang))} (t "back-to-start" "Palaa alkuun")]]]])))
