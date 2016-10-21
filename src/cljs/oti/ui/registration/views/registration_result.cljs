(ns oti.ui.registration.views.registration-result
  (:require [oti.ui.i18n :refer [t]]
            [re-frame.core :as rf]
            [re-frame.core :as re-frame]))

(defn result-panel [participant-data]
  (let [lang (rf/subscribe [:language])]
    (fn [{:keys [registration-message registration-status]}]
      [:div
       [:h3 registration-message]
       (when (= :pending registration-status)
         [:div.guide (t "retry-payment-guide")])
       [:div.buttons
        [:a.button {:href (str "/oti/abort?lang=" (name @lang))} (t "back-to-start" "Palaa alkuun")]
        (when (= :pending registration-status)
          [:button.button-primary {:on-click #(re-frame/dispatch [:retry-payment @lang])}
           (t "retry-payment" "Yritä maksua uudelleen")])]])))
