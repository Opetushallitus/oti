(ns oti.ui.registration.views.registration-result
  (:require [oti.ui.i18n :refer [t]]
            [re-frame.core :as rf]))

(defn result-panel [participant-data]
  (let [lang (rf/subscribe [:language])]
    (fn [{:keys [registration-message registration-status]}]
      [:div
       [:h3 (t registration-message registration-message)]
       (when (= :pending registration-status)
         [:div.guide (t "retry-payment-guide")])
       [:div.buttons
        [:a.button {:href (str "/oti/abort?lang=" (name @lang))} (t "back-to-start" "Palaa alkuun")]
        (when (= :pending registration-status)
          [:button.button-primary {:on-click #(rf/dispatch [:retry-payment @lang])}
           (t "retry-payment" "Yritä maksua uudelleen")])]])))
