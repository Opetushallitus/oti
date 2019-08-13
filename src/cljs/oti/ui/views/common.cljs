(ns oti.ui.views.common
  (:require [oti.ui.i18n :refer [t]]
            [re-frame.core :as re-frame]))

(defn loader [loading?]
  (when (or (nil? loading?) loading?)
    [:div.loader "Ladataan"]))

(defn small-loader []
  [:div.small-loader
   [:i.icon-spin3.animate-spin]])

(defn flash-message [flash-opts]
  (when (seq flash-opts)
    (let [{:keys [type text]} flash-opts]
      [:div.flash-container
       [:div.flash-message
        [:span.icon {:class (if (= :success type) "success" "error")}
         (if (= :success type)
           "\u2713"
           "\u26A0")]
        [:span.text text]]])))

(defn confirmation-dialog []
  (let [data (re-frame/subscribe [:confirmation-dialog])]
    (fn []
      (when (seq @data)
        [:div.confirmation
         [:div.dialog
          [:div.question
           (:question @data)]
          [:div.buttons
           [:div.right
            [:button
             {:on-click (fn []
                          (when-not (= (:cancel-fn @data) nil)
                            (apply (:cancel-fn @data) nil))
                          (re-frame/dispatch [:confirmation-cancel]))}
             (if (:event @data) "Peruuta" "OK")]
            (when (:event @data)
              [:button.button-danger
               {:on-click (fn []
                            (re-frame/dispatch (:event @data))
                            (re-frame/dispatch [:confirmation-cancel]))}
               (:button-text @data)])]]]
         [:div.overlay]]))))
